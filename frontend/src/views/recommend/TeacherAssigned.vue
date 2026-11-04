<script setup>
/*
 * 老师布置 (top of 学习推荐): interventions the teacher approved for me.
 *   A 变式练习 — the questions, answered here like the other recommended ones
 *   B 复习 + 重做对话 — links to the reading and to the chapter's AI dialogue
 *   A + B — B first; the questions only show up once the review step is done
 * Recently finished ones are listed with their before -> after effect.
 */
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getMyAssignments } from '@/api/intervention'
import { practiceRecommended } from '@/api/recommend'
import { valueText } from '@/views/intervention/interventionLabels'

const emit = defineEmits(['changed', 'loaded'])
const router = useRouter()
const LETTERS = 'ABCDEF'

const data = ref(null)
const chosen = reactive({}) // exerciseId -> letter
const results = reactive({}) // exerciseId -> graded result
const busy = ref(null)

async function load() {
  data.value = await getMyAssignments()
  emit('loaded', data.value.running.length)
}

// after answering: reload the progress here, and let the page reload its own list (mastery changed)
async function refresh() {
  for (const k of Object.keys(chosen)) delete chosen[k]
  for (const k of Object.keys(results)) delete results[k]
  await load()
  emit('changed')
}

function openItem(courseId, chapterId, itemId) {
  router.push({ name: 'course-detail', params: { id: courseId }, query: { chapter: chapterId, view: 'item:' + itemId } })
}

async function submit(ex) {
  if (!chosen[ex.exerciseId]) return ElMessage.warning('先选一个答案')
  busy.value = ex.exerciseId
  try {
    const r = await practiceRecommended({ [ex.exerciseId]: chosen[ex.exerciseId] })
    results[ex.exerciseId] = r.results[0]
  } finally {
    busy.value = null
  }
}

function optionClass(ex, letter) {
  const r = results[ex.exerciseId]
  if (!r) return { chosen: chosen[ex.exerciseId] === letter }
  return { right: r.answer === letter, wrong: r.chosen === letter && !r.correct }
}

const allAnswered = (iv) => iv.exercises.length && iv.exercises.every((e) => e.answered || results[e.exerciseId])

defineExpose({ load })
onMounted(load)
</script>

<template>
  <section v-if="data && (data.running.length || data.done.length)" class="assigned app-card">
    <div class="sw-heading">
      <div>
        <span class="sw-eyebrow">FROM YOUR TEACHER</span>
        <h3>老师布置</h3>
      </div>
      <button v-if="Object.keys(results).length" type="button" class="sw-link" @click="refresh">
        <el-icon><Refresh /></el-icon>刷新进度
      </button>
    </div>

    <article v-for="iv in data.running" :key="iv.id" class="task">
      <header>
        <b>「{{ iv.knowledgePointName }}」</b>
        <span v-if="iv.type === 'REVIEW_PRACTICE'" class="sw-tag purple">第 {{ iv.step }} / 2 步</span>
      </header>
      <blockquote class="note">{{ iv.note }}</blockquote>

      <!-- review: reading + redo the AI dialogue -->
      <div v-if="iv.items.length || iv.dialogue" class="todo">
        <button
          v-for="i in iv.items"
          :key="i.itemId"
          type="button"
          class="todo-row"
          :class="{ done: i.viewed }"
          @click="openItem(i.courseId, i.chapterId, i.itemId)"
        >
          <el-icon><component :is="i.viewed ? 'CircleCheckFilled' : i.type === 'video' ? 'VideoPlay' : 'Document'" /></el-icon>
          <span>复习：{{ i.title }}</span>
          <small>{{ i.viewed ? '已看' : '去看' }}</small>
        </button>
        <button
          v-if="iv.dialogue"
          type="button"
          class="todo-row"
          :class="{ done: iv.dialogue.finished }"
          @click="openItem(iv.dialogue.courseId, iv.dialogue.chapterId, iv.dialogue.itemId)"
        >
          <el-icon><component :is="iv.dialogue.finished ? 'CircleCheckFilled' : 'ChatDotRound'" /></el-icon>
          <span>重新做：{{ iv.dialogue.title }}</span>
          <small>{{ iv.dialogue.finished ? '已完成' : '去做' }}</small>
        </button>
        <p v-if="iv.type === 'REVIEW_PRACTICE' && iv.step === 1" class="later">做完上面两项，这里会出现几道练习。</p>
      </div>

      <!-- practice: answered one at a time -->
      <div v-for="(ex, n) in iv.exercises" :key="ex.exerciseId" class="question">
        <div class="stem">
          <span class="q-num">{{ n + 1 }}</span>
          <span class="stem-text">{{ ex.stem }}</span>
          <span v-if="ex.answered && !results[ex.exerciseId]" class="sw-tag gray">已做过</span>
        </div>
        <template v-if="!ex.answered || results[ex.exerciseId]">
          <div class="options">
            <button
              v-for="(opt, j) in ex.options"
              :key="j"
              type="button"
              class="option"
              :class="optionClass(ex, LETTERS[j])"
              :disabled="!!results[ex.exerciseId]"
              @click="chosen[ex.exerciseId] = LETTERS[j]"
            >
              <span class="letter">{{ LETTERS[j] }}</span>
              <span>{{ opt }}</span>
            </button>
          </div>
          <div v-if="results[ex.exerciseId]" class="explain" :class="{ ok: results[ex.exerciseId].correct }">
            <p class="verdict">
              {{ results[ex.exerciseId].correct ? '答对了。' : `答错了，正确答案是 ${results[ex.exerciseId].answer}。` }}
            </p>
            <p v-if="results[ex.exerciseId].note" class="wrong-note">
              你选的 {{ results[ex.exerciseId].chosen }} 可能是这样想的：{{ results[ex.exerciseId].note }}
            </p>
            <p v-if="results[ex.exerciseId].analysis">解析：{{ results[ex.exerciseId].analysis }}</p>
          </div>
          <div v-else class="q-actions">
            <el-button
              type="primary"
              size="small"
              :loading="busy === ex.exerciseId"
              :disabled="!chosen[ex.exerciseId]"
              @click="submit(ex)"
            >
              提交这道
            </el-button>
          </div>
        </template>
      </div>
      <p v-if="allAnswered(iv)" class="later">都做完了，点右上角「刷新进度」看看结果。</p>
    </article>

    <div v-if="data.done.length" class="finished">
      <span class="label">最近完成</span>
      <p v-for="iv in data.done" :key="iv.id">
        「{{ iv.knowledgePointName }}」{{ valueText(iv.type, iv.beforeValue) }}
        <el-icon><Right /></el-icon>
        <b>{{ valueText(iv.type, iv.afterValue) }}</b>
        <span class="sw-tag" :class="iv.effective ? 'teal' : 'orange'">{{ iv.effective ? '已经掌握' : '还要再练' }}</span>
      </p>
    </div>
  </section>
