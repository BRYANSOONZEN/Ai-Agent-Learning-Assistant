<script setup>
/*
 * TEMPLATE: Detail page (header with back button + info block + tabs).
 * Copy for any "one item" page: knowledge point, exercise, student, alert...
 */
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import { getCourse } from '@/api/course'

const route = useRoute()
const course = ref(null)
const loading = ref(true)
const activeTab = ref('chapters')

onMounted(async () => {
  try {
    course.value = await getCourse(route.params.id)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div v-loading="loading">
    <template v-if="course">
      <PageHeader :title="course.name" :description="course.description" back>
        <el-button type="primary" icon="VideoPlay">开始学习</el-button>
      </PageHeader>

      <div class="app-card">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="授课教师">{{ course.teacher }}</el-descriptions-item>
          <el-descriptions-item label="章节数">{{ course.chapterCount }}</el-descriptions-item>
          <el-descriptions-item label="学生数">{{ course.studentCount }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ course.createdAt }}</el-descriptions-item>
        </el-descriptions>
      </div>

      <div class="app-card">
        <el-tabs v-model="activeTab">
          <el-tab-pane label="章节目录" name="chapters">
            <el-empty description="章节与知识点列表（待开发）" />
          </el-tab-pane>
          <el-tab-pane label="习题" name="exercises">
            <el-empty description="课程习题（待开发）" />
          </el-tab-pane>
          <el-tab-pane label="资源" name="resources">
            <el-empty description="学习资源（待开发）" />
          </el-tab-pane>
        </el-tabs>
      </div>
    </template>
    <el-empty v-else-if="!loading" description="课程不存在" />
  </div>
</template>
