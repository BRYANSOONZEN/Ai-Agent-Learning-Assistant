<script setup>
/* Change your own password (any role). The backend checks the old password and the same rules as sign-up. */
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { changePassword } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const visible = defineModel({ type: Boolean, default: false })
// forced: first login with a password set by the admin; the dialog cannot be closed until it is changed
const props = defineProps({ forced: { type: Boolean, default: false } })
const userStore = useUserStore()
const formRef = ref()
const saving = ref(false)
const form = reactive({ oldPassword: '', newPassword: '', confirm: '' })
const rules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { pattern: /^(?=.*[A-Za-z])(?=.*[0-9]).{8,64}$/, message: '密码为 8～64 位，至少包含一个字母和一个数字', trigger: 'blur' },
  ],
  confirm: [
    {
      validator: (_r, v, cb) => (v === form.newPassword ? cb() : cb(new Error('两次输入的密码不一样'))),
      trigger: 'blur',
    },
  ],
}

function onOpen() {
  Object.assign(form, { oldPassword: '', newPassword: '', confirm: '' })
  formRef.value?.clearValidate()
}

async function submit() {
  if (!(await formRef.value.validate().catch(() => false))) return
  saving.value = true
  try {
    const data = await changePassword({ oldPassword: form.oldPassword, newPassword: form.newPassword })
    // other devices are signed out; this page keeps working with the new token
    if (data?.token) {
      userStore.setToken(data.token)
      userStore.setUser(data.user)
    }
    ElMessage.success('密码已修改，下次登录用新密码')
    visible.value = false
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="props.forced ? '第一次登录，请先修改密码' : '修改密码'"
    width="420px"
    append-to-body
    :show-close="!props.forced"
    :close-on-click-modal="!props.forced"
    :close-on-press-escape="!props.forced"
    @open="onOpen"
  >
    <el-alert v-if="props.forced" type="warning" show-icon :closable="false" class="forced-tip"
              title="这个账号的密码是管理员设的，改成只有你知道的密码后才能继续使用。" />
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item label="原密码" prop="oldPassword">
        <el-input v-model="form.oldPassword" type="password" show-password autocomplete="current-password" />
      </el-form-item>
      <el-form-item label="新密码" prop="newPassword">
        <el-input v-model="form.newPassword" type="password" show-password placeholder="8～64 位，含字母和数字"
                  autocomplete="new-password" />
      </el-form-item>
      <el-form-item label="确认新密码" prop="confirm">
        <el-input v-model="form.confirm" type="password" show-password autocomplete="new-password" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button v-if="!props.forced" @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.forced-tip {
  margin-bottom: 12px;
}
</style>
