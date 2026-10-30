<script setup>
/*
 * 首页, laid out like the Studywise prototype.
 * Teachers: their own courses come first (TeacherCourses).
 * Students: a dark welcome banner with the next thing to learn; three real numbers (study time from 学习记录,
 * mastered / weak knowledge points from 学情诊断); the chapter they were in as a path; recent activity;
 * their courses; questions picked by 学习推荐; and the learning chain (学 → 记 → 诊 → 画 → 荐).
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import TeacherCourses from './TeacherCourses.vue'
import { useUserStore } from '@/stores/user'
import { layoutChildren } from '@/router'
import StatCard from '@/components/StatCard.vue'
import { getLearnCourse, getLearnCourses, getRecords } from '@/api/learn'
import { getMyDiagnosis } from '@/api/diagnosis'
import { getRecommendations } from '@/api/recommend'
import { itemType } from '@/views/teacher/courseModules'
import { courseProgress, lastPosition, syncDone, totalDone } from '@/views/learn/learnProgress'

const router = useRouter()
const userStore = useUserStore()
const isTeacher = computed(() => ['teacher', 'admin'].includes(userStore.role))

const greeting = (() => {
  const h = new Date().getHours()
  return h < 11 ? '早上好' : h < 14 ? '中午好' : h < 18 ? '下午好' : '晚上好'
})()

/* ---------- student ---------- */
const last = lastPosition()
const learned = totalDone()
const courses = ref([])
const courseTotal = ref(0)
const steps = ref([]) // the chapter the student was in: intro + items, each { view, title, icon, done, current }
const records = ref(null)
const diagnosis = ref(null)
const recommended = ref([]) // up to 3 questions from 学习推荐

const today = new Date()
  .toLocaleDateString('en-US', { weekday: 'long', month: 'short', day: 'numeric', year: 'numeric' })
  .toUpperCase()
  .replace(', ', ' · ')

const chapterPct = computed(() => {
  const items = steps.value.filter((s) => s.view !== 'intro')
  return items.length ? Math.round((items.filter((s) => s.done).length / items.length) * 100) : 0
})
const studyTime = computed(() => {
  const m = records.value?.summary.minutes ?? 0
  return m >= 60 ? `${Math.floor(m / 60)}h ${m % 60}m` : `${m}m`
})
const activity = computed(() => (records.value?.timeline || []).slice(0, 4))

// timeline kinds from 学习记录 -> icon + tone
const KINDS = {
  study: { icon: 'Reading', tone: 'blue' },
  finish: { icon: 'Check', tone: 'green' },
  quiz: { icon: 'EditPen', tone: 'orange' },
  dialogue: { icon: 'ChatDotRound', tone: 'purple' },
}
function ago(t) {
  const d = Math.floor((Date.now() - new Date(t)) / 60000)
  if (d < 60) return `${Math.max(d, 1)} 分钟前`
  if (d < 1440) return `${Math.floor(d / 60)} 小时前`
  return d < 2880 ? '昨天' : `${Math.floor(d / 1440)} 天前`
}

const stages = computed(() =>
  layoutChildren.filter(
    (r) => r.meta.stage && !r.meta.hidden && (!r.meta.roles || r.meta.roles.includes(userStore.role)),
  ),
)

// the first unfinished step from where the student stopped (or the stop itself)
const next = computed(() => {
  const here = steps.value.findIndex((s) => s.current)
  return steps.value.slice(Math.max(here, 0)).find((s) => !s.done) || null
})

function go(view) {
  if (!last) return router.push({ name: 'courses' })
  router.push({
    name: 'course-detail',
    params: { id: last.courseId },
    query: { chapter: last.chapterId, view: view || last.view },
  })
}

async function loadPath() {
  if (!last) return
  const data = await getLearnCourse(last.courseId)
  const ch = data.chapters.find((c) => c.id === last.chapterId)
  if (!ch) return
  const done = syncDone(last.courseId, data.doneItemIds)
  steps.value = [
    {
      view: 'intro',
      title: '章节介绍',
      icon: 'Notebook',
      done: last.view !== 'intro' || ch.items.some((it) => done.includes(it.id)),
      current: last.view === 'intro',
    },
    ...ch.items.map((it) => ({
      view: 'item:' + it.id,
      title: it.title,
      icon: itemType(it.type)?.icon || 'Document',
      done: done.includes(it.id),
      current: last.view === 'item:' + it.id,
    })),
  ]
}

