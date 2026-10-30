<script setup>
/*
 * Teacher 首页 (Studywise look: a dark summary strip, then white rows).
 * The courses this teacher is in charge of, one row each,
 * showing what the course is made of (its modules) and how far it is.
 * Clicking a course opens its builder; 新建课程 asks which modules it uses, then opens the builder.
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import CourseFormDialog from '@/views/teacher/CourseFormDialog.vue'
import { MODULES } from '@/views/teacher/courseModules'
import { getCourses } from '@/api/teacherCourse'

defineProps({ greeting: { type: String, default: '' } })

const router = useRouter()

const statusMap = {
  published: { label: '已发布', cls: 'published' },
  draft: { label: '草稿', cls: 'draft' },
  archived: { label: '已归档', cls: 'archived' },
}

const courses = ref([])
const loading = ref(false)
const dialogVisible = ref(false)

const counts = computed(() => ({
  published: courses.value.filter((c) => c.status === 'published').length,
  draft: courses.value.filter((c) => c.status === 'draft').length,
  chapters: courses.value.reduce((n, c) => n + (c.chapterCount || 0), 0),
}))

async function load() {
  loading.value = true
  try {
    courses.value = (await getCourses({ page: 1, size: 50 })).records
  } finally {
    loading.value = false
  }
}

function open(course) {
  router.push({ name: 'teacher-course-builder', params: { id: course.id } })
}

onMounted(load)
</script>

<template>
  <div class="teacher-home">
    <header class="sw-summary">
      <div class="sw-summary-main">
        <span class="sw-eyebrow on-dark">MY COURSES</span>
        <h2>{{ greeting }}</h2>
        <p>按章节排好视频、文档、练习和 AI 对话，发布后学生就能按顺序学习。</p>
      </div>
      <div class="sw-summary-stat">
        <strong>{{ counts.published }}</strong>
        <span>门已发布</span>
      </div>
      <div class="sw-summary-stat">
        <strong>{{ counts.draft }}</strong>
        <span>门草稿</span>
      </div>
      <div class="sw-summary-stat">
        <strong>{{ counts.chapters }}</strong>
        <span>个章节</span>
      </div>
      <button type="button" class="sw-button-light" @click="dialogVisible = true">
        <el-icon><Plus /></el-icon>新建课程
      </button>
    </header>

    <div class="sw-heading">
      <div>
        <span class="sw-eyebrow">COURSES</span>
        <h3>我的课程</h3>
      </div>
      <router-link :to="{ name: 'teacher-courses' }" class="sw-link">
        在表格里管理全部课程<el-icon><Right /></el-icon>
      </router-link>
    </div>

    <div v-loading="loading" class="rows">
      <button v-for="c in courses" :key="c.id" type="button" class="row" @click="open(c)">
        <span class="status" :class="statusMap[c.status]?.cls">{{ statusMap[c.status]?.label }}</span>
        <span class="main">
          <strong>{{ c.name }}</strong>
          <small>{{ c.description || '还没有简介' }}</small>
        </span>
        <!-- all five modules in a fixed order; the ones this course uses are lit -->
        <span class="modules" aria-label="课程模块">
          <span
            v-for="m in MODULES"
            :key="m.key"
            class="module"
            :class="{ on: !c.modules?.length || c.modules.includes(m.key) }"
            :title="m.label"
          >
            <el-icon><component :is="m.icon" /></el-icon>
            <span>{{ m.label }}</span>
          </span>
        </span>
        <span class="chapters">
          <b>{{ c.chapterCount }}</b>
          <small>章节</small>
        </span>
        <span class="open">{{ c.chapterCount ? '继续建课' : '开始建课' }}</span>
      </button>

      <button v-if="!loading && !courses.length" type="button" class="empty" @click="dialogVisible = true">
        <strong>还没有课程</strong>
        <span>新建一门课，选好要用的模块，就可以开始按章节放内容了</span>
      </button>
    </div>

    <CourseFormDialog v-model="dialogVisible" @saved="(c) => open(c)" />
  </div>
</template>

<style scoped>
.teacher-home {
  display: grid;
  gap: 22px;
}
.teacher-home > .sw-heading {
  margin-bottom: -10px;
}

.rows {
  display: grid;
  gap: 10px;
  min-height: 80px;
}
.row {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr) auto 64px 80px;
  align-items: center;
  gap: 22px;
  padding: 18px 22px;
  border: 1px solid var(--app-border);
  border-radius: 13px;
  background: var(--app-surface);
  box-shadow: var(--app-shadow);
  font: inherit;
  color: var(--app-text);
  text-align: left;
  cursor: pointer;
  transition: border-color 0.18s, box-shadow 0.18s;
}
.row:hover {
  border-color: var(--el-color-primary-light-5);
  box-shadow: 0 6px 22px rgba(24, 55, 88, 0.06);
}
.status {
  justify-self: start;
  padding: 4px 8px;
  border-radius: 5px;
  font-size: 11px;
  font-weight: 600;
  background: #f0f3f6;
  color: #66788c;
}
.status.published {
  background: #e8f7f4;
  color: #2e8b7f;
}
.status.archived {
  background: #fff0df;
  color: #b8712c;
}
.main {
  display: grid;
  gap: 4px;
  min-width: 0;
}
.main strong {
  font-size: 16px;
}
.main small {
  color: var(--app-text-secondary);
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.modules {
  display: flex;
  gap: 4px;
}
.module {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 7px;
  border-radius: 6px;
  font-size: 12px;
  color: #c3cdd8;
}
.module.on {
  background: #f0edff;
  color: #7c6dd7;
}
.chapters {
  display: grid;
  justify-items: end;
}
.chapters b {
  font-size: 20px;
  font-variant-numeric: tabular-nums;
}
.chapters small {
  color: var(--app-text-muted);
  font-size: 12px;
}
.open {
  justify-self: end;
  color: var(--app-text-muted);
  font-size: 13px;
  font-weight: 600;
  transition: color 0.18s;
}
.row:hover .open {
  color: var(--app-primary);
}
.empty {
  display: grid;
  gap: 6px;
  padding: 36px;
  border: 1px dashed var(--el-border-color);
  border-radius: 14px;
  background: none;
  font: inherit;
  color: var(--app-text-secondary);
  cursor: pointer;
}
.empty strong {
  color: var(--app-text);
  font-size: 16px;
}

@media (max-width: 1180px) {
  .module span {
    display: none;
  }
}
@media (max-width: 860px) {
  .row {
    grid-template-columns: 1fr auto;
    gap: 10px 16px;
  }
  .main {
    grid-column: 1 / -1;
    grid-row: 2;
  }
  .modules {
    grid-column: 1 / -1;
  }
  .chapters,
  .open {
    display: none;
  }
}
</style>
