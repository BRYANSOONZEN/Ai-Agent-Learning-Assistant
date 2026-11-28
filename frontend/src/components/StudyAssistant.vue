<script setup>
/*
 * 个人学习助手. Design and rules: docs/模块说明/小助手-自动干预与聊天.md.
 * Two tabs: 学习情况 (per knowledge point, practice only, with the automatic intervention: review the
 * material, then 补练题 generated from this student's own mistakes) and 问问题 (free chat
 * with the model; no quiz answers, no questions before the first quiz submission).
 * Opens by itself after a quiz submission that left red / orange points (event 'assistant-open').
 * A floating button in the bottom-right corner; the panel opens only when clicked.
 * Hidden while an exercise is being answered or an AI dialogue is going (stores/assistantFocus).
 * 补练题 / 拓展题 are generated here; when ready the student clicks 去查看 and does them on the 学习推荐 page
 * (an extension of the assistant). 老师推送 is accepted here (接收) and then saved to 学习推荐 too.
 * Shows only this student's own data:
 *   - a chapter finished, or one whose exercises were done: a personal report per knowledge point
 *     (colour from the exercises only). From the exercise answers: which assessment type went wrong,
 *     which key points were missed, the recurring misconception, and an automatic review plan
 *     (watch the video from the right second / read the page → practise → explain it to the AI classmate);
 *     no teacher involved
 *   - 老师补充讲解 + 新题 pushed after the class analysis, deadlines, notices
 *   - chapters still in progress and the next item
 * The badge counts new things: a newly finished chapter, a deadline, a new push from the teacher.
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { acceptPush, getAssistantSummary, getMyPushes, getNotices, startExtend, startRemedy } from '@/api/assistant'
import AssistantChat from './AssistantChat.vue'
import { getMyDeadlines } from '@/api/learn'
import { studentBusy } from '@/stores/assistantFocus'

const route = useRoute()
const router = useRouter()

const open = ref(false)
const loading = ref(false)
const summary = ref(null) // { chapters, focusChapterId }
const deadlines = ref([]) // items due soon or overdue: the assistant reminds (no teacher involved)
const pushes = ref([]) // 老师补充讲解 (class intervention) pushed to me
const notices = ref([]) // 老师提醒
const openPush = ref(null)
const shownChapter = ref(null)
const mode = ref('report') // 'report' 学习情况 | 'chat' 问问题
// 补练、拓展：在这里让大模型出题，出好后点“去查看”到学习推荐页去做（学习推荐是小助手的延伸）
const gen = ref(null) // { kpId, kind: 'remedy' | 'extend', assessType, loading, text, count }
const extendType = ref('scenario')
const EXTEND_TYPES = [
  { value: 'scenario', label: '情境应用' },
  { value: 'data', label: '数据分析' },
  { value: 'concept', label: '概念辨析' },
  { value: 'process', label: '流程步骤' },
]
async function generate(p, kind, assessType) {
  if (gen.value?.loading) return
  if (kind === 'extend') extendType.value = assessType
  gen.value = { kpId: p.knowledgePointId, kind, assessType, loading: true, text: '', count: 0 }
  try {
    const v = kind === 'extend' ? await startExtend(p.knowledgePointId, assessType) : await startRemedy(p.knowledgePointId)
    gen.value = { ...gen.value, loading: false, text: v.text, count: v.questions?.length || 0 }
  } catch {
    gen.value = null
  }
}
function openExtend(p, type) {
  generate(p, 'extend', type)
}
function goRecommend(kind, kpId) {
  open.value = false
  router.push({ name: 'recommend', query: { kind: kind || undefined, kp: kpId || undefined } })
}
async function accept(p) {
  await acceptPush(p.id)
  p.accepted = true
  goRecommend('teacher')
}

const hidden = computed(() => studentBusy.value || route.name === 'dialogue')
watch(hidden, (h) => {
  if (h) open.value = false
})

// ---------- data ----------

async function refresh() {
  if (loading.value) return
  loading.value = true
  try {
    summary.value = await getAssistantSummary()
    deadlines.value = ((await getMyDeadlines()) || []).filter((d) => d.kind === 'item' && d.state !== 'later')
    pushes.value = (await getMyPushes()) || []
    notices.value = (await getNotices()) || []
    // a new push I have not done yet: open by itself
    if (pushes.value.some((p) => !p.done && !seen.value.includes('push:' + p.id)) && !hidden.value) {
      open.value = true
      mode.value = 'report'
      openPush.value = pushes.value.find((p) => !p.done)?.id ?? null
      markSeen() // pops up once; after the student closes it, only the badge stays
    }
    if (!summary.value.chapters.some((c) => c.chapterId === shownChapter.value)) {
      shownChapter.value = summary.value.focusChapterId
    }
  } catch {
    // the assistant is extra: if it cannot load, the page still works
  } finally {
    loading.value = false
  }
}

// after a quiz submission with red / orange points: open by itself on 学习情况
function openAfterQuiz(e) {
  mode.value = 'report'
  open.value = true
  shownChapter.value = e?.detail?.chapterId ?? null // 学情诊断“让小助手帮我补”：直接打开那一章
  refresh()
}

onMounted(() => {
  refresh()
  window.addEventListener('learn-progress', refresh)
  window.addEventListener('assistant-open', openAfterQuiz)
})
onBeforeUnmount(() => {
  window.removeEventListener('learn-progress', refresh)
  window.removeEventListener('assistant-open', openAfterQuiz)
})
watch(() => route.name, refresh)

const chapters = computed(() => summary.value?.chapters || [])
const current = computed(() => chapters.value.find((c) => c.chapterId === shownChapter.value) || null)
const inProgress = computed(() => chapters.value.filter((c) => !c.completed))

// ---------- what is new (badge) ----------

const SEEN_KEY = 'se-assistant-seen' // ['ch:1', 'iv:5', ...]
function readSeen() {
  try {
    return JSON.parse(localStorage.getItem(SEEN_KEY) || '[]')
  } catch {
    return []
  }
}
const seen = ref(readSeen())
const newKeys = computed(() => [
  ...chapters.value.filter((c) => c.completed).map((c) => 'ch:' + c.chapterId),
  ...deadlines.value.map((d) => `dl:${d.itemId}:${d.state}`),
  ...pushes.value.filter((p) => !p.done).map((p) => 'push:' + p.id),
  ...notices.value.map((n, i) => 'note:' + i + ':' + n.length),
].filter((k) => !seen.value.includes(k)))

function markSeen() {
  seen.value = [...new Set([...seen.value, ...newKeys.value])]
  try {
    localStorage.setItem(SEEN_KEY, JSON.stringify(seen.value))
  } catch {
    /* storage unavailable: the badge just shows again next time */
  }
}

