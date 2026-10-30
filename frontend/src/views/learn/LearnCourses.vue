<script setup>
/*
 * Student page: published courses as cards. Click one to start learning (LearnCourse.vue).
 */
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import { getLearnCourses } from '@/api/learn'
import { courseProgress } from './learnProgress'

const router = useRouter()

const query = reactive({ keyword: '', page: 1, size: 12 })
const list = ref([])
const total = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const data = await getLearnCourses(query)
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

function open(c) {
  router.push({ name: 'course-detail', params: { id: c.id } })
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader title="课程学习" description="选一门课开始学。每章按顺序学：看视频、读资料、做练习、和 AI 对话巩固。" />

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
    </div>

    <div v-loading="loading" class="grid">
      <button v-for="c in list" :key="c.id" type="button" class="card" @click="open(c)">
        <!-- cover: the course name set large, tinted per course so cards are easy to tell apart -->
        <div class="cover" :class="'tone-' + (c.id % 4)">
          <span class="cover-name">{{ c.name }}</span>
          <span class="cover-chapters">{{ c.chapterCount }} 章</span>
        </div>
        <div class="body">
          <p class="desc">{{ c.description || '还没有简介' }}</p>
          <div class="foot">
            <span class="teacher">{{ c.teacher }}</span>
            <span v-if="courseProgress(c.id)" class="progress">已学 {{ courseProgress(c.id) }} 项</span>
            <span v-else class="start">开始学习</span>
          </div>
        </div>
      </button>
    </div>
    <el-empty v-if="!loading && !list.length" description="还没有已发布的课程" />

    <el-pagination
      v-if="total > query.size"
      v-model:current-page="query.page"
      class="pager"
      layout="prev, pager, next"
      :page-size="query.size"
      :total="total"
      @current-change="load"
    />
  </div>
</template>

<style scoped>
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(270px, 100%), 1fr));
  gap: 18px;
  margin-top: var(--app-space-md);
  min-height: 120px;
}
.card {
  display: flex;
  flex-direction: column;
  padding: 0;
  overflow: hidden;
  border: 1px solid var(--app-border);
  border-radius: 13px;
  background: var(--app-surface);
  box-shadow: var(--app-shadow);
  font: inherit;
  text-align: left;
  color: var(--app-text);
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s, transform 0.2s;
}
.card:hover {
  border-color: var(--el-color-primary-light-5);
  box-shadow: 0 10px 28px rgba(24, 55, 88, 0.08);
  transform: translateY(-2px);
}
.cover {
  position: relative;
  min-height: 118px;
  display: flex;
  align-items: flex-end;
  overflow: hidden;
  padding: 18px 18px 16px;
  color: #fff;
  background: linear-gradient(115deg, #203e60, #193651 67%, #224967);
}
/* the Studywise orbit + planet, small, in the corner */
.cover::before {
  content: '';
  position: absolute;
  right: -30px;
  top: 18px;
  width: 150px;
  height: 56px;
  border: 1px solid rgba(150, 206, 210, 0.25);
  border-radius: 50%;
  transform: rotate(-24deg);
}
.cover::after {
  content: '';
  position: absolute;
  right: 34px;
  top: 30px;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: radial-gradient(circle at 34% 30%, #ffc384, #d47a67 75%);
  box-shadow: 0 0 18px rgba(255, 155, 119, 0.3);
}
.tone-1 {
  background: linear-gradient(115deg, #284f72, #1b3a5a);
}
.tone-1::after {
  background: #91d2d1;
  box-shadow: 0 0 14px #91d2d1;
}
.tone-2 {
  background: linear-gradient(115deg, #34407e, #1f2c5c);
}
.tone-2::after {
  background: radial-gradient(circle at 34% 30%, #d1ddff, #8f7cf5 75%);
}
.tone-3 {
  background: linear-gradient(115deg, #1f5a63, #173f4c);
}
.tone-3::after {
  background: #9bd7d0;
}
.cover-name {
  position: relative;
  z-index: 1;
  max-width: 70%;
  font-size: 21px;
  font-weight: 700;
  line-height: 1.25;
  letter-spacing: -0.02em;
}
.cover-chapters {
  position: absolute;
  z-index: 1;
  bottom: 16px;
  right: 16px;
  padding: 3px 8px;
  border-radius: 5px;
  background: rgba(255, 255, 255, 0.14);
  font-size: 12px;
}
.body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 14px 18px 16px;
}
.desc {
  flex: 1;
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--app-text-secondary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
}
.teacher {
  color: var(--app-text-muted);
}
.progress,
.start {
  padding: 3px 8px;
  border-radius: 5px;
  font-size: 11px;
  font-weight: 600;
}
.progress {
  background: #e8f7f4;
  color: #2e8b7f;
}
.start {
  background: #eff1ff;
  color: #5369db;
}
.pager {
  margin-top: var(--app-space-md);
  justify-content: center;
}
</style>