onMounted(async () => {
  if (isTeacher.value) return
  try {
    const data = await getLearnCourses({ page: 1, size: 6 })
    courses.value = data.records
    courseTotal.value = data.total
    await loadPath()
    // the rest is extra: one failing must not blank the page
    const [r, d, rec] = await Promise.allSettled([getRecords(), getMyDiagnosis(), getRecommendations()])
    if (r.status === 'fulfilled') records.value = r.value
    if (d.status === 'fulfilled') diagnosis.value = d.value
    if (rec.status === 'fulfilled') {
      recommended.value = rec.value.points
        .flatMap((p) => p.exercises.map((e) => ({ ...e, point: p.name })))
        .slice(0, 3)
    }
  } catch {
    /* the request helper already showed the error */
  }
})
</script>

<template>
  <!-- ================= teacher ================= -->
  <TeacherCourses v-if="isTeacher" :greeting="`${greeting}，${userStore.user?.name}`" />

  <!-- ================= student ================= -->
  <div v-else class="home">
    <section class="sw-banner">
      <div class="sw-banner-copy">
        <span class="sw-eyebrow on-dark">{{ today }}</span>
        <h2>{{ greeting }}，{{ userStore.user?.name }} <span class="spark">✦</span></h2>
        <template v-if="last">
          <p v-if="next">今天接着学「{{ next.title }}」吧，在{{ last.courseName }}的{{ last.chapterTitle }}。</p>
          <p v-else>{{ last.chapterTitle }}已经学完了，回到课程看看下一章。</p>
          <button type="button" class="sw-button-light" @click="go(next?.view)">
            {{ next ? '继续学习' : '回到课程' }}<el-icon><Right /></el-icon>
          </button>
        </template>
        <template v-else>
          <p>每章按顺序学：看视频、读资料、做练习，最后和 AI 对话巩固。</p>
          <button type="button" class="sw-button-light" @click="router.push({ name: 'courses' })">
            选一门课<el-icon><Right /></el-icon>
          </button>
        </template>
      </div>
      <div class="sw-art" aria-hidden="true">
        <div class="sw-orbit one" />
        <div class="sw-orbit two" />
        <div class="sw-planet big" />
        <div class="sw-planet small" />
        <div v-if="steps.length" class="sw-art-card">
          <strong>{{ chapterPct }}%</strong>
          <small>本章进度</small>
        </div>
      </div>
    </section>

    <section class="stats">
      <StatCard
        label="累计学习时长"
        :value="studyTime"
        icon="Timer"
        tone="blue"
        :hint="records ? `学了 ${records.summary.activeDays} 天` : ''"
      />
      <StatCard
        label="已掌握知识点"
        icon="CircleCheck"
        tone="green"
        :value="diagnosis ? `${diagnosis.summary.good} / ${diagnosis.summary.total}` : '-'"
        :hint="`已学完 ${learned} 项`"
      />
      <StatCard
        label="待补的薄弱知识点"
        icon="Aim"
        tone="orange"
        :value="diagnosis ? String(diagnosis.summary.weak).padStart(2, '0') : '-'"
        :hint="diagnosis?.summary.weak ? '去补一补' : ''"
      />
    </section>

    <section class="two-cols">
      <article class="app-card">
        <div class="sw-heading">
          <div>
            <span class="sw-eyebrow">CONTINUE LEARNING</span>
            <h3>接着上次学习</h3>
          </div>
          <router-link :to="{ name: 'courses' }" class="sw-link">查看课程<el-icon><Right /></el-icon></router-link>
        </div>
        <template v-if="last">
          <button type="button" class="lesson-row" @click="go(next?.view)">
            <span class="sw-icon lg tone-blue"><el-icon :size="22"><MagicStick /></el-icon></span>
            <span class="lesson-info">
              <strong>{{ last.chapterTitle }}</strong>
              <span>{{ last.courseName }}{{ next ? `，下一步：${next.title}` : '，本章学完了' }}</span>
              <span class="sw-progress"><span :style="{ width: chapterPct + '%' }" /></span>
              <small>已完成 {{ chapterPct }}%</small>
            </span>
          </button>
          <!-- the chapter as a path: what is done, what comes next -->
          <ol v-if="steps.length" class="stage-mini" :aria-label="last.chapterTitle">
            <li v-for="s in steps" :key="s.view" :class="{ done: s.done, current: s === next }">
              <button type="button" :title="s.title" @click="go(s.view)"><i />{{ s.title }}</button>
            </li>
          </ol>
        </template>
        <el-empty v-else :image-size="56" description="还没开始学，先选一门课" />
      </article>

      <article class="app-card">
        <div class="sw-heading">
          <div>
            <span class="sw-eyebrow">RECENT ACTIVITY</span>
            <h3>最近活动</h3>
          </div>
          <router-link :to="{ name: 'records' }" class="sw-link">学习记录<el-icon><Right /></el-icon></router-link>
        </div>
        <ul class="activity">
          <li v-for="(a, i) in activity" :key="i">
            <span class="sw-icon sm" :class="'tone-' + (KINDS[a.kind]?.tone || 'blue')">
              <el-icon :size="14"><component :is="KINDS[a.kind]?.icon || 'Reading'" /></el-icon>
            </span>
            <span class="activity-copy">
              <strong>{{ a.detail }}</strong>
              <small>{{ a.title }}，{{ ago(a.time) }}</small>
            </span>
          </li>
        </ul>
        <el-empty v-if="records && !activity.length" :image-size="56" description="还没有学习活动" />
      </article>
    </section>

    <section class="app-card">
      <div class="sw-heading">
        <div>
          <span class="sw-eyebrow">MY COURSES</span>
          <h3>我的课程</h3>
        </div>
        <router-link :to="{ name: 'courses' }" class="sw-link">
          全部 {{ courseTotal }} 门<el-icon><Right /></el-icon>
        </router-link>
      </div>
      <div class="course-list">
        <router-link
          v-for="(c, i) in courses"
          :key="c.id"
          :to="{ name: 'course-detail', params: { id: c.id } }"
          class="course-row"
        >
          <span class="sw-icon lg" :class="['tone-blue', 'tone-green', 'tone-orange', 'tone-purple'][i % 4]">
            <b>{{ c.name.slice(0, 1) }}</b>
          </span>
          <span class="course-main">
            <strong>{{ c.name }}</strong>
            <small>{{ c.teacher }}，{{ c.chapterCount }} 个章节</small>
          </span>
          <span class="sw-tag" :class="courseProgress(c.id) ? 'teal' : 'gray'">
            {{ courseProgress(c.id) ? `已学 ${courseProgress(c.id)} 项` : '未开始' }}
          </span>
        </router-link>
        <el-empty v-if="!courses.length" :image-size="60" description="还没有已发布的课程" />
      </div>
    </section>

    <section class="app-card">
      <div class="sw-heading">
        <div>
          <span class="sw-eyebrow">RECOMMENDED FOR YOU</span>
          <h3>为你推荐</h3>
        </div>
        <router-link :to="{ name: 'recommend' }" class="sw-link">去做题<el-icon><Right /></el-icon></router-link>
      </div>
      <div v-if="recommended.length" class="grid3">
        <router-link v-for="(r, i) in recommended" :key="r.exerciseId" :to="{ name: 'recommend' }" class="tile">
          <span class="tile-no">{{ String(i + 1).padStart(2, '0') }}</span>
          <span class="tile-main">
            <strong>{{ r.stem }}</strong>
            <small>{{ r.point }}</small>
          </span>
          <span class="sw-tag" :class="r.reason === '上次做错了' ? 'orange' : 'purple'">{{ r.reason }}</span>
        </router-link>
      </div>
      <p v-else class="muted">没有薄弱的知识点，暂时不用补。做错的题会出现在这里。</p>
    </section>

    <section class="app-card">
      <div class="sw-heading">
        <div>
          <span class="sw-eyebrow">LEARNING LOOP</span>
          <h3>学习主线</h3>
        </div>
        <span class="muted">学完内容后，按这个顺序回顾和提升</span>
      </div>
      <ol class="chain">
        <li v-for="(s, i) in stages" :key="s.name">
          <router-link :to="'/' + s.path" class="tile">
            <span class="tile-no">{{ String(i + 1).padStart(2, '0') }}</span>
            <span class="tile-main">
              <strong>{{ s.meta.title }}</strong>
              <small>{{ s.meta.subtitle }}</small>
            </span>
            <span class="sw-tag purple">{{ s.meta.stage }}</span>
          </router-link>
        </li>
      </ol>
    </section>
  </div>
