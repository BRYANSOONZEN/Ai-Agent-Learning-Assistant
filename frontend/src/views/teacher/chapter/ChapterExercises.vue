<script setup>
/*
 * The questions of one exercise item (a chapter can have several exercise items).
 * Three ways to add questions: 新建 by hand, AI 出题, 上传已有题目 (the last two arrive as drafts to confirm).
 * Six question types and four assessment types (docs/模块说明/练习与掌握度-设计.md).
 * Each question links to at least one knowledge point (so mastery can be computed);
 * wrong options (and wrong reasons in two-tier questions) carry a "误解说明" used for diagnosis.
 * Below: 覆盖检查 for the whole chapter, and the short-answer submissions to confirm.
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getExercises,
  createExercise,
  updateExercise,
  deleteExercise,
  getKnowledgePoints,
  confirmExercises,
} from '@/api/teacherCourse'
import ExerciseGenDialog from './ExerciseGenDialog.vue'
import ExerciseCoverage from './ExerciseCoverage.vue'
import ShortAnswerReview from './ShortAnswerReview.vue'
import { ASSESS_TYPES, CUSTOM_ASSESS, QUESTION_TYPES, SOURCE_LABEL, assessLabel, typeLabel } from './exerciseLabels'

const props = defineProps({
  chapter: { type: Object, required: true },
  itemId: { type: Number, required: true }, // the exercise item these questions belong to
})
const emit = defineEmits(['changed', 'go'])

const LETTERS = 'ABCDEF'
const list = ref([])
const kps = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const editingId = ref(null)
const saving = ref(false)
const genOpen = ref(false)
const genMode = ref('ai')
const coverage = ref(null)
const review = ref(null)

const drafts = computed(() => list.value.filter((e) => e.draft).length)

const emptyForm = () => ({
  type: 'single_choice',
  assessType: 'concept',
  stem: '',
  options: ['', '', '', ''],
  optionNotes: ['', '', '', ''],
  answer: 'A',
  multiAnswer: [],
  reasons: ['', '', ''],
  reasonNotes: ['', '', ''],
  reasonAnswer: 'A',
  accepts: [''],
  reference: '',
  keyPoints: ['', ''],
  analysis: '',
  difficulty: 2,
  knowledgePointIds: [],
})
const form = reactive(emptyForm())
const hasOptions = computed(() => ['single_choice', 'two_tier', 'multiple'].includes(form.type))

async function load() {
  loading.value = true
  try {
    ;[list.value, kps.value] = await Promise.all([
      getExercises(props.chapter.id, props.itemId),
      getKnowledgePoints(props.chapter.id),
    ])
  } finally {
    loading.value = false
  }
  coverage.value?.load()
}

function openCreate() {
  if (!kps.value.length) {
    ElMessage.warning('本章还没有知识点，题目要关联知识点，先去添加')
    return emit('go', 'kps')
  }
  Object.assign(form, emptyForm())
  if (kps.value.length === 1) form.knowledgePointIds = [kps.value[0].id]
  editingId.value = null
  dialogVisible.value = true
}

function openEdit(row) {
  Object.assign(form, emptyForm(), {
    type: row.type,
    assessType: row.assessType,
    stem: row.stem,
    options: row.options.length && row.type !== 'judge' ? [...row.options] : ['', '', '', ''],
    optionNotes: row.options.map((_, i) => row.optionNotes[i] || ''),
    answer: row.type === 'multiple' ? 'A' : row.answer || 'A',
    multiAnswer: row.type === 'multiple' ? row.answer.split('') : [],
    reasons: row.reasons.length ? [...row.reasons] : ['', '', ''],
    reasonNotes: row.reasons.length ? row.reasons.map((_, i) => row.reasonNotes[i] || '') : ['', '', ''],
    reasonAnswer: row.reasonAnswer || 'A',
    accepts: row.accepts.length ? [...row.accepts] : [''],
    reference: row.reference || '',
    keyPoints: row.keyPoints.length ? [...row.keyPoints] : ['', ''],
    analysis: row.analysis || '',
    difficulty: row.difficulty,
    knowledgePointIds: row.knowledgePoints.map((k) => k.id),
  })
  if (form.optionNotes.length < form.options.length) form.optionNotes = form.options.map((_, i) => form.optionNotes[i] || '')
  editingId.value = row.id
  dialogVisible.value = true
}

function addOption(listKey, notesKey) {
  if (form[listKey].length >= 6) return
  form[listKey].push('')
  if (notesKey) form[notesKey].push('')
}

function removeOption(i) {
  if (form.options.length <= 2) return
  form.options.splice(i, 1)
  form.optionNotes.splice(i, 1)
  const idx = LETTERS.indexOf(form.answer)
  if (idx >= form.options.length || idx === i) form.answer = 'A'
  else if (idx > i) form.answer = LETTERS[idx - 1]
  form.multiAnswer = form.multiAnswer.filter((l) => LETTERS.indexOf(l) < form.options.length)
}

function payload() {
  const base = {
    type: form.type,
    assessType: form.assessType,
    stem: form.stem,
    analysis: form.analysis,
    difficulty: form.difficulty,
    knowledgePointIds: form.knowledgePointIds,
  }
  if (form.type === 'fill') return { ...base, accepts: form.accepts.filter((a) => a.trim()) }
  if (form.type === 'short_answer') {
    return { ...base, reference: form.reference, keyPoints: form.keyPoints.filter((a) => a.trim()) }
  }
  if (form.type === 'judge') return { ...base, options: ['正确', '错误'], answer: form.answer, optionNotes: form.optionNotes.slice(0, 2) }
  const p = {
    ...base,
    options: form.options,
    optionNotes: form.optionNotes,
    answer: form.type === 'multiple' ? form.multiAnswer.join('') : form.answer,
  }
  if (form.type === 'two_tier') Object.assign(p, { reasons: form.reasons, reasonNotes: form.reasonNotes, reasonAnswer: form.reasonAnswer })
  return p
}

async function save() {
  if (!form.stem.trim()) return ElMessage.warning('请填写题干')
  if (hasOptions.value && form.options.some((o) => !o.trim())) return ElMessage.warning('选项不能为空')
  if (!form.knowledgePointIds.length) return ElMessage.warning('至少关联一个知识点')
  saving.value = true
  try {
    if (editingId.value) await updateExercise(editingId.value, payload())
    else await createExercise({ ...payload(), chapterId: props.chapter.id, itemId: props.itemId })
    ElMessage.success('已保存')
    dialogVisible.value = false
    await load()
    emit('changed')
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  await ElMessageBox.confirm('确定删除这道题吗？', '提示', { type: 'warning' })
  await deleteExercise(row.id)
  ElMessage.success('已删除')
  await load()
  emit('changed')
}

async function confirmAll() {
  const r = await confirmExercises(props.itemId)
  ElMessage.success(`已确认 ${r.confirmed} 道题，学生现在看得到`)
  await load()
  emit('changed')
}

function openGen(mode) {
  if (mode === 'upload' && !kps.value.length) {
    ElMessage.warning('本章还没有知识点，题目要关联知识点，先去添加')
    return emit('go', 'kps')
  }
  genMode.value = mode
  genOpen.value = true
}

onMounted(load)
</script>

<template>
  <div>
    <div class="bar">
      <p class="tip">学生做完全部题目才看到结果。客观题由程序判分；简答由 AI 判两次，不一致的交给你确认。</p>
      <div class="btns">
        <el-button type="primary" plain icon="MagicStick" @click="openGen('ai')">AI 出题</el-button>
        <el-button plain icon="Upload" @click="openGen('upload')">上传已有题目</el-button>
        <el-button type="primary" icon="Plus" @click="openCreate">新建题目</el-button>
      </div>
    </div>

    <el-alert v-if="drafts" type="warning" :closable="false" class="draft-bar">
      <template #title>
        有 {{ drafts }} 道草稿题（AI 生成或上传识别的），学生还看不到。检查、修改后点确认。
        <el-button size="small" type="primary" class="confirm" @click="confirmAll">确认全部草稿</el-button>
      </template>
    </el-alert>

    <ExerciseCoverage ref="coverage" :chapter-id="chapter.id" />

    <div v-loading="loading" class="list">
      <el-empty v-if="!loading && !list.length" description="还没有题目" />
      <div v-for="(ex, i) in list" :key="ex.id" class="exercise" :class="{ draft: ex.draft }">
        <div class="ex-head">
          <div>
            <div class="tags">
              <span class="sw-tag blue">{{ typeLabel(ex.type) }}</span>
              <span class="sw-tag gray">{{ assessLabel(ex.assessType) }}</span>
              <span v-if="ex.source !== 'manual'" class="sw-tag">{{ SOURCE_LABEL[ex.source] }}</span>
              <span v-if="ex.draft" class="sw-tag orange">草稿</span>
              <span v-if="ex.keyPointNo" class="muted">考要点 {{ ex.keyPointNo }}</span>
            </div>
            <strong>{{ i + 1 }}. {{ ex.stem }}</strong>
          </div>
          <div class="ops">
            <el-button link type="primary" @click="openEdit(ex)">编辑</el-button>
            <el-button link type="danger" @click="remove(ex)">删除</el-button>
          </div>
        </div>
        <div v-if="ex.checkNote" class="check">{{ ex.checkNote }}</div>
        <template v-if="ex.options.length">
          <div v-if="ex.type === 'two_tier'" class="part">答案</div>
          <div v-for="(opt, j) in ex.options" :key="j" class="option" :class="{ correct: ex.answer.includes(LETTERS[j]) }">
            <span>{{ LETTERS[j] }}. {{ opt }}</span>
            <span v-if="ex.answer.includes(LETTERS[j])" class="mark">正确</span>
            <span v-else-if="ex.optionNotes[j]" class="note">误解：{{ ex.optionNotes[j] }}</span>
          </div>
          <template v-if="ex.type === 'two_tier'">
            <div class="part">理由</div>
            <div v-for="(opt, j) in ex.reasons" :key="'r' + j" class="option" :class="{ correct: ex.reasonAnswer === LETTERS[j] }">
              <span>{{ LETTERS[j] }}. {{ opt }}</span>
              <span v-if="ex.reasonAnswer === LETTERS[j]" class="mark">正确</span>
              <span v-else-if="ex.reasonNotes[j]" class="note">误解：{{ ex.reasonNotes[j] }}</span>
            </div>
          </template>
        </template>
        <div v-else-if="ex.type === 'fill'" class="option correct">都算对：{{ ex.accepts.join(' / ') }}</div>
        <template v-else>
          <div class="analysis">参考答案：{{ ex.reference }}</div>
          <div v-for="(k, j) in ex.keyPoints" :key="j" class="option">判分要点 {{ j + 1 }}：{{ k }}</div>
        </template>
        <div v-if="ex.analysis" class="analysis">解析：{{ ex.analysis }}</div>
        <div class="kps">
          <el-tag v-for="k in ex.knowledgePoints" :key="k.id" size="small" effect="plain">{{ k.name }}</el-tag>
        </div>
      </div>
    </div>

    <ShortAnswerReview ref="review" :item-id="itemId" />

    <ExerciseGenDialog v-model="genOpen" :mode="genMode" :chapter-id="chapter.id" :item-id="itemId" @done="load" />

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑题目' : '新建题目'" width="min(860px, 96vw)" top="4vh">
      <el-form label-position="top">
        <div class="row2">
          <el-form-item label="题型" required>
            <el-select v-model="form.type" :disabled="!!editingId && form.type === 'short_answer'">
              <el-option v-for="t in QUESTION_TYPES" :key="t.value" :label="t.label" :value="t.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="考查类型" required>
            <el-select v-model="form.assessType">
              <el-option v-for="a in [...ASSESS_TYPES, CUSTOM_ASSESS]" :key="a.value" :label="a.label + '：' + a.hint" :value="a.value" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="题干" required>
          <el-input v-model="form.stem" type="textarea" :rows="3" :placeholder="form.type === 'fill' ? '用 ____ 表示要填的空' : ''" />
        </el-form-item>

        <el-form-item v-if="form.type === 'judge'" label="这个说法" required>
          <el-radio-group v-model="form.answer">
            <el-radio value="A">正确</el-radio>
            <el-radio value="B">错误</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item v-if="hasOptions" :label="form.type === 'multiple' ? '选项（勾选全部正确答案）' : '选项（点左边选正确答案；错误选项写上学生为什么会选它）'" required>
          <div class="opt-edit">
            <div v-for="(o, i) in form.options" :key="i" class="opt-row">
              <el-checkbox v-if="form.type === 'multiple'" v-model="form.multiAnswer" :value="LETTERS[i]">{{ LETTERS[i] }}</el-checkbox>
              <el-radio v-else v-model="form.answer" :value="LETTERS[i]">{{ LETTERS[i] }}</el-radio>
              <el-input v-model="form.options[i]" placeholder="选项内容" class="opt-text" />
              <el-input
                v-if="form.type !== 'multiple'"
                v-model="form.optionNotes[i]"
                :disabled="LETTERS[i] === form.answer"
                :placeholder="LETTERS[i] === form.answer ? '正确答案' : '误解说明（选填）'"
                class="opt-note"
              />
              <el-button icon="Delete" circle text :disabled="form.options.length <= 2" @click="removeOption(i)" />
            </div>
            <el-button text type="primary" icon="Plus" :disabled="form.options.length >= 6" @click="addOption('options', 'optionNotes')">
              加一个选项
            </el-button>
          </div>
        </el-form-item>

        <el-form-item v-if="form.type === 'two_tier'" label="理由（第二步：为什么）" required>
          <div class="opt-edit">
            <div v-for="(o, i) in form.reasons" :key="i" class="opt-row">
              <el-radio v-model="form.reasonAnswer" :value="LETTERS[i]">{{ LETTERS[i] }}</el-radio>
              <el-input v-model="form.reasons[i]" placeholder="理由" class="opt-text" />
              <el-input
                v-model="form.reasonNotes[i]"
                :disabled="LETTERS[i] === form.reasonAnswer"
                :placeholder="LETTERS[i] === form.reasonAnswer ? '正确理由' : '对应的误解（选填）'"
                class="opt-note"
              />
            </div>
            <el-button text type="primary" icon="Plus" :disabled="form.reasons.length >= 6" @click="addOption('reasons', 'reasonNotes')">
              加一个理由
            </el-button>
          </div>
        </el-form-item>

        <el-form-item v-if="form.type === 'fill'" label="都算对的说法（1～5 个，判分时去掉空格和标点、不分大小写）" required>
          <div class="opt-edit">
            <el-input v-for="(a, i) in form.accepts" :key="i" v-model="form.accepts[i]" class="line" placeholder="一种说法" />
            <el-button text type="primary" icon="Plus" :disabled="form.accepts.length >= 5" @click="form.accepts.push('')">加一个说法</el-button>
          </div>
        </el-form-item>

        <template v-if="form.type === 'short_answer'">
          <el-form-item label="参考答案" required>
            <el-input v-model="form.reference" type="textarea" :rows="3" />
          </el-form-item>
          <el-form-item label="判分要点（2～4 条；得分 = 答到的要点数 ÷ 要点总数）" required>
            <div class="opt-edit">
              <el-input v-for="(a, i) in form.keyPoints" :key="i" v-model="form.keyPoints[i]" class="line" :placeholder="'要点 ' + (i + 1)" />
              <el-button text type="primary" icon="Plus" :disabled="form.keyPoints.length >= 5" @click="form.keyPoints.push('')">加一条要点</el-button>
            </div>
          </el-form-item>
        </template>

        <el-form-item label="解析">
          <el-input v-model="form.analysis" type="textarea" :rows="2" />
        </el-form-item>
        <div class="row2">
          <el-form-item label="关联知识点" required>
            <el-select v-model="form.knowledgePointIds" multiple placeholder="选择知识点" style="width: 100%">
              <el-option v-for="k in kps" :key="k.id" :label="k.name" :value="k.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="难度">
            <el-radio-group v-model="form.difficulty">
              <el-radio :value="1">易</el-radio>
              <el-radio :value="2">中</el-radio>
              <el-radio :value="3">难</el-radio>
            </el-radio-group>
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.btns {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}
.draft-bar {
  margin-bottom: 12px;
}
.confirm {
  margin-left: 10px;
}
.exercise.draft {
  border-style: dashed;
  border-color: #f0c48a;
}
.tags {
  display: flex;
  gap: 6px;
  align-items: center;
  margin-bottom: 4px;
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.check {
  margin-bottom: 6px;
  padding: 4px 8px;
  border-radius: 6px;
  background: #fff4e5;
  color: #9a5a1c;
  font-size: 12px;
}
.part {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.line {
  margin-bottom: 6px;
}
.tip {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.list {
  min-height: 80px;
}
.exercise {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 12px;
  margin-bottom: 10px;
}
.ex-head {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}
.ops {
  flex: none;
}
.option {
  display: flex;
  gap: 12px;
  padding: 3px 0;
  font-size: 14px;
}
.option.correct {
  color: var(--el-color-success);
  font-weight: 600;
}
.mark,
.note {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  font-weight: normal;
}
.analysis {
  margin-top: 6px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}
.kps {
  margin-top: 8px;
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.opt-edit {
  width: 100%;
}
.opt-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.opt-text {
  flex: 1;
}
.opt-note {
  flex: 1;
}
.row2 {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 16px;
}
</style>
