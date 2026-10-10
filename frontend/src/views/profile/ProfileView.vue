<script setup>
/*
 * TEMPLATE: Dashboard page (stat cards row + charts grid).
 * Copy for any overview page: teacher class overview, diagnosis summary...
 */
import { computed, onMounted, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import ChartCard from '@/components/ChartCard.vue'
import { getStudentProfile } from '@/api/profile'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const profile = ref(null)

const percent = (v) => `${Math.round(v * 100)}%`

const radarOption = computed(() => ({
  tooltip: {},
  radar: {
    indicator: profile.value.mastery.map((m) => ({ name: m.name, max: 1 })),
    radius: '65%',
  },
  series: [
    {
      type: 'radar',
      areaStyle: { opacity: 0.2 },
      data: [{ name: '掌握度', value: profile.value.mastery.map((m) => m.value) }],
    },
  ],
}))

const trendOption = computed(() => {
  const t = profile.value.weeklyTrend
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: ['正确率', '学习时长'] },
    grid: { left: 40, right: 40, bottom: 30 },
    xAxis: { type: 'category', data: t.weeks },
    yAxis: [
      { type: 'value', max: 1, axisLabel: { formatter: (v) => percent(v) } },
      { type: 'value', name: '小时' },
    ],
    series: [
      { name: '正确率', type: 'line', smooth: true, data: t.accuracy },
      { name: '学习时长', type: 'bar', yAxisIndex: 1, barWidth: 18, data: t.studyHours },
    ],
  }
})

onMounted(async () => {
  profile.value = await getStudentProfile(userStore.user?.id)
})
</script>

<template>
  <div v-loading="!profile">
    <PageHeader
      title="学生画像"
      :description="profile ? `${profile.student.name} · ${profile.student.className}` : ''"
    >
      <el-button icon="Download">导出报告</el-button>
    </PageHeader>

    <template v-if="profile">
      <el-row :gutter="16">
        <el-col :span="12" :lg="6">
          <StatCard label="累计学习时长" :value="`${profile.stats.studyHours} h`" icon="Timer" />
        </el-col>
        <el-col :span="12" :lg="6">
          <StatCard label="完成习题" :value="profile.stats.exercisesDone" icon="EditPen" />
        </el-col>
        <el-col :span="12" :lg="6">
          <StatCard label="平均正确率" :value="percent(profile.stats.accuracy)" icon="Aim" />
        </el-col>
        <el-col :span="12" :lg="6">
          <StatCard
            label="薄弱知识点"
            :value="profile.stats.weakPoints"
            icon="Warning"
            hint="掌握度低于 60%"
          />
        </el-col>
      </el-row>

      <el-row :gutter="16" class="charts">
        <el-col :xs="24" :lg="10">
          <ChartCard title="知识点掌握度" :option="radarOption" />
        </el-col>
        <el-col :xs="24" :lg="14">
          <ChartCard title="每周学习趋势" :option="trendOption" />
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<style scoped>
.el-col {
  margin-bottom: var(--app-space-md);
}
</style>
