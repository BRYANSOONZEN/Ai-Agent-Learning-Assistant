<script setup>
import { computed, ref } from 'vue'
import Sidebar from './components/Sidebar.vue'
import TopBar from './components/TopBar.vue'
import DashboardView from './features/dashboard/DashboardView.vue'
import LearningView from './features/learning/LearningView.vue'
import ProgressView from './features/progress/ProgressView.vue'
import MaterialsView from './features/materials/MaterialsView.vue'
import TeacherView from './features/teacher/TeacherView.vue'

const activeView = ref('dashboard')
const pageMeta = {
  dashboard: ['总览', '把今天的学习安排得刚刚好'],
  learn: ['学习与提问', '在自己的思考之后，获得恰到好处的提示'],
  progress: ['掌握度诊断', '每一次作答，都会成为下一步的证据'],
  materials: ['课程资料', '和可核对的原理一起学习'],
  teacher: ['教师审核', '让每个判断都保留依据并可被复核'],
}
const currentMeta = computed(() => pageMeta[activeView.value])
</script>

<template>
  <div class="app-shell">
    <Sidebar :active-view="activeView" @navigate="activeView = $event" />
    <main class="app-main">
      <TopBar :title="currentMeta[0]" :subtitle="currentMeta[1]" />
      <div class="page-content">
        <DashboardView v-if="activeView === 'dashboard'" @navigate="activeView = $event" />
        <LearningView v-else-if="activeView === 'learn'" />
        <ProgressView v-else-if="activeView === 'progress'" />
        <MaterialsView v-else-if="activeView === 'materials'" />
        <TeacherView v-else-if="activeView === 'teacher'" />
      </div>
    </main>
  </div>
</template>
