<script setup>
/*
 * Under a chapter document: finished ⇔ read for at least 30 seconds AND the student clicks 我已读完.
 * Only time with the page in front counts (switching tabs pauses it). Seconds go to the server every 10 s;
 * the server caps them by real elapsed time and refuses the button before 30 s (ProgressService).
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { finishReading, reportReading } from '@/api/learn'

const props = defineProps({
  itemId: { type: Number, required: true },
  progress: { type: Object, default: null }, // server progress: { readSec, completed, readMinSec }
})
const emit = defineEmits(['completed'])

const MIN = props.progress?.readMinSec ?? 30
const readSec = ref(props.progress?.readSec || 0)
const completed = ref(!!props.progress?.completed)
const saving = ref(false)
let unsent = 0
let timer = null

const left = computed(() => Math.max(0, MIN - readSec.value))
const pct = computed(() => Math.min(100, Math.round((readSec.value / MIN) * 100)))

async function flush() {
  if (unsent <= 0) return
  const n = unsent
  unsent = 0
  try {
    await reportReading(props.itemId, n)
  } catch {
    /* the request helper already showed the error */
  }
}

async function done() {
  saving.value = true
  try {
    await flush()
    const res = await finishReading(props.itemId)
    readSec.value = Math.max(readSec.value, res.readSec)
    completed.value = true
    emit('completed')
  } catch {
    /* refused (for example not 30 s yet on the server): the message is already shown */
  } finally {
    saving.value = false
  }
}

function onVisibility() {
  if (document.hidden) flush()
}

onMounted(() => {
  timer = setInterval(() => {
    if (document.hidden || completed.value) return
    readSec.value++
    unsent++
    if (unsent >= 10) flush()
  }, 1000)
  document.addEventListener('visibilitychange', onVisibility)
})
onBeforeUnmount(() => {
  clearInterval(timer)
  document.removeEventListener('visibilitychange', onVisibility)
  flush()
})
</script>

<template>
  <div class="reading-timer">
    <template v-if="completed">
      <span class="sw-tag teal"><el-icon><CircleCheckFilled /></el-icon>已读完，下一项已解锁</span>
    </template>
    <template v-else>
      <div class="line">
        <span>已读 <b>{{ Math.min(readSec, MIN) }}</b> / {{ MIN }} 秒</span>
        <span class="sw-progress bar"><span :style="{ width: pct + '%' }" /></span>
        <el-button type="primary" :disabled="left > 0" :loading="saving" @click="done">
          {{ left > 0 ? `还需 ${left} 秒` : '我已读完' }}
        </el-button>
      </div>
      <span class="muted">至少读 {{ MIN }} 秒，再点“我已读完”，才能解锁下一项；切到别的页面时暂停计时</span>
    </template>
  </div>
</template>

<style scoped>
.reading-timer {
  display: grid;
  gap: 6px;
  margin-top: 16px;
  padding: 12px 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
}
.line {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  font-size: 13px;
}
.bar {
  flex: 1;
  min-width: 120px;
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
