<script setup>
/*
 * Teacher page: build one course (Studywise look).
 * Top: a dark summary strip (name, status, modules, chapter counts, 课程设置).
 * Left: 课程大纲. Each chapter opens / closes like a folder and shows, in the order students learn it:
 *   章节介绍 → its items (视频 / 文档 / 练习 / AI 对话 — any number, any order) → + 添加内容
 *   then the chapter's 知识点 and 预览发布.
 * Right: the workspace for whatever is open, with a "下一步" button at the bottom.
 * What is open lives in the URL (?chapter=3&view=item:12), so a refresh keeps it.
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import ChapterKnowledgePoints from './chapter/ChapterKnowledgePoints.vue'
import ChapterIntro from './chapter/ChapterIntro.vue'
import ChapterPublish from './chapter/ChapterPublish.vue'
import ItemEditor from './chapter/ItemEditor.vue'
import CourseFormDialog from './CourseFormDialog.vue'
import { has, moduleLabel, ITEM_TYPES, itemType, itemSummary } from './courseModules'
import {
  getCourse,
  getChapters,
  createChapter,
  patchChapter,
  deleteChapter,
  reorderChapters,
  getItems,
  createItem,
  deleteItem,
  reorderItems,
} from '@/api/teacherCourse'

const route = useRoute()
const router = useRouter()
const courseId = computed(() => Number(route.params.id))

const course = ref(null)
const chapters = ref([])
const itemsByChapter = ref({}) // chapterId -> items, loaded when a chapter is opened
const loading = ref(false)
const settingsVisible = ref(false)

const statusMap = {
  published: { label: '已发布', type: 'success' },
  draft: { label: '草稿', type: 'info' },
  archived: { label: '已归档', type: 'warning' },
}

// ---------- what is open ----------

const selectedId = computed(() => Number(route.query.chapter) || null)
const selected = computed(() => chapters.value.find((c) => c.id === selectedId.value) || null)
// 'intro' | 'kps' | 'publish' | 'item:<id>'
const view = computed(() => route.query.view || 'intro')
const items = computed(() => itemsByChapter.value[selectedId.value] || [])
const currentItem = computed(() =>
  view.value.startsWith('item:') ? items.value.find((it) => 'item:' + it.id === view.value) || null : null,
)

function open(chapterId, v = 'intro') {
  return router.replace({ query: { ...route.query, chapter: chapterId, view: v } })
}

// the order the "下一步" button walks through
const sequence = computed(() => [
  'intro',
  ...items.value.map((it) => 'item:' + it.id),
  ...(has(course.value, 'knowledge') ? ['kps'] : []),
  'publish',
])
const next = computed(() => sequence.value[sequence.value.indexOf(view.value) + 1] || null)

const FIXED = {
  intro: { label: '章节介绍', icon: 'Memo', desc: '学生进入本章最先看到的学习目标和概要。' },
  kps: { label: '知识点', icon: 'Collection', desc: '老师整理用：题目和 AI 对话都关联知识点，学情诊断按知识点统计。' },
  publish: { label: '预览发布', icon: 'Promotion', desc: '检查每一项内容，按学生看到的顺序预览，然后发布。' },
}
const ITEM_DESC = {
  video: '一个视频。一章要放几个视频，就加几个视频项。',
  document: '上传一个文件，或者直接写一段阅读内容。',
  exercise: '一次练习，里面放几道单选题。一章可以有好几次练习。',
  dialogue: '学生学到这里时和 AI 对话，按这里的话题顺序提问。',
}
const head = computed(() => {
  if (currentItem.value) {
    const t = itemType(currentItem.value.type)
    return { label: currentItem.value.title, icon: t.icon, desc: `${t.label} · ${ITEM_DESC[t.type]}` }
  }
  return FIXED[view.value] || FIXED.intro
})
function labelOf(v) {
  if (v.startsWith('item:')) return items.value.find((it) => 'item:' + it.id === v)?.title || ''
  return FIXED[v].label
}

// content types the course allows
const addable = computed(() => ITEM_TYPES.filter((t) => has(course.value, t.type)))

// ---------- outline open / closed ----------

const expanded = ref([])
const isOpen = (ch) => expanded.value.includes(ch.id)

async function loadItems(chapterId) {
  itemsByChapter.value = { ...itemsByChapter.value, [chapterId]: await getItems(chapterId) }
}

function toggle(ch) {
  if (isOpen(ch)) {
    expanded.value = expanded.value.filter((id) => id !== ch.id)
  } else {
    expanded.value = [...expanded.value, ch.id]
    if (!itemsByChapter.value[ch.id]) loadItems(ch.id)
  }
}

watch(
  selectedId,
  (id) => {
    if (!id) return
    if (!expanded.value.includes(id)) expanded.value = [...expanded.value, id]
    if (!itemsByChapter.value[id]) loadItems(id)
  },
  { immediate: true },
)

// click the open chapter: fold / unfold it; another chapter: switch to it, from its intro
function clickChapter(ch) {
  if (ch.id === selectedId.value) toggle(ch)
  else open(ch.id)
}

// ---------- loading ----------

async function loadChapters() {
  chapters.value = await getChapters(courseId.value)
  if (!selected.value && chapters.value.length) open(chapters.value[0].id)
}

// after any edit inside the chapter: counts on chapters + the item list
async function refresh() {
  await Promise.all([loadChapters(), selectedId.value && loadItems(selectedId.value)])
}

async function load() {
  loading.value = true
  try {
    course.value = await getCourse(courseId.value)
    await loadChapters()
  } catch {
    // not this teacher's course (403) or it does not exist (404): the error is already shown, go back to the list
    router.replace({ name: 'teacher-courses' })
  } finally {
    loading.value = false
  }
}

// ---------- chapters ----------

async function addChapter() {
  const { value } = await ElMessageBox.prompt('章节标题', '添加章节', {
    inputValue: `第 ${chapters.value.length + 1} 章 `,
    inputValidator: (v) => (v && v.trim() ? true : '标题不能为空'),
  })
  const created = await createChapter({ courseId: courseId.value, title: value })
  ElMessage.success('章节已创建，先写章节介绍，再添加内容')
  await loadChapters()
  open(created.id)
}

async function renameChapter(ch) {
  const { value } = await ElMessageBox.prompt('章节标题', '修改标题', {
    inputValue: ch.title,
    inputValidator: (v) => (v && v.trim() ? true : '标题不能为空'),
  })
  await patchChapter(ch, { title: value })
  ElMessage.success('已保存')
  loadChapters()
}

async function moveChapter(index, delta) {
  const ids = chapters.value.map((c) => c.id)
  const target = index + delta
  if (target < 0 || target >= ids.length) return
  ;[ids[index], ids[target]] = [ids[target], ids[index]]
  chapters.value = await reorderChapters(courseId.value, ids)
}

async function removeChapter(ch) {
  await ElMessageBox.confirm(`确定删除「${ch.title}」吗？`, '提示', { type: 'warning' })
  await deleteChapter(ch.id)
  ElMessage.success('已删除')
  if (ch.id === selectedId.value) router.replace({ query: {} })
  loadChapters()
}

function onChapterCommand(cmd, ch, i) {
  if (cmd === 'rename') renameChapter(ch)
  else if (cmd === 'up') moveChapter(i, -1)
  else if (cmd === 'down') moveChapter(i, 1)
  else if (cmd === 'delete') removeChapter(ch)
}

// ---------- items ----------

async function addItem(ch, type) {
  const t = itemType(type)
  const { value } = await ElMessageBox.prompt('标题（学生在章节里看到的名字）', `添加${t.label}`, {
    inputValue: t.placeholder,
    inputValidator: (v) => (v && v.trim() ? true : '标题不能为空'),
  })
  const created = await createItem({ chapterId: ch.id, type, title: value })
  await Promise.all([loadItems(ch.id), loadChapters()])
  open(ch.id, 'item:' + created.id)
}

async function moveItem(ch, index, delta) {
  const ids = itemsByChapter.value[ch.id].map((it) => it.id)
  const target = index + delta
  if (target < 0 || target >= ids.length) return
  ;[ids[index], ids[target]] = [ids[target], ids[index]]
  itemsByChapter.value = { ...itemsByChapter.value, [ch.id]: await reorderItems(ch.id, ids) }
}

async function removeItem(ch, it) {
  const inside =
    it.type === 'exercise' && it.exerciseCount
      ? `，里面的 ${it.exerciseCount} 道题也会一起删除`
      : it.type === 'dialogue' && it.topicCount
        ? `，里面的 ${it.topicCount} 个话题也会一起删除`
        : it.material
          ? '，文件也会一起删除'
          : ''
  await ElMessageBox.confirm(`确定删除「${it.title}」吗${inside}。`, '提示', { type: 'warning' })
  await deleteItem(it.id)
  ElMessage.success('已删除')
  if (view.value === 'item:' + it.id) open(ch.id)
  await Promise.all([loadItems(ch.id), loadChapters()])
}

function onItemCommand(cmd, ch, it, i) {
  if (cmd === 'up') moveItem(ch, i, -1)
  else if (cmd === 'down') moveItem(ch, i, 1)
  else if (cmd === 'delete') removeItem(ch, it)
}

// ---------- drag to reorder: chapters, and items inside one chapter ----------
// (⋯ 上移 / 下移 stay as the keyboard-friendly way)

const drag = ref(null) // { kind: 'chapter' | 'item', chapterId, from, over }

function onDragStart(e, kind, from, chapterId = null) {
  drag.value = { kind, chapterId, from, over: from }
  e.dataTransfer.effectAllowed = 'move'
  e.dataTransfer.setData('text/plain', '') // Firefox only starts a drag when there is data
}

// only accept the same kind, and items only inside their own chapter
function sameDrag(kind, chapterId) {
  return drag.value && drag.value.kind === kind && drag.value.chapterId === chapterId
}

function onDragOver(e, kind, index, chapterId = null) {
  if (!sameDrag(kind, chapterId)) return
  e.preventDefault() // allows dropping here
  drag.value.over = index
}

// line above or below the row the dragged one will land next to
function dropClass(kind, index, chapterId = null) {
  if (!sameDrag(kind, chapterId)) return null
  const { from, over } = drag.value
  if (index === from) return 'dragging'
  if (index !== over) return null
  return over < from ? 'drop-before' : 'drop-after'
}

function moved(list, from, to) {
  const copy = [...list]
  copy.splice(to, 0, copy.splice(from, 1)[0])
  return copy
}

async function onDrop(kind, chapterId = null) {
  if (!sameDrag(kind, chapterId)) return
  const { from, over } = drag.value
  drag.value = null
  if (from === over) return
  try {
    if (kind === 'item') {
      const list = moved(itemsByChapter.value[chapterId], from, over)
      itemsByChapter.value = { ...itemsByChapter.value, [chapterId]: list } // show the new order right away
      const saved = await reorderItems(chapterId, list.map((it) => it.id))
      itemsByChapter.value = { ...itemsByChapter.value, [chapterId]: saved }
    } else {
      const list = moved(chapters.value, from, over)
      chapters.value = list
      chapters.value = await reorderChapters(courseId.value, list.map((c) => c.id))
    }
  } catch {
    // save failed (error already shown): put back what the server has
    if (kind === 'item') loadItems(chapterId)
    else loadChapters()
  }
}

const publishedCount = computed(() => chapters.value.filter((c) => c.status === 'published').length)

watch(courseId, load)
onMounted(load)
</script>

<template>
  <div v-loading="loading" class="page">
    <!-- course header -->
    <header v-if="course" class="sw-summary course-head">
      <div class="sw-summary-main">
        <span class="sw-eyebrow on-dark">COURSE BUILDER</span>
        <div class="title-line">
          <h2>{{ course.name }}</h2>
          <span class="status" :class="course.status">{{ statusMap[course.status]?.label }}</span>
        </div>
        <p>{{ course.description || '还没有简介，可在「课程设置」里填写' }}</p>
        <div class="head-modules">
          <span v-for="m in course.modules" :key="m" class="module">{{ moduleLabel(m) }}</span>
        </div>
      </div>
      <div class="sw-summary-stat">
        <strong>{{ chapters.length }}</strong>
        <span>个章节</span>
      </div>
      <div class="sw-summary-stat">
        <strong>{{ publishedCount }}</strong>
        <span>个已发布</span>
      </div>
      <button type="button" class="sw-button-light" @click="settingsVisible = true">
        <el-icon><Setting /></el-icon>课程设置
      </button>
    </header>

    <div class="builder">
      <!-- outline -->
      <aside class="app-card outline">
        <div class="outline-head">
          <span class="sw-eyebrow">COURSE OUTLINE</span>
          <strong>课程大纲</strong>
        </div>

        <div v-for="(ch, i) in chapters" :key="ch.id" class="chapter-group">
          <div
            class="chapter"
            :class="[{ active: ch.id === selectedId }, dropClass('chapter', i)]"
            draggable="true"
            title="拖动调整章节顺序"
            @click="clickChapter(ch)"
            @dragstart="onDragStart($event, 'chapter', i)"
            @dragover="onDragOver($event, 'chapter', i)"
            @drop.prevent="onDrop('chapter')"
            @dragend="drag = null"
          >
            <el-icon class="chevron" :class="{ open: isOpen(ch) }" @click.stop="toggle(ch)">
              <ArrowRight />
            </el-icon>
            <span class="num" :class="{ published: ch.status === 'published' }">
              <el-icon v-if="ch.status === 'published'"><Check /></el-icon>
              <template v-else>{{ i + 1 }}</template>
            </span>
            <div class="chapter-body">
              <div class="chapter-title">{{ ch.title }}</div>
              <div class="chapter-sub">
                {{ ch.status === 'published' ? '已发布' : '草稿' }} · {{ ch.itemCount }} 项内容
              </div>
            </div>
            <el-dropdown trigger="click" @command="(cmd) => onChapterCommand(cmd, ch, i)" @click.stop>
              <el-button link class="more" icon="MoreFilled" @click.stop />
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="rename" icon="Edit">改名</el-dropdown-item>
                  <el-dropdown-item command="up" icon="Top" :disabled="i === 0">上移</el-dropdown-item>
                  <el-dropdown-item command="down" icon="Bottom" :disabled="i === chapters.length - 1">
                    下移
                  </el-dropdown-item>
                  <el-dropdown-item command="delete" icon="Delete" divided>删除</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>

          <!-- an open chapter: intro, its items in learning order, then the teacher-side parts -->
          <ul v-if="isOpen(ch)" class="items">
            <li
              class="item fixed"
              :class="{ active: ch.id === selectedId && view === 'intro' }"
              @click="open(ch.id, 'intro')"
            >
              <el-icon class="item-icon"><Memo /></el-icon>
              <span class="item-label">章节介绍</span>
              <el-icon v-if="ch.intro?.trim()" class="item-state ok"><CircleCheckFilled /></el-icon>
              <span v-else class="item-state" title="待完成" />
            </li>

            <li
              v-for="(it, j) in itemsByChapter[ch.id] || []"
              :key="it.id"
              class="item"
              :class="[{ active: ch.id === selectedId && view === 'item:' + it.id }, dropClass('item', j, ch.id)]"
              draggable="true"
              @click="open(ch.id, 'item:' + it.id)"
              @dragstart.stop="onDragStart($event, 'item', j, ch.id)"
              @dragover.stop="onDragOver($event, 'item', j, ch.id)"
              @drop.stop.prevent="onDrop('item', ch.id)"
              @dragend="drag = null"
            >
              <el-icon class="grip" title="拖动调整顺序"><Rank /></el-icon>
              <el-icon class="item-icon"><component :is="itemType(it.type)?.icon" /></el-icon>
              <span class="item-text">
                <span class="item-label">{{ it.title }}</span>
                <span class="item-sub">{{ itemSummary(it) }}</span>
              </span>
              <el-dropdown trigger="click" @command="(cmd) => onItemCommand(cmd, ch, it, j)" @click.stop>
                <el-button link class="more item-more" icon="MoreFilled" @click.stop />
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="up" icon="Top" :disabled="j === 0">上移</el-dropdown-item>
                    <el-dropdown-item
                      command="down"
                      icon="Bottom"
                      :disabled="j === itemsByChapter[ch.id].length - 1"
                    >
                      下移
                    </el-dropdown-item>
                    <el-dropdown-item command="delete" icon="Delete" divided>删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
              <el-icon v-if="it.ready" class="item-state ok"><CircleCheckFilled /></el-icon>
              <span v-else class="item-state" title="还没完成" />
            </li>

            <li class="add-item" @click.stop>
              <el-dropdown trigger="click" @command="(type) => addItem(ch, type)">
                <span class="add-item-btn"><el-icon><Plus /></el-icon> 添加内容</span>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-for="t in addable" :key="t.type" :command="t.type" :icon="t.icon">
                      {{ t.label }}
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </li>

            <li class="divider" />
            <li
              v-if="has(course, 'knowledge')"
              class="item fixed"
              :class="{ active: ch.id === selectedId && view === 'kps' }"
              @click="open(ch.id, 'kps')"
            >
              <el-icon class="item-icon"><Collection /></el-icon>
              <span class="item-label">知识点</span>
              <span class="item-count">{{ ch.knowledgePointCount }}</span>
            </li>
            <li
              class="item fixed"
              :class="{ active: ch.id === selectedId && view === 'publish' }"
              @click="open(ch.id, 'publish')"
            >
              <el-icon class="item-icon"><Promotion /></el-icon>
              <span class="item-label">预览发布</span>
              <el-icon v-if="ch.status === 'published'" class="item-state ok"><CircleCheckFilled /></el-icon>
            </li>
          </ul>
        </div>

        <button type="button" class="add-chapter" @click="addChapter">
          <el-icon><Plus /></el-icon> 添加章节
        </button>
      </aside>

      <!-- workspace -->
      <section class="app-card workspace">
        <div v-if="!selected" class="empty">
          <el-empty :description="chapters.length ? '在左边选一个章节' : '这门课还没有章节'">
            <el-button type="primary" icon="Plus" @click="addChapter">添加第一个章节</el-button>
          </el-empty>
        </div>
        <template v-else>
          <div class="ws-head">
            <div class="crumb">
              {{ selected.title }}
              <el-tag :type="selected.status === 'published' ? 'success' : 'info'" size="small">
                {{ selected.status === 'published' ? '已发布' : '草稿' }}
              </el-tag>
            </div>
            <h2>
              <span class="sw-icon tone-blue"><el-icon><component :is="head.icon" /></el-icon></span>
              {{ head.label }}
            </h2>
            <p class="ws-desc">{{ head.desc }}</p>
          </div>

          <ChapterIntro v-if="view === 'intro'" :key="'i' + selected.id" :chapter="selected" @changed="refresh" />
          <ChapterKnowledgePoints
            v-else-if="view === 'kps'"
            :key="'k' + selected.id"
            :chapter="selected"
            @changed="refresh"
          />
          <ChapterPublish
            v-else-if="view === 'publish'"
            :key="'p' + selected.id"
            :chapter="selected"
            :items="items"
            @changed="refresh"
            @go="(v) => open(selected.id, v)"
          />
          <ItemEditor
            v-else-if="currentItem"
            :key="'it' + currentItem.id"
            :chapter="selected"
            :item="currentItem"
            @changed="refresh"
            @go="(v) => open(selected.id, v)"
          />
          <el-empty v-else description="这一项已经不存在了" />

          <!-- empty chapter: point at the add menu -->
          <el-alert
            v-if="view === 'intro' && !items.length"
            class="hint"
            type="info"
            show-icon
            :closable="false"
            title="写好介绍后，在左边本章下面点「+ 添加内容」，加视频、文档、练习或 AI 对话，想加几个都可以。"
          />

          <div v-if="next" class="ws-foot">
            <el-button type="primary" plain @click="open(selected.id, next)">
              下一步：{{ labelOf(next) }}
              <el-icon class="el-icon--right"><ArrowRight /></el-icon>
            </el-button>
          </div>
        </template>
      </section>
    </div>

    <CourseFormDialog v-model="settingsVisible" :course="course" @saved="(c) => (course = c)" />
  </div>
</template>

<style scoped>
/* ---------- course header (dark summary strip) ---------- */
.title-line {
  display: flex;
  align-items: center;
  gap: 10px;
}
.title-line h2 {
  margin: 7px 0 6px;
}
.status {
  padding: 3px 8px;
  border-radius: 5px;
  background: rgba(255, 255, 255, 0.14);
  color: #d7e4f1;
  font-size: 11px;
  font-weight: 600;
}
.status.published {
  background: rgba(142, 215, 207, 0.2);
  color: #9be3d6;
}
.head-modules {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 12px;
}
.module {
  padding: 3px 8px;
  border-radius: 5px;
  background: rgba(255, 255, 255, 0.1);
  color: #c4d5e4;
  font-size: 11px;
}

