<script setup>
// Small number tile for dashboards (Studywise style): tinted icon, big value, label, optional hint
defineProps({
  label: { type: String, required: true },
  value: { type: [String, Number], required: true },
  icon: { type: String, default: '' },
  hint: { type: String, default: '' },
  tone: { type: String, default: 'blue' }, // blue / green / orange / purple
})
</script>

<template>
  <div class="app-card stat-card" :class="`tone-${tone}`">
    <div class="top">
      <span v-if="icon" class="icon"><el-icon :size="18"><component :is="icon" /></el-icon></span>
      <span v-if="hint" class="hint">{{ hint }}</span>
    </div>
    <div class="value">{{ value }}</div>
    <div class="label">{{ label }}</div>
  </div>
</template>

<style scoped>
.stat-card {
  height: 100%; /* equal heights within an el-row */
  padding: 17px 19px 16px;
}
.top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  min-height: 31px;
}
.icon {
  width: 31px;
  height: 31px;
  border-radius: 9px;
  display: grid;
  place-items: center;
}
.tone-blue .icon {
  color: #5c7cfa;
  background: #eef1ff;
}
.tone-green .icon {
  color: #33ad99;
  background: #e9f8f3;
}
.tone-orange .icon {
  color: #e99a4e;
  background: #fff4e8;
}
.tone-purple .icon {
  color: #8f7cf5;
  background: #f0edff;
}
.hint {
  font-size: 12px;
  color: var(--app-success);
}
.tone-orange .hint {
  color: var(--app-warning);
}
.value {
  margin-top: 14px;
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -0.5px;
  white-space: nowrap;
}
.label {
  margin-top: 2px;
  color: var(--app-text-muted);
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
