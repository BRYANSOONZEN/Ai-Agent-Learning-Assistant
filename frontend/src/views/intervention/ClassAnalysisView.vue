<script setup>
/*
 * 老师干预：全班分析 (docs/模块说明/老师干预-全班分析.md). Replaces the old per-student A / B / AB alerts.
 * Per exercise item: waiting (before the deadline) / void (fewer than 80% submitted: remind or extend) / done.
 * Done: cards grouped by knowledge point.
 *   - rule 1, a question wrong for > 80%: check it first (no problem / fixed / void)
 *   - rule 2, key point x assessment type wrong > 50%: the plan follows the assessment type;
 *     handle -> supplementary explanation (AI draft or the teacher's own) + 3 new questions -> push to those who got it wrong
 * Below: what was pushed, and the effect (error rate before -> after).
 */
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCourses } from '@/api/teacherCourse'
import { deleteExercise } from '@/api/teacherCourse'
import {
  createPush, extendDeadline, getClassAnalysis, ignoreIssue, questionFixed, questionOk, questionVoid,
  regeneratePush, remindMissing, sendPush, updatePush,
} from '@/api/classAnalysis'

const courses = ref([])
const courseId = ref(null)
const board = ref(null)
const loading = ref(false)

const STATUS = {
  waiting: { text: '等截止', type: 'info' },
  void: { text: '分析作废', type: 'danger' },
  done: { text: '已分析', type: 'success' },
  nodue: { text: '没设截止时间', type: 'warning' },
}
const ISSUE_STATUS = { ok: '题目没问题', fixed: '已修改', voided: '已作废', ignored: '已忽略', pushed: '已推送' }
const pct = (r) => (r == null ? '-' : Math.round(r * 100) + '%')
const fmt = (t) => (t ? String(t).slice(5, 16) : '')

async function load() {
  if (!courseId.value) return
  loading.value = true
  try {
    board.value = await getClassAnalysis(courseId.value)
    window.dispatchEvent(new CustomEvent('alerts-changed'))
  } finally {
    loading.value = false
  }
}
watch(courseId, load)

onMounted(async () => {
  courses.value = (await getCourses({ page: 1, size: 50 })).records
  const start = courses.value.find((c) => c.status === 'published' && c.chapterCount) || courses.value[0]
  courseId.value = start?.id ?? null
})

// ---------- rule 1: check a question ----------
async function check(issue, action) {
  if (action === 'void') {
    await ElMessageBox.confirm('作废后这道题删掉，不算进任何人的掌握度，做过的学生会重算。', '作废这道题',
      { confirmButtonText: '作废', cancelButtonText: '取消' })
    await questionVoid(issue.id)
  } else if (action === 'ok') await questionOk(issue.id)
  else await questionFixed(issue.id)
  ElMessage.success('已处理')
  await load()
}

async function ignore(issue) {
  await ignoreIssue(issue.id)
  await load()
}

// ---------- void: remind / extend ----------
async function remind(item) {
  const n = await remindMissing(item.itemId, '')
  ElMessage.success(`已提醒 ${n} 人（显示在他们的小助手里）`)
}
const extendItem = ref(null)
const extendAt = ref(null)
async function doExtend() {
  await extendDeadline(extendItem.value.itemId, extendAt.value)
  ElMessage.success('已延长截止时间，到新的截止再分析')
  extendItem.value = null
  await load()
}

// ---------- rule 2: handle cards -> push ----------
const picked = ref({}) // issueId -> true (several cards of one knowledge point can be merged)
const draft = ref(null)
const working = ref(false)
const pickedIds = (group) => group.issues.filter((i) => picked.value[i.id]).map((i) => i.id)

async function handle(group, issue, ai) {
  const ids = pickedIds(group).length ? pickedIds(group) : [issue.id]
  working.value = true
  try {
    draft.value = await createPush(ids, ai)
    picked.value = {}
  } finally {
    working.value = false
  }
}
async function saveDraft() {
  draft.value = await updatePush(draft.value.id, { explanation: draft.value.explanation, dueAt: draft.value.dueAt })
  ElMessage.success('已保存')
}
async function removeQuestion(q) {
  await deleteExercise(q.id)
  draft.value.questions = draft.value.questions.filter((x) => x.id !== q.id)
}
async function regenerate() {
  working.value = true
  try {
    draft.value = await regeneratePush(draft.value.id)
  } finally {
    working.value = false
  }
}
async function push() {
  await updatePush(draft.value.id, { explanation: draft.value.explanation, dueAt: draft.value.dueAt })
  await sendPush(draft.value.id)
  ElMessage.success(`已推送给 ${draft.value.students.length} 人`)
  draft.value = null
  await load()
}

