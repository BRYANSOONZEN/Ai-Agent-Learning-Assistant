<script setup>
/*
 * One mastery value (0..1) as a thin bar, with ticks at the 0.6 / 0.8 level thresholds
 * and the level label + percent next to it as text (the color is never the only cue).
 */
import { LEVELS, THRESHOLDS, percent } from './levels'

defineProps({
  value: { type: Number, default: null },
  level: { type: String, default: 'none' },
})
</script>

<template>
  <span class="mastery-meter" :title="`掌握度 ${percent(value)}，${LEVELS[level].label}`">
    <span class="track">
      <span v-if="value != null" class="fill" :class="level" :style="{ width: Math.max(value * 100, 2) + '%' }" />
      <i v-for="t in THRESHOLDS" :key="t" class="tick" :style="{ left: t * 100 + '%' }" />
    </span>
    <span class="value">{{ percent(value) }}</span>
    <span class="level" :class="level">{{ LEVELS[level].label }}</span>
  </span>
</template>

<style scoped>
.mastery-meter {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}
.track {
  position: relative;
  flex: 1;
  min-width: 80px;
  height: 8px;
  border-radius: 4px;
  background: var(--el-fill-color);
}
.fill {
  position: absolute;
  inset: 0 auto 0 0;
  border-radius: 4px;
}
.fill.good {
  background: var(--app-success);
}
.fill.medium {
  background: var(--app-warning);
}
.fill.weak {
  background: var(--app-danger);
}
/* threshold ticks sit above the fill, cut out of it with the surface color */
.tick {
  position: absolute;
  top: -3px;
  bottom: -3px;
  width: 2px;
  margin-left: -1px;
  background: var(--app-surface);
  box-shadow: 0 0 0 1px var(--el-border-color);
}
.value {
  flex: none;
  width: 38px;
  text-align: right;
  font-weight: 700;
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}
.level {
  flex: none;
  width: 52px;
  padding: 2px 0;
  border-radius: 6px;
  text-align: center;
  font-size: 12px;
  font-weight: 600;
  background: var(--el-fill-color);
  color: var(--app-text-secondary);
}
.level.good {
  background: var(--el-color-success-light-9);
  color: #1f7a69; /* darker than the fill so the label keeps text contrast */
}
.level.medium {
  background: var(--el-color-warning-light-9);
  color: #9a5a1c;
}
.level.weak {
  background: var(--el-color-danger-light-9);
  color: #b23b2f;
}
</style>
