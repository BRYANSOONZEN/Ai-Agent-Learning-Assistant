<script setup>
/*
 * 干预管理 (teacher, the 干 stage). Opening the page scans the course: every student x knowledge point is
 * judged on two layers — concept (AI dialogue topics) and application (exercise mastery) — giving
 * A 应用问题 / B 概念问题 / A + B. Each open alert comes with the system's suggestion (template, no AI):
 * the teacher reads the evidence, may untick exercises or rewrite the note, then 通过并推送 or 忽略.
 * Approved interventions are listed below with their progress and the before -> after effect.
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCourses } from '@/api/teacherCourse'
import { getInterventionBoard, ignoreAlert, reviewIntervention } from '@/api/intervention'
import { TOPIC_RESULTS } from '@/views/diagnosis/levels'
import { ALERT_TYPES, APPLICATION, CONCEPT, INTERVENTION_TYPES, valueText } from './interventionLabels'

const courses = ref([])
const courseId = ref(null)
const board = ref(null)
const loading = ref(false)
const activeId = ref(null)
const busy = ref(false)
const draft = reactive({ exerciseIds: [], note: '' }) // the teacher's edits to the active suggestion

const active = computed(() => board.value?.alerts.find((a) => a.id === activeId.value) || null)
const headline = computed(() => {
  const n = board.value?.counts.open || 0
  return n ? `${n} 条预警等你确认` : '没有待处理的预警'
})

async function load() {
  if (!courseId.value) return
  loading.value = true
  try {
    board.value = await getInterventionBoard(courseId.value)
    // the sidebar badge only refreshes on route changes; tell it the new number now
    window.dispatchEvent(new CustomEvent('alerts-changed'))
    if (!board.value.alerts.some((a) => a.id === activeId.value)) {
      activeId.value = board.value.alerts[0]?.id ?? null
    }
  } finally {
    loading.value = false
  }
}

// reset the edit box whenever another alert is opened
watch(active, (a) => {
  draft.exerciseIds = a?.suggestion?.exercises.map((e) => e.exerciseId) || []
  draft.note = a?.suggestion?.note || ''
})
watch(courseId, load)

async function approve() {
  const s = active.value.suggestion
  if (s.type !== 'REVIEW' && !draft.exerciseIds.length) return ElMessage.warning('至少保留一道题')
  busy.value = true
  try {
    await reviewIntervention(s.id, {
      approve: true,
      exerciseIds: s.type === 'REVIEW' ? null : draft.exerciseIds,
      note: draft.note,
    })
    ElMessage.success(`已推送给${active.value.studentName}`)
    await load()
  } finally {
    busy.value = false
  }
}

async function ignore() {
  await ElMessageBox.confirm('这次不干预，这条预警标为已忽略。之后学生有新的作答或对话，仍满足条件时会重新提醒。', '忽略预警', {
    confirmButtonText: '忽略',
    cancelButtonText: '取消',
  })
  busy.value = true
  try {
    await ignoreAlert(active.value.id)
    await load()
  } finally {
    busy.value = false
  }
}

function progress(iv) {
  const parts = []
  if (iv.items.length) parts.push(`课件 ${iv.items.filter((i) => i.viewed).length}/${iv.items.length}`)
  if (iv.dialogue) parts.push(iv.dialogue.finished ? '对话已重做' : '对话未重做')
  if (iv.type === 'REVIEW_PRACTICE' && iv.step === 1) parts.push('练习在第 2 步')
  else if (iv.exercises.length) parts.push(`练习 ${iv.exercises.filter((e) => e.answered).length}/${iv.exercises.length}`)
  return parts.join(' · ')
}

const time = (t) => (t ? t.slice(5, 16).replace('T', ' ') : '')

onMounted(async () => {
  courses.value = (await getCourses({ page: 1, size: 50 })).records
  const start =
    courses.value.find((c) => c.status === 'published' && c.chapterCount) ||
    courses.value.find((c) => c.status === 'published') ||
    courses.value[0]
  courseId.value = start?.id ?? null
})
</script>

<template>
  <div class="intervention">
    <div class="toolbar">
      <el-select v-model="courseId" placeholder="选择课程" style="width: 240px">
        <el-option v-for="c in courses" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-button icon="Refresh" :loading="loading" @click="load">重新扫描</el-button>
    </div>

    <div v-loading="loading">
      <template v-if="board">
        <section class="sw-summary">
          <div class="sw-summary-main">
            <span class="sw-eyebrow on-dark">INTERVENTION</span>
            <h2>{{ headline }}</h2>
            <p>
              每个学生的每个知识点看两层：AI 对话考基础概念，练习题考应用。系统按两层给出建议，你确认后才会推给学生。
            </p>
          </div>
          <div class="sw-summary-stat">
            <strong>{{ board.counts.open }}</strong>
            <span>待处理预警</span>
          </div>
          <div class="sw-summary-stat">
            <strong>{{ board.counts.running }}</strong>
            <span>执行中</span>
          </div>
          <div class="sw-summary-stat">
            <strong>{{ board.counts.done }}</strong>
            <span>已完成（有效 {{ board.counts.effective }}）</span>
          </div>
        </section>

        <!-- open alerts: list on the left, evidence + suggestion on the right -->
        <div v-if="board.alerts.length" class="alerts">
          <aside class="app-card alert-list">
            <div class="list-head">
              <span class="sw-eyebrow">OPEN ALERTS</span>
              <strong>待处理</strong>
            </div>
            <button
              v-for="a in board.alerts"
              :key="a.id"
              type="button"
              class="alert-row"
              :class="{ active: a.id === activeId }"
              @click="activeId = a.id"
            >
              <span class="row-top">
                <b>{{ a.studentName }}</b>
                <span class="sw-tag" :class="ALERT_TYPES[a.type].tag">{{ ALERT_TYPES[a.type].short }}</span>
                <span v-if="a.level === 'high'" class="sw-tag red">高</span>
              </span>
              <small>{{ a.knowledgePointName }}</small>
            </button>
          </aside>

          <section v-if="active" class="app-card detail">
            <header class="detail-head">
              <div>
                <span class="sw-eyebrow">{{ active.chapterTitle }}</span>
                <h3>{{ active.studentName }} ·「{{ active.knowledgePointName }}」</h3>
                <p>{{ active.message }}</p>
              </div>
              <span class="sw-tag big" :class="ALERT_TYPES[active.type].tag">{{ ALERT_TYPES[active.type].label }}</span>
            </header>
            <p class="type-hint">{{ ALERT_TYPES[active.type].hint }}</p>

            <!-- the two layers, side by side -->
            <div class="layers">
              <div class="layer">
                <div class="layer-head">
                  <span>概念层 · AI 对话</span>
                  <span class="sw-tag" :class="CONCEPT[active.evidence.concept].tag">
                    {{ CONCEPT[active.evidence.concept].label }}
                  </span>
                </div>
                <ul v-if="active.evidence.topics.length" class="facts">
                  <li v-for="t in active.evidence.topics" :key="t.title">
                    <span>{{ t.title }}</span>
                    <span class="sw-tag" :class="['REVEALED', 'SKIPPED_IRRELEVANT'].includes(t.result) ? 'red' : 'teal'">
                      {{ TOPIC_RESULTS[t.result]?.label || t.result }}
                    </span>
                    <small>答错 {{ t.wrongCount }} 次，卡住 {{ t.stuckCount }} 次</small>
                  </li>
                </ul>
                <p v-else class="muted">这个知识点还没有对话记录</p>
              </div>
              <div class="layer">
                <div class="layer-head">
                  <span>应用层 · 练习题</span>
                  <span class="sw-tag" :class="APPLICATION[active.evidence.application].tag">
                    {{ APPLICATION[active.evidence.application].label }}
                  </span>
                </div>
                <p v-if="active.evidence.mastery != null" class="mastery">
                  掌握度 <b>{{ Math.round(active.evidence.mastery * 100) }}%</b>
                  <small>答对 {{ active.evidence.correctCount }} / {{ active.evidence.attemptCount }} 次</small>
                </p>
                <ul v-if="active.evidence.recentAnswers.length" class="facts">
                  <li v-for="(r, i) in active.evidence.recentAnswers" :key="i">
                    <span class="answer" :class="{ ok: r.correct }">{{ r.correct ? '对' : '错' }} · 选 {{ r.chosen }}</span>
                    <span class="stem">{{ r.stem }}</span>
                    <small v-if="r.note" class="note">{{ r.note }}</small>
                  </li>
                </ul>
                <p v-else class="muted">还没做过这个知识点的题</p>
              </div>
            </div>

            <p v-if="active.evidence.misconception" class="misconception">
              主要误解：{{ active.evidence.misconception }}
            </p>

            <!-- the system's suggestion, editable -->
            <div v-if="active.suggestion" class="suggestion">
              <div class="sw-heading">
                <div>
                  <span class="sw-eyebrow">SUGGESTION</span>
                  <h3>系统建议：{{ INTERVENTION_TYPES[active.suggestion.type] }}</h3>
                </div>
              </div>

              <ol class="steps">
                <li v-if="active.suggestion.items.length || active.suggestion.dialogue">
                  <b>{{ active.suggestion.type === 'REVIEW_PRACTICE' ? '第 1 步：' : '' }}复习并重做对话</b>
                  <span v-for="i in active.suggestion.items" :key="i.itemId" class="chip">
                    <el-icon><component :is="i.type === 'video' ? 'VideoPlay' : 'Document'" /></el-icon>{{ i.title }}
                  </span>
                  <span v-if="active.suggestion.dialogue" class="chip">
                    <el-icon><ChatDotRound /></el-icon>{{ active.suggestion.dialogue.title }}
                  </span>
                </li>
                <li v-if="active.suggestion.exercises.length">
                  <b>{{ active.suggestion.type === 'REVIEW_PRACTICE' ? '第 2 步：' : '' }}变式练习（可去掉不想布置的题）</b>
                  <el-checkbox-group v-model="draft.exerciseIds" class="ex-list">
                    <el-checkbox v-for="e in active.suggestion.exercises" :key="e.exerciseId" :value="e.exerciseId">
                      {{ e.stem }}
                    </el-checkbox>
                  </el-checkbox-group>
                </li>
              </ol>

              <label class="note-label">给学生的话</label>
              <el-input v-model="draft.note" type="textarea" :rows="3" maxlength="500" show-word-limit />

              <div class="actions">
                <el-button :disabled="busy" @click="ignore">忽略</el-button>
                <el-button type="primary" :loading="busy" @click="approve">通过并推送</el-button>
              </div>
            </div>
          </section>
        </div>
        <el-empty v-else class="app-card" description="现在没有学生需要干预。学生有新的作答或对话后，再打开这里会重新扫描。" />

        <!-- approved interventions: progress and effect -->
        <section v-if="board.interventions.length" class="app-card records">
          <div class="sw-heading">
            <div>
              <span class="sw-eyebrow">FOLLOW-UP</span>
              <h3>干预记录</h3>
            </div>
          </div>
          <div class="record head">
            <span>学生 / 知识点</span><span>措施</span><span>进度</span><span>效果</span>
          </div>
          <div v-for="iv in board.interventions" :key="iv.id" class="record">
            <span class="who">
              <b>{{ iv.studentName }}</b>
              <small>{{ iv.knowledgePointName }} · {{ time(iv.reviewedAt) }}</small>
            </span>
            <span>{{ INTERVENTION_TYPES[iv.type] }}</span>
            <span class="muted">
              <span class="sw-tag" :class="iv.status === 'done' ? 'gray' : 'purple'">
                {{ iv.status === 'done' ? '已完成' : '执行中' }}
              </span>
              {{ progress(iv) }}
            </span>
            <span class="effect">
              {{ valueText(iv.type, iv.beforeValue) }}
              <template v-if="iv.status === 'done'">
                <el-icon><Right /></el-icon>
                <b>{{ valueText(iv.type, iv.afterValue) }}</b>
                <span class="sw-tag" :class="iv.effective ? 'teal' : 'red'">{{ iv.effective ? '有效' : '无效' }}</span>
              </template>
            </span>
          </div>
        </section>
      </template>
    </div>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  justify-content: space-between;
  margin-bottom: 18px;
}
.sw-summary {
  margin-bottom: 18px;
}

/* ---------- open alerts ---------- */
.alerts {
  display: grid;
  grid-template-columns: minmax(0, 260px) minmax(0, 1fr);
  gap: 18px;
  align-items: start;
}
.alert-list {
  display: grid;
  gap: 4px;
  padding: 18px 10px 12px;
}
.list-head {
  padding: 0 8px 10px;
}
.list-head strong {
  display: block;
  margin-top: 5px;
  font-size: 15px;
}
.alert-row {
  display: grid;
  gap: 4px;
  padding: 10px 12px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  font: inherit;
  text-align: left;
  cursor: pointer;
}
.alert-row:hover {
  background: var(--el-fill-color-light);
}
.alert-row.active {
  background: #f6f8ff;
  box-shadow: inset 3px 0 #6e80eb;
}
.row-top {
  display: flex;
  align-items: center;
  gap: 6px;
}
.alert-row small {
  color: var(--app-text-muted);
  font-size: 12px;
}