</template>

<style scoped>
.assigned {
  margin-bottom: 18px;
  padding: 20px 24px;
  border-left: 3px solid #6e80eb;
}
.task {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.task header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
}
.note {
  margin: 10px 0 0;
  padding: 10px 14px;
  border-radius: 10px;
  background: #f6f8ff;
  color: var(--app-text-secondary);
  line-height: 1.6;
}
.todo {
  display: grid;
  gap: 6px;
  margin-top: 12px;
}
.todo-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 12px;
  border: 1px solid var(--app-border);
  border-radius: 8px;
  background: var(--el-bg-color);
  font: inherit;
  text-align: left;
  cursor: pointer;
}
.todo-row span {
  flex: 1;
  min-width: 0;
}
.todo-row small {
  color: var(--app-primary);
}
.todo-row.done {
  color: var(--app-text-muted);
}
.todo-row.done .el-icon {
  color: #35aa94;
}
.todo-row.done small {
  color: #2e8b7f;
}
.later {
  margin: 8px 0 0;
  color: var(--app-text-muted);
  font-size: 13px;
}
.question {
  margin-top: 14px;
}
.stem {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  line-height: 1.6;
  font-weight: 500;
}
.stem-text {
  flex: 1;
  min-width: 0;
}
.q-num {
  flex: none;
  width: 22px;
  height: 22px;
  margin-top: 2px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--el-fill-color);
  font-size: 12px;
}
.options {
  display: grid;
  gap: 8px;
  margin: 10px 0 0 32px;
}
.option {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
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
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--el-border-color);
  border-radius: 50%;
  font-size: 12px;
}
.q-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
}
.explain {
  margin: 10px 0 0 32px;
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--el-color-danger-light-9);
  font-size: 13px;
  line-height: 1.6;
}
.explain.ok {
  background: var(--el-color-success-light-9);
}
.explain p {
  margin: 0;
}
.explain p + p {
  margin-top: 4px;
}
.verdict {
  font-weight: 600;
}
.wrong-note {
  color: #b23b2f;
}
.finished {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--el-border-color-lighter);
  font-size: 13px;
}
.finished p {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin: 6px 0 0;
}
.label {
  color: var(--app-text-muted);
  font-size: 12px;
}
</style>
