<script setup>
/*
 * 学生画像 (the 画 stage). Everything comes from GET /api/profiles/me, which works it out from the
 * learning records (记) and mastery / dialogue results (诊) when the page opens:
 * tags with the reason behind each, six dimensions (each says how it was computed, or that there is
 * not enough data yet), study minutes over the last 14 days, and the score of every quiz submission.
 */
import { computed, onMounted, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import ChartCard from '@/components/ChartCard.vue'
import { getMyProfile } from '@/api/profile'

const profile = ref(null)
const loading = ref(false)

const TAG_TYPES = {
  behavior: { label: '学习习惯', cls: 'behavior' },
  strength: { label: '优势', cls: 'strength' },
  weakness: { label: '薄弱', cls: 'weakness' },
}

const percent = (v) => (v == null ? '-' : Math.round(v * 100) + '%')
const md = (d) => `${Number(d.slice(5, 7))}/${Number(d.slice(8, 10))}`

const tagGroups = computed(() =>
  Object.keys(TAG_TYPES)
    .map((type) => ({ type, ...TAG_TYPES[type], tags: profile.value.tags.filter((t) => t.type === type) }))
    .filter((g) => g.tags.length),
)

// shared, recessive chart chrome: light grid, muted axis text, one series so no legend
const AXIS_TEXT = { color: '#8e9eb0', fontSize: 11 }
const SPLIT = { lineStyle: { color: '#edf1f5' } }
const PRIMARY = '#5c76e9'

const daysOption = computed(() => {
  const days = profile.value.days
  return {
    grid: { left: 36, right: 12, top: 16, bottom: 28 },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow', shadowStyle: { color: 'rgba(92,118,233,0.06)' } },
      formatter: ([p]) => {
        const d = days[p.dataIndex]
        return `${d.date}<br/>学习 <b>${d.minutes}</b> 分钟，${d.actions} 次学习活动`
      },
    },
    xAxis: {
      type: 'category',
      data: days.map((d) => md(d.date)),
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#dce5ed' } },
      axisLabel: AXIS_TEXT,
    },
    yAxis: { type: 'value', name: '分钟', nameTextStyle: AXIS_TEXT, minInterval: 1, axisLabel: AXIS_TEXT, splitLine: SPLIT },
    series: [
      {
        type: 'bar',
        data: days.map((d) => d.minutes),
        barMaxWidth: 18,
        itemStyle: { color: PRIMARY, borderRadius: [4, 4, 0, 0] },
      },
    ],
  }
})

const quizOption = computed(() => {
  const q = profile.value.quizTrend
  return {
    grid: { left: 40, right: 16, top: 16, bottom: 28 },
    tooltip: {
      trigger: 'axis',
      formatter: ([p]) => {
        const a = q[p.dataIndex]
        return `${a.time.slice(0, 16)}<br/>${a.title}<br/>答对 <b>${a.correct} / ${a.total}</b>`
      },
    },
    xAxis: {
      type: 'category',
      data: q.map((a) => md(a.time)),
      boundaryGap: false,
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#dce5ed' } },
      axisLabel: AXIS_TEXT,
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: 100,
      interval: 25,
      axisLabel: { ...AXIS_TEXT, formatter: '{value}%' },
      splitLine: SPLIT,
    },
    series: [
      {
        type: 'line',
        data: q.map((a) => Math.round((a.correct / a.total) * 100)),
        lineStyle: { width: 2, color: PRIMARY },
        itemStyle: { color: PRIMARY, borderColor: '#fff', borderWidth: 2 },
        symbolSize: 10,
      },
    ],
  }
})

// ECharts pulls a missing value to the centre, which reads as 0%, so only dimensions with data are plotted
const plotted = computed(() => profile.value.dimensions.filter((d) => d.value != null))
const unplotted = computed(() => profile.value.dimensions.filter((d) => d.value == null))

