<script setup>
/*
 * 补练题 inside the study assistant (automatic intervention, docs/模块说明/小助手-自动干预与聊天.md section 4).
 * The questions are generated on the spot from this student's own mistakes; the program grades them right away.
 * Only types with an exact answer the program can grade: single choice, judge, multiple.
 * mode 'extend' (E1 拓展): a mastered point, harder questions of the assessment type the student picks;
 * as many rounds as they like; does not change mastery.
 * Up to 2 rounds; recovered (>= 2 questions, average >= 0.8) turns the point from red to orange
 * (was wrong once, keep an eye on it); otherwise it goes to the teacher.
 */
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { startExtend, startRemedy, submitExtend, submitRemedy } from '@/api/assistant'

const props = defineProps({
  kpId: { type: Number, required: true },
  mode: { type: String, default: 'remedy' }, // 'remedy' 补练 | 'extend' 拓展
  assessType: { type: String, default: 'scenario' },
  // 学习推荐页里做：出新一轮要回到小助手（要先经过小助手），这里不显示“再来一轮 / 再来一组”
  noContinue: { type: Boolean, default: false },
})
const emit = defineEmits(['changed'])

const view = ref(null)
const result = ref(null)
const loading = ref(false)
const choice = reactive({})
const reason = reactive({})
const multi = reactive({})

const LETTERS = 'ABCDEF'

async function start() {
  loading.value = true
  result.value = null
  for (const box of [choice, reason, multi]) Object.keys(box).forEach((k) => delete box[k])
  try {
    view.value = props.mode === 'extend' ? await startExtend(props.kpId, props.assessType) : await startRemedy(props.kpId)
  } finally {
    loading.value = false
  }
}

function answerOf(q) {
  if (q.type === 'multiple') return (multi[q.id] || []).slice().sort().join('')
  if (q.type === 'two_tier') return `${choice[q.id] || ''}|${reason[q.id] || ''}`
  return choice[q.id] || ''
}
const answered = computed(() => (view.value?.questions || []).every((q) =>
  q.type === 'multiple' ? (multi[q.id] || []).length : q.type === 'two_tier' ? choice[q.id] && reason[q.id] : choice[q.id]))

function toggleMulti(q, l) {
  const list = multi[q.id] || []
  multi[q.id] = list.includes(l) ? list.filter((x) => x !== l) : [...list, l]
}

async function submit() {
  if (!answered.value) return ElMessage.warning('每道题都要作答')
  loading.value = true
  try {
    const answers = Object.fromEntries(view.value.questions.map((q) => [q.id, answerOf(q)]))
    result.value = props.mode === 'extend' ? await submitExtend(props.kpId, answers) : await submitRemedy(props.kpId, answers)
    emit('changed')
  } finally {
    loading.value = false
  }
}

const byId = computed(() => Object.fromEntries((result.value?.results || []).map((r) => [r.id, r])))
function tag(r) {
  if (r.correct) return { type: 'success', text: '答对' }
  if (r.score > 0) return { type: 'warning', text: `少选，得 ${r.score} 分` }
  return { type: 'danger', text: '答错' }
}

start()
</script>

<template>
  <div v-loading="loading" class="remedy" element-loading-text="正在根据你的错题出题……">
    <template v-if="view">
      <p class="say">{{ result ? result.text : view.text }}</p>
      <p v-if="view.questions.length && mode !== 'extend'" class="muted">第 {{ view.round }} / {{ view.maxRounds }} 轮</p>

      <div v-for="(q, i) in view.questions" :key="q.id" class="q">
        <p class="stem">{{ i + 1 }}. {{ q.stem }}</p>
        <template v-if="q.type === 'multiple'">
          <label v-for="(o, k) in q.options" :key="k" class="opt">
            <input type="checkbox" :disabled="!!result" :checked="(multi[q.id] || []).includes(LETTERS[k])" @change="toggleMulti(q, LETTERS[k])" />
            {{ LETTERS[k] }}. {{ o }}
          </label>
        </template>
        <template v-else>
          <label v-for="(o, k) in q.options" :key="k" class="opt">
            <input v-model="choice[q.id]" type="radio" :name="'q' + q.id" :value="LETTERS[k]" :disabled="!!result" />
            {{ LETTERS[k] }}. {{ o }}
          </label>
        </template>
        <template v-if="q.type === 'two_tier'">
          <p class="muted">理由：</p>
          <label v-for="(o, k) in q.reasons" :key="'r' + k" class="opt">
            <input v-model="reason[q.id]" type="radio" :name="'r' + q.id" :value="LETTERS[k]" :disabled="!!result" />
            {{ LETTERS[k] }}. {{ o }}
          </label>
        </template>
        <div v-if="byId[q.id]" class="res">
          <el-tag :type="tag(byId[q.id]).type" size="small">{{ tag(byId[q.id]).text }}</el-tag>
          <span v-if="!byId[q.id].correct">正确答案 {{ byId[q.id].answer }}<template v-if="byId[q.id].reasonAnswer">｜理由 {{ byId[q.id].reasonAnswer }}</template></span>
          <p v-if="byId[q.id].note" class="note">你可能是这样想的：{{ byId[q.id].note }}</p>
          <p class="muted">{{ byId[q.id].analysis }}</p>
        </div>
      </div>

      <div class="actions">
        <el-button v-if="view.questions.length && !result" type="primary" size="small" @click="submit">交补练题</el-button>
        <el-button v-if="result?.canContinue && !noContinue" size="small" type="primary" plain @click="start">{{ mode === 'extend' ? '再来一组' : '再来一轮' }}</el-button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.remedy {
  min-height: 60px;
  margin-top: 8px;
  padding: 10px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
}
.say {
  margin: 0 0 4px;
}
.q {
  margin-top: 10px;
}
.stem {
  margin: 0 0 4px;
  font-weight: 600;
}
.opt {
  display: block;
  margin: 2px 0;
  font-size: 13px;
  cursor: pointer;
}
.res {
  margin-top: 4px;
  font-size: 12px;
}
.res span {
  margin-left: 6px;
}
.note {
  margin: 4px 0 0;
  color: var(--el-color-warning-dark-2);
}
.muted {
  margin: 2px 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.actions {
  margin-top: 10px;
}
</style>
