<script setup>
/*
 * TEMPLATE: Form dialog (create + edit in one component, with validation).
 * Usage: <XxxFormDialog v-model="visible" :xxx="rowOrNull" @saved="reload" />
 */
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createCourse, updateCourse } from '@/api/course'

const visible = defineModel({ type: Boolean, default: false })
const props = defineProps({
  course: { type: Object, default: null }, // null = create mode
})
const emit = defineEmits(['saved'])

const isEdit = computed(() => !!props.course)
const formRef = ref()
const saving = ref(false)

const emptyForm = () => ({ name: '', teacher: '', status: 'draft', description: '' })
const form = reactive(emptyForm())

const rules = {
  name: [
    { required: true, message: '请输入课程名称', trigger: 'blur' },
    { max: 50, message: '不超过 50 个字符', trigger: 'blur' },
  ],
  teacher: [{ required: true, message: '请输入授课教师', trigger: 'blur' }],
}

// Fill the form each time the dialog opens
watch(visible, (open) => {
  if (!open) return
  Object.assign(form, emptyForm(), props.course || {})
  formRef.value?.clearValidate()
})

async function submit() {
  await formRef.value.validate()
  saving.value = true
  try {
    if (isEdit.value) await updateCourse(props.course.id, { ...form })
    else await createCourse({ ...form })
    ElMessage.success(isEdit.value ? '已保存' : '已创建')
    visible.value = false
    emit('saved')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" :title="isEdit ? '编辑课程' : '新建课程'" width="520px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="课程名称" prop="name">
        <el-input v-model="form.name" placeholder="例如：软件工程导论" />
      </el-form-item>
      <el-form-item label="授课教师" prop="teacher">
        <el-input v-model="form.teacher" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-radio-group v-model="form.status">
          <el-radio value="draft">草稿</el-radio>
          <el-radio value="published">已发布</el-radio>
          <el-radio value="archived">已归档</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="课程简介" prop="description">
        <el-input v-model="form.description" type="textarea" :rows="3" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>
