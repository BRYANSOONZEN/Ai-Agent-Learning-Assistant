<script setup>
/*
 * 学习推荐 (the 荐 stage). GET /api/recommendations picks questions for the weakest knowledge points
 * (mastery < 60%): last answered wrong > never done > wrong once then right, at most 3 per point, 5 in all.
 * Each point card says why (mastery, the misconception from the last wrong answer), what to re-read first,
 * then the questions. A question is answered right here, one at a time: it is graded, recorded as
 * 「推荐练习」 and mastery is recomputed. Right answers and 换一道 are not recommended again.
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import MasteryMeter from '@/views/diagnosis/MasteryMeter.vue'
import { LEVELS, percent } from '@/views/diagnosis/levels'
import { getRecommendations, practiceRecommended, recommendationFeedback } from '@/api/recommend'

const router = useRouter()
const LETTERS = 'ABCDEF'

const data = ref(null)
const loading = ref(false)
const chosen = reactive({}) // exerciseId -> letter
const results = reactive({}) // exerciseId -> graded result
const changes = reactive({}) // knowledgePointId -> latest mastery change
const busy = ref(null) // exerciseId being submitted

const total = computed(() => (data.value?.points || []).reduce((n, p) => n + p.exercises.length, 0))
const answeredSome = computed(() => Object.keys(results).length > 0)
const headline = computed(() => {
  const d = data.value
  if (!d.weakCount) return '没有薄弱的知识点，暂时不用补'
  const first = d.points[0]
  if (!total.value) return `「${first.name}」的题都做完了，先回看内容`
  return d.points.length === 1
    ? `先补「${first.name}」，做这 ${total.value} 道题`
    : `先补 ${d.points.length} 个薄弱知识点，一共 ${total.value} 道题`
})

async function load() {
  loading.value = true
  try {
    data.value = await getRecommendations()
    for (const k of Object.keys(chosen)) delete chosen[k]
    for (const k of Object.keys(results)) delete results[k]
    for (const k of Object.keys(changes)) delete changes[k]
  } finally {
    loading.value = false
  }
}

async function submit(point, ex) {
  if (!chosen[ex.exerciseId]) return ElMessage.warning('先选一个答案')
  busy.value = ex.exerciseId
  try {
    const r = await practiceRecommended({ [ex.exerciseId]: chosen[ex.exerciseId] })
    results[ex.exerciseId] = r.results[0]
    const c = r.masteryChanges.find((m) => m.knowledgePointId === point.knowledgePointId)
    if (c) changes[point.knowledgePointId] = c
  } finally {
    busy.value = null
  }
}

async function dismiss(point, ex) {
  await recommendationFeedback(ex.recommendationId, 'dismissed')
  point.exercises = point.exercises.filter((e) => e !== ex)
  ElMessage.success('换掉了，刷新后会补上别的题')
}

function optionClass(ex, letter) {
  const r = results[ex.exerciseId]
  if (!r) return { chosen: chosen[ex.exerciseId] === letter }
  return { right: r.answer === letter, wrong: r.chosen === letter && !r.correct }
}

function openItem(point, item) {
  router.push({
    name: 'course-detail',
    params: { id: point.courseId },
    query: { chapter: point.chapterId, view: 'item:' + item.itemId },
  })
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="recommend">
    <PageHeader
      title="学习推荐"
      description="按学情诊断里最薄弱的知识点挑题：先补上次做错的，再做没做过的。在这里做题会记进学习记录，掌握度跟着变。"
    />

    <template v-if="data">
      <section class="sw-summary conclusion">
        <div class="sw-summary-main">
          <span class="sw-eyebrow on-dark">TODAY'S PLAN</span>
          <h2>{{ headline }}</h2>
          <p>掌握度低于 60% 算薄弱，最低的排在前面。做对的题不会再推；不想做可以换一道。</p>
        </div>
        <div class="sw-summary-stat">
          <strong>{{ data.weakCount }}</strong>
          <span>薄弱知识点</span>
        </div>
        <div class="sw-summary-stat">
          <strong>{{ total }}</strong>
          <span>推荐的题</span>
        </div>
        <div class="sw-summary-stat">
          <strong>{{ data.doneCount }}</strong>
          <span>已经做对</span>
        </div>
        <button v-if="answeredSome" type="button" class="sw-button-light" @click="load">
          <el-icon><Refresh /></el-icon>重新推荐
        </button>
      </section>

      <el-empty v-if="!data.weakCount" description="继续学新的内容吧，做错的题会出现在这里">
        <el-button @click="router.push({ name: 'diagnosis' })">看学情诊断</el-button>
      </el-empty>

      <article v-for="p in data.points" :key="p.knowledgePointId" class="point app-card">
        <header class="point-head">
          <div class="point-name">
            <span class="sw-eyebrow">WEAK POINT</span>
            <h3>{{ p.name }}</h3>
            <small>{{ p.courseName }} / {{ p.chapterTitle }}，答对 {{ p.correctCount }} / {{ p.attemptCount }} 次</small>
          </div>
          <div class="meter">
            <MasteryMeter :value="p.mastery" :level="p.level" />
          </div>
        </header>

        <div v-if="changes[p.knowledgePointId]" class="change">
          掌握度 {{ percent(changes[p.knowledgePointId].before) }}
          <el-icon><Right /></el-icon>
          <b>{{ percent(changes[p.knowledgePointId].after) }}</b>
          <span class="lv" :class="changes[p.knowledgePointId].level">
            {{ LEVELS[changes[p.knowledgePointId].level].label }}
          </span>
        </div>

        <div class="point-body">
          <!-- why + what to re-read first -->
          <aside class="review">
            <div v-if="p.misconception" class="block">
              <span class="label">上次答错时可能是这样想的</span>
              <p class="mis">{{ p.misconception }}</p>
            </div>
            <div v-if="p.content" class="block">
              <span class="label">要点</span>
              <p>{{ p.content }}</p>
            </div>
            <div v-if="p.review.length" class="block">
              <span class="label">先回看</span>
              <button v-for="r in p.review" :key="r.itemId" type="button" class="link" @click="openItem(p, r)">
                <el-icon><component :is="r.type === 'video' ? 'VideoPlay' : 'Document'" /></el-icon>
                {{ r.title }}
              </button>
            </div>
          </aside>

          <!-- the recommended questions, answered one at a time -->
          <div class="questions">
            <p v-if="!p.exercises.length" class="empty-note">
              这个知识点推荐的题都做完或换掉了。先回看左边的内容，再去学情诊断看看掌握度。
            </p>
            <div v-for="(ex, i) in p.exercises" :key="ex.exerciseId" class="question">
              <div class="stem">
                <span class="q-num">{{ i + 1 }}</span>
                <span class="stem-text">{{ ex.stem }}</span>
                <span class="reason" :class="{ hot: ex.reason === '上次做错了' }">{{ ex.reason }}</span>
              </div>
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
                  {{ results[ex.exerciseId].correct ? '答对了，这道题不会再推荐。' : `答错了，正确答案是 ${results[ex.exerciseId].answer}。` }}
                </p>
                <p v-if="results[ex.exerciseId].note" class="note">
                  你选的 {{ results[ex.exerciseId].chosen }} 可能是这样想的：{{ results[ex.exerciseId].note }}
                </p>
                <p v-if="results[ex.exerciseId].analysis">解析：{{ results[ex.exerciseId].analysis }}</p>
              </div>
              <div v-else class="q-actions">
                <el-button text size="small" @click="dismiss(p, ex)">换一道</el-button>
                <el-button
                  type="primary"
                  size="small"
                  :loading="busy === ex.exerciseId"
                  :disabled="!chosen[ex.exerciseId]"
                  @click="submit(p, ex)"
                >
                  提交这道
                </el-button>
              </div>
            </div>
          </div>
        </div>
      </article>
    </template>
  </div>
</template>

<style scoped>
.conclusion {
  margin-bottom: 18px;
}

/* ---------- one weak point ---------- */
.point + .point {
  margin-top: 18px;
}
.point-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.point-name h3 {
  margin: 0 0 4px;
  font-size: 18px;
}
.point-name small {
  color: var(--app-text-muted);
  font-size: 13px;
}
.meter {
  width: min(340px, 100%);
}
.change {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 12px;
  padding: 8px 12px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  font-size: 13px;
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
.point-body {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.6fr);
  gap: 28px;
  padding-top: 18px;
}