function toggle() {
  open.value = !open.value
  if (open.value) {
    markSeen()
    refresh()
  }
}

// ---------- labels and actions ----------

// portrait colour (MasteryService.color): green = mastered at the first try,
// orange = was wrong at first, recovered with 补练题 (keep an eye on it), red = not yet, none = not practised
const VERDICT = {
  green: { text: '第一次就掌握', type: 'success' },
  orange: { text: '补回来了，要留意', type: 'warning' },
  red: { text: '还没掌握', type: 'danger' },
  none: { text: '未练习', type: 'info' },
}
const REMEDY = { recovered: '已补回来', practicing: '补练中' }


function goItem(ch, itemId) {
  open.value = false
  router.push({
    name: 'course-detail',
    params: { id: ch.courseId },
    query: { chapter: ch.chapterId, view: itemId ? 'item:' + itemId : 'intro' },
  })
}

function dueText(d) {
  const h = Math.max(0, Math.round(d.minutesLeft / 60))
  return h < 24 ? `还剩 ${h} 小时截止` : `还剩 ${Math.round(h / 24)} 天截止`
}

function goPractice() {
  goRecommend()
}

// one step of the automatic review plan: open the video at the right second / the document, or go practise
function doStep(ch, st, p) {
  if (st.kind === 'review' && st.itemId) {
    open.value = false
    router.push({
      name: 'course-detail',
      params: { id: ch.courseId },
      query: { chapter: ch.chapterId, view: 'item:' + st.itemId, ...(st.startSec != null ? { t: st.startSec } : {}) },
    })
  } else if (st.kind === 'dialogue') goItem(ch, ch.dialogueItemId)
  else if (st.kind === 'remedy') generate(p, 'remedy')
  else goPractice()
}

function act(ch, p) {
  if (p.action === 'dialogue') goItem(ch, ch.dialogueItemId)
  else if (p.action === 'remedy') generate(p, 'remedy')
  else goPractice()
}
</script>

