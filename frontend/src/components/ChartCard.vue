<script setup>
// Card containing one ECharts chart. Pass an ECharts `option`; the shared theme is applied.
// `plain` drops the card and title, for a chart that sits inside a section with its own heading.
import { onMounted, onBeforeUnmount, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { CHART_THEME } from '@/styles/chart-theme'

const props = defineProps({
  title: { type: String, default: '' },
  plain: { type: Boolean, default: false },
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
  <div :class="{ 'app-card': !plain }">
    <div v-if="title" class="chart-title">{{ title }}</div>
    <div ref="el" :style="{ height }" />
  </div>
</template>

<style scoped>
.chart-title {
  font-weight: 600;
  margin-bottom: var(--app-space-md);
}
</style>
