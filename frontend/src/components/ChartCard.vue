<script setup>
// Card containing one ECharts chart. Pass an ECharts `option`; the shared theme is applied.
import { onMounted, onBeforeUnmount, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { CHART_THEME } from '@/styles/chart-theme'

const props = defineProps({
  title: { type: String, required: true },
  option: { type: Object, required: true },
  height: { type: String, default: '320px' },
})

const el = ref(null)
let chart = null
let observer = null

onMounted(() => {
  chart = echarts.init(el.value, CHART_THEME)
  chart.setOption(props.option)
  observer = new ResizeObserver(() => chart?.resize())
  observer.observe(el.value)
})

watch(
  () => props.option,
  (option) => chart?.setOption(option, true),
  { deep: true },
)

onBeforeUnmount(() => {
  observer?.disconnect()
  chart?.dispose()
})
</script>

<template>
  <div class="app-card">
    <div class="chart-title">{{ title }}</div>
    <div ref="el" :style="{ height }" />
  </div>
</template>

<style scoped>
.chart-title {
  font-weight: 600;
  margin-bottom: var(--app-space-md);
}
</style>