.detail {
  min-width: 0;
  padding: 22px 24px;
}
.detail-head {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px 16px;
}
.detail-head h3 {
  margin: 6px 0 6px;
  font-size: 19px;
  letter-spacing: -0.3px;
}
.detail-head p {
  margin: 0;
  color: var(--app-text-secondary);
  line-height: 1.6;
}
.sw-tag.big {
  padding: 5px 10px;
  font-size: 12px;
}
.type-hint {
  margin: 10px 0 0;
  color: var(--app-text-muted);
  font-size: 13px;
}

.layers {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(280px, 100%), 1fr));
  gap: 14px;
  margin-top: 18px;
}
.layer {
  min-width: 0;
  padding: 14px 16px;
  border: 1px solid var(--app-border);
  border-radius: 12px;
}
.layer-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  font-weight: 600;
  font-size: 13px;
}
.facts {
  display: grid;
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.facts li {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 8px;
  font-size: 13px;
  line-height: 1.5;
}
.facts small {
  width: 100%;
  color: var(--app-text-muted);
  font-size: 12px;
}
.facts .note {
  color: #b23b2f;
}
.answer {
  flex: none;
  padding: 1px 6px;
  border-radius: 5px;
  background: #fdeeec;
  color: #b23b2f;
  font-size: 12px;
}
.answer.ok {
  background: #e8f7f4;
  color: #2e8b7f;
}
.stem {
  flex: 1;
  min-width: 0;
}
.mastery {
  margin: 0 0 10px;
  font-size: 13px;
}
.mastery b {
  font-size: 18px;
}
.mastery small {
  margin-left: 6px;
  color: var(--app-text-muted);
}
.misconception {
  margin: 14px 0 0;
  padding: 10px 14px;
  border-radius: 10px;
  background: #fdeeec;
  color: #b23b2f;
  font-size: 13px;
}

.suggestion {
  margin-top: 20px;
  padding-top: 18px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.steps {
  display: grid;
  gap: 14px;
  margin: 14px 0 16px;
  padding-left: 18px;
  font-size: 13px;
}
.steps b {
  display: block;
  margin-bottom: 8px;
}
.chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin: 0 8px 6px 0;
  padding: 4px 10px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
}
.ex-list {
  display: grid;
  gap: 4px;
}
.ex-list :deep(.el-checkbox) {
  height: auto;
  align-items: flex-start;
  white-space: normal;
}
.ex-list :deep(.el-checkbox__label) {
  line-height: 1.5;
}
.note-label {
  display: block;
  margin-bottom: 6px;
  color: var(--app-text-muted);
  font-size: 12px;
}
.actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 14px;
}
.muted {
  color: var(--app-text-muted);
  font-size: 13px;
}

/* ---------- follow-up ---------- */
.records {
  margin-top: 18px;
  padding: 20px 24px;
}
.record {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr) minmax(0, 1.4fr) minmax(0, 1fr);
  align-items: center;
  gap: 14px;
  padding: 12px 0;
  border-top: 1px solid var(--el-border-color-lighter);
  font-size: 13px;
}
.record.head {
  margin-top: 10px;
  border-top: 0;
  color: var(--app-text-muted);
  font-size: 12px;
}
.who {
  display: grid;
  gap: 2px;
}
.who small {
  color: var(--app-text-muted);
  font-size: 12px;
}
.effect {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

@media (max-width: 900px) {
  .alerts {
    grid-template-columns: minmax(0, 1fr);
  }
  .record {
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  }
  .record.head {
    display: none;
  }
}
</style>
