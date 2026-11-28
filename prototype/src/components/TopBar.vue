<script setup>
import AppIcon from './AppIcon.vue'

defineProps({ title: String, subtitle: String })
</script>

<template>
  <header class="topbar">
    <div>
      <h1>{{ title }}</h1>
      <p>{{ subtitle }}</p>
    </div>
    <div class="topbar-actions">
      <div class="streak-pill"><span class="streak-dot"></span><strong>5</strong> 天连续学习</div>
      <button class="icon-button" aria-label="通知"><span class="notification-dot"></span><AppIcon name="dots" :size="19" /></button>
      <span class="avatar avatar-top">林</span>
    </div>
  </header>
</template>
