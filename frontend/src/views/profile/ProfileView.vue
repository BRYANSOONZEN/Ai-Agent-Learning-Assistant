<script setup>
/*
 * 学生画像 (docs/模块说明/学生画像-设计.md), per course:
 *   - every chapter: learning-flow progress (incl. the AI dialogue), content progress (without it), exercises
 *     submitted, first-attempt accuracy, and each knowledge point's colour (green = mastered at the first try,
 *     orange = recovered, red = not yet). Shown all along; once a chapter is done it is its 学习小结.
 *   - the course's four dimensions, open only when every chapter is done: 学习持续性, 学习完成度, 按时完成率,
 *     有效作答正确率. The AI dialogue is never counted. A dimension without a denominator shows "暂无数据";
 *     the polygon is drawn only when all four have values.
 */
import { computed, onMounted, ref, watch } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import ChartCard from '@/components/ChartCard.vue'
import { getCourseProfile, getProfileCourses } from '@/api/profile'

const PRIMARY = '#940b01'
const courses = ref([])
const courseId = ref(null)
const profile = ref(null)
const loading = ref(false)

const pct = (v) => (v == null ? '暂无数据' : Math.round(v * 100) + '%')
const COLORS = {
  green: { label: '第一次就掌握', cls: 'green' },
  orange: { label: '补回来了', cls: 'orange' },
  red: { label: '还没掌握', cls: 'red' },
  none: { label: '没练过', cls: 'none' },
}

onMounted(async () => {
  courses.value = (await getProfileCourses()) || []
  const start = courses.value.find((c) => c.chaptersDone > 0) || courses.value[0]
  courseId.value = start?.courseId ?? null
})
watch(courseId, async (id) => {
  if (!id) return
  loading.value = true
  try {
    profile.value = await getCourseProfile(id)
  } finally {
    loading.value = false
  }
})

const complete = computed(() => profile.value?.dimensions.every((d) => d.value != null))
const radarOption = computed(() => {
  const dims = profile.value?.dimensions || []
  return {
    tooltip: { trigger: 'item', formatter: () => dims.map((d) => `${d.label}：<b>${pct(d.value)}</b>`).join('<br/>') },
    radar: {
      center: ['50%', '52%'],
      radius: window.innerWidth < 600 ? '50%' : '62%',
      splitNumber: 4,
      indicator: dims.map((d) => ({ name: `${d.label}\n${pct(d.value)}`, max: 1 })),
      axisName: { color: '#262626', fontSize: 12, lineHeight: 18 },
      axisLine: { lineStyle: { color: '#d9d9d9' } },
      splitLine: { lineStyle: { color: '#eeeeee' } },
      splitArea: { show: false },
    },
    // missing values are not plotted as 0: the polygon is only drawn when all four have data
    series: complete.value
      ? [{
          type: 'radar',
          symbolSize: 7,
          lineStyle: { width: 2, color: PRIMARY },
          itemStyle: { color: PRIMARY, borderColor: '#fff', borderWidth: 2 },
          areaStyle: { color: PRIMARY, opacity: 0.12 },
          data: [{ value: dims.map((d) => d.value), name: '课程四维' }],
        }]
      : [],
  }
})

const activeDays = computed(() => (profile.value?.days || []).filter((d) => d.minutes > 0 || d.actions > 0).length)
</script>

