<script setup>
/*
 * A chapter video that counts what was really watched.
 *   P = Σ (e − s) ÷ duration, over the merged ranges [s, e] that actually played.
 *   P ≥ 90%  ->  finished, the next item unlocks.
 * Dragging the bar skips time, so skipped seconds never become a range; watching a part again adds nothing.
 * Ranges go to the server every 10 s while playing, and on pause / seek / leaving the page.
 * The server merges them, checks them against real elapsed time and decides "finished" (ProgressService).
 * Reopening the video continues from the last position.
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { reportVideo } from '@/api/learn'

const props = defineProps({
  itemId: { type: Number, required: true },
  src: { type: String, required: true },
  progress: { type: Object, default: null }, // server progress: { ranges, lastPosition, durationSec, completed, videoPass }
  startAt: { type: Number, default: null }, // opened from the 知识点地图: start here instead of the last position
})
const emit = defineEmits(['completed'])

const PASS = props.progress?.videoPass ?? 0.9
const MAX_RATE = 2

const el = ref(null)
const duration = ref(props.progress?.durationSec || 0)
const ranges = ref((props.progress?.ranges || []).map((r) => [r[0], r[1]]))
const completed = ref(!!props.progress?.completed)

let segStart = null // where the range being played now started
let last = null // the last playback time seen in this range
let dirty = false // ranges changed since the last report
let timer = null

function merge(list) {
  const sorted = [...list].sort((a, b) => a[0] - b[0])
  const out = []
  for (const [s, e] of sorted) {
    const prev = out[out.length - 1]
    if (prev && s <= prev[1] + 0.5) prev[1] = Math.max(prev[1], e)
    else out.push([s, e])
  }
  return out
}

const watched = computed(() => ranges.value.reduce((sum, [s, e]) => sum + (e - s), 0))
const pct = computed(() => (duration.value ? Math.min(1, watched.value / duration.value) : 0))
const pctText = computed(() => Math.floor(pct.value * 100))

// close the range being played into `ranges`; keep going from here if still playing
function commit() {
  if (segStart !== null && last !== null && last > segStart) {
    ranges.value = merge([...ranges.value, [segStart, last]])
    dirty = true
  }
  segStart = last
}

function startRange() {
  const v = el.value
  // `playing` also fires after buffering mid-range: keep the range that is already open
  if (v && !v.paused && segStart === null) segStart = last = v.currentTime
}

function resume() {
  if (props.startAt != null && props.startAt < duration.value) {
    el.value.currentTime = props.startAt
    return
  }
  const p = props.progress?.lastPosition
  el.value.currentTime = p && p < duration.value - 2 ? p : 0
}

let probing = false // webm recorded in a browser often has no duration until the end is read
function onLoaded() {
  const v = el.value
  if (Number.isFinite(v.duration)) {
    duration.value = v.duration
    resume()
  } else {
    probing = true
    v.currentTime = 1e101 // jumping past the end makes the browser work the duration out
  }
}
function onDuration() {
  const v = el.value
  if (!probing || !Number.isFinite(v.duration)) return
  probing = false
  duration.value = v.duration
  resume()
}

function onTime() {
  const v = el.value
  if (segStart === null || v.paused) return
  const t = v.currentTime
  // a normal step forward while playing; anything else (a jump) starts a new range
  if (t < last || t - last > 1.5 * v.playbackRate + 1) {
    commit()
    segStart = t
  }
  last = t
}

function onSeeking() {
  commit()
  segStart = last = null
}

function onRate() {
  if (el.value.playbackRate > MAX_RATE) el.value.playbackRate = MAX_RATE
}

function onStop(e) {
  // the last timeupdate can come a fraction of a second before the end: playing to the end covers it
  if (e?.type === 'ended' && segStart !== null && Number.isFinite(el.value.duration)) last = el.value.duration
  commit()
  segStart = last = null
  flush()
}

async function flush() {
  const v = el.value
  if (v && !v.paused) commit()
  if (!dirty || !duration.value) return
  dirty = false
  try {
    const res = await reportVideo(props.itemId, {
      duration: duration.value,
      ranges: ranges.value,
      position: v ? v.currentTime : null,
    })
    ranges.value = merge([...ranges.value, ...(res.ranges || [])])
    if (res.completed && !completed.value) {
      completed.value = true
      emit('completed')
    }
  } catch {
    dirty = true // try again next time; the request helper already showed the error
  }
}

function onVisibility() {
  if (document.hidden) flush()
}

onMounted(() => {
  timer = setInterval(() => {
    if (el.value && !el.value.paused) flush()
  }, 10000)
  document.addEventListener('visibilitychange', onVisibility)
})
onBeforeUnmount(() => {
  clearInterval(timer)
  document.removeEventListener('visibilitychange', onVisibility)
  flush()
})
</script>

<template>
  <div class="video-block">
    <video
      ref="el"
      :src="src"
      controls
      class="player"
      @loadedmetadata="onLoaded"
      @durationchange="onDuration"
      @play="startRange"
      @playing="startRange"
      @seeked="startRange"
      @timeupdate="onTime"
      @seeking="onSeeking"
      @pause="onStop"
      @ended="onStop"
      @ratechange="onRate"
    />
    <div class="watch">
      <div class="track" :aria-label="`已看 ${pctText}%`">
        <span
          v-for="(r, i) in ranges"
          :key="i"
          class="seen"
          :style="{ left: (r[0] / (duration || 1)) * 100 + '%', width: ((r[1] - r[0]) / (duration || 1)) * 100 + '%' }"
        />
        <span class="pass" :style="{ left: PASS * 100 + '%' }" />
      </div>
      <div class="watch-text">
        <span v-if="completed" class="sw-tag teal">
          <el-icon><CircleCheckFilled /></el-icon>已看完 {{ pctText }}%，下一项已解锁
        </span>
        <template v-else>
          <span>已看 <b>{{ pctText }}%</b>，看到 {{ PASS * 100 }}% 解锁下一项</span>
          <span class="muted">只算真正播放过的部分，拖进度条跳过的不算</span>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
.video-block {
  display: grid;
  gap: 10px;
}
.player {
  width: 100%;
  max-height: 520px;
  border-radius: 10px;
  background: #000;
}
.track {
  position: relative;
  height: 8px;
  border-radius: 4px;
  background: #e8edf3;
  overflow: hidden;
}
.seen {
  position: absolute;
  top: 0;
  bottom: 0;
  background: #2bb3a3;
}
.pass {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 2px;
  background: #e0a03c;
}
.watch {
  display: grid;
  gap: 6px;
}
.watch-text {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
  align-items: center;
  font-size: 13px;
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
