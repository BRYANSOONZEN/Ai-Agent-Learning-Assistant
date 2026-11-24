<script setup>
import { withToken } from '@/api/request'
/*
 * Student page: learn one course, laid out like Studywise's 学习与提问 page.
 * Top: where we are, then the open chapter as a stage rail (intro + items, done ones in teal).
 * Left: the open item — video, document, exercise (LearnQuiz) or AI dialogue (AiDialogue),
 *   with 上一项 / 下一项 at the bottom.
 * Right: a progress ring for the course, then the outline — published chapters, each opening into its
 *   items in learning order (the same structure the teacher built in CourseBuilder).
 * Items unlock in order: item k+1 opens once item k is finished (the first item of a chapter is always open).
 *   Video: watched ≥ 90% (VideoPlayer). Document: 30 s of reading + 我已读完 (ReadingTimer). The server checks too.
 * What is open lives in the URL (?chapter=1&view=item:2), so a refresh keeps it.
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AiDialogue from '@/components/AiDialogue.vue'
import FeynmanChat from '@/components/FeynmanChat.vue'
import LearnQuiz from './LearnQuiz.vue'
import ReadingTimer from './ReadingTimer.vue'
import VideoPlayer from './VideoPlayer.vue'
import { getChapterKpMap, getFeynman, getLearnCourse, logEvent } from '@/api/learn'
import KpCircles from '@/components/KpCircles.vue'
import { itemType } from '@/views/teacher/courseModules'
import { markDone, saveLast, syncDone } from './learnProgress'
import { dueInfo, fmtDue } from '@/utils/deadline'

const route = useRoute()
const router = useRouter()
const courseId = computed(() => Number(route.params.id))

const data = ref(null) // { course, chapters }
const loading = ref(false)
const done = ref([]) // finished item ids
const expanded = ref([])

const chapters = computed(() => data.value?.chapters || [])
const allItems = computed(() => chapters.value.flatMap((ch) => ch.items))
const progress = computed(() => ({
  done: allItems.value.filter((it) => done.value.includes(it.id)).length,
  total: allItems.value.length,
}))
const pct = computed(() => (progress.value.total ? Math.round((progress.value.done / progress.value.total) * 100) : 0))

// ---------- what is open ----------

const chapter = computed(
  () => chapters.value.find((c) => c.id === Number(route.query.chapter)) || chapters.value[0] || null,
)
const view = computed(() => route.query.view || 'intro') // 'intro' | 'item:<id>'
const item = computed(() =>
  view.value.startsWith('item:') ? chapter.value?.items.find((it) => 'item:' + it.id === view.value) : null,
)

// deadline of the open item (exercise / AI dialogue only), as this student sees it
const due = computed(() =>
  item.value?.dueAt ? dueInfo(item.value.dueAt, item.value.allowLate, done.value.includes(item.value.id)) : null,
)
const ALERT = { done: 'info', overdue: 'warning', soon: 'warning', later: 'info' }

// server progress of videos / documents: itemId -> { ranges, lastPosition, readSec, completed, ... }
const progressMap = computed(() => data.value?.progress || {})

// locked = not the first item of its chapter, the item before it is not finished, and it is not finished itself
// (the same rule as the server's CompletionService, so finishing an item unlocks the next one at once)
function lockedBy(ch, it) {
  const i = ch?.items.indexOf(it) ?? -1
  if (i <= 0) return null
  const prev = ch.items[i - 1]
  return !done.value.includes(prev.id) && !done.value.includes(it.id) ? prev : null
}
const lockPrev = computed(() => (item.value ? lockedBy(chapter.value, item.value) : null))

const chapterNo = computed(() => chapters.value.indexOf(chapter.value) + 1)
const chapterDone = computed(() => (chapter.value?.items || []).filter((it) => done.value.includes(it.id)).length)

// the open chapter as a stage rail: intro, then every item
const rail = computed(() => {
  const ch = chapter.value
  if (!ch) return []
  return [
    { v: 'intro', title: '章节介绍', kind: '先看目标', done: ch.items.some((it) => done.value.includes(it.id)) },
    ...ch.items.map((it) => ({
      v: 'item:' + it.id,
      title: it.title,
      kind: itemType(it.type)?.label,
      done: done.value.includes(it.id),
      locked: !!lockedBy(ch, it),
    })),
  ]
})

function open(ch, v = 'intro', extra = {}) {
  router.replace({ query: { chapter: ch.id, view: v, ...extra } })
  window.scrollTo({ top: 0 })
}

// 知识点地图 (only what the teacher confirmed): clicking a time opens that video there
const kpMap = ref(null)
watch(
  () => chapter.value?.id,
  async (id) => {
    kpMap.value = null
    if (!id) return
    try {
      kpMap.value = await getChapterKpMap(id)
    } catch {
      /* no map: the chapter page works without it */
    }
  },
  { immediate: true },
)
function jumpTo({ itemId, startSec }) {
  open(chapter.value, 'item:' + itemId, startSec != null ? { t: startSec } : {})
}
// an AI 对话 item uses the 费曼对话 (explain to 小林) when the teacher has set one up; otherwise the old dialogue
const feynmanOn = ref(null) // null = still checking
watch(
  () => item.value,
  async (it) => {
    feynmanOn.value = null
    if (it?.type !== 'dialogue') return
    try {
      feynmanOn.value = (await getFeynman(it.id)).enabled
    } catch {
      feynmanOn.value = false
    }
  },
  { immediate: true },
)