/* ---------- left: why and what to re-read ---------- */
.review {
  display: grid;
  gap: 18px;
  align-content: start;
}
.label {
  display: block;
  margin-bottom: 6px;
  color: var(--app-text-muted);
  font-size: 12px;
}
.block p {
  margin: 0;
  line-height: 1.65;
}
.block .mis {
  color: #b23b2f;
}
.link {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 0;
  border: 0;
  background: none;
  color: var(--app-primary);
  font: inherit;
  cursor: pointer;
  text-align: left;
}
.link:hover {
  text-decoration: underline;
}

/* ---------- right: questions ---------- */
.empty-note {
  margin: 0;
  color: var(--app-text-muted);
  line-height: 1.6;
}
.question + .question {
  margin-top: 18px;
  padding-top: 18px;
  border-top: 1px solid var(--el-border-color-lighter);
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
.reason {
  flex: none;
  padding: 1px 8px;
  border-radius: 6px;
  background: var(--el-fill-color);
  color: var(--app-text-secondary);
  font-size: 12px;
  font-weight: 400;
}
.reason.hot {
  background: var(--el-color-danger-light-9);
  color: #b23b2f;
}
.options {
  display: grid;
  gap: 8px;
  margin: 12px 0 0 32px;
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
  gap: 8px;
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
.note {
  color: #b23b2f;
}

@media (max-width: 1100px) {
  .point-body {
    grid-template-columns: 1fr;
  }
}
</style>
