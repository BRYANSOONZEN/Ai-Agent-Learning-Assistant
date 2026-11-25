<script setup>
/*
 * 覆盖检查: for every knowledge point of the chapter, how many confirmed questions there are
 * per assessment type and per question type (all exercise items of the chapter together),
 * plus drafts still waiting. Fewer than 3 questions → mastery is not reliable yet.
 */
import { onMounted, ref } from 'vue'
import { getExerciseCoverage } from '@/api/teacherCourse'
import { ASSESS_TYPES, CUSTOM_ASSESS, QUESTION_TYPES } from './exerciseLabels'

const props = defineProps({ chapterId: { type: Number, required: true } })
const rows = ref([])

async function load() {
  rows.value = await getExerciseCoverage(props.chapterId)
}
onMounted(load)
defineExpose({ load })

function typesText(row) {
  return QUESTION_TYPES.filter((t) => row.byType[t.value]).map((t) => `${t.label} ${row.byType[t.value]}`).join('、') || '—'
}
</script>

<template>
  <div v-if="rows.length" class="cov">
    <div class="head">
      <strong>覆盖检查</strong>
      <span class="muted">本章所有练习合计，只算已确认的题；每个知识点建议至少 3 道</span>
    </div>
    <el-table :data="rows" size="small" border>
      <el-table-column prop="name" label="知识点" min-width="150" show-overflow-tooltip />
      <el-table-column v-for="a in [...ASSESS_TYPES, CUSTOM_ASSESS]" :key="a.value" :label="a.label" width="72" align="center">
        <template #default="{ row }">
          <span :class="{ zero: !row.byAssess[a.value] }">{{ row.byAssess[a.value] }}</span>
        </template>
      </el-table-column>
      <el-table-column label="合计 / 提醒" min-width="170">
        <template #default="{ row }">
          <b>{{ row.total }}</b>
          <span v-if="row.warning" class="warn"> {{ row.warning }}</span>
          <span v-if="row.drafts" class="muted">（草稿 {{ row.drafts }}）</span>
          <span v-if="!row.warning && !row.drafts" class="ok"> 够了</span>
          <div class="muted">{{ typesText(row) }}</div>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
.cov {
  margin-bottom: 14px;
}
.head {
  display: flex;
  gap: 10px;
  align-items: baseline;
  margin-bottom: 6px;
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.zero {
  color: var(--el-text-color-placeholder);
}
.warn {
  color: #b23b2f;
}
.ok {
  color: #1f7a69;
}
</style>
