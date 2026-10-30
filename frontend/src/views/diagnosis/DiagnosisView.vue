<script setup>
/*
 * 学情诊断 (the 诊 stage). Students see their own knowledge points (StudentDiagnosis);
 * teachers see one of their courses across all students (CourseDiagnosis).
 */
import { computed } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import StudentDiagnosis from './StudentDiagnosis.vue'
import CourseDiagnosis from './CourseDiagnosis.vue'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const isTeacher = computed(() => ['teacher', 'admin'].includes(userStore.role))
</script>

<template>
  <div>
    <PageHeader
      title="学情诊断"
      :description="
        isTeacher
          ? '全班在每个知识点上掌握得怎么样，哪些知识点值得再讲一次，学生错在哪里。'
          : '根据答题记录，看每个知识点掌握得怎么样，薄弱的知识点错在哪里、该回去做什么。'
      "
    />
    <CourseDiagnosis v-if="isTeacher" />
    <StudentDiagnosis v-else />
  </div>
</template>
