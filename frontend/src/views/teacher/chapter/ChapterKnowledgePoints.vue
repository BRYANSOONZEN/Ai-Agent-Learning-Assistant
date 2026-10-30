<script setup>
/*
 * Knowledge points of this chapter (teacher-side; students do not study them as a separate item).
 * Each one has a short explanation (the "原理句" from the courseware). Exercises link to these,
 * and dialogue topics are written from them.
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getKnowledgePoints,
  createKnowledgePoint,
  updateKnowledgePoint,
  deleteKnowledgePoint,
} from '@/api/teacherCourse'

const props = defineProps({ chapter: { type: Object, required: true } })
const emit = defineEmits(['changed'])

const difficultyLabel = { 1: '易', 2: '中', 3: '难' }

const list = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const editingId = ref(null)
const saving = ref(false)
const emptyForm = () => ({ name: '', content: '', difficulty: 2, isKey: false, isDifficult: false })
const form = reactive(emptyForm())

async function load() {
  loading.value = true
  try {
    list.value = await getKnowledgePoints(props.chapter.id)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, emptyForm())
  editingId.value = null
  dialogVisible.value = true
}

function openEdit(row) {
  Object.assign(form, {
    name: row.name,
    content: row.content || '',
    difficulty: row.difficulty,
    isKey: row.isKey,
    isDifficult: row.isDifficult,
  })
  editingId.value = row.id
  dialogVisible.value = true
}

async function save() {
  if (!form.name.trim()) return ElMessage.warning('请填写知识点名称')
  saving.value = true
  try {
    if (editingId.value) await updateKnowledgePoint(editingId.value, form)
    else await createKnowledgePoint({ ...form, chapterId: props.chapter.id })
    ElMessage.success('已保存')
    dialogVisible.value = false
    await load()
    emit('changed')
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  await ElMessageBox.confirm(`确定删除知识点「${row.name}」吗？`, '提示', { type: 'warning' })
  await deleteKnowledgePoint(row.id)
  ElMessage.success('已删除')
  await load()
  emit('changed')
}

onMounted(load)
</script>

<template>
  <div>
    <div class="bar">
      <p class="tip">对照资料整理本章的知识点，"说明"里写课件中的原理句。题目和对话话题都挂在知识点上。</p>
      <el-button type="primary" icon="Plus" @click="openCreate">新建知识点</el-button>
    </div>

    <el-table v-loading="loading" :data="list" empty-text="还没有知识点">
      <el-table-column type="index" label="#" width="50" />
      <el-table-column prop="name" label="名称" min-width="150">
        <template #default="{ row }">
          {{ row.name }}
          <el-tag v-if="row.isKey" size="small" type="danger">重点</el-tag>
          <el-tag v-if="row.isDifficult" size="small" type="warning">难点</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="content" label="说明（原理句）" min-width="240" show-overflow-tooltip />
      <el-table-column label="难度" width="70">
        <template #default="{ row }">{{ difficultyLabel[row.difficulty] }}</template>
      </el-table-column>
      <el-table-column prop="exerciseCount" label="题目数" width="80" />
      <el-table-column label="操作" width="130">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑知识点' : '新建知识点'" width="min(560px, 94vw)">
      <el-form label-position="top">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" maxlength="100" placeholder="例：边界测试值的选取" />
        </el-form-item>
        <el-form-item label="说明（原理句）">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="3"
            placeholder="例：边界值分析：在边界上、边界外侧、边界内侧各取值进行测试。"
          />
        </el-form-item>
        <el-form-item label="难度">
          <el-radio-group v-model="form.difficulty">
            <el-radio :value="1">易</el-radio>
            <el-radio :value="2">中</el-radio>
            <el-radio :value="3">难</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="标注">
          <el-checkbox v-model="form.isKey">重点</el-checkbox>
          <el-checkbox v-model="form.isDifficult">难点</el-checkbox>
        </el-form-item>
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
</style>
