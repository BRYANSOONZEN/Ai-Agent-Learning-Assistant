<script setup>
/*
 * One exercise item, student side: answer every question, then submit to see all results at once.
 * Six question types (docs/模块说明/练习与掌握度-设计.md):
 *   single_choice / judge — pick one; multiple — pick all that apply (all correct = right);
 *   two_tier — pick the answer, then the reason (both correct = right);
 *   fill — a word or phrase, compared with the accepted answers;
 *   short_answer — a paragraph, judged by the model twice against the key points
 *     (the two disagree → waiting for the teacher, not counted yet).
 * Wrong answers show the correct one, the analysis, and the misconception behind the chosen option or reason.
 * Below the score: how each knowledge point's mastery moved (学情诊断).
 */
import { computed, onBeforeUnmount, onMounted, reactive, ref, watchEffect } from 'vue'
import { ElMessage } from 'element-plus'
import { getQuestions, submitQuiz } from '@/api/learn'
import { LEVELS, percent } from '@/views/diagnosis/levels'
import { hold, release } from '@/stores/assistantFocus'

const props = defineProps({ itemId: { type: Number, required: true } })
const emit = defineEmits(['done'])

const LETTERS = 'ABCDEF'
const TYPE_LABEL = {
  single_choice: '单选',
  two_tier: '两段式：先选答案，再选理由',
  multiple: '多选：选出全部正确的',
  judge: '判断',
  fill: '填空',
  short_answer: '简答',
}
const questions = ref([])
const choice = reactive({}) // questionId -> letter (single / judge / two_tier answer)
const reason = reactive({}) // questionId -> letter (two_tier reason)
const multi = reactive({}) // questionId -> [letters]
const text = reactive({}) // questionId -> text (fill / short)
const result = ref(null)
const loading = ref(false)
const submitting = ref(false)

function answerOf(q) {
  switch (q.type) {
    case 'two_tier':
      return choice[q.id] && reason[q.id] ? `${choice[q.id]}|${reason[q.id]}` : ''
    case 'multiple':
      return (multi[q.id] || []).slice().sort().join('')
    case 'fill':
    case 'short_answer':
      return (text[q.id] || '').trim()
    default:
      return choice[q.id] || ''
  }
}

const answered = computed(() => questions.value.filter((q) => answerOf(q)).length)
const byId = computed(() => Object.fromEntries((result.value?.results || []).map((r) => [r.id, r])))
const hasShort = computed(() => questions.value.some((q) => q.type === 'short_answer'))

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
    return ElMessage.warning(`还有 ${questions.value.length - answered.value} 道题没做完`)
  }
  submitting.value = true
  try {
    const answers = Object.fromEntries(questions.value.map((q) => [q.id, answerOf(q)]))
    result.value = await submitQuiz(props.itemId, answers)
    emit('done')
    window.scrollTo({ top: 0, behavior: 'smooth' })
  } finally {
    submitting.value = false
  }
}

function retry() {
  for (const box of [choice, reason, multi, text]) Object.keys(box).forEach((k) => delete box[k])
  result.value = null
}

function toggleMulti(q, letter) {
  const list = multi[q.id] || []
  multi[q.id] = list.includes(letter) ? list.filter((x) => x !== letter) : [...list, letter]
}

// option style: before submitting = chosen; after = green correct, red wrong choice
function optionClass(q, letter) {
  const r = byId.value[q.id]
  if (q.type === 'multiple') {
    const picked = (multi[q.id] || []).includes(letter)
    if (!r) return { chosen: picked }
    return { right: r.answer.includes(letter), wrong: picked && !r.answer.includes(letter) }
  }
  if (!r) return { chosen: choice[q.id] === letter }
  return { right: r.answer === letter, wrong: r.chosen === letter && r.answer !== letter }
}

function reasonClass(q, letter) {
  const r = byId.value[q.id]
  if (!r) return { chosen: reason[q.id] === letter }
  return { right: r.reasonAnswer === letter, wrong: r.reasonChosen === letter && r.reasonAnswer !== letter }
}

function tag(r) {
  if (r.status === 'pending') return { type: 'warning', text: '等老师确认' }
  if (r.type === 'short_answer' && !r.correct) {
    return { type: r.score > 0 ? 'warning' : 'danger', text: `答到 ${Math.round(r.score * r.points.length)}/${r.points.length} 条要点` }
  }
  if (r.type === 'multiple' && !r.correct && r.score > 0) return { type: 'warning', text: `少选，得 ${r.score} 分` }
  return r.correct ? { type: 'success', text: '答对' } : { type: 'danger', text: '答错' }
}

// total score: 1 per question, multiple partial 0.5, short answer by key points
const scoreText = computed(() => {
  const s = result.value?.score ?? result.value?.correct ?? 0
  return Number.isInteger(s) ? String(s) : s.toFixed(1).replace(/\.0$/, '')
})

onMounted(load)

// the study assistant stays hidden while the quiz is being answered, and comes back after submitting
const focusKey = 'quiz-' + props.itemId
watchEffect(() => (result.value ? release(focusKey) : hold(focusKey)))
onBeforeUnmount(() => release(focusKey))
</script>