<template>
  <div v-if="!hidden" class="assistant">
    <transition name="pop">
      <section v-if="open" class="panel" role="dialog" aria-label="学习助手">
        <header class="panel-head">
          <div>
            <strong>学习助手</strong>
            <small>只看你自己的学习情况</small>
          </div>
          <button class="close" type="button" aria-label="关闭" @click="open = false">×</button>
        </header>

        <div v-if="chapters.length" class="modes">
          <button type="button" :class="{ active: mode === 'report' }" @click="mode = 'report'">学习情况</button>
          <button type="button" :class="{ active: mode === 'chat' }" @click="mode = 'chat'">问问题</button>
        </div>

        <div v-if="mode === 'chat' && current" class="panel-body">
          <AssistantChat :chapter-id="current.chapterId" :chapter-title="current.title" />
        </div>

        <div v-else class="panel-body">
          <!-- nothing yet -->
          <div v-if="summary && !chapters.length" class="empty">
            <p>你还没开始学习。选一门课，从第一章开始吧。</p>
            <el-button type="primary" size="small" @click="router.push({ name: 'courses' }); open = false">去选课</el-button>
          </div>

          <!-- 老师补充讲解 (class intervention) and 老师提醒 -->
          <div v-if="pushes.length || notices.length" class="teacher">
            <h4>老师给你的</h4>
            <p v-for="(n, i) in notices.slice(0, 3)" :key="'n' + i" class="notice">{{ n }}</p>
            <div v-for="p in pushes" :key="p.id" class="push-item">
              <div class="push-head" @click="openPush = openPush === p.id ? null : p.id">
                <b>老师补充讲解：「{{ p.knowledgePointName }}」</b>
                <el-tag size="small" :type="p.done ? 'success' : p.accepted ? 'primary' : 'warning'">{{ p.done ? '已完成' : p.accepted ? '已接收' : '待接收' }}</el-tag>
              </div>
              <small>{{ p.keyPoints.join('；') }}<template v-if="p.dueAt"> · 期限 {{ String(p.dueAt).slice(5, 16) }}</template></small>
              <div v-if="openPush === p.id" class="push-body">
                <p class="explain">{{ p.explanation }}</p>
                <p class="muted">老师还出了 {{ p.questions.length }} 道新题。接收后保存到“学习推荐 · 老师推送”，在那里做。</p>
                <el-button v-if="!p.accepted && !p.done" size="small" type="primary" @click="accept(p)">接收</el-button>
                <el-button v-else size="small" type="primary" plain @click="goRecommend('teacher')">
                  {{ p.done ? '去学习推荐查看' : '已接收，去学习推荐做题' }}
                </el-button>
              </div>
            </div>
          </div>

          <!-- deadline reminders: due within a few days, or overdue -->
          <div v-if="deadlines.length" class="due">
            <h4>截止提醒</h4>
            <div v-for="d in deadlines" :key="d.itemId" class="due-row" :class="d.state">
              <span>
                <b>{{ d.title }}</b>
                <small>{{ d.chapterTitle }} · {{ d.state === 'overdue' ? (d.allowLate ? '已过期，还可以补做（算迟交）' : '已过期') : dueText(d) }}</small>
              </span>
              <el-button v-if="d.state === 'soon' || d.allowLate" size="small" type="primary" link
                @click="goItem({ courseId: d.courseId, chapterId: d.chapterId }, d.itemId)">去做</el-button>
            </div>
          </div>

          <!-- chapter tabs, when the student has more than one -->
          <div v-if="chapters.length > 1" class="tabs">
            <button
              v-for="c in chapters"
              :key="c.chapterId"
              type="button"
              :class="{ active: c.chapterId === shownChapter }"
              @click="shownChapter = c.chapterId"
            >
              {{ c.title }}<span v-if="c.completed" class="tick">✓</span>
            </button>
          </div>

          <!-- the chosen chapter -->
          <template v-if="current">
            <div class="chapter-line">
              <span class="course">{{ current.courseName }}</span>
              <span class="count">{{ current.done }} / {{ current.total }} 项</span>
            </div>
            <el-progress :percentage="Math.round((current.done / current.total) * 100)" :show-text="false" :stroke-width="6" />

            <!-- finished: personal report -->
            <template v-if="current.report">
              <p class="headline">{{ current.report.headline }}</p>
              <ul class="points">
                <li v-for="p in current.report.points" :key="p.knowledgePointId" :class="'v-' + p.verdict">
                  <div class="point-head">
                    <b>{{ p.name }}</b>
                    <el-tag :type="VERDICT[p.verdict]?.type" size="small" effect="light">
                      {{ VERDICT[p.verdict]?.text }}
                    </el-tag>
                    <el-tag v-if="p.remedyStatus === 'practicing'" type="info" size="small" effect="plain">
                      {{ REMEDY[p.remedyStatus] }}
                    </el-tag>
                  </div>
                  <p>{{ p.text }}</p>
                  <p v-if="p.dialogue" class="dialogue-note">{{ p.dialogue }}</p>
                  <ul v-if="p.details?.length && p.verdict !== 'good'" class="details">
                    <li v-for="(d, k) in p.details" :key="k">{{ d }}</li>
                  </ul>
                  <ol v-if="p.plan?.length" class="plan">
                    <li v-for="(st, k) in p.plan" :key="k">
                      <span>{{ st.text }}</span>
                      <el-button size="small" link type="primary" @click="doStep(current, st, p)">
                        {{ st.kind === 'review' ? '去看' : st.kind === 'dialogue' ? '去讲' : st.kind === 'remedy' ? '出补练题' : '去做题' }}
                      </el-button>
                    </li>
                  </ol>
                  <div v-if="p.action === 'extend'" class="extend">
                    <span>来点更难的：</span>
                    <el-button v-for="t in EXTEND_TYPES" :key="t.value" size="small" :type="gen?.kind === 'extend' && gen.kpId === p.knowledgePointId && extendType === t.value ? 'primary' : 'default'" plain @click="openExtend(p, t.value)">{{ t.label }}</el-button>
                  </div>

                  <el-button v-if="p.action && p.action !== 'extend' && !p.plan?.length" size="small" type="primary" plain @click="act(current, p)">
                    {{ p.action === 'dialogue' ? '巩固一下（AI 对话）' : '做补练题' }}
                  </el-button>
                  <div v-if="gen && gen.kpId === p.knowledgePointId" class="gen">
                    <p v-if="gen.loading" class="muted"><el-icon class="is-loading"><Loading /></el-icon>
                      {{ gen.kind === 'extend' ? '正在出更难的题……' : '正在根据你的错题出补练题……' }}</p>
                    <template v-else>
                      <p>{{ gen.text }}</p>
                      <el-button v-if="gen.count" size="small" type="primary" @click="goRecommend(gen.kind, gen.kpId)">
                        题目出好了，去查看（学习推荐）
                      </el-button>
                    </template>
                  </div>
                </li>
              </ul>
            </template>

            <!-- still learning: where to continue -->
            <div v-if="!current.completed" class="continue">
              <p v-if="current.report">这一章还有 {{ current.total - current.done }} 项没完成，上面是根据你目前练习给的分析。</p>
              <p v-else>这一章还有 {{ current.total - current.done }} 项没完成，做完练习后我会给你一份分析和复习方案。</p>
              <el-button type="primary" size="small" @click="goItem(current, current.nextItemId)">
                继续：{{ current.nextItemTitle }}
              </el-button>
            </div>
          </template>

          <!-- other chapters in progress -->
          <div v-if="inProgress.length && current?.completed" class="others">
            <h4>还没学完的章节</h4>
            <button v-for="c in inProgress" :key="c.chapterId" type="button" class="other" @click="goItem(c, c.nextItemId)">
              {{ c.title }}<small>{{ c.done }} / {{ c.total }}，下一项：{{ c.nextItemTitle }}</small>
            </button>
          </div>
        </div>
      </section>
    </transition>

    <button class="fab" type="button" :aria-label="open ? '收起学习助手' : '打开学习助手'" @click="toggle">
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path d="M4 5.5A2.5 2.5 0 0 1 6.5 3h11A2.5 2.5 0 0 1 20 5.5v8a2.5 2.5 0 0 1-2.5 2.5H10l-4.2 3.6A.5.5 0 0 1 5 19.2V16h0A2.5 2.5 0 0 1 4 13.5Z" />
        <circle cx="9" cy="9.5" r="1.1" class="dot" />
        <circle cx="15" cy="9.5" r="1.1" class="dot" />
      </svg>
      <span v-if="newKeys.length && !open" class="badge">{{ newKeys.length }}</span>
    </button>
  </div>