const radarOption = computed(() => {
  const dims = plotted.value
  return {
    tooltip: {
      trigger: 'item',
      formatter: () => dims.map((d) => `${d.label}：<b>${percent(d.value)}</b>`).join('<br/>'),
    },
    radar: {
      center: ['50%', '52%'],
      // narrower on a phone so the side labels stay inside the canvas
      radius: window.innerWidth < 600 ? '50%' : '64%',
      axisNameGap: 8,
      splitNumber: 4,
      indicator: dims.map((d) => ({ name: d.label, max: 1 })),
      axisName: {
        formatter: (name) => `{name|${name}}\n{value|${percent(dims.find((d) => d.label === name).value)}}`,
        rich: {
          name: { color: '#4a5a6c', fontSize: 12, lineHeight: 18, align: 'center' },
          value: { color: '#1f2d3d', fontSize: 13, fontWeight: 700, lineHeight: 18, align: 'center' },
        },
      },
      axisLine: { lineStyle: { color: '#dce5ed' } },
      splitLine: { lineStyle: { color: '#edf1f5' } },
      splitArea: { show: false },
    },
    series: [
      {
        type: 'radar',
        symbolSize: 8,
        lineStyle: { width: 2, color: PRIMARY },
        itemStyle: { color: PRIMARY, borderColor: '#fff', borderWidth: 2 },
        areaStyle: { color: PRIMARY, opacity: 0.14 },
        data: [{ value: dims.map((d) => d.value), name: '六个维度' }],
      },
    ],
  }
})

