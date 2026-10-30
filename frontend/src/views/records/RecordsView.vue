<script setup>
/*
 * 学习记录 (the 记 stage). Everything comes from GET /api/learn/records:
 * totals, recent activity by day, progress per course, every quiz submission (with the wrong answers)
 * and every AI dialogue. 学情诊断 and 学生画像 are meant to be computed from these same records.
 */
import { computed, onMounted, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import { getRecords } from '@/api/learn'

const data = ref(null)
const loading = ref(false)
const openQuiz = ref(null) // index of the quiz whose wrong answers are shown

const KINDS = {
  study: { label: '学习', icon: 'Reading', cls: 'study' },
  finish: { label: '学完', icon: 'CircleCheck', cls: 'finish' },
  quiz: { label: '练习', icon: 'EditPen', cls: 'quiz' },
  dialogue: { label: 'AI 对话', icon: 'ChatDotRound', cls: 'dialogue' },
}

const DIALOGUE_STATUS = {
  OPEN: { label: '进行中', cls: 'open' },
  PAUSED: { label: '已暂停', cls: 'paused' },
  FINISHED: { label: '已完成', cls: 'finished' },
}

const s = computed(() => data.value?.summary)
const accuracy = computed(() =>
  s.value?.questionsAnswered ? Math.round((s.value.correctAnswers / s.value.questionsAnswered) * 100) : null,
)

// "2026-10-29 14:30:00" -> day label 今天 / 昨天 / 10月28日 星期三
function dayKey(t) {
  return t.slice(0, 10)
}
function dayLabel(key) {
  const d = new Date(key + 'T00:00:00')
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const diff = Math.round((today - d) / 86400000)
  if (diff === 0) return '今天'
  if (diff === 1) return '昨天'
  return d.toLocaleDateString('zh-CN', { month: 'long', day: 'numeric', weekday: 'short' })
}
const clock = (t) => t.slice(11, 16)
const shortDate = (t) => `${Number(t.slice(5, 7))}月${Number(t.slice(8, 10))}日 ${clock(t)}`

const days = computed(() => {
  const groups = []
  for (const e of data.value?.timeline || []) {
    const key = dayKey(e.time)
    let g = groups[groups.length - 1]
    if (!g || g.key !== key) groups.push((g = { key, label: dayLabel(key), entries: [] }))
    g.entries.push(e)
  }
  return groups
})

async function load() {
  loading.value = true
  try {
    data.value = await getRecords()
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="records">
    <PageHeader
      title="学习记录"
      description="学过什么、做过哪些题、和 AI 聊了什么，都记在这里。学情诊断和学生画像会用这些记录来分析。"
    >
      <el-button icon="Refresh" @click="load">刷新</el-button>
    </PageHeader>

    <template v-if="data">
      <!-- totals: the dark summary strip -->
      <section class="sw-summary records-summary">
        <div class="sw-summary-main">
          <span class="sw-eyebrow on-dark">SUMMARY</span>
          <h2>一共学了 {{ s.minutes }} 分钟，学完 {{ s.itemsFinished }} 项内容</h2>
          <p>答对 {{ s.correctAnswers }} / {{ s.questionsAnswered }} 题，交卷 {{ s.quizAttempts }} 次。</p>
        </div>
        <div class="sw-summary-stat">
          <strong>{{ s.activeDays }}</strong>
          <span>学习天数</span>
        </div>
        <div class="sw-summary-stat">
          <strong>{{ accuracy ?? '-' }}{{ accuracy !== null ? '%' : '' }}</strong>
          <span>答题正确率</span>
        </div>
        <div class="sw-summary-stat">
          <strong>{{ s.dialogues }}</strong>
          <span>AI 对话</span>
        </div>
      </section>

      <div class="columns">
        <!-- ============ recent activity ============ -->
        <section class="app-card timeline">
          <div class="sw-heading card-head">
            <div>
              <span class="sw-eyebrow">TIMELINE</span>
              <h3>最近动态</h3>
            </div>
          </div>
          <el-empty v-if="!days.length" :image-size="60" description="还没有学习记录，去「课程学习」开始学吧" />
          <div v-for="g in days" :key="g.key" class="day">
            <h3>{{ g.label }}</h3>
            <ol>
              <li v-for="(e, i) in g.entries" :key="i" class="entry">
                <span class="kind" :class="KINDS[e.kind].cls" :title="KINDS[e.kind].label">
                  <el-icon><component :is="KINDS[e.kind].icon" /></el-icon>
                </span>
                <div class="entry-main">
                  <strong>{{ e.title }}</strong>
                  <span class="detail">{{ e.detail }}</span>
                  <span class="where">
                    <span>{{ e.courseName }}</span>
                    <span v-if="e.chapterTitle">{{ e.chapterTitle }}</span>
                  </span>
                </div>
                <time>{{ clock(e.time) }}</time>
              </li>
            </ol>
          </div>
        </section>

        <div class="side">
          <!-- ============ per course ============ -->
          <section class="app-card">
            <div class="sw-heading card-head">
              <div>
                <span class="sw-eyebrow">COURSES</span>
                <h3>课程进度</h3>
              </div>
            </div>
            <el-empty v-if="!data.courses.length" :image-size="50" description="还没学过课程" />
            <router-link
              v-for="c in data.courses"
              :key="c.courseId"
              :to="{ name: 'course-detail', params: { id: c.courseId } }"
              class="course"
            >
              <div class="course-top">
                <strong>{{ c.courseName }}</strong>
                <span>{{ c.done }} / {{ c.total }} 项</span>
              </div>
              <el-progress
                :percentage="c.total ? Math.round((c.done / c.total) * 100) : 0"
                :stroke-width="6"
                :show-text="false"
              />
              <small>学习了 {{ c.minutes }} 分钟</small>
            </router-link>
          </section>

          <!-- ============ quizzes ============ -->
          <section class="app-card">
            <div class="sw-heading card-head">
              <div>
                <span class="sw-eyebrow">QUIZZES</span>
                <h3>答题记录</h3>
              </div>
            </div>
            <el-empty v-if="!data.quizzes.length" :image-size="50" description="还没交过练习" />
            <ul class="quizzes">
              <li v-for="(q, i) in data.quizzes" :key="i" class="quiz" :class="{ open: openQuiz === i }">
                <button type="button" class="quiz-row" :disabled="!q.wrong.length" @click="openQuiz = openQuiz === i ? null : i">
                  <span class="score" :class="{ full: q.correct === q.total }">{{ q.correct }}/{{ q.total }}</span>
                  <span class="quiz-main">
                    <strong>{{ q.title }}</strong>
                    <small>{{ shortDate(q.submittedAt) }}</small>
                  </span>
                  <span class="quiz-state">
                    {{ q.wrong.length ? `错 ${q.wrong.length} 题` : '全对' }}
                    <el-icon v-if="q.wrong.length" class="chev"><ArrowDown /></el-icon>
                  </span>
                </button>
                <div v-if="openQuiz === i" class="wrong-list">
                  <div v-for="(w, j) in q.wrong" :key="j" class="wrong">
                    <p>{{ w.stem }}</p>
                    <span class="mine">{{ w.chosen ? `你选了 ${w.chosen}` : '没有作答' }}</span>
                    <span class="right">正确答案 {{ w.answer }}</span>
                  </div>
                </div>
              </li>
            </ul>
          </section>

          <!-- ============ AI dialogues ============ -->
          <section class="app-card">
            <div class="sw-heading card-head">
              <div>
                <span class="sw-eyebrow">AI DIALOGUE</span>
                <h3>AI 对话</h3>
              </div>
            </div>
            <el-empty v-if="!data.dialogues.length" :image-size="50" description="还没和 AI 对话过" />
            <ul class="dialogues">
              <li v-for="d in data.dialogues" :key="d.sessionId">
                <span class="dot" :class="DIALOGUE_STATUS[d.status]?.cls" />
                <span class="dialogue-main">
                  <strong>{{ d.title }}</strong>
                  <small>{{ shortDate(d.startedAt) }}，回答了 {{ d.studentMessages }} 次</small>
                </span>
                <span class="status">{{ DIALOGUE_STATUS[d.status]?.label }}</span>
              </li>
            </ul>
          </section>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.card-head {
  margin-bottom: 16px;
}
.records-summary {
  margin-bottom: 22px;
}


.columns {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(0, 1fr);
  gap: 20px;
  align-items: start;
}
.side {
  display: grid;
  gap: 16px;
}
.side .app-card + .app-card {
  margin-top: 0;
}

/* ---------- timeline ---------- */
.day + .day {
  margin-top: 18px;
}
.day h3 {
  margin: 0 0 6px;
  color: var(--app-text-secondary);
  font-size: 13px;
  font-weight: 600;
}
.day ol {
  margin: 0;
  padding: 0;
  list-style: none;
}
.entry {
  position: relative;
  display: flex;
  gap: 14px;
  padding: 10px 0;
}
/* the line joining one day's entries */
.entry:not(:last-child)::before {
  content: '';
  position: absolute;
  left: 15px;
  top: 42px;
  bottom: -10px;
  width: 1px;
  background: var(--app-border);
}
.kind {
  flex: none;
  width: 31px;
  height: 31px;
  display: grid;
  place-items: center;
  border-radius: 9px;
  font-size: 15px;
}
.kind.study {
  background: var(--el-color-primary-light-9);
  color: var(--app-primary);
}
.kind.finish {
  background: var(--el-color-success-light-9);
  color: var(--app-success);
}
.kind.quiz {
  background: var(--el-color-warning-light-9);
  color: var(--app-warning);
}
.kind.dialogue {
  background: #eef0ff;
  color: #6973d1;
}
.entry-main {
  flex: 1;
  min-width: 0;
  display: grid;
  gap: 2px;
}
.entry-main strong {
  font-size: 14px;
}
.detail {
  color: var(--app-text);
  font-size: 13px;
}
.where {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
  color: var(--app-text-muted);
  font-size: 12px;
}
time {
  flex: none;
  color: var(--app-text-muted);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

/* ---------- courses ---------- */
.course {
  display: grid;
  gap: 8px;
  padding: 12px;
  margin: 0 -12px;
  border-radius: 10px;
  color: var(--app-text);
  text-decoration: none;
}
.course:hover {
  background: var(--el-fill-color-light);
}
.course-top {
  display: flex;
  justify-content: space-between;
  gap: 10px;
}
.course-top span,
.course small {
  color: var(--app-text-muted);
  font-size: 12px;
}

/* ---------- quizzes ---------- */
.quizzes,
.dialogues {
  margin: 0;
  padding: 0;
  list-style: none;
}
.quiz + .quiz {
  border-top: 1px solid var(--el-border-color-lighter);
}
.quiz-row {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  border: 0;
  background: none;
  font: inherit;
  color: inherit;
  text-align: left;
  cursor: pointer;
}
.quiz-row:disabled {
  cursor: default;
}
.score {
  flex: none;
  min-width: 44px;
  padding: 4px 0;
  border-radius: 8px;
  text-align: center;
  font-weight: 700;
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  background: var(--el-color-warning-light-9);
  color: var(--app-warning);
}
.score.full {
  background: var(--el-color-success-light-9);
  color: var(--app-success);
}
.quiz-main,
.dialogue-main {
  flex: 1;
  min-width: 0;
  display: grid;
  gap: 2px;
}
.quiz-main small,
.dialogue-main small,
.quiz-state,
.status {
  color: var(--app-text-muted);
  font-size: 12px;
}
.quiz-state {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.chev {
  transition: transform 0.2s;
}
.quiz.open .chev {
  transform: rotate(180deg);
}
.wrong-list {
  display: grid;
  gap: 8px;
  padding: 0 0 12px 56px;
}
.wrong {
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  font-size: 13px;
}
.wrong p {
  margin: 0 0 6px;
  line-height: 1.55;
}
.mine {
  margin-right: 14px;
  color: var(--app-danger);
}
.right {
  color: var(--app-success);
}

/* ---------- dialogues ---------- */
.dialogues li {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
}
.dialogues li + li {
  border-top: 1px solid var(--el-border-color-lighter);
}
.dot {
  flex: none;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--app-text-muted);
}
.dot.open {
  background: var(--app-primary);
}
.dot.finished {
  background: var(--app-success);
}
.dot.paused {
  background: var(--app-warning);
}

@media (max-width: 1100px) {
  .columns {
    grid-template-columns: 1fr;
  }
}
</style>