</template>

<style scoped>
.assistant {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 2000;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 12px;
}

.fab {
  position: relative;
  width: 54px;
  height: 54px;
  border: 0;
  border-radius: 50%;
  background: var(--app-navy);
  box-shadow: 0 8px 22px rgba(17, 42, 70, 0.28);
  cursor: pointer;
  display: grid;
  place-items: center;
  transition: transform 0.15s ease;
}
.fab:hover {
  transform: translateY(-2px);
}
.fab svg {
  width: 28px;
  height: 28px;
  fill: var(--app-accent-teal);
}
.fab .dot {
  fill: var(--app-navy);
}
.badge {
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 20px;
  height: 20px;
  padding: 0 5px;
  border-radius: 10px;
  background: var(--app-danger);
  color: #fff;
  font-size: 12px;
  line-height: 20px;
  font-weight: 600;
}

.panel {
  width: 380px;
  max-width: calc(100vw - 32px);
  max-height: min(640px, calc(100vh - 120px));
  display: flex;
  flex-direction: column;
  background: var(--app-surface);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);
  box-shadow: 0 16px 40px rgba(17, 42, 70, 0.18);
  overflow: hidden;
}
.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
  background: var(--app-navy);
  color: #fff;
}
.panel-head strong {
  display: block;
  font-size: 15px;
}
.panel-head small {
  color: #a9bdd2;
  font-size: 12px;
}
.close {
  border: 0;
  background: transparent;
  color: #cfdbe7;
  font-size: 22px;
  line-height: 1;
  cursor: pointer;
}
.panel-body {
  padding: 14px 16px 16px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
  color: var(--app-text);
  font-size: 13px;
}
.panel-body p {
  margin: 0;
  line-height: 1.6;
}
.panel-body h4 {
  margin: 0 0 6px;
  font-size: 13px;
  color: var(--app-text-secondary);
}

.tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.tabs button {
  border: 1px solid var(--app-border);
  background: var(--app-bg);
  border-radius: 14px;
  padding: 3px 10px;
  font-size: 12px;
  color: var(--app-text-secondary);
  cursor: pointer;
}
.tabs button.active {
  border-color: var(--app-primary);
  color: var(--app-primary);
  background: #eef1fd;
}
.tick {
  margin-left: 4px;
  color: var(--app-success);
}

.chapter-line {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--app-text-muted);
}
.headline {
  font-weight: 600;
}

.points {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.points > li {
  border: 1px solid var(--app-border);
  border-left: 3px solid var(--app-info);
  border-radius: 8px;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: flex-start;
}
.points > li.v-green {
  border-left-color: var(--app-success);
}
.points > li.v-orange {
  border-left-color: var(--app-warning);
}
.points > li.v-red {
  border-left-color: var(--app-danger);
}
.extend {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
  font-size: 12px;
  margin-top: 4px;
}
.extend .el-button + .el-button {
  margin-left: 0;
}
.teacher {
  margin-bottom: 10px;
}
.notice {
  margin: 4px 0;
  padding: 6px 8px;
  border-radius: 6px;
  background: var(--el-color-warning-light-9);
  font-size: 13px;
}
.push-item {
  margin-top: 6px;
  padding: 8px;
  border: 1px solid var(--el-color-primary-light-7);
  border-radius: 8px;
}
.push-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  cursor: pointer;
}
.push-item small {
  color: var(--el-text-color-secondary);
}
.due {
  margin-bottom: 10px;
}
.due-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  margin-top: 4px;
  border-radius: 6px;
  background: var(--el-color-warning-light-9);
}
.due-row.overdue {
  background: var(--el-color-danger-light-9);
}
.due-row small {
  display: block;
  color: var(--el-text-color-secondary);
}
.modes {
  display: flex;
  border-bottom: 1px solid var(--app-border);
}
.modes button {
  flex: 1;
  padding: 8px 0;
  border: none;
  background: transparent;
  cursor: pointer;
  color: var(--el-text-color-regular);
}
.modes button.active {
  color: var(--el-color-primary);
  font-weight: 600;
  box-shadow: inset 0 -2px 0 var(--el-color-primary);
}
.point-head {
  display: flex;
  gap: 8px;
  align-items: center;
}
.details {
  margin: 4px 0 0;
  padding-left: 16px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.plan {
  margin: 6px 0 0;
  padding-left: 18px;
  font-size: 12px;
}
.plan li span {
  margin-right: 4px;
}
.points > li > p {
  color: var(--app-text-secondary);
}

.continue,
.empty {
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: flex-start;
}

.dialogue-note {
  margin-top: 4px;
  font-size: 12px;
  color: var(--app-text-muted);
}

.others {
  border-top: 1px solid var(--app-border);
  padding-top: 12px;
}
.other {
  display: block;
  width: 100%;
  text-align: left;
  border: 0;
  background: transparent;
  padding: 6px 0;
  color: var(--app-text);
  cursor: pointer;
  font-size: 13px;
}
.other small {
  display: block;
  color: var(--app-text-muted);
  font-size: 12px;
}

.pop-enter-active,
.pop-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}
.pop-enter-from,
.pop-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

@media (max-width: 600px) {
  .assistant {
    right: 16px;
    bottom: 16px;
  }
}
</style>