async function load() {
  loading.value = true
  try {
    profile.value = await getMyProfile()
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="profile">
    <PageHeader
      title="学生画像"
      description="用你的学习记录、答题和 AI 对话现算出来的：你是怎样的学习者、强在哪、弱在哪。每一条都写了依据。"
    />

    <template v-if="profile">
      <!-- who: name, the key numbers, and the tags with their reasons -->
      <section class="sw-summary portrait">
        <span class="avatar">{{ profile.studentName?.[0] }}</span>
        <div class="sw-summary-main">
          <span class="sw-eyebrow on-dark">LEARNER PROFILE</span>
          <h2>{{ profile.studentName }}</h2>
          <p>学了 {{ profile.stats.minutes }} 分钟，学完 {{ profile.stats.itemsFinished }} 项内容。</p>
        </div>
        <div class="sw-summary-stat">
          <strong>{{ profile.stats.activeDays14 }}</strong>
          <span>近 14 天学习天数</span>
        </div>
        <div class="sw-summary-stat">
          <strong>{{ percent(profile.stats.accuracy) }}</strong>
          <span>答题正确率</span>
        </div>
        <div class="sw-summary-stat">
          <strong>{{ profile.stats.weakPoints }}</strong>
          <span>薄弱知识点</span>
        </div>
      </section>

      <section class="app-card tags-card">
        <div class="sw-heading">
          <div>
            <span class="sw-eyebrow">YOUR PATTERN</span>
            <h3>画像标签</h3>
          </div>
        </div>
        <div class="tags">
          <p v-if="!tagGroups.length" class="empty-note">学习记录还太少，多学几次就会出现标签。</p>
          <div v-for="g in tagGroups" :key="g.type" class="tag-group">
            <span class="group-label">{{ g.label }}</span>
            <ul>
              <li v-for="t in g.tags" :key="t.name" class="tag" :class="g.cls">
                <strong>{{ t.name }}</strong>
                <span>{{ t.reason }}</span>
              </li>
            </ul>
          </div>
        </div>
      </section>

      <!-- six dimensions: the radar shows the shape, the list beside it gives each number and its reason -->
      <section class="app-card dims">
        <div class="sw-heading dims-head">
          <div>
            <span class="sw-eyebrow">DIMENSIONS</span>
            <h3>六个维度</h3>
          </div>
          <router-link :to="{ name: 'diagnosis' }" class="sw-link">
            知识点详情在学情诊断<el-icon><Right /></el-icon>
          </router-link>
        </div>
        <div class="dims-body">
          <div>
            <ChartCard v-if="plotted.length >= 3" plain :option="radarOption" height="340px" />
            <p v-else class="empty-note radar-empty">有数据的维度不足 3 个，多学几次就能画出图。</p>
            <p v-if="plotted.length >= 3 && unplotted.length" class="radar-note">
              {{ unplotted.map((d) => d.label).join('、') }}还没有数据，图上没有画。
            </p>
          </div>
          <div class="dim-list">
            <div v-for="d in profile.dimensions" :key="d.key" class="dim" :class="{ missing: d.value == null }">
              <span class="dim-label">{{ d.label }}</span>
              <span class="dim-value">{{ d.value == null ? '数据不足' : percent(d.value) }}</span>
              <span class="dim-reason">{{ d.reason }}</span>
            </div>
          </div>
        </div>
      </section>

      <div class="charts">
        <ChartCard title="近 14 天学习时长" :option="daysOption" height="240px" />
        <div v-if="profile.quizTrend.length">
          <ChartCard title="每次交卷的正确率" :option="quizOption" height="240px" />
        </div>
        <div v-else class="app-card empty-chart">
          <div class="chart-title">每次交卷的正确率</div>
          <el-empty :image-size="60" description="还没交过练习" />
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.profile .app-card + .app-card {
  margin-top: 0;
}

/* ---------- portrait ---------- */
.portrait {
  margin-bottom: 22px;
}
.tags-card {
  margin-bottom: 22px;
}
.tags-card .tags {
  margin-top: 16px;
}
.avatar {
  flex: none;
  width: 58px;
  height: 58px;
  display: grid;
  place-items: center;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
  font-size: 24px;
  font-weight: 700;
}
.tags {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(260px, 100%), 1fr));
  gap: 16px 28px;
  align-content: start;
}
.empty-note {
  margin: 0;
  color: var(--app-text-muted);
}
.group-label {
  display: block;
  margin-bottom: 8px;
  color: var(--app-text-muted);
  font-size: 12px;
}
.tag-group ul {
  display: grid;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.tag {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 4px 12px;
}
.tag strong {
  padding: 4px 8px;
  border-radius: 5px;
  font-size: 12px;
  background: #f0edff;
  color: #6d5fd0;
}
.tag.strength strong {
  background: #e8f7f4;
  color: #1f7a69;
}
.tag.weakness strong {
  background: #fdeeec;
  color: #b23b2f;
}
.tag span {
  color: var(--app-text-secondary);
  font-size: 13px;
}

/* ---------- dimensions ---------- */
.dims {
  margin: 22px 0;
}
.dims-head {
  margin-bottom: 8px;
}
.dims-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.1fr);
  align-items: center;
  gap: 24px;
}
.radar-note {
  margin: 4px 0 0;
  color: var(--app-text-muted);
  font-size: 12px;
  text-align: center;
}
.radar-empty {
  text-align: center;
}
.dim {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 2px 12px;
  padding: 10px 0;
  border-top: 1px solid var(--el-border-color-lighter);
}
.dim:first-child {
  border-top: 0;
}
.dim-label {
  font-weight: 600;
}
.dim-value {
  text-align: right;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
.missing .dim-value {
  color: var(--app-text-muted);
  font-weight: 500;
  font-size: 12px;
}
.dim-reason {
  grid-column: 1 / -1;
  color: var(--app-text-secondary);
  font-size: 13px;
  line-height: 1.5;
}

/* ---------- charts ---------- */
.charts {
  display: grid;
  /* minmax(0, …) so a chart canvas can never push its column wider than the page */
  grid-template-columns: minmax(0, 1.3fr) minmax(0, 1fr);
  gap: 18px;
}
.chart-title {
  font-weight: 600;
  margin-bottom: var(--app-space-md);
}

@media (max-width: 1100px) {
  .charts {
    grid-template-columns: 1fr;
  }
  .dims-body {
    grid-template-columns: 1fr;
  }
}
</style>
