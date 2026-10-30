<script setup>
/*
 * One exercise item, student side: answer every question, then submit to see all results at once.
 * Wrong answers show the correct one, the analysis, and the teacher's 误解说明 for the chosen option.
 * Below the score: how each knowledge point's mastery moved (学情诊断).
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getQuestions, submitQuiz } from '@/api/learn'
import { LEVELS, percent } from '@/views/diagnosis/levels'

const props = defineProps({ itemId: { type: Number, required: true } })
const emit = defineEmits(['done'])

const LETTERS = 'ABCDEF'
const questions = ref([])
const answers = reactive({}) // questionId -> letter
const result = ref(null)
const loading = ref(false)
const submitting = ref(false)

const answered = computed(() => questions.value.filter((q) => answers[q.id]).length)
const byId = computed(() => Object.fromEntries((result.value?.results || []).map((r) => [r.id, r])))

async function load() {
  loading.value = true
  try {
    questions.value = await getQuestions(props.itemId)
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (answered.value < questions.value.length) {
    return ElMessage.warning(`还有 ${questions.value.length - answered.value} 道题没做`)
  }
  submitting.value = true
  try {
    result.value = await submitQuiz(props.itemId, answers)
    emit('done')
    window.scrollTo({ top: 0, behavior: 'smooth' })
  } finally {
    submitting.value = false
  }
}

function retry() {
  Object.keys(answers).forEach((k) => delete answers[k])
  result.value = null
}

// option style after submitting: green = correct answer, red = the wrong one chosen
function optionClass(q, letter) {
  const r = byId.value[q.id]
  if (!r) return { chosen: answers[q.id] === letter }
  return { right: r.answer === letter, wrong: r.chosen === letter && !r.correct }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="quiz">
    <div v-if="result" class="score" :class="{ full: result.correct === result.total }">
      <div class="score-num">{{ result.correct }} / {{ result.total }}</div>
      <div>
        {{ result.correct === result.total ? '全部答对，继续下一项吧。' : '看看下面答错的题，读一下解析再试一次。' }}
      </div>
      <el-button @click="retry">再做一次</el-button>
    </div>
    <div v-if="result?.masteryChanges?.length" class="changes">
      <span class="changes-title">知识点掌握度</span>
      <span v-for="c in result.masteryChanges" :key="c.knowledgePointId" class="change">
        <span class="kp">{{ c.name }}</span>
        <span class="from">{{ percent(c.before) }}</span>
        <el-icon><Right /></el-icon>
        <b>{{ percent(c.after) }}</b>
        <span class="lv" :class="c.level">{{ LEVELS[c.level].label }}</span>
      </span>
      <router-link :to="{ name: 'diagnosis' }" class="to-diagnosis">看学情诊断</router-link>
    </div>
    <p v-else-if="!result" class="tip">共 {{ questions.length }} 道单选题，全部做完后交卷，统一看结果。</p>

    <div v-for="(q, i) in questions" :key="q.id" class="question">
      <div class="stem">
        <span class="q-num">{{ i + 1 }}</span>
        <span>{{ q.stem }}</span>
        <el-tag v-if="byId[q.id]" :type="byId[q.id].correct ? 'success' : 'danger'" size="small">
          {{ byId[q.id].correct ? '答对' : '答错' }}
        </el-tag>
      </div>
      <div class="options">
        <button
          v-for="(opt, j) in q.options"
          :key="j"
          type="button"
          class="option"
          :class="optionClass(q, LETTERS[j])"
          :disabled="!!result"
          @click="answers[q.id] = LETTERS[j]"
        >
          <span class="letter">{{ LETTERS[j] }}</span>
          <span>{{ opt }}</span>
        </button>
      </div>
      <div v-if="byId[q.id]" class="explain">
        <p v-if="!byId[q.id].correct">正确答案：{{ byId[q.id].answer }}</p>
        <p v-if="byId[q.id].note" class="note">你选的 {{ byId[q.id].chosen }} 可能是这样想的：{{ byId[q.id].note }}</p>
        <p v-if="byId[q.id].analysis">解析：{{ byId[q.id].analysis }}</p>
      </div>
    </div>

    <div v-if="!result && questions.length" class="actions">
      <span class="muted">已做 {{ answered }} / {{ questions.length }}</span>
      <el-button type="primary" :loading="submitting" @click="submit">交卷</el-button>
    </div>
    <el-empty v-if="!loading && !questions.length" description="这次练习还没有题目" />
  </div>
</template>

<style scoped>
.quiz {
  max-width: 760px;
}
.tip,
.muted {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.score {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 14px 16px;
  margin-bottom: 16px;
  border-radius: 8px;
  background: var(--el-color-warning-light-9);
}
.score.full {
  background: var(--el-color-success-light-9);
}
.changes {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 18px;
  margin: -6px 0 16px;
  padding: 12px 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  font-size: 13px;
}
.changes-title {
  color: var(--el-text-color-secondary);
}
.change {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.change .kp {
  font-weight: 600;
}
.change .from {
  color: var(--el-text-color-secondary);
}
.lv {
  padding: 0 6px;
  border-radius: 5px;
  font-size: 12px;
  background: var(--el-fill-color);
}
.lv.good {
  background: var(--el-color-success-light-9);
  color: #1f7a69;
}
.lv.medium {
  background: var(--el-color-warning-light-9);
  color: #9a5a1c;
}
.lv.weak {
  background: var(--el-color-danger-light-9);
  color: #b23b2f;
}
.to-diagnosis {
  margin-left: auto;
  color: var(--app-primary);
}
.score-num {
  font-size: 24px;
  font-weight: 700;
}
.score > div:nth-child(2) {
  flex: 1;
}
.question {
  padding: 16px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.stem {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  line-height: 1.6;
  font-weight: 500;
}
.q-num {
  flex: none;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--el-fill-color);
  font-size: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-top: 2px;
}
.options {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 12px 0 0 32px;
}
.option {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  background: var(--el-bg-color);
  font: inherit;
  text-align: left;
  cursor: pointer;
}
.option:hover:not(:disabled) {
  border-color: var(--el-color-primary-light-5);
}
.option:disabled {
  cursor: default;
  color: var(--el-text-color-primary);
}
.option.chosen {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}
.option.right {
  border-color: var(--el-color-success);
  background: var(--el-color-success-light-9);
}
.option.wrong {
  border-color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
}
.letter {
  flex: none;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  border: 1px solid var(--el-border-color);
  font-size: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.explain {
  margin: 10px 0 0 32px;
  padding: 10px 12px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
  font-size: 13px;
  line-height: 1.6;
}
.explain p {
  margin: 0;
}
.explain p + p {
  margin-top: 4px;
}
.note {
  color: var(--el-color-danger);
}
.actions {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 12px;
}
</style>