// the circles grey out shortcuts to items the student has not unlocked yet
function itemLocked(itemId) {
  const it = chapter.value?.items.find((x) => x.id === itemId)
  return !!(it && lockedBy(chapter.value, it))
}

// every stop in learning order across the course: each chapter's intro, then its items
const stops = computed(() =>
  chapters.value.flatMap((ch) => [{ ch, v: 'intro' }, ...ch.items.map((it) => ({ ch, v: 'item:' + it.id }))]),
)
const here = computed(() => stops.value.findIndex((s) => s.ch.id === chapter.value?.id && s.v === view.value))
const prev = computed(() => stops.value[here.value - 1] || null)
const next = computed(() => stops.value[here.value + 1] || null)
function stopLabel(s) {
  if (s.v === 'intro') return s.ch.title
  return s.ch.items.find((it) => 'item:' + it.id === s.v)?.title
}

watch(
  chapter,
  (ch) => {
    if (ch && !expanded.value.includes(ch.id)) expanded.value = [...expanded.value, ch.id]
  },
  { immediate: true },
)

function toggle(ch) {
  expanded.value = expanded.value.includes(ch.id)
    ? expanded.value.filter((id) => id !== ch.id)
    : [...expanded.value, ch.id]
}

// ---------- progress ----------

// videos and documents are judged finished by the server (log = false); exercises / dialogues log it here
function finish(it, log = true) {
  done.value = markDone(courseId.value, it.id, log)
}

// remember where the student is, for "接着上次学习" on 首页
watch([chapter, view, progress], () => {
  if (!data.value || !chapter.value) return
  saveLast({
    courseId: courseId.value,
    courseName: data.value.course.name,
    chapterId: chapter.value.id,
    chapterTitle: chapter.value.title,
    view: view.value,
    title: item.value?.title || '章节介绍',
    done: progress.value.done,
    total: progress.value.total,
  })
})

// finished: video watched ≥ 90%, document read 30 s + 我已读完, exercise submitted, AI dialogue all topics done

// ---------- study time (学习记录): how long each item stays open while the tab is visible ----------

let viewing = null // { itemId, since }

function stopViewing() {
  if (!viewing) return
  const sec = Math.round((Date.now() - viewing.since) / 1000)
  if (sec >= 5) logEvent('view_item', viewing.itemId, sec).catch(() => {})
  viewing = null
}

function startViewing() {
  stopViewing()
  if (item.value && !document.hidden) viewing = { itemId: item.value.id, since: Date.now() }
}

