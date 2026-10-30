<script setup>
/*
 * Create / edit a course: name, intro, status, and which modules the course uses.
 * Used by 首页 (teacher) and 我的课程. Emits `saved` with the course and whether it is new.
 */
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createCourse, updateCourse } from '@/api/teacherCourse'
import { MODULES, ALL_MODULE_KEYS } from './courseModules'

const visible = defineModel({ type: Boolean, default: false })
const props = defineProps({ course: { type: Object, default: null } }) // null = create
const emit = defineEmits(['saved'])

const form = reactive({ name: '', description: '', status: 'draft', modules: [] })
const saving = ref(false)

watch(visible, (open) => {
  if (!open) return
  const c = props.course
  Object.assign(form, {
    name: c?.name || '',
    description: c?.description || '',
    status: c?.status || 'draft',
    modules: c?.modules?.length ? [...c.modules] : [...ALL_MODULE_KEYS],
  })
})

// exercises link to knowledge points, so 题目 needs 知识点
watch(
  () => form.modules.slice(),
  (now, before = []) => {
    const k = 'knowledge'
    if (now.includes('exercise') && !now.includes(k)) {
      if (before.includes(k)) form.modules = now.filter((m) => m !== 'exercise')
      else form.modules = [...now, k]
    }
  },
)

function toggle(key) {
  form.modules = form.modules.includes(key) ? form.modules.filter((m) => m !== key) : [...form.modules, key]
}

async function save() {
  if (!form.name.trim()) return ElMessage.warning('请填写课程名')
  if (!form.modules.length) return ElMessage.warning('至少选一个模块')
  saving.value = true
  try {
    const saved = props.course ? await updateCourse(props.course.id, form) : await createCourse(form)
    ElMessage.success(props.course ? '已保存' : '课程已创建，接着添加章节')
    visible.value = false
    emit('saved', saved, !props.course)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" :title="course ? '课程设置' : '新建课程'" width="min(640px, 94vw)">
    <el-form label-position="top">
      <el-form-item label="课程名" required>
        <el-input v-model="form.name" maxlength="100" placeholder="例：软件测试" />
      </el-form-item>
      <el-form-item label="简介">
        <el-input v-model="form.description" type="textarea" :rows="3" />
      </el-form-item>
      <el-form-item label="这门课用哪些模块" required>
        <div class="modules">
          <button
            v-for="m in MODULES"
            :key="m.key"
            type="button"
            class="module"
            :class="{ on: form.modules.includes(m.key) }"
            :aria-pressed="form.modules.includes(m.key)"
            @click="toggle(m.key)"
          >
            <el-icon class="check"><CircleCheckFilled /></el-icon>
            <el-icon :size="22" class="icon"><component :is="m.icon" /></el-icon>
            <strong>{{ m.label }}</strong>
            <span class="desc">{{ m.desc }}</span>
          </button>
        </div>
        <div class="hint">
          章节介绍和预览发布每门课都有。题目要关联知识点，选题目会自动带上知识点。以后可以在「课程设置」里改，关掉的模块内容不会删除。
        </div>
      </el-form-item>
      <el-form-item label="状态">
        <el-radio-group v-model="form.status">
          <el-radio value="draft">草稿</el-radio>
          <el-radio value="published">已发布</el-radio>
          <el-radio value="archived">已归档</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.modules {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 10px;
  width: 100%;
}
.module {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  padding: 12px;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  background: var(--el-bg-color);
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}
.module:hover {
  border-color: var(--el-color-primary-light-5);
}
.module.on {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}
.icon {
  color: var(--el-text-color-secondary);
}
.module.on .icon {
  color: var(--el-color-primary);
}
.check {
  position: absolute;
  top: 8px;
  right: 8px;
  font-size: 18px;
  color: var(--el-border-color);
}
.module.on .check {
  color: var(--el-color-primary);
}
.desc {
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-text-color-secondary);
}
.hint {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}
</style>
