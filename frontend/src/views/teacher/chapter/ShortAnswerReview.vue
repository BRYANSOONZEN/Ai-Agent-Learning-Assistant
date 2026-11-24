<script setup>
/*
 * Short-answer submissions of one exercise item. The model judged each answer twice;
 * when the two disagree the answer waits here (待确认) and does not count toward mastery yet.
 * The teacher can confirm those, or change any judgement: tick each key point answered or not.
 * After the teacher's decision the answer counts with full weight (r = 1).
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getShortAnswers, reviewShortAnswer } from '@/api/teacherCourse'

const props = defineProps({ itemId: { type: Number, required: true } })

const list = ref([])
const editing = reactive({}) // recordId -> [bool]
const pending = computed(() => list.value.filter((a) => a.status === 'pending').length)

async function load() {
  list.value = await getShortAnswers(props.itemId)
}

function start(a) {
  editing[a.recordId] = a.points.map((p) => p.hit === true)
}

async function save(a) {
  const updated = await reviewShortAnswer(a.recordId, editing[a.recordId])
  Object.assign(a, updated)
  delete editing[a.recordId]
  ElMessage.success('已保存，掌握度已重新计算')
}

onMounted(load)
defineExpose({ load })
</script>

<template>
  <div v-if="list.length" class="sa">
    <div class="head">
      <strong>简答作答</strong>
      <span v-if="pending" class="sw-tag orange">待确认 {{ pending }}</span>
      <span class="muted">AI 判两次：一致的直接采用（权重 0.5）；不一致的等你确认。你确认或改判后权重为 1。</span>
    </div>
    <div v-for="a in list" :key="a.recordId" class="row" :class="{ pending: a.status === 'pending' }">
      <div class="who">
        <b>{{ a.studentName }}</b> <small>{{ a.studentNo }} · {{ a.submittedAt }}</small>
        <span class="sw-tag" :class="a.status === 'pending' ? 'orange' : a.reviewed ? 'blue' : 'teal'">
          {{ a.status === 'pending' ? '待确认' : a.reviewed ? '老师已确认' : 'AI 判' }}
        </span>
        <span v-if="a.status !== 'pending'" class="muted">得分 {{ Math.round((a.score || 0) * 100) }}%</span>
      </div>
      <div class="stem">{{ a.stem }}</div>
      <div class="answer">{{ a.answer }}</div>
      <ul>
        <li v-for="(p, i) in a.points" :key="i">
          <template v-if="editing[a.recordId]">
            <el-radio-group v-model="editing[a.recordId][i]" size="small">
              <el-radio-button :value="true">答到</el-radio-button>
              <el-radio-button :value="false">没答到</el-radio-button>
            </el-radio-group>
          </template>
          <span v-else :class="p.hit ? 'hit' : p.hit === false ? 'miss' : 'unsure'">
            {{ p.hit ? '答到' : p.hit === false ? '没答到' : '两次不一致' }}
          </span>
          要点 {{ i + 1 }}：{{ p.text }}
          <small v-if="p.quote" class="muted">（原话：“{{ p.quote }}”）</small>
        </li>
      </ul>
      <div class="ops">
        <template v-if="editing[a.recordId]">
          <el-button size="small" type="primary" @click="save(a)">保存</el-button>
          <el-button size="small" @click="delete editing[a.recordId]">取消</el-button>
        </template>
        <el-button v-else size="small" :type="a.status === 'pending' ? 'primary' : 'default'" @click="start(a)">
          {{ a.status === 'pending' ? '确认' : '改判' }}
        </el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.sa {
  margin-top: 18px;
  display: grid;
  gap: 10px;
}
.head {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.row {
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  font-size: 13px;
}
.row.pending {
  border-color: #f0c48a;
  background: #fffaf3;
}
.who {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}
.stem {
  margin-top: 6px;
  color: var(--el-text-color-secondary);
}
.answer {
  margin: 6px 0;
  padding: 6px 10px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
  white-space: pre-line;
}
ul {
  margin: 0;
  padding-left: 0;
  list-style: none;
  display: grid;
  gap: 4px;
}
.hit {
  color: #1f7a69;
  font-weight: 600;
}
.miss {
  color: var(--el-color-danger);
  font-weight: 600;
}
.unsure {
  color: #9a5a1c;
  font-weight: 600;
}
.ops {
  margin-top: 6px;
}
</style>