function onVisibility() {
  if (document.hidden) stopViewing()
  else startViewing()
}

watch(item, startViewing)
document.addEventListener('visibilitychange', onVisibility)
onBeforeUnmount(() => {
  stopViewing()
  document.removeEventListener('visibilitychange', onVisibility)
})

function fileKind(m) {
  return m?.kind // video / pdf / image / text / office / other
}

async function load() {
  loading.value = true
  try {
    data.value = await getLearnCourse(courseId.value)
    done.value = syncDone(courseId.value, data.value.doneItemIds)
  } catch {
    router.replace({ name: 'courses' }) // not published or not found: the error is already shown
  } finally {
    loading.value = false
  }
}

watch(courseId, load)
onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <template v-if="data">
      <!-- page intro (Studywise): where we are, then the chapter as a stage rail -->
      <div class="intro">
        <div>
          <span class="sw-eyebrow">{{ data.course.name }} · CHAPTER {{ String(chapterNo).padStart(2, '0') }}</span>
          <h2>{{ item ? item.title : chapter?.title || data.course.name }}</h2>
          <p>{{ item ? chapter?.title : data.course.description }}</p>
        </div>
        <router-link :to="{ name: 'courses' }" class="outline-button">
          <el-icon><Grid /></el-icon>全部课程
        </router-link>
      </div>

      <el-empty v-if="!chapters.length" class="app-card" description="老师还没有发布章节" />

      <div v-else class="learning-layout">
        <div class="learning-main">
          <ol v-if="chapter" class="stage-rail" :aria-label="chapter.title">
            <li
              v-for="(s, i) in rail"
              :key="s.v"
              class="stage-node"
              :class="{ passed: s.done, active: s.v === view }"
              @click="open(chapter, s.v)"
            >
              <span class="stage-circle">
                <el-icon v-if="s.done && s.v !== view"><Check /></el-icon>
                <el-icon v-else-if="s.locked && s.v !== view"><Lock /></el-icon>
                <template v-else>{{ String(i + 1).padStart(2, '0') }}</template>
              </span>
              <span class="stage-text">
                <strong>{{ s.title }}</strong>
                <small>{{ s.kind }}</small>
              </span>
            </li>
          </ol>

          <!-- content -->
          <section v-if="chapter" class="app-card content">
            <div class="concept-header">
              <span class="concept-label">
                {{ view === 'intro' ? 'CHAPTER INTRO' : (item?.type || '').toUpperCase() }}
              </span>
              <span class="soft-tag">{{ view === 'intro' ? '章节介绍' : itemType(item?.type)?.label }}</span>
            </div>

            <!-- chapter intro: what this chapter is, and what is in it -->
            <template v-if="view === 'intro'">
              <h3>{{ chapter.title }}</h3>
              <p class="pre">{{ chapter.intro || '（老师没有写章节介绍）' }}</p>
              <template v-if="kpMap?.rows.length">
                <h4 class="sub">本章 {{ kpMap.rows.length }} 个知识点</h4>
                <KpCircles :map="kpMap" :is-locked="itemLocked" @jump="jumpTo" />
              </template>
              <h4 class="sub">本章内容</h4>
              <ol class="path">
                <li v-for="(it, i) in chapter.items" :key="it.id" @click="open(chapter, 'item:' + it.id)">
                  <span class="source-id">{{ String(i + 1).padStart(2, '0') }}</span>
                  <span class="path-main">
                    <strong>{{ it.title }}</strong>
                    <small>{{ itemType(it.type)?.label }}</small>
                  </span>
                  <span
                    v-if="it.dueAt && !done.includes(it.id)"
                    class="sw-tag"
                    :class="dueInfo(it.dueAt, it.allowLate).tone"
                  >
                    {{ dueInfo(it.dueAt, it.allowLate).text }}
                  </span>
                  <span v-if="lockedBy(chapter, it)" class="sw-tag gray">
                    <el-icon><Lock /></el-icon>未解锁
                  </span>
                  <span v-else class="sw-tag" :class="done.includes(it.id) ? 'teal' : 'gray'">
                    {{ done.includes(it.id) ? '已学完' : '未学' }}
                  </span>
                </li>
              </ol>
            </template>

            <template v-else-if="item">
              <h3>{{ item.title }}</h3>
              <el-alert
                v-if="due"
                :type="due.blocked ? 'error' : ALERT[due.state]"
                :closable="false"
                show-icon
                class="due-alert"
                :title="
                  due.state === 'done'
                    ? `截止时间 ${fmtDue(item.dueAt)}，你已经完成了`
                    : due.state === 'overdue'
                      ? `截止时间 ${fmtDue(item.dueAt)} 已过。${due.blocked ? '老师设置了过期后不能再交。' : '还可以补做，会记为迟交。'}`
                      : `截止时间 ${fmtDue(item.dueAt)}，${due.text}`
                "
              />
              <p v-if="item.description && item.type !== 'document'" class="pre lead">{{ item.description }}</p>

              <!-- locked: the item before it is not finished yet -->
              <div v-if="lockPrev" class="locked">
                <el-icon :size="34"><Lock /></el-icon>
                <strong>这一项还没解锁</strong>
                <p>先完成上一项：《{{ lockPrev.title }}》</p>
                <el-button type="primary" @click="open(chapter, 'item:' + lockPrev.id)">去完成</el-button>
              </div>

              <!-- video: watched ≥ 90% = finished -->
              <template v-else-if="item.type === 'video'">
                <VideoPlayer
                  v-if="item.material"
                  :key="item.id"
                  :item-id="item.id"
                  :src="withToken(item.material.url)"
                  :progress="progressMap[item.id]"
                  :start-at="route.query.t != null ? Number(route.query.t) : null"
                  @completed="finish(item, false)"
                />
                <el-empty v-else description="老师还没有上传视频" />
              </template>

              <!-- document: reading text and / or a file -->
              <template v-else-if="item.type === 'document'">
                <div v-if="item.description" class="reading pre">{{ item.description }}</div>
                <template v-if="item.material">
                  <iframe
                    v-if="['pdf', 'text'].includes(fileKind(item.material))"
                    :key="item.id"
                    :src="withToken(item.material.url)"
                    class="doc-frame"
                    :title="item.material.originalName"
                  />
                  <img
                    v-else-if="fileKind(item.material) === 'image'"
                    :src="withToken(item.material.url)"
                    :alt="item.title"
                    class="doc-image"
                  />
                  <div class="file">
                    <el-icon><Document /></el-icon>
                    <el-link :href="withToken(item.material.url)" target="_blank" type="primary">
                      {{ item.material.originalName }}
                    </el-link>
                    <span class="muted">在新窗口打开</span>
                  </div>
                </template>
                <!-- read ≥ 30 s, then 我已读完 = finished -->
                <ReadingTimer
                  :key="item.id"
                  :item-id="item.id"
                  :progress="progressMap[item.id]"
                  @completed="finish(item, false)"
                />
              </template>

              <!-- exercise -->
              <el-empty
                v-else-if="item.type === 'exercise' && due?.blocked"
                description="已经过了截止时间，这次练习不能再交了"
              />
              <LearnQuiz
                v-else-if="item.type === 'exercise'"
                :key="item.id"
                :item-id="item.id"
                @done="finish(item)"
              />

              <!-- AI dialogue: the same component as the AI 巩固对话 page, only this item's topics -->
              <FeynmanChat
                v-else-if="item.type === 'dialogue' && feynmanOn"
                :key="'f' + item.id"
                :item-id="item.id"
                @finished="finish(item, false)"
              />
              <AiDialogue
                v-else-if="item.type === 'dialogue' && feynmanOn === false"
                :key="item.id"
                :chapter-id="chapter.id"
                :item-id="item.id"
                compact
                @finished="finish(item)"
              />
            </template>
            <el-empty v-else description="这一项不存在了" />

            <div class="nav">
              <el-button v-if="prev" @click="open(prev.ch, prev.v)">
                <el-icon class="el-icon--left"><ArrowLeft /></el-icon>{{ stopLabel(prev) }}
              </el-button>
              <span v-else />
              <el-button v-if="next" type="primary" @click="open(next.ch, next.v)">
                下一项：{{ stopLabel(next) }}<el-icon class="el-icon--right"><ArrowRight /></el-icon>
              </el-button>
              <span v-else class="sw-tag teal">本课程已经学到最后一项了</span>
            </div>
          </section>
        </div>

        <!-- right column: progress ring + the outline -->
        <aside class="learning-sidebar">
          <section class="app-card side-card">
            <div class="side-title">本课学习</div>
            <div class="session-progress">
              <span class="progress-ring" :style="{ '--p': pct + '%' }">
                <span>{{ pct }}<small>%</small></span>
              </span>
              <div>
                <strong>学习进度</strong>
                <p>已学 {{ progress.done }} / {{ progress.total }} 项</p>
                <span class="small-muted">{{ data.course.teacher }} 授课</span>
              </div>
            </div>
            <div class="session-divider" />
            <div class="session-row">
              <span>本章</span>
              <strong>{{ chapterDone }} / {{ chapter?.items.length || 0 }} 项</strong>
            </div>
            <div class="session-row">
              <span>章节</span>
              <strong>{{ chapters.length }} 章</strong>
            </div>
          </section>

          <section class="app-card side-card outline">
            <div class="side-title">
              课程大纲
              <span class="source-count">{{ chapters.length }}</span>
            </div>
            <div v-for="(ch, i) in chapters" :key="ch.id" class="chapter-group">
              <button type="button" class="chapter" :class="{ active: ch.id === chapter?.id }" @click="toggle(ch)">
                <span class="source-id">{{ String(i + 1).padStart(2, '0') }}</span>
                <span class="chapter-body">
                  <strong>{{ ch.title }}</strong>
                  <small>{{ ch.items.filter((it) => done.includes(it.id)).length }} / {{ ch.items.length }} 项</small>
                </span>
                <el-icon class="chevron" :class="{ open: expanded.includes(ch.id) }"><ArrowRight /></el-icon>
              </button>
              <ul v-if="expanded.includes(ch.id)" class="items">
                <li
                  class="item"
                  :class="{ active: ch.id === chapter?.id && view === 'intro' }"
                  @click="open(ch, 'intro')"
                >
                  <el-icon class="item-icon"><Memo /></el-icon>
                  <span class="item-label">章节介绍</span>
                </li>
                <li
                  v-for="it in ch.items"
                  :key="it.id"
                  class="item"
                  :class="{ active: ch.id === chapter?.id && view === 'item:' + it.id }"
                  @click="open(ch, 'item:' + it.id)"
                >
                  <el-icon class="item-icon"><component :is="itemType(it.type)?.icon" /></el-icon>
                  <span class="item-label">{{ it.title }}</span>
                  <el-icon v-if="done.includes(it.id)" class="item-state ok"><CircleCheckFilled /></el-icon>
                  <el-icon v-else-if="lockedBy(ch, it)" class="item-state lock"><Lock /></el-icon>
                  <span v-else class="item-state" />
                </li>
              </ul>
            </div>
          </section>
        </aside>
      </div>
    </template>
  </div>