</template>

<style scoped>
.home {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 22px;
}
.stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}
.two-cols {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(0, 0.85fr);
  gap: 20px;
}
.home .app-card + .app-card {
  margin-top: 0;
}
.muted {
  margin: 0;
  color: var(--app-text-muted);
  font-size: 12px;
}

/* ---------- continue learning ---------- */
.lesson-row {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 13px;
  margin-top: 22px;
  padding: 0;
  border: 0;
  background: none;
  font: inherit;
  color: inherit;
  text-align: left;
  cursor: pointer;
}
.lesson-info {
  flex: 1;
  min-width: 0;
  display: grid;
  gap: 3px;
}
.lesson-info strong {
  font-size: 14px;
}
.lesson-info > span:not(.sw-progress) {
  color: var(--app-text-muted);
  font-size: 12px;
}
.lesson-info .sw-progress {
  margin-top: 9px;
}
.lesson-info small {
  margin-top: 3px;
  color: #8e9eb0;
  font-size: 11px;
}
.stage-mini {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  margin: 22px 0 0;
  padding: 14px 0 0;
  border-top: 1px solid #eef1f5;
  list-style: none;
}
.stage-mini li {
  min-width: 0;
}
.stage-mini button {
  max-width: 100%;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0;
  border: 0;
  background: none;
  color: #a2adba;
  font: inherit;
  font-size: 11px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  cursor: pointer;
}
.stage-mini i {
  flex: none;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #d9e1e8;
}
.stage-mini .done button {
  color: #2f8f7d;
}
.stage-mini .done i {
  background: #6ac8b6;
}
.stage-mini .current button {
  color: #5c6fe0;
  font-weight: 600;
}
.stage-mini .current i {
  background: #7988f4;
  box-shadow: 0 0 0 3px #e9ebff;
}

