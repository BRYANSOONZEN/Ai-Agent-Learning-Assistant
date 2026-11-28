<script setup>
/*
 * 站内消息: a bell in the top bar of every page, so reminders do not depend on opening the home page.
 * It gathers what the platform already knows (no new backend table):
 *   student — deadlines due within 3 days or overdue (GET /api/learn/deadlines), the teacher's supplementary
 *             explanation + new questions not done yet (GET /api/assistant/pushes), teacher reminders (GET /api/assistant/notices)
 *   teacher — things waiting for confirmation (GET /api/teacher/todo), class-analysis cards to handle
 *             (GET /api/class-analysis/open-count), overdue items with students who have not done them (GET /api/teacher/deadlines)
 * Read / unread is remembered per user in this browser (localStorage); refreshed every 2 minutes and on route change.
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { getMyDeadlines } from '@/api/learn'
import { getMyPushes, getNotices } from '@/api/assistant'
import { getTeacherDeadlines, getTeacherTodo } from '@/api/teacherCourse'
import { getOpenIssueCount } from '@/api/classAnalysis'
import { fmtDue } from '@/utils/deadline'

const userStore = useUserStore()
const router = useRouter()
const route = useRoute()
const items = ref([]) // { key, title, text, to }
const KEY = computed(() => 'se-messages-read-' + (userStore.user?.id ?? ''))

function readKeys() {
  try {
    return JSON.parse(localStorage.getItem(KEY.value) || '[]')
  } catch {
    return []
  }
}
const read = ref(readKeys())
const unread = computed(() => items.value.filter((m) => !read.value.includes(m.key)).length)

function markAllRead() {
  read.value = [...new Set([...read.value, ...items.value.map((m) => m.key)])].slice(-300)
  try {
    localStorage.setItem(KEY.value, JSON.stringify(read.value))
  } catch {
    /* private window: unread just comes back next time */
  }
}

async function safe(p) {
  try {
    return (await p) || []
  } catch {
    return []
  }
}

async function load() {
  const role = userStore.role
  const list = []
  if (role === 'student') {
    const [deadlines, pushes, notices] = await Promise.all([safe(getMyDeadlines()), safe(getMyPushes()), safe(getNotices())])
    for (const d of deadlines.filter((x) => x.state !== 'later')) {
      list.push({
        key: `dl:${d.itemId}:${d.state}`,
        title: d.state === 'overdue' ? '已过截止时间' : '快到截止时间',
        text: `${d.title}（${d.courseName}）截止 ${fmtDue(d.dueAt)}`,
        to: { name: 'course-detail', params: { id: d.courseId }, query: { chapter: d.chapterId, view: 'item:' + d.itemId } },
      })
    }
    for (const p of pushes.filter((x) => !x.done)) {
      list.push({ key: 'push:' + p.id, title: '老师推送了补充讲解和新题', text: `「${p.knowledgePointName}」，在右下角的学习助手里做`, to: null })
    }
    notices.forEach((n, i) => list.push({ key: `note:${i}:${n}`, title: '老师提醒', text: n, to: null }))
  } else if (role === 'teacher') {
    const [todo, open, deadlines] = await Promise.all([safe(getTeacherTodo()), safe(getOpenIssueCount()), safe(getTeacherDeadlines())])
    for (const t of todo) {
      list.push({
        key: `todo:${t.kind}:${t.chapterId}:${t.view}:${t.count}`,
        title: '待你确认',
        text: `${t.title}（${t.courseName} · ${t.chapterTitle}）`,
        to: { name: 'teacher-course-builder', params: { id: t.courseId }, query: { chapter: t.chapterId, view: t.view } },
      })
    }
    if (open > 0) {
      list.push({ key: 'issues:' + open, title: '全班分析有待处理的', text: `${open} 项（题目检查、要点 × 考查类型）`, to: { name: 'intervention' } })
    }
    for (const d of deadlines.filter((x) => x.state === 'overdue' && x.missing?.length)) {
      list.push({
        key: `late:${d.itemId}:${d.missing.length}`,
        title: '截止后还有人没做',
        text: `${d.title}：${d.missing.length} 人没做`,
        to: { name: 'dashboard' },
      })
    }
  }
  items.value = list
}

function go(m) {
  if (m.to) router.push(m.to)
}

let timer
onMounted(() => {
  load()
  timer = setInterval(load, 120000)
})
onBeforeUnmount(() => clearInterval(timer))
watch(() => route.fullPath, load)
</script>

<template>
  <el-popover
    v-if="userStore.role === 'student' || userStore.role === 'teacher'" placement="bottom-end" :width="360"
    trigger="click" @show="markAllRead"
  >
    <template #reference>
      <button type="button" class="bell" :aria-label="`消息，${unread} 条未读`">
        <el-badge :value="unread" :hidden="!unread" :max="99">
          <el-icon :size="18"><Bell /></el-icon>
        </el-badge>
      </button>
    </template>
    <div class="msg-head">消息</div>
    <el-empty v-if="!items.length" description="暂时没有新消息" :image-size="60" />
    <ul v-else class="msgs">
      <li v-for="m in items" :key="m.key" :class="{ link: !!m.to }" @click="go(m)">
        <b>{{ m.title }}</b>
        <span>{{ m.text }}</span>
      </li>
    </ul>
  </el-popover>
</template>

<style scoped>
.bell {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: var(--app-text-secondary);
  cursor: pointer;
}
.bell:hover {
  background: var(--el-fill-color-light);
}
.msg-head {
  font-weight: 600;
  margin-bottom: 8px;
}
.msgs {
  list-style: none;
  margin: 0;
  padding: 0;
  max-height: 360px;
  overflow-y: auto;
  display: grid;
  gap: 6px;
}
.msgs li {
  display: grid;
  gap: 2px;
  padding: 8px 10px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  font-size: 13px;
}
.msgs li.link {
  cursor: pointer;
}
.msgs li.link:hover {
  background: var(--el-color-primary-light-9);
}
.msgs span {
  color: var(--app-text-secondary);
  font-size: 12px;
}
</style>