</template>

<style scoped>
/* ---------- page intro ---------- */
.intro {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 16px;
  margin: 7px 0 20px;
}
.intro h2 {
  margin: 9px 0 5px;
  font-size: 25px;
  letter-spacing: -0.6px;
}
.intro p {
  margin: 0;
  color: var(--app-text-muted);
  font-size: 13px;
}
.outline-button {
  flex: none;
  min-height: 36px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 0 12px;
  border: 1px solid #dce5ed;
  border-radius: 8px;
  background: #fff;
  color: #60758d;
  font-size: 12px;
  text-decoration: none;
}
.outline-button:hover {
  color: var(--app-primary);
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

/* ---------- layout ---------- */
.learning-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 22px;
  align-items: start;
}
.learning-main {
  min-width: 0;
}
.learning-sidebar {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 15px;
  position: sticky;
  top: 12px;
}
.learning-sidebar .app-card {
  margin-top: 0;
}

/* ---------- stage rail ---------- */
.stage-rail {
  display: flex;
  margin: 0 0 18px;
  padding: 0 7px 18px;
  border-bottom: 1px solid var(--app-border);
  list-style: none;
  overflow-x: auto;
}
.stage-node {
  position: relative;
  flex: 1;
  min-width: 110px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: #a6b3bf;
  cursor: pointer;
}
.stage-node:not(:last-child)::after {
  content: '';
  position: absolute;
  top: 14px;
  left: 36px;
  right: 8px;
  height: 1px;
  background: #dce5ed;
}
.stage-node.passed::after {
  background: #96d5cb;
}
.stage-circle {
  position: relative;
  z-index: 1;
  flex: 0 0 28px;
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border: 3px solid #f5f8fb;
  border-radius: 50%;
  background: #eaf0f5;
  color: #94a3b3;
  font-size: 10px;
  font-weight: 700;
}
.stage-text {
  position: relative;
  z-index: 1;
  min-width: 0;
  padding-right: 6px;
  background: var(--app-bg);
}
.stage-text strong,
.stage-text small {
  display: block;
  max-width: 120px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.stage-text strong {
  font-size: 11px;
  font-weight: 600;
}
.stage-text small {
  margin-top: 2px;
  color: #b3bec9;
  font-size: 10px;
}
.stage-node.passed {
  color: #2f9a88;
}
.stage-node.passed .stage-circle {
  background: #6fc6b6;
  color: #fff;
}
.stage-node.active {
  color: #5c6fe0;
}
.stage-node.active .stage-circle {
  background: #7180ef;
  color: #fff;
  box-shadow: 0 0 0 4px #e5e8ff;
}

/* ---------- content card ---------- */
.content {
  min-height: 420px;
  padding: 22px 24px 18px;
}
.concept-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.concept-label {
  color: #8494a6;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.7px;
}
.soft-tag {
  padding: 5px 8px;
  border-radius: 5px;
  background: #eff1ff;
  color: #6978dc;
  font-size: 11px;
}
.content h3 {
  margin: 14px 0 10px;
  font-size: 19px;
  letter-spacing: -0.3px;
}
.sub {
  margin: 24px 0 10px;
  color: #7f8e9e;
  font-size: 12px;
  font-weight: 700;
}
.pre {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.8;
}
.lead {
  margin-bottom: 14px;
  color: var(--el-text-color-regular);
}
.path {
  margin: 0;
  padding: 0;
  list-style: none;
}
.path li {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 6px;
  border-bottom: 1px solid #edf1f5;
  cursor: pointer;
}
.path li:hover {
  background: #f8faff;
}
.path-main {
  flex: 1;
  min-width: 0;
  display: grid;
  gap: 2px;
}
.path-main strong {
  font-size: 13px;
}
.path-main small {
  color: #a4afbb;
  font-size: 11px;
}
.source-id {
  flex: none;
  min-width: 24px;
  height: 22px;
  display: grid;
  place-items: center;
  border-radius: 5px;
  background: #eef0ff;
  color: #6e80e7;
  font-size: 10px;
  font-weight: 700;
}
.locked {
  display: grid;
  justify-items: center;
  gap: 6px;
  padding: 36px 16px;
  border: 1px dashed var(--el-border-color);
  border-radius: 10px;
  color: var(--el-text-color-secondary);
  text-align: center;
}
.locked strong {
  color: var(--el-text-color-primary);
}
.locked p {
  margin: 0 0 6px;
}
.reading {
  max-width: 760px;
  padding: 16px 18px;
  border-radius: 10px;
  background: #f5f8fb;
  font-size: 15px;
}
.doc-frame {
  width: 100%;
  height: 70vh;
  margin-top: 14px;
  border: 1px solid #e3eaf1;
  border-radius: 10px;
}
.doc-image {
  max-width: 100%;
  margin-top: 14px;
  border-radius: 10px;
}
.file {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
}
.nav {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-top: 28px;
  padding-top: 16px;
  border-top: 1px solid #edf1f5;
}
/* long item titles: let the buttons wrap instead of pushing the page wider */
.nav :deep(.el-button) {
  max-width: 100%;
  height: auto;
  min-height: 32px;
  margin-left: 0;
  white-space: normal;
  text-align: left;
  line-height: 1.4;
}
@media (max-width: 600px) {
  .nav {
    flex-direction: column;
    align-items: stretch;
  }
}

/* ---------- right column ---------- */
.side-card {
  padding: 18px;
}
.side-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: #7f8e9e;
  font-size: 12px;
  font-weight: 700;
}
.source-count {
  padding: 3px 8px;
  border-radius: 10px;
  background: #e7f7f3;
  color: #2e8b7f;
  font-size: 11px;
}
.session-progress {
  display: flex;
  align-items: center;
  gap: 13px;
  margin: 20px 0 16px;
}
.progress-ring {
  flex: none;
  width: 70px;
  height: 70px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: radial-gradient(#fff 57%, transparent 59%), conic-gradient(#7686f2 var(--p), #edf0f5 0);
  color: #6473d8;
}
.progress-ring span {
  font-size: 19px;
  font-weight: 700;
}
.progress-ring small {
  font-size: 10px;
}
.session-progress strong {
  display: block;
  font-size: 13px;
}
.session-progress p {
  margin: 5px 0 6px;
  color: #718397;
  font-size: 12px;
}
.small-muted {
  color: #aab5c0;
  font-size: 11px;
}
.session-divider {
  height: 1px;
  background: #edf1f5;
}
.session-row {
  display: flex;
  justify-content: space-between;
  margin-top: 12px;
  color: #95a2af;
  font-size: 12px;
}
.session-row strong {
  color: #526c8b;
}

/* outline in the right column */
.outline .chapter-group:first-of-type {
  margin-top: 10px;
}
.chapter {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 11px 4px;
  border: 0;
  border-bottom: 1px solid #edf1f5;
  background: none;
  font: inherit;
  color: inherit;
  text-align: left;
  cursor: pointer;
}
.chapter-body {
  flex: 1;
  min-width: 0;
  display: grid;
  gap: 2px;
}
.chapter-body strong {
  font-size: 12px;
  line-height: 1.4;
}
.chapter.active .chapter-body strong {
  color: #5369db;
}
.chapter-body small {
  color: #a4afbb;
  font-size: 11px;
}
.chevron {
  flex: none;
  font-size: 12px;
  color: #a4afbb;
  transition: transform 0.15s;
}
.chevron.open {
  transform: rotate(90deg);
}
.items {
  margin: 6px 0 8px;
  padding: 0;
  list-style: none;
}
.item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 7px;
  color: #5f7189;
  font-size: 12px;
  cursor: pointer;
}
.item:hover {
  background: #f6f8ff;
}
.item.active {
  background: #f6f8ff;
  color: #5369db;
  font-weight: 600;
  box-shadow: inset 3px 0 #6e80eb;
}
.item-icon {
  flex: none;
}
.item-label {
  flex: 1;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.item-state {
  flex: none;
  width: 13px;
  height: 13px;
  border: 1.5px solid #d3dce5;
  border-radius: 50%;
}
.item-state.lock {
  border: 0;
  color: #a3adb8;
  font-size: 13px;
}
.item-state.ok {
  border: 0;
  color: #54b9a5;
  font-size: 14px;
}

@media (max-width: 1000px) {
  .learning-layout {
    grid-template-columns: minmax(0, 1fr);
  }
  .learning-sidebar {
    position: static;
  }
  .intro {
    flex-wrap: wrap;
  }
}
.due-alert {
  margin: 4px 0 14px;
}
</style>
