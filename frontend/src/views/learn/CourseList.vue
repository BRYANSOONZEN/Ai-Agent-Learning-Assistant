<script setup>
/*
 * TEMPLATE: List page (search bar + table + pagination + create/edit dialog).
 * Copy this file for any "list of things" page: exercises, students, records, alerts...
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import CourseFormDialog from './CourseFormDialog.vue'
import { getCourses, deleteCourse } from '@/api/course'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const canEdit = userStore.role !== 'student'

const statusMap = {
  published: { label: '已发布', type: 'success' },
  draft: { label: '草稿', type: 'info' },
  archived: { label: '已归档', type: 'warning' },
}

const query = reactive({ keyword: '', page: 1, size: 10 })
const list = ref([])
const total = ref(0)
const loading = ref(false)

const dialogVisible = ref(false)
const editing = ref(null)

async function load() {
  loading.value = true
  try {
    const data = await getCourses(query)
    list.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  load()
}

function openCreate() {
  editing.value = null
  dialogVisible.value = true
}

function openEdit(row) {
  editing.value = row
  dialogVisible.value = true
}

async function remove(row) {
  await ElMessageBox.confirm(`确定删除课程「${row.name}」吗？`, '提示', { type: 'warning' })
  await deleteCourse(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader title="课程学习" description="浏览课程，进入章节与知识点学习。">
      <el-button v-if="canEdit" type="primary" icon="Plus" @click="openCreate">新建课程</el-button>
    </PageHeader>

    <div class="app-card">
      <div class="app-toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="搜索课程名称"
          prefix-icon="Search"
          clearable
          style="max-width: 280px"
          @keyup.enter="search"
          @clear="search"
        />
        <el-button icon="Refresh" @click="load">刷新</el-button>
      </div>

      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column prop="name" label="课程名称" min-width="180">
          <template #default="{ row }">
            <router-link :to="`/learn/courses/${row.id}`" class="link">{{ row.name }}</router-link>
          </template>
        </el-table-column>
        <el-table-column prop="teacher" label="授课教师" width="120" />
        <el-table-column prop="chapterCount" label="章节数" width="100" align="center" />
        <el-table-column prop="studentCount" label="学生数" width="100" align="center" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusMap[row.status].type" size="small">
              {{ statusMap[row.status].label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="120" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="$router.push(`/learn/courses/${row.id}`)">
              查看
            </el-button>
            <template v-if="canEdit">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" @click="remove(row)">删除</el-button>
            </template>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无课程" /></template>
      </el-table>

      <div class="app-pagination">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          @change="load"
        />
      </div>
    </div>

    <CourseFormDialog v-model="dialogVisible" :course="editing" @saved="load" />
  </div>
</template>

<style scoped>
.link {
  color: var(--app-primary);
  text-decoration: none;
  font-weight: 500;
}
</style>
