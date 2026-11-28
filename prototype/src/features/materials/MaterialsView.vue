<script setup>
import { ref } from 'vue'
import AppIcon from '../../components/AppIcon.vue'
import { materials } from '../../data/demo'
const filter = ref('all')
const filters = [{ id: 'all', label: '全部资料' }, { id: 'principle', label: '原理句' }, { id: 'example', label: '例题答案' }]
</script>

<template>
  <div class="view-stack">
    <section class="page-intro"><div><span class="eyebrow dark-eyebrow">KNOWLEDGE BASE · M1–M4</span><h2>课程资料</h2><p>将课件拆分为可核对的原理句与例题答案，控制学生端的可见范围。</p></div><button class="button button-primary"><AppIcon name="plus" :size="16" /> 添加资料</button></section>
    <div class="filter-row"><button v-for="item in filters" :key="item.id" :class="{ active: filter === item.id }" @click="filter = item.id">{{ item.label }} <span>{{ item.id === 'all' ? materials.length : materials.filter(m => m.type === item.id).length }}</span></button><div class="filter-spacer"></div><span class="library-status"><i></i> 资料库已同步</span></div>
    <section class="material-list"><article v-for="material in materials.filter(m => filter === 'all' || m.type === filter)" :key="material.id" class="material-row card"><div class="material-icon" :class="material.type"><AppIcon :name="material.type === 'principle' ? 'book-open' : 'review'" :size="21" /></div><div class="material-main"><div class="material-title"><span class="source-id">{{ material.id }}</span><h3>{{ material.title }}</h3><span class="tag" :class="material.type === 'principle' ? 'tag-teal' : 'tag-orange'">{{ material.typeLabel }}</span></div><p>{{ material.detail }}</p><div class="material-meta"><span>{{ material.source }}</span><span>最后确认 · 今天 09:20</span></div></div><div class="material-access"><span :class="material.visibleToStudent ? 'access-open' : 'access-locked'">{{ material.visibleToStudent ? '学生可引用' : '仅教师可见' }}</span><button class="icon-button"><AppIcon name="dots" :size="18" /></button></div></article></section>
    <section class="split-note"><div class="split-note-icon">✦</div><div><strong>为什么要拆分资料？</strong><p>第二课探查发现，直接指向含具体答案的例题会破坏“先引导、不直接给答案”的目标。学生端只接收原理句，教师端保留完整出处。</p></div></section>
  </div>
</template>
