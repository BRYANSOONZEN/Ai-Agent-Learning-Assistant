<script setup>
/*
 * App shell in the Studywise style (group prototype Ai-agent-learning-assistant):
 * navy sidebar (brand, "我的工作区" menu, a small study prompt, user card at the bottom)
 * + light top bar (page title and subtitle, today, user).
 * The menu comes straight from the route table, filtered by the current role.
 * The student prompt is real: weak knowledge points from 学情诊断, linking to 学习推荐.
 * Teachers see the number of open alerts next to 干预管理.
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { layoutChildren } from '@/router'
import { getMyDiagnosis } from '@/api/diagnosis'
import { getOpenAlertCount } from '@/api/intervention'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const collapsed = ref(false)

const roleLabels = { student: '学生账户', teacher: '教师账户', admin: '管理员' }

const menuItems = computed(() =>
  layoutChildren.filter(
    (r) => !r.meta.hidden && (!r.meta.roles || r.meta.roles.includes(userStore.role)),
  ),
)

// Detail pages point back to the menu item (and breadcrumb) of their parent list page
const parentRoute = computed(
  () => route.meta.parent && layoutChildren.find((r) => r.name === route.meta.parent),
)

const activePath = computed(() => (parentRoute.value ? '/' + parentRoute.value.path : route.path))

// top bar: page title + one line under it (the page's description, or the way back for detail pages)
const subtitle = computed(() => route.meta.subtitle || route.meta.description || '')

// student prompt card: how many knowledge points are weak / mastered (refreshed on every page change)
const study = ref(null)
watch(
  () => [route.name, userStore.role],
  async () => {
    if (userStore.role !== 'student') return (study.value = null)
    try {
      study.value = (await getMyDiagnosis()).summary
    } catch {
      study.value = null
    }
  },
  { immediate: true },
)
// teacher: open alerts, shown as a number on 干预管理 (refreshed on every page change)
const openAlerts = ref(0)
async function refreshAlerts() {
  if (userStore.role !== 'teacher') return (openAlerts.value = 0)
  try {
    openAlerts.value = await getOpenAlertCount()
  } catch {
    openAlerts.value = 0
  }
}
watch(() => [route.name, userStore.role], refreshAlerts, { immediate: true })
// 干预管理 fires this after the teacher approves or ignores an alert
onMounted(() => window.addEventListener('alerts-changed', refreshAlerts))
onBeforeUnmount(() => window.removeEventListener('alerts-changed', refreshAlerts))

const studyPrompt = computed(() => {
  const s = study.value
  if (!s?.total) return null
  const practised = s.total - s.none
  if (s.weak) return { title: `${s.weak} 个知识点待补`, text: '去学习推荐做几道针对的题。', to: { name: 'recommend' }, ratio: s.good / s.total }
  if (!practised) return { title: '先做一次练习', text: '做完练习就能看到自己的掌握度。', to: { name: 'courses' }, ratio: 0 }
  return { title: '学习节奏不错', text: `练过的 ${practised} 个知识点都不薄弱，继续学新内容。`, to: { name: 'courses' }, ratio: s.good / s.total }
})

const today = new Date().toLocaleDateString('zh-CN', { month: 'long', day: 'numeric', weekday: 'long' })

function logout() {
  userStore.logout()
  router.push('/login')
}
</script>

<template>
  <div class="shell" :class="{ collapsed }">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark"><span /><span /><span /></div>
        <div v-show="!collapsed" class="brand-text">
          <strong>智能学习平台</strong>
          <small>软件工程 · AI 学习助手</small>
        </div>
      </div>

      <div v-show="!collapsed" class="workspace-label">我的工作区</div>
      <nav class="main-nav" aria-label="主导航">
        <router-link
          v-for="item in menuItems"
          :key="item.name"
          :to="'/' + item.path"
          class="nav-item"
          :class="{ active: activePath === '/' + item.path }"
          :title="collapsed ? item.meta.title : undefined"
        >
          <el-icon :size="18"><component :is="item.meta.icon" /></el-icon>
          <span v-show="!collapsed">{{ item.meta.title }}</span>
          <span v-if="item.name === 'intervention' && openAlerts && !collapsed" class="nav-badge">{{ openAlerts }}</span>
        </router-link>
      </nav>

      <div class="sidebar-bottom">
        <router-link v-if="studyPrompt && !collapsed" :to="studyPrompt.to" class="sidebar-prompt">
          <span class="prompt-orb"><el-icon :size="16"><MagicStick /></el-icon></span>
          <strong>{{ studyPrompt.title }}</strong>
          <p>{{ studyPrompt.text }}</p>
          <span class="prompt-progress" :title="`已掌握 ${Math.round(studyPrompt.ratio * 100)}%`">
            <span :style="{ width: Math.max(studyPrompt.ratio * 100, 3) + '%' }" />
          </span>
        </router-link>
        <el-dropdown trigger="click" placement="top-start">
          <button type="button" class="profile-row">
            <span class="avatar">{{ userStore.user?.name?.[0] }}</span>
            <span v-show="!collapsed" class="profile-copy">
              <strong>{{ userStore.user?.name }}</strong>
              <small>{{ roleLabels[userStore.role] }}</small>
            </span>
            <el-icon v-show="!collapsed" class="profile-more"><MoreFilled /></el-icon>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item icon="SwitchButton" @click="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </aside>

    <div class="app-main">
      <header class="topbar">
        <div class="topbar-left">
          <button type="button" class="icon-button" :title="collapsed ? '展开菜单' : '收起菜单'" @click="collapsed = !collapsed">
            <el-icon :size="18"><component :is="collapsed ? 'Expand' : 'Fold'" /></el-icon>
          </button>
          <div class="page-title">
            <h1>{{ route.meta.title }}</h1>
            <p v-if="parentRoute">
              <router-link :to="'/' + parentRoute.path" class="back">
                <el-icon><ArrowLeft /></el-icon>{{ parentRoute.meta.title }}
              </router-link>
            </p>
            <p v-else-if="subtitle">{{ subtitle }}</p>
          </div>
        </div>
        <div class="topbar-right">
          <span class="today"><i class="dot" />{{ today }}</span>
          <span class="avatar avatar-top">{{ userStore.user?.name?.[0] }}</span>
        </div>
      </header>

      <main class="page-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.shell {
  height: 100vh;
  display: flex;
}

/* ---------- sidebar ---------- */
.sidebar {
  width: var(--app-sidebar-width);
  flex: 0 0 var(--app-sidebar-width);
  background: var(--app-sidebar-bg);
  color: #d7e4f1;
  padding: 26px 16px 18px;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  transition: width 0.2s, flex-basis 0.2s;
}
.collapsed .sidebar {
  width: 72px;
  flex-basis: 72px;
  padding-left: 12px;
  padding-right: 12px;
}
.brand {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 0 10px 36px;
  color: #fff;
}
.brand-text strong {
  display: block;
  font-size: 16px;
  letter-spacing: -0.3px;
  white-space: nowrap;
}
.brand-text small {
  display: block;
  margin-top: 2px;
  color: #7890aa;
  font-size: 11px;
  white-space: nowrap;
}
/* three little bars, same mark as the prototype */
.brand-mark {
  flex: none;
  width: 30px;
  height: 30px;
  border-radius: 9px;
  background: #2c5f86;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  gap: 3px;
  padding-bottom: 7px;
}
.brand-mark span {
  width: 4px;
  border-radius: 3px;
}
.brand-mark span:nth-child(1) {
  height: 8px;
  background: #9bd7d0;
}
.brand-mark span:nth-child(2) {
  height: 13px;
  background: #d1ddff;
}
.brand-mark span:nth-child(3) {
  height: 18px;
  background: #f5b776;
}
.workspace-label {
  padding: 0 12px 12px;
  color: #607993;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 1.2px;
}
.main-nav {
  display: grid;
  gap: 5px;
}
.nav-item {
  min-height: 44px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 12px;
  border-radius: 9px;
  color: var(--app-sidebar-text);
  font-weight: 500;
  text-decoration: none;
  white-space: nowrap;
  transition: 0.18s;
}
.collapsed .nav-item {
  justify-content: center;
  padding: 0;
}
.nav-item:hover {
  background: rgba(255, 255, 255, 0.06);
  color: #fff;
}
.nav-item.active {
  background: var(--app-sidebar-active);
  color: #fff;
  box-shadow: inset 3px 0 #8ed7cf;
}
.nav-badge {
  margin-left: auto;
  min-width: 20px;
  padding: 1px 6px;
  border-radius: 10px;
  background: #e8775f;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  text-align: center;
}
.sidebar-bottom {
  margin-top: auto;
  padding-top: 16px;
}
.sidebar-prompt {
  display: block;
  margin: 0 6px 18px;
  padding: 15px;
  border: 1px solid #2c4966;
  border-radius: 12px;
  background: #193853;
  text-decoration: none;
  transition: border-color 0.18s;
}
.sidebar-prompt:hover {
  border-color: #3d6488;
}
.prompt-orb {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  margin-bottom: 11px;
  border-radius: 8px;
  background: #365c84;
  color: #b8e7dd;
}
.sidebar-prompt strong {
  display: block;
  color: #f0f6fb;
  font-size: 13px;
}
.sidebar-prompt p {
  margin: 6px 0 13px;
  color: #91aac0;
  font-size: 12px;
  line-height: 1.55;
}
.prompt-progress {
  display: block;
  height: 4px;
  border-radius: 4px;
  background: #294e6e;
}
.prompt-progress span {
  display: block;
  height: 100%;
  border-radius: 4px;
  background: #79cdbd;
}
.sidebar-bottom :deep(.el-dropdown) {
  width: 100%;
}
.profile-row {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px 8px 2px;
  border: 0;
  border-top: 1px solid #29445e;
  background: transparent;
  color: #fff;
  font: inherit;
  text-align: left;
  cursor: pointer;
}
.collapsed .profile-row {
  justify-content: center;
  padding-left: 0;
  padding-right: 0;
}
.profile-copy {
  display: grid;
  gap: 2px;
  min-width: 0;
}
.profile-copy strong {
  font-size: 13px;
}
.profile-copy small {
  font-size: 11px;
  color: #7691aa;
}
.profile-more {
  margin-left: auto;
  color: #6d88a1;
}
.avatar {
  flex: none;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  display: inline-grid;
  place-items: center;
  background: #cce2ff;
  color: #426895;
  font-weight: 700;
  font-size: 13px;
}

