<script setup>
// Title row used at the top of EVERY page. Put page-level buttons in the default slot.
defineProps({
  title: { type: String, required: true },
  description: { type: String, default: '' },
  back: { type: Boolean, default: false },
})
</script>

<template>
  <div class="page-header">
    <div class="left">
      <el-button v-if="back" icon="ArrowLeft" circle size="small" @click="$router.back()" />
      <div>
        <h1 class="title">{{ title }}</h1>
        <p v-if="description" class="description">{{ description }}</p>
      </div>
    </div>
    <div class="actions"><slot /></div>
  </div>
</template>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--app-space-md);
  margin-bottom: var(--app-space-lg);
}

.left {
  display: flex;
  align-items: center;
  gap: var(--app-space-md);
}

.title {
  margin: 0;
  font-size: var(--app-font-size-title);
  font-weight: 600;
}

.description {
  margin: var(--app-space-xs) 0 0;
  color: var(--app-text-secondary);
}

.actions {
  display: flex;
  gap: var(--app-space-sm);
}
</style>
