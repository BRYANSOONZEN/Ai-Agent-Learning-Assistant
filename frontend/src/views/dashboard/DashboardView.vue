<script setup>
import { computed } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import { useUserStore } from '@/stores/user'
import { layoutChildren } from '@/router'

const userStore = useUserStore()

// One shortcut per chain stage the current role can access
const stages = computed(() =>
  layoutChildren.filter(
    (r) => r.meta.stage && !r.meta.hidden && (!r.meta.roles || r.meta.roles.includes(userStore.role)),
  ),
)
</script>

<template>
  <div>
    <PageHeader :title="`你好，${userStore.user?.name}`" description="欢迎回到软件工程智能学习平台" />

    <el-row :gutter="16">
      <el-col :span="12" :lg="6"><StatCard label="在学课程" value="4" icon="Reading" /></el-col>
      <el-col :span="12" :lg="6"><StatCard label="本周学习" value="10 h" icon="Timer" /></el-col>
      <el-col :span="12" :lg="6"><StatCard label="待完成习题" value="12" icon="EditPen" /></el-col>
      <el-col :span="12" :lg="6"><StatCard label="新推荐" value="5" icon="Star" /></el-col>
    </el-row>

    <div class="app-card">
      <h3 class="section-title">学习主线</h3>
      <div class="stages">
        <router-link v-for="s in stages" :key="s.name" :to="'/' + s.path" class="stage">
          <span class="stage-char">{{ s.meta.stage }}</span>
          <span class="stage-title">{{ s.meta.title }}</span>
        </router-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
.el-col {
  margin-bottom: var(--app-space-md);
}

.section-title {
  margin: 0 0 var(--app-space-md);
  font-size: var(--app-font-size-lg);
}

.stages {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: var(--app-space-md);
}

.stage {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--app-space-sm);
  padding: var(--app-space-lg) var(--app-space-md);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);
  text-decoration: none;
  color: var(--app-text);
  transition: border-color 0.2s, background 0.2s;
}

.stage:hover {
  border-color: var(--app-primary);
  background: var(--el-color-primary-light-9);
}

.stage-char {
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--app-primary);
  color: #fff;
  font-size: 20px;
  font-weight: 600;
}
</style>