/* ---------- top bar + content ---------- */
.app-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}
.topbar {
  min-height: 92px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 24px 40px 14px;
  background: #f7f9fc;
}
.page-title {
  min-width: 0;
}
.page-title h1 {
  margin: 0;
  font-size: 23px;
  letter-spacing: -0.6px;
}
.page-title p {
  margin: 5px 0 0;
  color: var(--app-text-muted);
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #57769a;
  text-decoration: none;
}
.back:hover {
  color: var(--app-primary);
}
.topbar-left,
.topbar-right {
  display: flex;
  align-items: center;
  gap: 14px;
}
.icon-button {
  border: 0;
  background: transparent;
  color: var(--app-text-muted);
  display: inline-grid;
  place-items: center;
  padding: 5px;
  border-radius: 6px;
  cursor: pointer;
}
.icon-button:hover {
  background: var(--el-fill-color);
  color: var(--app-text);
}
.today {
  color: var(--app-text-secondary);
  font-size: 12px;
  white-space: nowrap;
}
.dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  margin-right: 7px;
  border-radius: 50%;
  background: var(--app-accent-orange);
  box-shadow: 0 0 0 3px #ffeedf;
}
.avatar-top {
  width: 34px;
  height: 34px;
}
.page-content {
  width: 100%;
  max-width: 1390px;
  margin: 0 auto;
  padding: 14px 40px 48px;
}

@media (max-width: 760px) {
  .sidebar {
    width: 72px;
    flex-basis: 72px;
    padding-left: 12px;
    padding-right: 12px;
  }
  .brand-text,
  .workspace-label,
  .nav-item span,
  .profile-copy,
  .profile-more,
  .sidebar-prompt,
  .page-title p,
  .today {
    display: none !important;
  }
  .nav-item {
    justify-content: center;
    padding: 0;
  }
  .topbar,
  .page-content {
    padding-left: 16px;
    padding-right: 16px;
  }
}
</style>
