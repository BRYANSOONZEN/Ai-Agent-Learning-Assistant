<script setup>
/*
 * Teacher 首页「待你确认」: what the model has finished and only counts after the teacher looks at it
 * (GET /api/teacher/todo): recognized knowledge points, AI dialogue drafts, AI-generated questions still
 * drafts, short answers whose two gradings disagree. Each row opens that page of the course builder.
 * Nothing is shown when there is nothing to confirm.
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getTeacherTodo } from '@/api/teacherCourse'

const router = useRouter()
const list = ref([])

const KIND = {
  kp: { label: '知识点', icon: 'Collection', tone: 'tone-orange' },
  feynman: { label: 'AI 对话', icon: 'ChatDotRound', tone: 'tone-blue' },
  exercise: { label: '题目', icon: 'EditPen', tone: 'tone-blue' },
  short: { label: '简答', icon: 'Edit', tone: 'tone-orange' },
}

function go(t) {
  router.push({ name: 'teacher-course-builder', params: { id: t.courseId }, query: { chapter: t.chapterId, view: t.view } })
}

onMounted(async () => {
  try {
    list.value = (await getTeacherTodo()) || []
  } catch {
    /* the request helper already showed the error */
  }
})
</script>

<template>
  <section v-if="list.length" class="board">
    <div class="sw-heading">
      <div>
        <span class="sw-eyebrow">TO CONFIRM</span>
        <h3>待你确认</h3>
      </div>
      <span class="muted">大模型做好的内容，你确认后才生效</span>
    </div>
    <ul class="rows">
      <li v-for="(t, i) in list" :key="i" class="row" @click="go(t)">
        <span class="sw-icon sm" :class="KIND[t.kind].tone">
          <el-icon :size="14"><component :is="KIND[t.kind].icon" /></el-icon>
        </span>
        <span class="main">
          <strong>{{ t.title }}</strong>
          <small>{{ t.courseName }} · {{ t.chapterTitle }}<template v-if="t.kind === 'kp'"> · {{ t.count }} 个知识点</template></small>
        </span>
        <span class="sw-tag orange">{{ KIND[t.kind].label }}</span>
        <el-icon class="arrow"><ArrowRight /></el-icon>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.board {
  display: grid;
  gap: 12px;
}
.rows {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 8px;
}
.row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: var(--el-bg-color);
  cursor: pointer;
}
.row:hover {
  border-color: var(--el-color-primary-light-5);
}
.main {
  display: grid;
  gap: 2px;
  flex: 1;
  min-width: 0;
}
.main small,
.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.arrow {
  color: var(--el-text-color-secondary);
}
</style>
