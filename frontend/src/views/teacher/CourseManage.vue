<script setup>
/*
 * Teacher page: my courses. Create / edit / delete a course, then "管理内容" to build its chapters.
 * Always uses the real backend (api/teacherCourse.js).
 */
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import CourseFormDialog from './CourseFormDialog.vue'
import { moduleLabel } from './courseModules'
import { getCourses, deleteCourse } from '@/api/teacherCourse'

const router = useRouter()

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
const editing = ref(null) // null = create

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

function onSaved(course, isNew) {
  if (isNew) manage(course)
  else load()
}

async function remove(row) {
  await ElMessageBox.confirm(`确定删除课程「${row.name}」吗？`, '提示', { type: 'warning' })
  await deleteCourse(row.id)
  ElMessage.success('已删除')
  load()
}

function manage(row) {
  router.push({ name: 'teacher-course-builder', params: { id: row.id } })
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader title="我的课程" description="这里只显示你创建的课程。新建时选好这门课用哪些模块（视频、文档、AI 对话……），进入课程后按章节填内容。">
      <el-button type="primary" icon="Plus" @click="openCreate">新建课程</el-button>
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
        <el-button @click="search">搜索</el-button>
      </div>

      <el-table v-loading="loading" :data="list" style="width: 100%">
        <el-table-column prop="name" label="课程名" min-width="160">
          <template #default="{ row }">
            <el-link type="primary" @click="manage(row)">{{ row.name }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="简介" min-width="220" show-overflow-tooltip />
        <el-table-column label="模块" min-width="200">
          <template #default="{ row }">
            <el-tag v-for="m in row.modules" :key="m" size="small" effect="plain" class="module-tag">
              {{ moduleLabel(m) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="chapterCount" label="章节数" width="80" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusMap[row.status]?.type" size="small">{{ statusMap[row.status]?.label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="manage(row)">管理内容</el-button>
            <el-button link type="primary" @click="openEdit(row)">设置</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        class="pager"
        layout="total, prev, pager, next"
        :total="total"
        @current-change="load"
      />
    </div>

    <CourseFormDialog v-model="dialogVisible" :course="editing" @saved="onSaved" />
  </div>
</template>

<style scoped>
.module-tag {
  margin: 2px 4px 2px 0;
}
.pager {
  margin-top: var(--app-space-md);
  justify-content: flex-end;
}
</style>