const pushedCount = computed(() => (board.value?.items || []).reduce((n, i) => n + (i.pushes?.length || 0), 0))
</script>

<template>
  <div v-loading="loading" class="class-analysis">
    <div class="top">
      <div>
        <h2>干预管理 · 全班分析</h2>
        <p class="muted">
          每次练习到截止（或全班交完）才分析，交卷不到 {{ pct(board?.minSubmitRatio) }} 作废。
          一道题错 &gt; 80% 先请你检查题目；某条要点 × 某种考查类型错 &gt; 50% 给你推荐方案，推给做错的学生。
          学生个人的补练由小助手负责，这里不用处理。
        </p>
      </div>
      <el-select v-model="courseId" placeholder="选课程" style="width: 220px">
        <el-option v-for="c in courses" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
    </div>

    <el-empty v-if="board && !board.items.length" description="这门课还没有练习" />

    <section v-for="item in board?.items || []" :key="item.itemId" class="app-card item">
      <header class="item-head">
        <div>
          <b>{{ item.title }}</b>
          <small>{{ item.chapterTitle }} · 截止 {{ fmt(item.dueAt) || '未设' }} · 交卷 {{ item.submitted }}/{{ item.students }}</small>
        </div>
        <el-tag :type="STATUS[item.status].type">{{ STATUS[item.status].text }}</el-tag>
      </header>

      <p v-if="item.status === 'waiting'" class="muted">还没到截止时间，截止后（或全班交完）自动分析。</p>
      <p v-if="item.status === 'nodue'" class="muted">请先给这次练习设截止时间。</p>

      <div v-if="item.status === 'void'" class="void">
        <p>截止时只有 {{ item.submitted }}/{{ item.students }} 人交了，这次分析作废。没交的：{{ item.missing.map((s) => s.name).join('、') }}</p>
        <el-button size="small" type="primary" @click="remind(item)">提醒他们</el-button>
        <el-button size="small" @click="extendItem = item; extendAt = null">延长截止时间</el-button>
      </div>

      <template v-if="item.status === 'done'">
        <p v-if="!item.groups.length" class="muted">没有发现需要处理的问题。</p>
        <div v-for="g in item.groups" :key="g.knowledgePointId" class="group">
          <h4>「{{ g.name }}」</h4>
          <div v-for="i in g.issues" :key="i.id" class="issue" :class="[i.kind, i.status]">
            <div class="issue-main">
              <el-checkbox v-if="i.kind === 'cell' && i.status === 'open'" v-model="picked[i.id]" title="同一个知识点的几张卡片可以合并处理" />
              <div>
                <div class="issue-title">
                  <template v-if="i.kind === 'question'">
                    <el-tag size="small" type="danger">题目检查</el-tag> {{ i.stem }}
                  </template>
                  <template v-else>
                    <el-tag size="small">{{ i.assessLabel }}</el-tag>
                    {{ i.keyPointNo ? `要点 ${i.keyPointNo}` : '（未标要点）' }}<span v-if="i.keyPointText">：{{ i.keyPointText }}</span>
                  </template>
                  <span class="rate">错 {{ pct(i.rate) }}</span>
                </div>
                <p class="muted">{{ i.advice }}</p>
                <p v-if="i.method" class="method">推荐方案：<b>{{ i.method }}</b> + 同类型新题（按考查类型「{{ i.assessLabel }}」）</p>
              </div>
            </div>
            <div class="issue-actions">
              <template v-if="i.status !== 'open'"><el-tag type="info" size="small">{{ ISSUE_STATUS[i.status] }}</el-tag></template>
              <template v-else-if="i.kind === 'question'">
                <el-button size="small" @click="check(i, 'ok')">没问题</el-button>
                <el-button size="small" @click="check(i, 'fixed')">已修改</el-button>
                <el-button size="small" type="danger" plain @click="check(i, 'void')">作废</el-button>
              </template>
              <template v-else>
                <el-button size="small" type="primary" :loading="working" @click="handle(g, i, true)">AI 起草讲解 + 出题</el-button>
                <el-button size="small" :loading="working" @click="handle(g, i, false)">我自己写讲解</el-button>
                <el-button size="small" link @click="ignore(i)">忽略</el-button>
              </template>
            </div>
          </div>
        </div>

        <div v-if="item.pushes?.length" class="pushes">
          <h4>已推送 / 草稿</h4>
          <div v-for="p in item.pushes" :key="p.id" class="push-row">
            <span>
              「{{ p.knowledgePointName }}」{{ p.assessLabel }} · {{ p.method }}
              <el-tag size="small" :type="p.status === 'pushed' ? 'success' : 'warning'">{{ p.status === 'pushed' ? '已推送' : '草稿' }}</el-tag>
            </span>
            <span v-if="p.status === 'pushed'" class="effect">
              推给 {{ p.effect.targeted }} 人，{{ p.effect.done }} 人做完；错误率 {{ pct(p.beforeRate) }} → {{ pct(p.effect.afterRate) }}；
              补回来 {{ p.effect.recovered }} 人
            </span>
            <el-button v-else size="small" link type="primary" @click="draft = p">继续编辑</el-button>
          </div>
        </div>
      </template>
    </section>
    <p v-if="pushedCount" class="muted">推送后的效果：推送对象做新题（第一次）的错误率，和推送前比。</p>

    <!-- draft: explanation + new questions -> push -->
    <el-dialog :model-value="!!draft" title="补充讲解 + 新题" width="min(760px, 96vw)" top="4vh" @close="draft = null">
      <template v-if="draft">
        <p class="muted">
          「{{ draft.knowledgePointName }}」{{ draft.keyPoints.join('；') }} · 考查类型 {{ draft.assessLabel }} · 讲解形式 <b>{{ draft.method }}</b>
        </p>
        <h4>补充讲解 <small>{{ draft.explanationBy === 'ai' ? '大模型起草，请检查修改' : '你自己写（也可以贴新的资料链接）' }}</small></h4>
        <el-input v-model="draft.explanation" type="textarea" :rows="8" maxlength="2000" show-word-limit />
        <h4>新题（{{ draft.questions.length }} 道，学生看不到答案）
          <el-button size="small" link type="primary" :loading="working" @click="regenerate">重新出题</el-button>
        </h4>
        <div v-for="(q, k) in draft.questions" :key="q.id" class="q">
          <p><b>{{ k + 1 }}.</b> {{ q.stem }} <el-button size="small" link type="danger" @click="removeQuestion(q)">删掉</el-button></p>
          <p v-for="(o, j) in q.options" :key="j" class="opt" :class="{ right: q.answer.includes('ABCDEF'[j]) }">{{ 'ABCDEF'[j] }}. {{ o }}</p>
          <p v-if="q.checkNote" class="warn">{{ q.checkNote }}</p>
        </div>
        <h4>推送给（{{ draft.students.length }} 人，做错的学生）</h4>
        <p class="muted">{{ draft.students.map((s) => s.name).join('、') }}</p>
        <h4>期限</h4>
        <el-date-picker v-model="draft.dueAt" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" />
      </template>
      <template #footer>
        <el-button @click="saveDraft">保存草稿</el-button>
        <el-button type="primary" @click="push">确认推送</el-button>
      </template>
    </el-dialog>

    <el-dialog :model-value="!!extendItem" title="延长截止时间" width="380px" @close="extendItem = null">
      <p class="muted">到新的截止时间再分析一次，延期前交的都算。</p>
      <el-date-picker v-model="extendAt" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" />
      <template #footer>
        <el-button @click="extendItem = null">取消</el-button>
        <el-button type="primary" :disabled="!extendAt" @click="doExtend">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.class-analysis {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.top {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}
.top h2 {
  margin: 0 0 4px;
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  margin: 4px 0;
}
.item {
  padding: 14px 16px;
}
.item-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.item-head small {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
}
.void {
  margin-top: 8px;
  padding: 10px;
  border-radius: 8px;
  background: var(--el-color-danger-light-9);
}
.group h4 {
  margin: 14px 0 6px;
}
.issue {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 10px;
  margin-bottom: 6px;
  border-left: 3px solid var(--el-color-warning);
  border-radius: 6px;
  background: var(--el-fill-color-lighter);
}
.issue.question {
  border-left-color: var(--el-color-danger);
}
.issue:not(.open) {
  opacity: 0.7;
}
.issue-main {
  display: flex;
  gap: 8px;
}
.issue-title {
  font-weight: 600;
}
.rate {
  margin-left: 8px;
  color: var(--el-color-danger);
}
.method {
  margin: 2px 0 0;
  font-size: 13px;
}
.issue-actions {
  display: flex;
  gap: 4px;
  align-items: flex-start;
  flex-wrap: wrap;
  justify-content: flex-end;
  min-width: 220px;
}
.pushes h4 {
  margin: 14px 0 6px;
}
.push-row {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 0;
  border-top: 1px dashed var(--el-border-color);
  font-size: 13px;
}
.effect {
  color: var(--el-color-success-dark-2);
}
.q {
  margin: 8px 0;
}
.q p {
  margin: 2px 0;
}
.opt.right {
  color: var(--el-color-success);
  font-weight: 600;
}
.warn {
  color: var(--el-color-warning-dark-2);
  font-size: 12px;
}
@media (max-width: 720px) {
  .top,
  .issue {
    flex-direction: column;
  }
}
</style>
