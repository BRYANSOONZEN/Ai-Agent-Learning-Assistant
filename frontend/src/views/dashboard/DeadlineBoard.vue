<script setup>
/*
 * Teacher 首页: every exercise / AI dialogue with a deadline in my published courses.
 * Overdue first. Each row: how many students finished, how many were late, and who has not done it yet.
 * Nothing is shown when no item has a deadline.
 */
import { onMounted, ref } from 'vue'
import { getTeacherDeadlines } from '@/api/teacherCourse'
import { itemType } from '@/views/teacher/courseModules'
import { fmtDue, leftText, toDate } from '@/utils/deadline'

const list = ref([])
const opened = ref([]) // itemIds whose "还没做" list is open

const STATE = {
  overdue: { label: '已过期', cls: 'red' },
  soon: { label: '快到期', cls: 'orange' },
  later: { label: '未到期', cls: 'gray' },
}

function when(d) {
  const ms = toDate(d.dueAt) - Date.now()
  return ms > 0 ? leftText(ms) : d.allowLate ? '过期后可补做' : '过期后不能再交'
}

function toggle(id) {
  opened.value = opened.value.includes(id) ? opened.value.filter((x) => x !== id) : [...opened.value, id]
}

onMounted(async () => {
  try {
    list.value = await getTeacherDeadlines()
  } catch {
    /* the request helper already showed the error */
  }
})
</script>

<template>
  <section v-if="list.length" class="board">
    <div class="sw-heading">
      <div>
        <span class="sw-eyebrow">DEADLINES</span>
        <h3>截止情况</h3>
      </div>
      <span class="muted">只列已发布、设了截止时间的练习和 AI 对话</span>
    </div>
    <ul class="rows">
      <li v-for="d in list" :key="d.itemId" class="row">
        <div class="line">
          <span class="sw-icon sm" :class="d.state === 'overdue' ? 'tone-orange' : 'tone-blue'">
            <el-icon :size="14"><component :is="itemType(d.type)?.icon || 'EditPen'" /></el-icon>
          </span>
          <span class="main">
            <strong>{{ d.title }}</strong>
            <small>{{ d.courseName }} · {{ d.chapterTitle }} · 截止 {{ fmtDue(d.dueAt) }}，{{ when(d) }}</small>
          </span>
          <span class="sw-tag" :class="STATE[d.state].cls">{{ STATE[d.state].label }}</span>
        </div>
        <div class="line">
          <span class="count">
            已完成 <b>{{ d.done }}</b> / {{ d.total }} 人<template v-if="d.late">，其中迟交 {{ d.late }} 人</template>
          </span>
          <span class="sw-progress bar"><span :style="{ width: (d.total ? (d.done / d.total) * 100 : 0) + '%' }" /></span>
          <el-button v-if="d.missing.length" link type="primary" @click="toggle(d.itemId)">
            {{ opened.includes(d.itemId) ? '收起' : `还没做 ${d.missing.length} 人` }}
          </el-button>
          <span v-else class="muted">全部完成</span>
        </div>
        <div v-if="opened.includes(d.itemId)" class="missing">
          <span v-for="s in d.missing" :key="s.id" class="who">{{ s.name }}（{{ s.username }}）</span>
        </div>
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
  gap: 10px;
}
.row {
  display: grid;
  gap: 8px;
  padding: 12px 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: var(--el-bg-color);
}
.line {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
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
.count {
  font-size: 13px;
}
.bar {
  flex: 1;
  min-width: 120px;
}
.missing {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.who {
  padding: 2px 8px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
  font-size: 12px;
}
</style>