<template>
  <div v-loading="loading" class="profile">
    <PageHeader title="学生画像" subtitle="每章看知识点颜色和练习情况；全部章节学完后开放课程四维画像。AI 对话不计入画像分数。">
      <el-select v-model="courseId" placeholder="选课程" style="width: 240px">
          <el-option v-for="c in courses" :key="c.courseId" :label="`${c.name}（学完 ${c.chaptersDone}/${c.chapters} 章）`" :value="c.courseId" />
      </el-select>
    </PageHeader>

    <template v-if="profile">
      <!-- course four dimensions -->
      <section class="app-card block">
        <div class="block-head">
          <h3>课程四维画像</h3>
          <span class="muted">计算时间 {{ String(profile.computedAt).slice(0, 16) }} · 持续性参考日 {{ profile.refDate }}</span>
        </div>
        <div v-if="!profile.open" class="locked">
          学完全部章节后开放（已学完 {{ profile.chaptersDone }}/{{ profile.chapters }} 章）。下面先看每一章的情况。
        </div>
        <div v-else class="dims">
          <div class="radar">
            <ChartCard plain :option="radarOption" height="320px" />
            <p v-if="!complete" class="muted center">画像数据不完整：有维度暂无数据，没有画多边形</p>
          </div>
          <div class="dim-list">
            <div v-for="d in profile.dimensions" :key="d.key" class="dim">
              <div class="dim-top">
                <b>{{ d.label }}</b>
                <span class="dim-value" :class="{ none: d.value == null }">{{ pct(d.value) }}</span>
              </div>
              <p class="dim-reason">{{ d.reason }}</p>
              <p class="muted">算法：{{ d.formula }}</p>
              <p class="muted">数据：{{ d.source }}</p>
            </div>
          </div>
        </div>
      </section>

      <!-- every chapter -->
      <section v-for="ch in profile.chapterProfiles" :key="ch.chapterId" class="app-card block">
        <div class="block-head">
          <h3>{{ ch.title }}</h3>
          <el-tag :type="ch.completed ? 'success' : 'info'" size="small">{{ ch.completed ? '学习小结' : '学习中' }}</el-tag>
        </div>
        <div class="bars">
          <div>
            <span class="muted">学习流程（含 AI 对话）{{ ch.flowDone }}/{{ ch.flowTotal }}</span>
            <el-progress :percentage="ch.flowTotal ? Math.round((ch.flowDone / ch.flowTotal) * 100) : 0" :stroke-width="8" :color="PRIMARY" />
          </div>
          <div>
            <span class="muted">内容（视频、文档、练习）{{ ch.contentDone }}/{{ ch.contentTotal }}</span>
            <el-progress :percentage="ch.contentTotal ? Math.round((ch.contentDone / ch.contentTotal) * 100) : 0" :stroke-width="8" color="#595959" />
          </div>
          <div class="stat">
            <span class="muted">练习交了</span><b>{{ ch.exercisesSubmitted }}/{{ ch.exercises }} 组</b>
          </div>
          <div class="stat">
            <span class="muted">第一次作答全对</span>
            <b>{{ ch.graded ? `${ch.correct}/${ch.graded}` : '暂无' }}</b>
            <small v-if="ch.pending" class="muted">（{{ ch.pending }} 题等老师确认）</small>
          </div>
        </div>
        <div v-if="ch.points.length" class="points">
          <span v-for="p in ch.points" :key="p.knowledgePointId" class="point" :class="COLORS[p.color].cls"
            :title="p.firstMastery != null ? `第一次 ${Math.round(p.firstMastery * 100)}%` : ''">
            {{ p.name }}<small>{{ COLORS[p.color].label }}</small>
          </span>
        </div>
        <p v-if="ch.points.some((p) => p.dialogue)" class="dialogue-title">AI 对话里讲得怎么样（只是说明，不计分）</p>
        <ul v-if="ch.points.some((p) => p.dialogue)" class="dialogue-notes">
          <li v-for="p in ch.points.filter((x) => x.dialogue)" :key="p.knowledgePointId">
            <b>{{ p.name }}</b>{{ p.dialogue.replace(/^AI 对话：/, '：') }}
          </li>
        </ul>
        <p class="summary">{{ ch.summary }}</p>
      </section>

      <p class="muted">近 14 天有学习记录的日子：{{ activeDays }} 天（看视频、文档 ≥ 30 秒或交过题才算“有效学习日”）。</p>
    </template>
    <el-empty v-else-if="!loading" description="还没有可以看画像的课程" />
  </div>
</template>

<style scoped>
.profile {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.block {
  padding: 16px 18px;
}
.block-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}
.block-head h3 {
  margin: 0;
}
.muted {
  color: #595959;
  font-size: 12px;
  margin: 2px 0;
}
.center {
  text-align: center;
}
.locked {
  padding: 14px;
  border-radius: 8px;
  background: #f5f5f5;
  color: #595959;
}
.dims {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.2fr);
  gap: 16px;
}
.dim-list {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.dim {
  padding: 10px 12px;
  border-radius: 8px;
  background: #f7f7f7;
}
.dim-top {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}
.dim-value {
  font-size: 22px;
  font-weight: 700;
  color: #940b01;
}
.dim-value.none {
  font-size: 14px;
  color: #8c8c8c;
}
.dim-reason {
  margin: 4px 0;
  font-size: 13px;
}
.bars {
  display: grid;
  grid-template-columns: 1fr 1fr 0.6fr 0.8fr;
  gap: 14px;
  align-items: end;
}
.stat b {
  display: block;
  font-size: 18px;
}
.points {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}
.point {
  padding: 4px 10px;
  border-radius: 14px;
  font-size: 13px;
  border: 1px solid transparent;
}
.point small {
  margin-left: 6px;
  font-size: 11px;
}
.point.green {
  background: #e6f2ef;
  color: #2f7d6d;
}
.point.orange {
  background: #fbf1e4;
  color: #c77a1f;
}
.point.red {
  background: #f8e7e5;
  color: #b4433a;
}
.point.none {
  background: #f5f5f5;
  color: #8c8c8c;
}
.dialogue-title {
  margin: 12px 0 0;
  font-size: 12px;
  color: #8c8c8c;
}
.dialogue-notes {
  margin: 4px 0 0;
  padding-left: 18px;
  font-size: 12px;
  color: #595959;
  line-height: 1.7;
}
.dialogue-notes b {
  color: #262626;
  font-weight: 600;
}
.summary {
  margin: 10px 0 0;
  font-size: 13px;
  color: #262626;
}
@media (max-width: 820px) {
  .dims,
  .bars,
  .dim-list {
    grid-template-columns: 1fr;
  }
}
</style>
