import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'
import AppLayout from '@/layout/AppLayout.vue'

/*
 * Every page is registered here. The sidebar menu is generated from these routes:
 *   meta.title  — menu text and breadcrumb
 *   meta.icon   — Element Plus icon name
 *   meta.stage  — which part of the 学→记→诊→画→荐→干 chain the page belongs to
 *   meta.roles  — who may see it (omit = everyone). Students and teachers get separate menus:
 *                 student pages list 'student', teacher pages 'teacher'; admin sees both
 *   meta.eyebrow / meta.subtitle — small English label above the page heading / line under the top-bar title
 *   meta.hidden — true = routable but not shown in the menu (e.g. detail pages)
 * Menu order = order in this array (each role only sees its own items).
 * To add a page: create the view, add a route here. Nothing else to change.
 */
export const layoutChildren = [
  {
    path: 'dashboard',
    name: 'dashboard',
    component: () => import('@/views/dashboard/DashboardView.vue'),
    meta: { eyebrow: 'OVERVIEW', subtitle: '把今天的学习安排得刚刚好', title: '首页', icon: 'HomeFilled' },
  },
  {
    path: 'learn/courses',
    name: 'courses',
    component: () => import('@/views/learn/LearnCourses.vue'),
    meta: { eyebrow: 'COURSES', subtitle: '选一门课，按章节一步步学', title: '课程学习', icon: 'Reading', stage: '学', roles: ['student', 'admin'] },
  },
  {
    path: 'learn/courses/:id',
    name: 'course-detail',
    component: () => import('@/views/learn/LearnCourse.vue'),
    meta: { eyebrow: 'LEARNING', title: '学习', stage: '学', hidden: true, parent: 'courses', roles: ['student', 'admin'] },
  },
  {
    // not in the menu: students reach AI 对话 inside a course (课程学习 → a chapter → its AI 对话 item).
    // Kept as a standalone page for the AI module report / demos (chapter 1, all topics).
    path: 'learn/dialogue',
    name: 'dialogue',
    component: () => import('@/views/learn/DialogueView.vue'),
    meta: { eyebrow: 'AI DIALOGUE', subtitle: '先自己想，再看提示', title: 'AI 巩固对话', stage: '学', hidden: true, roles: ['student', 'admin'] },
  },
  {
    path: 'teacher/courses',
    name: 'teacher-courses',
    component: () => import('@/views/teacher/CourseManage.vue'),
    meta: { eyebrow: 'MY COURSES', subtitle: '按章节放好内容，发布后学生就能学', title: '我的课程', icon: 'Management', stage: '学', roles: ['teacher', 'admin'] },
  },
  {
    path: 'teacher/courses/:id',
    name: 'teacher-course-builder',
    component: () => import('@/views/teacher/CourseBuilder.vue'),
    meta: {
      eyebrow: 'COURSE BUILDER', title: '建课',
      stage: '学',
      hidden: true,
      parent: 'teacher-courses',
      roles: ['teacher', 'admin'],
    },
  },
  {
    // not in the menu: teachers edit topics inside 建课 → a chapter → 对话话题
    path: 'teacher/topics',
    name: 'teacher-topics',
    component: () => import('@/views/teacher/TopicManage.vue'),
    meta: { eyebrow: 'DIALOGUE TOPICS', title: '对话话题', stage: '学', hidden: true, roles: ['teacher', 'admin'] },
  },
  {
    path: 'records',
    name: 'records',
    component: () => import('@/views/records/RecordsView.vue'),
    meta: {
      eyebrow: 'LEARNING RECORDS', subtitle: '每一次学习都留下记录', title: '学习记录',
      icon: 'Document',
      stage: '记',
      roles: ['student', 'admin'],
      description: '答题记录、学习时长与行为日志。',
    },
  },
  {
    path: 'diagnosis',
    name: 'diagnosis',
    component: () => import('@/views/diagnosis/DiagnosisView.vue'),
    meta: { eyebrow: 'DIAGNOSIS', subtitle: '每一次作答，都成为下一步的依据', title: '学情诊断', icon: 'DataAnalysis', stage: '诊', description: '各知识点掌握度诊断结果。' },
  },
  {
    path: 'profile',
    name: 'profile',
    component: () => import('@/views/profile/ProfileView.vue'),
    meta: { eyebrow: 'LEARNER PROFILE', subtitle: '你是怎样的学习者，每一条都有依据', title: '学生画像', icon: 'User', stage: '画', roles: ['student', 'admin'] },
  },
  {
    path: 'recommend',
    name: 'recommend',
    component: () => import('@/views/recommend/RecommendView.vue'),
    meta: {
      eyebrow: 'RECOMMENDED FOR YOU', subtitle: '针对薄弱的地方，补得刚刚好', title: '学习推荐',
      icon: 'Star',
      stage: '荐',
      roles: ['student', 'admin'],
      description: '根据薄弱知识点推荐习题与资源。',
    },
  },
  {
    path: 'intervention',
    name: 'intervention',
    component: () => import('@/views/intervention/InterventionView.vue'),
    meta: {
      eyebrow: 'INTERVENTION', title: '干预管理',
      icon: 'Bell',
      stage: '干',
      roles: ['teacher', 'admin'],
      description: '学业预警、干预建议与教师审核。',
    },
  },
  {
    // last on purpose: a technical / admin page, not part of daily teaching
    path: 'teacher/ai-logs',
    name: 'ai-logs',
    component: () => import('@/views/teacher/AiCallLog.vue'),
    meta: { eyebrow: 'AI CALL LOG', subtitle: '每一次模型调用的耗时和费用', title: 'AI 调用记录', icon: 'Histogram', stage: '学', roles: ['teacher', 'admin'] },
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
