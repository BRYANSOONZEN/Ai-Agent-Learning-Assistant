<script setup>
/*
 * Login: username + password only. The role is not chosen here — it comes from the account.
 * Errors from the backend (wrong password, locked, disabled) are shown inside the card, not as a toast.
 */
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { homeFor } from '@/router'
import { demoLogin, getDemoAccounts, login } from '@/api/auth'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref()
const loading = ref(false)
const error = ref('')
const form = reactive({ username: '', password: '', remember: false })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

// demo accounts (shown only when the backend's demo switch is on): one click, no password
const demos = ref([])
const ROLE = { teacher: '老师', student: '学生', admin: '管理员' }
getDemoAccounts().then((list) => (demos.value = list || [])).catch(() => {})
async function quick(u) {
  error.value = ''
  try {
    const data = await demoLogin(u.username)
    userStore.setLogin(data)
    router.push(homeFor(data.user.role))
  } catch (e) {
    error.value = e.message
  }
}

async function submit() {
  error.value = ''
  if (!(await formRef.value.validate().catch(() => false))) return
  loading.value = true
  try {
    const data = await login(form)
    userStore.setLogin(data)
    router.push(route.query.redirect || homeFor(data.user.role))
  } catch (e) {
    error.value = e.message
    form.password = ''
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
      <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="error" />
      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="submit">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" prefix-icon="User" autocomplete="username" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            prefix-icon="Lock"
            autocomplete="current-password"
            show-password
          />
        </el-form-item>
        <el-checkbox v-model="form.remember" class="remember">记住我（7 天内不用重新登录，公用电脑不要勾）</el-checkbox>
        <el-button type="primary" :loading="loading" class="submit" @click="submit">
          登录
        </el-button>
      </el-form>
      <div v-if="demos.length" class="demos">
        <span class="foot-muted">演示账号一键登录：</span>
        <el-button v-for="u in demos" :key="u.username" size="small" @click="quick(u)">
          {{ u.name }}<small class="demo-role">{{ ROLE[u.role] }}</small>
        </el-button>
      </div>
      <p class="foot">
        还没有账号？<router-link :to="{ name: 'register' }">学生注册</router-link>
        <span class="foot-muted">老师账号由管理员开通</span>
      </p>
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

.error {
  margin-bottom: var(--app-space-md);
}

.submit {
  width: 100%;
}
.demos {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 16px;
}
.demos .el-button {
  margin: 0;
}
.demo-role {
  margin-left: 4px;
  color: var(--el-text-color-secondary);
}
.remember {
  margin: -6px 0 12px;
}

.foot {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  font-size: var(--app-font-size-sm);
  color: var(--app-text-muted);
  margin: var(--app-space-md) 0 0;
}

.foot a {
  color: var(--app-primary);
  font-weight: 600;
}

.foot-muted {
  font-size: 12px;
}
</style>
