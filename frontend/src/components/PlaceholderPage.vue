<script setup>
// Temporary page for modules not built yet. Replace the route's component when you start work.
import PageHeader from './PageHeader.vue'
</script>

<template>
  <div>
    <PageHeader :title="$route.meta.title" :description="$route.meta.description" />
    <div class="app-card">
      <el-empty :description="`「${$route.meta.stage}」模块开发中`">
        <p class="hint">复制模板页面开始开发（参见 README）。</p>
      </el-empty>
    </div>
  </div>
</template>

<style scoped>
.hint {
  color: var(--app-text-muted);
  margin: 0;
}
</style>
