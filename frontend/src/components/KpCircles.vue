<script setup>
/*
 * Student view of a chapter's knowledge points: one numbered circle per point, in teaching order.
 * No connecting lines: when the circles wrap onto several rows, lines made it look as if some points were related
 * and others not; the number alone shows the order.
 * Key points (重点) are bigger; once the student has practised, each circle takes their colour (green / orange / red).
 * Clicking a circle shows its one-line summary and two shortcuts:
 * the main place in a video where it is taught (start time) and the courseware pages.
 * The teacher sees the full table (KpMap) instead, to check where each point was recognized from.
 */
import { computed, ref } from 'vue'

const props = defineProps({
  map: { type: Object, required: true }, // same data as KpMap: { columns, rows }
  isLocked: { type: Function, default: () => false }, // itemId -> true if the student cannot open it yet
})
const emit = defineEmits(['jump'])

const picked = ref(0)
const kp = computed(() => props.map.rows[picked.value] || null)
// colours only when the student has practised at least one point (otherwise every circle stays plain)
const colored = computed(() => props.map.rows.some((r) => r.color && r.color !== 'none'))

function mmss(sec) {
  const s = Math.max(0, Math.floor(sec || 0))
  return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`
}

// the video where this point is taught the most (most segments), from its earliest segment
const video = computed(() => {
  if (!kp.value) return null
  let best = null
  for (const c of props.map.columns.filter((c) => c.type === 'video')) {
    const spots = (kp.value.spots[c.itemId] || []).filter((s) => s.startSec != null)
    if (spots.length && (!best || spots.length > best.count)) {
      best = { col: c, count: spots.length, startSec: Math.min(...spots.map((s) => s.startSec)) }
    }
  }
  return best
})

// courseware pages, shown as 第 12–16 页
const doc = computed(() => {
  if (!kp.value) return null
  for (const c of props.map.columns.filter((c) => c.type !== 'video')) {
    const pages = [...new Set((kp.value.spots[c.itemId] || []).filter((s) => s.page != null).map((s) => s.page))]
    if (!pages.length) continue
    pages.sort((a, b) => a - b)
    const text = pages[0] === pages[pages.length - 1] ? `${pages[0]}` : `${pages[0]}–${pages[pages.length - 1]}`
    return { col: c, page: pages[0], text: `第 ${text} 页` }
  }
  return null
})
</script>

<template>
  <div class="kp-circles">
    <div class="legend">
      <span><i class="dot big" />重点</span>
      <span><i class="dot" />一般</span>
      <span>数字是讲课的先后顺序，点圆圈看说明</span>
      <template v-if="colored">
        <span><i class="dot fill green" />第一次就掌握</span>
        <span><i class="dot fill orange" />补回来了</span>
        <span><i class="dot fill red" />还没掌握</span>
        <span><i class="dot" />还没练</span>
      </template>
    </div>

    <div class="track">
      <div v-for="(r, i) in map.rows" :key="r.kpId" class="stop">
        <span class="num">{{ i + 1 }}</span>
        <button
          type="button"
          class="circle"
          :class="[{ big: r.importance === '重点', on: i === picked }, r.color && r.color !== 'none' ? 'c-' + r.color : '']"
          :title="r.name"
          @click="picked = i"
        >
          <span class="name">{{ r.name }}</span>
        </button>
      </div>
    </div>

    <div v-if="kp" class="card">
      <div class="title">
        {{ picked + 1 }}. {{ kp.name }}
        <span v-if="kp.importance === '重点'" class="sw-tag orange">重点</span>
      </div>
      <p v-if="kp.summary" class="summary">{{ kp.summary }}</p>
      <div class="btns">
        <el-button
          v-if="video"
          :disabled="isLocked(video.col.itemId)"
          @click="emit('jump', { itemId: video.col.itemId, startSec: video.startSec })"
        >
          <el-icon class="el-icon--left"><component :is="isLocked(video.col.itemId) ? 'Lock' : 'VideoPlay'" /></el-icon>
          {{ video.col.title }} · 从 {{ mmss(video.startSec) }} 看
        </el-button>
        <el-button
          v-if="doc"
          :disabled="isLocked(doc.col.itemId)"
          @click="emit('jump', { itemId: doc.col.itemId, page: doc.page })"
        >
          <el-icon class="el-icon--left"><component :is="isLocked(doc.col.itemId) ? 'Lock' : 'Document'" /></el-icon>
          课件{{ doc.text }}
        </el-button>
      </div>
      <p v-if="(video && isLocked(video.col.itemId)) || (doc && isLocked(doc.col.itemId))" class="hint">
        带锁的还没解锁，先按顺序学完前面的内容
      </p>
    </div>
  </div>
</template>

<style scoped>
.kp-circles {
  display: grid;
  gap: 12px;
}
.legend {
  display: flex;
  gap: 14px;
  flex-wrap: wrap;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  margin-right: 4px;
  border: 1.5px solid #5b6ef5;
  border-radius: 50%;
  vertical-align: -1px;
}
.dot.big {
  width: 14px;
  height: 14px;
  vertical-align: -3px;
}
.track {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 14px;
}
.stop {
  position: relative;
}
.num {
  position: absolute;
  top: -7px;
  left: -7px;
  z-index: 1;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #5b6ef5;
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  line-height: 20px;
  text-align: center;
}
.circle {
  flex: none;
  width: 72px;
  height: 72px;
  padding: 8px;
  border: 1.5px solid #5b6ef5;
  border-radius: 50%;
  background: #fff;
  color: #3946c9;
  font-size: 12px;
  line-height: 1.3;
  cursor: pointer;
  transition: transform 0.15s, background 0.15s;
}
/* the student's own result on this point (same colours as practice, the assistant and the profile) */
.circle.c-green {
  border-color: #36aa95;
  background: #e6f2ef;
  color: #2f7d6d;
}
.circle.c-orange {
  border-color: #e99a4e;
  background: #fbf1e4;
  color: #b8691b;
}
.circle.c-red {
  border-color: #e5675a;
  background: #f8e7e5;
  color: #b4433a;
}
.dot.fill.green {
  border-color: #36aa95;
  background: #e6f2ef;
}
.dot.fill.orange {
  border-color: #e99a4e;
  background: #fbf1e4;
}
.dot.fill.red {
  border-color: #e5675a;
  background: #f8e7e5;
}
.circle.big {
  width: 90px;
  height: 90px;
  font-weight: 600;
}
.circle:hover {
  transform: scale(1.05);
}
.circle.on {
  background: #eef0fe;
  box-shadow: 0 0 0 3px #dfe3fd;
}
.name {
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.card {
  padding: 12px 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
}
.title {
  font-weight: 600;
}
.summary {
  margin: 6px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.btns {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 10px;
}
.btns .el-button + .el-button {
  margin-left: 0;
}
.hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