<template>
  <div v-loading="loading" class="quiz">
    <div v-if="result" class="score" :class="{ full: result.correct === result.total }">
      <div class="score-num">{{ scoreText }} / {{ result.total }} 分</div>
      <div>
        {{ result.correct === result.total ? '全部答对，继续下一项吧。' : '看看下面答错的题，读一下解析再试一次。' }}
        <span v-if="result.pending" class="muted">有 {{ result.pending }} 道简答两次判分不一致，等老师确认后再算进掌握度。</span>
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
    <p v-else-if="!result" class="tip">
      共 {{ questions.length }} 道题，全部做完后交卷，统一看结果。{{ hasShort ? '简答题由 AI 对照要点判分，交卷后要等几秒。' : '' }}
    </p>

    <div v-for="(q, i) in questions" :key="q.id" class="question">
      <div class="stem">
        <span class="q-num">{{ i + 1 }}</span>
        <span>
          <small class="qtype">{{ TYPE_LABEL[q.type] || '单选' }}</small>
          {{ q.stem }}
        </span>
        <el-tag v-if="byId[q.id]" :type="tag(byId[q.id]).type" size="small">{{ tag(byId[q.id]).text }}</el-tag>
      </div>

      <!-- choice types -->
      <div v-if="q.options.length" class="options">
        <div v-if="q.type === 'two_tier'" class="part">第一步：选答案</div>
        <button
          v-for="(opt, j) in q.options"
          :key="j"
          type="button"
          class="option"
          :class="optionClass(q, LETTERS[j])"
          :disabled="!!result"
          @click="q.type === 'multiple' ? toggleMulti(q, LETTERS[j]) : (choice[q.id] = LETTERS[j])"
        >
          <span class="letter" :class="{ square: q.type === 'multiple' }">{{ LETTERS[j] }}</span>
          <span>{{ opt }}</span>
        </button>
        <template v-if="q.type === 'two_tier'">
          <div class="part">第二步：为什么？</div>
          <button
            v-for="(opt, j) in q.reasons"
            :key="'r' + j"
            type="button"
            class="option"
            :class="reasonClass(q, LETTERS[j])"
            :disabled="!!result"
            @click="reason[q.id] = LETTERS[j]"
          >
            <span class="letter">{{ LETTERS[j] }}</span>
            <span>{{ opt }}</span>
          </button>
        </template>
      </div>

      <!-- text types -->
      <div v-else class="options">
        <el-input v-if="q.type === 'fill'" v-model="text[q.id]" :disabled="!!result" maxlength="50" placeholder="填一个词或短语" />
        <el-input
          v-else
          v-model="text[q.id]"
          :disabled="!!result"
          type="textarea"
          :autosize="{ minRows: 3, maxRows: 8 }"
          maxlength="800"
          show-word-limit
          placeholder="用自己的话回答，说清楚理由"
        />
      </div>

      <div v-if="byId[q.id]" class="explain">
        <template v-if="q.type === 'short_answer'">
          <p v-for="p in byId[q.id].points" :key="p.no" :class="p.hit ? 'hit' : p.hit === false ? 'miss' : 'unsure'">
            要点 {{ p.no }}：{{ p.text }} ——
            {{ p.hit ? '答到了' : p.hit === false ? '没答到' : '两次判分不一致，等老师确认' }}
            <span v-if="p.hit && p.quote" class="quote">（你写的：“{{ p.quote }}”）</span>
          </p>
          <p v-if="byId[q.id].reference">参考答案：{{ byId[q.id].reference }}</p>
        </template>
        <template v-else-if="!byId[q.id].correct">
          <p v-if="q.type === 'fill'">都算对的答案：{{ byId[q.id].accepts.join('、') }}</p>
          <p v-else-if="q.type === 'two_tier'">正确答案：{{ byId[q.id].answer }}，正确理由：{{ byId[q.id].reasonAnswer }}</p>
          <p v-else>正确答案：{{ byId[q.id].answer }}</p>
        </template>
        <p v-if="byId[q.id].note" class="note">你可能是这样想的：{{ byId[q.id].note }}</p>
        <p v-if="byId[q.id].analysis">解析：{{ byId[q.id].analysis }}</p>
      </div>
    </div>

    <div v-if="!result && questions.length" class="actions">
      <span class="muted">已做 {{ answered }} / {{ questions.length }}</span>
      <el-button type="primary" :loading="submitting" @click="submit">
        {{ submitting && hasShort ? 'AI 正在判简答…' : '交卷' }}
      </el-button>
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
.qtype {
  display: inline-block;
  margin-right: 6px;
  padding: 0 6px;
  border-radius: 4px;
  background: var(--el-fill-color);
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: normal;
}
.part {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.letter.square {
  border-radius: 4px;
}
.hit {
  color: #1f7a69;
}
.miss {
  color: var(--el-color-danger);
}
.unsure {
  color: #9a5a1c;
}
.quote {
  color: var(--el-text-color-secondary);
}
.actions {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 12px;
}
</style>
