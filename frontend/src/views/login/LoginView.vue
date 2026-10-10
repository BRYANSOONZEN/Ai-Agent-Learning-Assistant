<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref()
const loading = ref(false)
const form = reactive({ username: '', password: '', role: 'student' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function submit() {
  await formRef.value.validate()
  loading.value = true
  try {
    userStore.setLogin(await login(form))
    router.push(route.query.redirect || '/dashboard')
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
        <h1>软件工程智能学习平台</h1>
        <p>学 · 记 · 诊 · 画 · 荐 · 干</p>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="submit">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            prefix-icon="Lock"
            show-password
          />
        </el-form-item>
        <el-form-item>
          <el-radio-group v-model="form.role" class="roles">
            <el-radio-button value="student">学生</el-radio-button>
            <el-radio-button value="teacher">教师</el-radio-button>
            <el-radio-button value="admin">管理员</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-button type="primary" :loading="loading" class="submit" @click="submit">
          登录
        </el-button>
      </el-form>
      <p class="mock-hint">演示模式：任意用户名和密码均可登录</p>
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
  max-width: 400px;
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
  letter-spacing: 2px;
}

.roles {
  width: 100%;
  display: flex;
}

.roles :deep(.el-radio-button) {
  flex: 1;
}

.roles :deep(.el-radio-button__inner) {
  width: 100%;
}

.submit {
  width: 100%;
}

.mock-hint {
  text-align: center;
  font-size: var(--app-font-size-sm);
  color: var(--app-text-muted);
  margin: var(--app-space-md) 0 0;
}
</style>