/* ---------- recent activity ---------- */
.activity {
  margin: 12px 0 0;
  padding: 0;
  list-style: none;
}
.activity li {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 0;
  border-bottom: 1px solid #f0f3f6;
}
.activity li:last-child {
  border-bottom: 0;
}
.activity-copy {
  min-width: 0;
  display: grid;
  gap: 3px;
}
.activity-copy strong {
  font-size: 12px;
  font-weight: 600;
}
.activity-copy small {
  color: #8e9bab;
  font-size: 11px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ---------- courses ---------- */
.course-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(300px, 100%), 1fr));
  gap: 12px;
  margin-top: 18px;
}
.course-row {
  display: flex;
  align-items: center;
  gap: 13px;
  padding: 13px;
  border: 1px solid #e9eef4;
  border-radius: 10px;
  color: var(--app-text);
  text-decoration: none;
  transition: border-color 0.18s, background 0.18s;
}
.course-row:hover {
  border-color: var(--el-color-primary-light-5);
  background: #fafbff;
}
.course-row b {
  font-size: 17px;
}
.course-main {
  flex: 1;
  min-width: 0;
  display: grid;
  gap: 3px;
}
.course-main strong {
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.course-main small {
  color: var(--app-text-muted);
  font-size: 11px;
}

/* ---------- numbered tiles: recommended + learning chain ---------- */
.grid3,
.chain {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(220px, 100%), 1fr));
  gap: 12px;
  margin: 18px 0 0;
  padding: 0;
  list-style: none;
}
.chain {
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
}
.tile {
  height: 100%;
  min-height: 82px;
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 13px;
  border: 1px solid #e9eef4;
  border-radius: 9px;
  color: var(--app-text);
  text-decoration: none;
  transition: border-color 0.18s;
}
.tile:hover {
  border-color: var(--el-color-primary-light-5);
}
.tile-no {
  color: #b3bfca;
  font-size: 11px;
  font-weight: 700;
}
.tile-main {
  flex: 1;
  min-width: 0;
  display: grid;
  gap: 5px;
}
.tile-main strong {
  font-size: 12px;
  line-height: 1.45;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.tile-main small {
  color: #8e9bab;
  font-size: 11px;
  line-height: 1.5;
}

@media (max-width: 1000px) {
  .two-cols,
  .stats {
    grid-template-columns: 1fr;
  }
  .stage-mini {
    flex-wrap: wrap;
  }
}
</style>
