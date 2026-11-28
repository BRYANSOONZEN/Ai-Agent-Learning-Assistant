<script setup>
import AppIcon from './AppIcon.vue'
defineProps({ title: String, value: String, detail: String, icon: String, tone: String })
</script>

<template>
  <article class="stat-card" :class="`tone-${tone}`">
    <div class="stat-top"><span class="stat-icon"><AppIcon :name="icon" :size="19" /></span><span class="stat-detail">{{ detail }}</span></div>
    <div class="stat-value">{{ value }}</div>
    <div class="stat-title">{{ title }}</div>
  </article>
</template>