/* ---------- layout ---------- */
.builder {
  display: grid;
  grid-template-columns: 300px 1fr;
  gap: var(--app-space-md);
  align-items: start;
  margin-top: var(--app-space-md);
}
@media (max-width: 900px) {
  .builder {
    grid-template-columns: 1fr;
  }
}
/* global .app-card + .app-card adds a top margin for stacked cards; these sit in a grid */
.builder > .app-card {
  margin-top: 0;
}

/* ---------- outline ---------- */
.outline {
  padding: 18px 10px 12px;
}
.outline-head {
  padding: 0 8px 12px;
}
.outline-head strong {
  display: block;
  margin-top: 5px;
  font-size: 15px;
}
.chapter-group {
  margin-bottom: 2px;
}
.chapter {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 8px;
  border-radius: 8px;
  cursor: pointer;
  border-left: 3px solid transparent;
}
.chapter:hover {
  background: var(--el-fill-color-light);
}
.chapter.active {
  background: #f6f8ff;
  border-left-color: #6e80eb;
}
.chevron {
  flex: none;
  margin-top: 5px;
  margin-right: -4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  transition: transform 0.15s;
}
.chevron.open {
  transform: rotate(90deg);
}
.num {
  flex: none;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  background: var(--el-fill-color);
  color: var(--el-text-color-regular);
}
.num.published {
  background: #6fc6b6;
  color: #fff;
}
.chapter-body {
  flex: 1;
  min-width: 0;
}
.chapter-title {
  font-size: 14px;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.chapter.active .chapter-title {
  font-weight: 600;
  color: #5369db;
}
.chapter-sub {
  margin-top: 2px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.more {
  opacity: 0.4;
}
.chapter:hover .more,
.chapter.active .more {
  opacity: 1;
}
.add-chapter {
  width: 100%;
  margin-top: 8px;
  padding: 10px;
  border: 1px dashed var(--el-border-color);
  border-radius: 8px;
  background: none;
  color: var(--el-color-primary);
  font: inherit;
  font-size: 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}
.add-chapter:hover {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

/* items of an open chapter */
.items {
  list-style: none;
  margin: 2px 0 8px;
  padding: 0 0 0 40px;
  position: relative;
}
.items::before {
  content: '';
  position: absolute;
  left: 31px;
  top: 4px;
  bottom: 4px;
  width: 1px;
  background: var(--el-border-color-lighter);
}
.item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 8px 7px 10px;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
  color: var(--el-text-color-regular);
}
.item:hover {
  background: var(--el-fill-color-light);
}
.item.active {
  background: #f6f8ff;
  color: #5369db;
  font-weight: 600;
  box-shadow: inset 3px 0 #6e80eb;
}
.item.fixed {
  color: var(--el-text-color-secondary);
}
.item.fixed.active {
  color: #5369db;
}
.item-icon {
  flex: none;
}
.item-text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  line-height: 1.35;
}
.item-label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.item-sub {
  font-size: 11px;
  opacity: 0.7;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.item-count {
  font-size: 12px;
  opacity: 0.7;
}
.item-more {
  opacity: 0;
  color: inherit;
}
.item:hover .item-more,
.item.active .item-more {
  opacity: 1;
}
.item-state {
  flex: none;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 1.5px solid var(--el-border-color);
}
.item-state.ok {
  border: none;
  font-size: 15px;
  color: #54b9a5;
}

.add-item {
  padding: 4px 10px;
}

/* drag to reorder */
.item:not(.fixed) {
  position: relative;
}
.grip {
  position: absolute;
  left: -18px;
  font-size: 13px;
  color: var(--el-text-color-placeholder);
  cursor: grab;
  opacity: 0;
}
.item:hover .grip {
  opacity: 1;
}
.chapter[draggable='true'] {
  cursor: grab;
}
.dragging {
  opacity: 0.4;
}
.drop-before {
  box-shadow: inset 0 2px 0 var(--el-color-primary);
}
.drop-after {
  box-shadow: inset 0 -2px 0 var(--el-color-primary);
}
.add-item-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--el-color-primary);
  cursor: pointer;
}
.divider {
  height: 1px;
  margin: 6px 10px;
  background: var(--el-border-color-lighter);
}

/* ---------- workspace ---------- */
.workspace {
  min-height: 460px;
  min-width: 0;
}
.empty {
  padding: 60px 0;
}
.ws-head {
  padding-bottom: 14px;
  margin-bottom: 18px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.crumb {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.ws-head h2 {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 8px 0 6px;
  font-size: 19px;
  letter-spacing: -0.3px;
}
.ws-desc {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.hint {
  margin-top: 20px;
  max-width: 760px;
}
.ws-foot {
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid var(--el-border-color-lighter);
  display: flex;
  justify-content: flex-end;
}
</style>
