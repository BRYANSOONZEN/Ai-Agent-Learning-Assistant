import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'
import AppLayout from '@/layout/AppLayout.vue'

/*
 * Every page is registered here. The sidebar menu is generated from these routes:
 *   meta.title  — menu text and breadcrumb
 *   meta.icon   — Element Plus icon name
 *   meta.stage  — which part of the 学→记→诊→画→荐→干 chain the page belongs to
 *   meta.roles  — who may see it (omit = everyone)
 *   meta.hidden — true = routable but not shown in the menu (e.g. detail pages)
 * To add a page: create the view, add a route here. Nothing else to change.
 */
export const layoutChildren = [
  {
    path: 'dashboard',
    name: 'dashboard',
    component: () => import('@/views/dashboard/DashboardView.vue'),
    meta: { title: '首页', icon: 'HomeFilled' },
  },
  {
    path: 'learn/courses',
    name: 'courses',
    component: () => import('@/views/learn/CourseList.vue'),
    meta: { title: '课程学习', icon: 'Reading', stage: '学' },
  },
  {
    path: 'learn/courses/:id',
    name: 'course-detail',
    component: () => import('@/views/learn/CourseDetail.vue'),
    meta: { title: '课程详情', stage: '学', hidden: true, parent: 'courses' },
  },
  {
    path: 'records',
    name: 'records',
    component: () => import('@/components/PlaceholderPage.vue'),
    meta: { title: '学习记录', icon: 'Document', stage: '记', description: '答题记录、学习时长与行为日志。' },
  },
  {
    path: 'diagnosis',
    name: 'diagnosis',
    component: () => import('@/components/PlaceholderPage.vue'),
    meta: { title: '学情诊断', icon: 'DataAnalysis', stage: '诊', description: '各知识点掌握度诊断结果。' },
  },
  {
    path: 'profile',
    name: 'profile',
    component: () => import('@/views/profile/ProfileView.vue'),
    meta: { title: '学生画像', icon: 'User', stage: '画' },
  },
  {
    path: 'recommend',
    name: 'recommend',
    component: () => import('@/components/PlaceholderPage.vue'),
    meta: { title: '学习推荐', icon: 'Star', stage: '荐', description: '根据薄弱知识点推荐习题与资源。' },
  },
  {
    path: 'intervention',
    name: 'intervention',
    component: () => import('@/components/PlaceholderPage.vue'),
    meta: {
      title: '干预管理',
      icon: 'Bell',
      stage: '干',
      roles: ['teacher', 'admin'],
      description: '学业预警、干预建议与教师审核。',
    },
  },
]

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { title: '登录', public: true },
  },
  {
    path: '/',
    component: AppLayout,
    redirect: '/dashboard',
    children: layoutChildren,
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  const userStore = useUserStore()
  if (!to.meta.public && !userStore.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.roles && !to.meta.roles.includes(userStore.role)) {
    return { name: 'dashboard' }
  }
})

router.afterEach((to) => {
  document.title = `${to.meta.title || ''} · 软件工程智能学习平台`
})

export default router
