<script setup>
/*
 * The questions of one exercise item (a chapter can have several exercise items).
 * Single choice only.
 * Each exercise links to at least one knowledge point (so mastery can be computed),
 * and each wrong option can carry a "误解说明" used later by the wrong-answer review dialogue.
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getExercises,
  createExercise,
  updateExercise,
  deleteExercise,
  getKnowledgePoints,
} from '@/api/teacherCourse'

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

const emptyForm = () => ({
  stem: '',
  options: ['', '', '', ''],
  optionNotes: ['', '', '', ''],
  answer: 'A',
  analysis: '',
  difficulty: 2,
  knowledgePointIds: [],
})
const form = reactive(emptyForm())
const answerIndex = computed(() => LETTERS.indexOf(form.answer))

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
}

function openCreate() {
  if (!kps.value.length) {
    ElMessage.warning('本章还没有知识点，题目要关联知识点，先去添加')
    return emit('go', 'kps')
  }
  Object.assign(form, emptyForm())
  // a new exercise usually belongs to the only knowledge point when there is just one
  if (kps.value.length === 1) form.knowledgePointIds = [kps.value[0].id]
  editingId.value = null
  dialogVisible.value = true
}

function openEdit(row) {
  Object.assign(form, {
    stem: row.stem,
    options: [...row.options],
    optionNotes: row.options.map((_, i) => row.optionNotes[i] || ''),
    answer: row.answer,
    analysis: row.analysis || '',
    difficulty: row.difficulty,
    knowledgePointIds: row.knowledgePoints.map((k) => k.id),
  })
  editingId.value = row.id
  dialogVisible.value = true
}

function addOption() {
  if (form.options.length >= 6) return
  form.options.push('')
  form.optionNotes.push('')
}

function removeOption(i) {
  if (form.options.length <= 2) return
  form.options.splice(i, 1)
  form.optionNotes.splice(i, 1)
  if (answerIndex.value >= form.options.length || answerIndex.value === i) form.answer = 'A'
  else if (answerIndex.value > i) form.answer = LETTERS[answerIndex.value - 1]
}

async function save() {
  if (!form.stem.trim()) return ElMessage.warning('请填写题干')
  if (form.options.some((o) => !o.trim())) return ElMessage.warning('选项不能为空')
  if (!form.knowledgePointIds.length) return ElMessage.warning('至少关联一个知识点')
  saving.value = true
  try {
    if (editingId.value) await updateExercise(editingId.value, form)
    else await createExercise({ ...form, chapterId: props.chapter.id, itemId: props.itemId })
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

onMounted(load)
</script>

<template>
  <div>
    <div class="bar">
      <p class="tip">
        练习里没有 AI：学生做完全部题目才看到结果。答错的题可以进入 AI 错题讲解，讲解会用到每个错误选项的"误解说明"。
      </p>
      <el-button type="primary" icon="Plus" @click="openCreate">新建题目</el-button>
    </div>

    <div v-loading="loading" class="list">
      <el-empty v-if="!loading && !list.length" description="还没有题目" />
      <div v-for="(ex, i) in list" :key="ex.id" class="exercise">
        <div class="ex-head">
          <strong>{{ i + 1 }}. {{ ex.stem }}</strong>
          <div class="ops">
            <el-button link type="primary" @click="openEdit(ex)">编辑</el-button>
            <el-button link type="danger" @click="remove(ex)">删除</el-button>
          </div>
        </div>
        <div v-for="(opt, j) in ex.options" :key="j" class="option" :class="{ correct: LETTERS[j] === ex.answer }">
          <span>{{ LETTERS[j] }}. {{ opt }}</span>
          <span v-if="LETTERS[j] === ex.answer" class="mark">正确答案</span>
          <span v-else-if="ex.optionNotes[j]" class="note">误解：{{ ex.optionNotes[j] }}</span>
        </div>
        <div v-if="ex.analysis" class="analysis">解析：{{ ex.analysis }}</div>
        <div class="kps">
          <el-tag v-for="k in ex.knowledgePoints" :key="k.id" size="small" effect="plain">{{ k.name }}</el-tag>
        </div>
      </div>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑题目' : '新建单选题'"
      width="min(860px, 96vw)"
      top="4vh"
    >
      <el-form label-position="top">
        <el-form-item label="题干" required>
          <el-input v-model="form.stem" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="选项（点左边的圆圈选正确答案；错误选项写上学生为什么会选它）" required>
          <div class="opt-edit">
            <div v-for="(o, i) in form.options" :key="i" class="opt-row">
              <el-radio v-model="form.answer" :value="LETTERS[i]">{{ LETTERS[i] }}</el-radio>
              <el-input v-model="form.options[i]" placeholder="选项内容" class="opt-text" />
              <el-input
                v-model="form.optionNotes[i]"
                :disabled="LETTERS[i] === form.answer"
                :placeholder="LETTERS[i] === form.answer ? '正确答案' : '误解说明（选填）'"
                class="opt-note"
              />
              <el-button icon="Delete" circle text :disabled="form.options.length <= 2" @click="removeOption(i)" />
            </div>
            <el-button text type="primary" icon="Plus" :disabled="form.options.length >= 6" @click="addOption">
              加一个选项
            </el-button>
          </div>
        </el-form-item>
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
  margin-bottom: 12px;
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
  grid-template-columns: 1fr auto;
  gap: 16px;
}
</style>
