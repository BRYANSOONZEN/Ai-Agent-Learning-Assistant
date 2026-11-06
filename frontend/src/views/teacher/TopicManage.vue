<script setup>
/*
 * Teacher page: dialogue topics for one chapter.
 * Flow: teacher writes question + reference answer -> "AI 拆要点" -> edit the draft -> save.
 * Saved topics are what the student-side AI dialogue will ask, in this order.
 * Used two ways: as its own page (chapter 1), and embedded in CourseBuilder inside an AI 对话 item.
 * Each topic tests one knowledge point (the concept layer); 干预 matches it with that point's exercises.
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import {
  getAiStatus,
  draftTopic,
  getTopics,
  createTopic,
  updateTopic,
  deleteTopic,
} from '@/api/teacherTopic'
import { getKnowledgePoints } from '@/api/teacherCourse'

const props = defineProps({
  chapterId: { type: Number, default: 1 },
  itemId: { type: Number, default: undefined }, // the AI 对话 item these topics belong to (CourseBuilder)
  embedded: { type: Boolean, default: false }, // true = inside CourseBuilder, no page header
})
const emit = defineEmits(['changed'])

const aiStatus = ref({ available: true, model: '' })
const topics = ref([])
const points = ref([]) // this chapter's knowledge points, for the topic's 考哪个知识点
const loading = ref(false)

const dialogVisible = ref(false)
const editingId = ref(null)
const drafting = ref(false)
const saving = ref(false)
const draftInfo = ref('') // e.g. "gemini-2.5-flash，用时 3.2 秒"

const emptyForm = () => ({
  question: '',
  answer: '', // saved as revealText
  sourceRef: '',
  sourceText: '',
  title: '',
  keyPoints: [],
  simpleQuestion: '',
  hints: ['', '', ''],
  transferQuestion: '',
  transferPoints: '', // one per line in the form
  transferReveal: '',
  createdByAi: false,
  knowledgePointId: null,
})
const form = reactive(emptyForm())

const pointName = (id) => points.value.find((kp) => kp.id === id)?.name
const hasDraft = computed(() => form.keyPoints.length > 0 || form.title !== '')

async function load() {
  loading.value = true
  try {
    topics.value = await getTopics(props.chapterId, props.itemId)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, emptyForm())
  editingId.value = null
  draftInfo.value = ''
  dialogVisible.value = true
}

function openEdit(t) {
  Object.assign(form, {
    question: t.question,
    answer: t.revealText,
    sourceRef: t.sourceRef || '',
    sourceText: t.sourceText || '',
    title: t.title,
    keyPoints: [...t.keyPoints],
    simpleQuestion: t.simpleQuestion || '',
    hints: [t.hints[0] || '', t.hints[1] || '', t.hints[2] || ''],
    transferQuestion: t.transferQuestion || '',
    transferPoints: (t.transferPoints || []).join('\n'),
    transferReveal: t.transferReveal || '',
    createdByAi: t.createdByAi,
    knowledgePointId: t.knowledgePointId ?? null,
  })
  editingId.value = t.id
  draftInfo.value = ''
  dialogVisible.value = true
}

async function runDraft() {
  if (!form.question.trim() || !form.answer.trim()) {
    ElMessage.warning('先填写情境问题和参考答案')
    return
  }
  drafting.value = true
  try {
    const d = await draftTopic({
      question: form.question,
      answer: form.answer,
      sourceRef: form.sourceRef,
      sourceText: form.sourceText,
    })
    form.title = d.title
    form.keyPoints = d.keyPoints
    form.simpleQuestion = d.simpleQuestion
    form.hints = [d.hints[0] || '', d.hints[1] || '', d.hints[2] || '']
    form.transferQuestion = d.transferQuestion || ''
    form.transferPoints = (d.transferPoints || []).join('\n')
    form.transferReveal = d.transferReveal || ''
    form.createdByAi = true
    draftInfo.value = `${d.model}，用时 ${(d.latencyMs / 1000).toFixed(1)} 秒`
  } finally {
    drafting.value = false
  }
}

function addPoint() {
  form.keyPoints.push('')
}

function removePoint(i) {
  form.keyPoints.splice(i, 1)
}

async function save() {
  if (!form.title.trim()) return ElMessage.warning('话题名不能为空')
  if (!form.keyPoints.some((p) => p.trim())) return ElMessage.warning('至少要有一条要点')
  saving.value = true
  try {
    const payload = {
      title: form.title,
      question: form.question,
      simpleQuestion: form.simpleQuestion,
      keyPoints: form.keyPoints,
      hints: form.hints,
      transferQuestion: form.transferQuestion,
      transferPoints: form.transferPoints.split('\n'),
      transferReveal: form.transferReveal,
      revealText: form.answer,
      sourceRef: form.sourceRef,
      sourceText: form.sourceText,
      createdByAi: form.createdByAi,
      knowledgePointId: form.knowledgePointId,
    }
    if (editingId.value) await updateTopic(editingId.value, payload)
    else await createTopic(payload, props.chapterId, props.itemId)
    ElMessage.success('已保存')
    dialogVisible.value = false
    load()
    emit('changed')
  } finally {
    saving.value = false
  }
}

async function remove(t) {
  await ElMessageBox.confirm(`确定删除话题「${t.title}」吗？`, '提示', { type: 'warning' })
  await deleteTopic(t.id)
  ElMessage.success('已删除')
  load()
  emit('changed')
}

onMounted(async () => {
  load()
  getKnowledgePoints(props.chapterId)
    .then((list) => (points.value = list))
    .catch(() => {})
  aiStatus.value = await getAiStatus()
})
</script>

<template>
  <div>
    <div v-if="embedded" class="embedded-bar">
      <p>每个话题对应一个知识点：填写情境问题和参考答案，AI 拆出要点和提示，确认后保存。学生学到这一项时，AI 按这里的顺序提问。</p>
      <el-button type="primary" icon="Plus" @click="openCreate">新建话题</el-button>
    </div>
    <PageHeader
      v-else
      title="对话话题"
      description="填写情境问题和参考答案，AI 拆出要点和提示，确认后保存。学生的 AI 对话按这里的顺序提问。"
    >
      <el-button type="primary" icon="Plus" @click="openCreate">新建话题</el-button>
    </PageHeader>

    <el-alert
      v-if="!aiStatus.available"
      type="warning"
      show-icon
      :closable="false"
      title="AI 暂时不可用：后端没有设置环境变量 LLM_API_KEY。可以手动填写要点后保存。"
      class="mb"
    />

    <div v-loading="loading" class="topic-list">
      <el-empty v-if="!loading && topics.length === 0" description="还没有话题" />

      <div v-for="(t, i) in topics" :key="t.id" class="app-card topic">
        <div class="topic-head">
          <div class="topic-title">
            <span class="order">话题 {{ i + 1 }}</span>
            <strong>{{ t.title }}</strong>
            <el-tag v-if="t.createdByAi" size="small" type="warning">AI 起草</el-tag>
            <el-tag v-if="pointName(t.knowledgePointId)" size="small" effect="plain">考：{{ pointName(t.knowledgePointId) }}</el-tag>
          </div>
          <div>
            <el-button link type="primary" @click="openEdit(t)">编辑</el-button>
            <el-button link type="danger" @click="remove(t)">删除</el-button>
          </div>
        </div>

        <p class="question">{{ t.question }}</p>

        <div class="row">
          <span class="label">要点</span>
          <div class="points">
            <el-tag v-for="(p, j) in t.keyPoints" :key="j" type="info" effect="plain">
              {{ j + 1 }}. {{ p }}
            </el-tag>
          </div>
        </div>
        <div v-if="t.simpleQuestion" class="row">
          <span class="label">简单问法</span>
          <span>{{ t.simpleQuestion }}</span>
        </div>
        <div v-for="(h, j) in t.hints" :key="'h' + j" class="row">
          <span class="label">提示 L{{ j + 1 }}</span>
          <span>{{ h }}</span>
        </div>
        <div class="row">
          <span class="label">讲解</span>
          <span>{{ t.revealText }}</span>
        </div>
        <div v-if="t.transferQuestion" class="row">
          <span class="label">变式题</span>
          <span>{{ t.transferQuestion }}</span>
        </div>
        <div v-if="t.transferQuestion" class="row">
          <span class="label">变式要点</span>
          <div class="points">
            <el-tag v-for="(p, j) in t.transferPoints" :key="j" type="info" effect="plain">
              {{ j + 1 }}. {{ p }}
            </el-tag>
          </div>
        </div>
        <div v-if="t.transferQuestion" class="row">
          <span class="label">变式答案</span>
          <span>{{ t.transferReveal }}</span>
        </div>
        <div v-if="t.sourceRef" class="row muted">
          <span class="label">出处</span>
          <span>{{ t.sourceRef }}<template v-if="t.sourceText">：{{ t.sourceText }}</template></span>
        </div>
      </div>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑话题' : '新建话题'"
      width="min(1080px, 96vw)"
      top="4vh"
    >
      <div class="editor">
        <section>
          <h3>1. 老师填写</h3>
          <el-form label-position="top">
            <el-form-item label="情境问题" required>
              <el-input
                v-model="form.question"
                type="textarea"
                :rows="3"
                placeholder="例：某网上商城规定每单购买数量为 1 到 99 件。测试下限时，你会挑哪几个数量来测？"
              />
            </el-form-item>
            <el-form-item label="参考答案（也作为错两次后的讲解原文）" required>
              <el-input
                v-model="form.answer"
                type="textarea"
                :rows="4"
                placeholder="例：取 0、1、2。0 刚越过下边界，系统应拒绝；1 正好在边界上……"
              />
            </el-form-item>
            <el-form-item label="出处">
              <el-input v-model="form.sourceRef" placeholder="例：课件第 13 页" />
            </el-form-item>
            <el-form-item label="依据原文（选填）">
              <el-input
                v-model="form.sourceText"
                type="textarea"
                :rows="2"
                placeholder="课件里对应的原理句"
              />
            </el-form-item>
          </el-form>
          <el-button
            type="primary"
            icon="MagicStick"
            :loading="drafting"
            :disabled="!aiStatus.available"
            @click="runDraft"
          >
            {{ drafting ? 'AI 正在拆要点…' : hasDraft ? '让 AI 重新拆' : 'AI 拆要点' }}
          </el-button>
          <span v-if="draftInfo" class="draft-info">{{ draftInfo }}</span>
        </section>

        <section>
          <h3>2. 确认草稿（可直接修改）</h3>
          <el-form label-position="top">
            <el-form-item label="话题名" required>
              <el-input v-model="form.title" placeholder="AI 起草，或自己填写" />
            </el-form-item>
            <el-form-item label="考哪个知识点（AI 对话考基础概念，干预时和这个知识点的练习题对照）">
              <el-select v-model="form.knowledgePointId" placeholder="选择本章的知识点" clearable style="width: 100%">
                <el-option v-for="kp in points" :key="kp.id" :label="kp.name" :value="kp.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="要点（学生要答到的几条）" required>
              <div class="point-edit">
                <div v-for="(p, i) in form.keyPoints" :key="i" class="point-row">
                  <span class="num">{{ i + 1 }}</span>
                  <el-input v-model="form.keyPoints[i]" />
                  <el-button icon="Delete" circle text @click="removePoint(i)" />
                </div>
                <el-button icon="Plus" text type="primary" @click="addPoint">加一条要点</el-button>
              </div>
            </el-form-item>
            <el-form-item label="简单问法（学生答非所问时用）">
              <el-input v-model="form.simpleQuestion" />
            </el-form-item>
            <el-form-item label="提示 L1（只指方向）">
              <el-input v-model="form.hints[0]" />
            </el-form-item>
            <el-form-item label="提示 L2（缩小范围，反问错误想法）">
              <el-input v-model="form.hints[1]" />
            </el-form-item>
            <el-form-item label="提示 L3（拆成一个很小的问题）">
              <el-input v-model="form.hints[2]" />
            </el-form-item>
            <el-form-item label="变式题（换情境考同一个知识点，学生试过才看完整讲解）">
              <el-input v-model="form.transferQuestion" type="textarea" :rows="2" />
            </el-form-item>
            <el-form-item label="变式要点（一行一条）">
              <el-input v-model="form.transferPoints" type="textarea" :rows="3" />
            </el-form-item>
            <el-form-item label="变式参考答案">
              <el-input v-model="form.transferReveal" type="textarea" :rows="2" />
            </el-form-item>
          </el-form>
        </section>
      </div>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">确认保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.embedded-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.embedded-bar p {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.mb {
  margin-bottom: var(--app-space-md);
}
.topic-list {
  display: flex;
  flex-direction: column;
  gap: var(--app-space-md);
  min-height: 120px;
}
.topic-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
.topic-title {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.order {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.question {
  margin: 10px 0 12px;
  line-height: 1.6;
}
.row {
  display: flex;
  gap: 12px;
  margin-top: 6px;
  line-height: 1.6;
  font-size: 14px;
}
.label {
  flex: 0 0 64px;
  color: var(--el-text-color-secondary);
}
.points {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.muted {
  color: var(--el-text-color-secondary);
}
.editor {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
}
@media (max-width: 760px) {
  .editor {
    grid-template-columns: 1fr;
  }
}
.editor h3 {
  margin: 0 0 12px;
  font-size: 15px;
}
.draft-info {
  margin-left: 12px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.point-edit {
  width: 100%;
}
.point-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.num {
  width: 18px;
  color: var(--el-text-color-secondary);
}
</style>
