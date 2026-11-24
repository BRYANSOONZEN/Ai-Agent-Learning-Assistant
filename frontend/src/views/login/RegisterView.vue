<script setup>
/*
 * Student self sign-up. There is no role field: the backend always creates a student.
 * The same rules are checked here (for instant feedback) and again by the backend.
 */
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { register } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { homeFor } from '@/router'

const router = useRouter()
const userStore = useUserStore()

const formRef = ref()
const loading = ref(false)
const error = ref('')
const form = reactive({ username: '', name: '', password: '', confirm: '' })

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_]{4,32}$/, message: '用户名为 4～32 位字母、数字或下划线', trigger: 'blur' },
  ],
  name: [
    { required: true, message: '请输入姓名', trigger: 'blur' },
    { max: 32, message: '姓名不能超过 32 个字', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { pattern: /^(?=.*[A-Za-z])(?=.*[0-9]).{8,64}$/, message: '密码为 8～64 位，至少包含一个字母和一个数字', trigger: 'blur' },
  ],
  confirm: [
    { required: true, message: '请再输入一次密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) =>
        value === form.password ? callback() : callback(new Error('两次输入的密码不一样')),
      trigger: 'blur',
    },
  ],
}

async function submit() {
  error.value = ''
  if (!(await formRef.value.validate().catch(() => false))) return
  loading.value = true
  try {
    const data = await register(form)
    userStore.setLogin(data)
    router.push(homeFor(data.user.role))
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card app-card">
      <div class="brand">
        <el-icon :size="32"><School /></el-icon>
        <h1>学生注册</h1>
        <p>注册后直接登录，开始学习</p>
      </div>
      <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="error" />
      <el-form ref="formRef" :model="form" :rules="rules" size="large" label-position="top" @keyup.enter="submit">
        <el-form-item label="用户名（登录用，建议用学号）" prop="username">
          <el-input v-model="form.username" placeholder="4～32 位字母、数字或下划线" autocomplete="username" />
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" placeholder="老师看到的名字" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="8～64 位，含字母和数字"
                    autocomplete="new-password" show-password />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirm">
          <el-input v-model="form.confirm" type="password" placeholder="再输入一次" autocomplete="new-password" show-password />
        </el-form-item>
        <el-button type="primary" :loading="loading" class="submit" @click="submit">注册并登录</el-button>
      </el-form>
      <p class="foot">已有账号？<router-link :to="{ name: 'login' }">去登录</router-link></p>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--app-space-md);
  background: linear-gradient(135deg, var(--app-sidebar-bg), var(--app-primary));
}

.login-card {
  width: 100%;
  max-width: 420px;
  padding: var(--app-space-xl);
}

.brand {
  text-align: center;
  color: var(--app-primary);
  margin-bottom: var(--app-space-lg);
}

.brand h1 {
  margin: var(--app-space-sm) 0 var(--app-space-xs);
  font-size: var(--app-font-size-title);
  color: var(--app-text);
}

.brand p {
  margin: 0;
  color: var(--app-text-muted);
}

.error {
  margin-bottom: var(--app-space-md);
}

.submit {
  width: 100%;
  margin-top: var(--app-space-sm);
}

.foot {
  text-align: center;
  font-size: var(--app-font-size-sm);
  color: var(--app-text-muted);
  margin: var(--app-space-md) 0 0;
}

.foot a {
  color: var(--app-primary);
  font-weight: 600;
}
</style>
