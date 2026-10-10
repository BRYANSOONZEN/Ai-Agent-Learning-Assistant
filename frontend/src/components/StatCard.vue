<script setup>
// Small number tile for dashboards: label, big value, optional icon and hint
defineProps({
  label: { type: String, required: true },
  value: { type: [String, Number], required: true },
  icon: { type: String, default: '' },
  hint: { type: String, default: '' },
})
</script>

<template>
  <div class="app-card stat-card">
    <div>
      <div class="label">{{ label }}</div>
      <div class="value">{{ value }}</div>
      <div v-if="hint" class="hint">{{ hint }}</div>
    </div>
    <el-icon v-if="icon" :size="28" class="icon"><component :is="icon" /></el-icon>
  </div>
</template>

<style scoped>
.stat-card {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--app-space-sm);
  height: 100%; /* equal heights within an el-row */
  padding: var(--app-space-md) var(--app-space-lg);
}

.stat-card > div {
  min-width: 0;
}

.label {
  color: var(--app-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.value {
  margin-top: var(--app-space-sm);
  font-size: 28px;
  font-weight: 600;
  white-space: nowrap;
}

.hint {
  margin-top: var(--app-space-xs);
  font-size: var(--app-font-size-sm);
  color: var(--app-text-muted);
}

.icon {
  color: var(--app-primary);
  background: var(--el-color-primary-light-9);
  padding: 10px;
  border-radius: var(--app-radius);
  box-sizing: content-box;
  flex-shrink: 0;
}
</style>
