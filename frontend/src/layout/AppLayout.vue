<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { layoutChildren } from '@/router'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const collapsed = ref(false)

const roleLabels = { student: '学生', teacher: '教师', admin: '管理员' }

// Menu comes straight from the route table, filtered by the current role
const menuItems = computed(() =>
  layoutChildren.filter(
    (r) => !r.meta.hidden && (!r.meta.roles || r.meta.roles.includes(userStore.role)),
  ),
)

// Detail pages point back to the menu item (and breadcrumb) of their parent list page
const parentRoute = computed(
  () => route.meta.parent && layoutChildren.find((r) => r.name === route.meta.parent),
)

const activeMenu = computed(() =>
  parentRoute.value ? '/' + parentRoute.value.path : route.path,
)

const breadcrumbs = computed(() => {
  const items = []
  if (parentRoute.value) {
    items.push({ title: parentRoute.value.meta.title, to: '/' + parentRoute.value.path })
  }
  items.push({ title: route.meta.title })
  return items
})

function logout() {
  userStore.logout()
  router.push('/login')
}
</script>

<template>
  <el-container class="layout">
    <el-aside :width="collapsed ? '64px' : 'var(--app-sidebar-width)'" class="sidebar">
      <div class="brand">
        <el-icon :size="22"><School /></el-icon>
        <span v-show="!collapsed">智能学习平台</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="collapsed"
        router
        background-color="var(--app-sidebar-bg)"
        text-color="var(--app-sidebar-text)"
        active-text-color="#ffffff"
        class="menu"
      >
        <el-menu-item v-for="item in menuItems" :key="item.name" :index="'/' + item.path">
          <el-icon><component :is="item.meta.icon" /></el-icon>
          <template #title>
            <span>{{ item.meta.title }}</span>
            <span v-if="item.meta.stage" class="stage-tag">{{ item.meta.stage }}</span>
          </template>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-icon class="collapse-btn" :size="18" @click="collapsed = !collapsed">
            <component :is="collapsed ? 'Expand' : 'Fold'" />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item v-for="b in breadcrumbs" :key="b.title" :to="b.to">
              {{ b.title }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <el-dropdown trigger="click">
          <span class="user">
            <el-avatar :size="30">{{ userStore.user?.name?.[0] }}</el-avatar>
            <span>{{ userStore.user?.name }}</span>
            <el-tag size="small" effect="plain">{{ roleLabels[userStore.role] }}</el-tag>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item icon="SwitchButton" @click="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>

      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100vh;
}

.sidebar {
  background: var(--app-sidebar-bg);
  transition: width 0.2s;
  overflow-x: hidden;
}

.brand {
  height: var(--app-header-height);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--app-space-sm);
  color: #fff;
  font-weight: 600;
  font-size: var(--app-font-size-lg);
  white-space: nowrap;
}

.menu {
  border-right: none;
}

.menu :deep(.el-menu-item.is-active) {
  background: var(--app-primary);
}

.stage-tag {
  margin-left: auto;
  font-size: 12px;
  padding: 0 6px;
  line-height: 18px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.1);
}

.header {
  height: var(--app-header-height);
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--app-surface);
  border-bottom: 1px solid var(--app-border);
}

.header-left {
  display: flex;
  align-items: center;
  gap: var(--app-space-md);
}

.collapse-btn {
  cursor: pointer;
  color: var(--app-text-secondary);
}

.user {
  display: flex;
  align-items: center;
  gap: var(--app-space-sm);
  cursor: pointer;
  color: var(--app-text);
}

.main {
  padding: var(--app-space-lg);
  overflow-y: auto;
}
</style>
