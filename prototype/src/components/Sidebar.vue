<script setup>
import AppIcon from './AppIcon.vue'
import { navItems } from '../data/demo'

defineProps({ activeView: String })
const emit = defineEmits(['navigate'])
</script>

<template>
  <aside class="sidebar">
    <div class="brand">
      <div class="brand-mark"><span></span><span></span><span></span></div>
      <div><strong>studywise</strong><small>AI learning assistant</small></div>
    </div>

    <div class="workspace-label">我的工作区</div>
    <nav class="main-nav" aria-label="主导航">
      <button v-for="item in navItems" :key="item.id" class="nav-item" :class="{ active: activeView === item.id }" @click="emit('navigate', item.id)">
        <AppIcon :name="item.icon" :size="19" />
        <span>{{ item.label }}</span>
        <span v-if="item.badge" class="nav-badge">{{ item.badge }}</span>
      </button>
    </nav>

    <div class="sidebar-bottom">
      <div class="sidebar-prompt">
        <div class="prompt-orb"><AppIcon name="spark" :size="17" /></div>
        <strong>学习节奏不错</strong>
        <p>再完成 1 个练习，就能解锁今日徽章。</p>
        <div class="prompt-progress"><span></span></div>
      </div>
      <button class="profile-row">
        <span class="avatar avatar-small">林</span>
        <span class="profile-copy"><strong>林晓雨</strong><small>学生账户</small></span>
        <AppIcon name="chevron" :size="16" />
      </button>
    </div>
  </aside>
</template>
