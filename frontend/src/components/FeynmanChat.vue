<script setup>
/*
 * 费曼式对话 (student): explain the knowledge point to an AI classmate who has not learned it yet
 * (name and background come from the teacher-confirmed draft). The classmate follows up (three hint levels
 * per key point, then points to the material, never gives the answer), ends by itself when everything is
 * explained or after 10 turns, recaps, fills in what was missing (only if the student really tried) and
 * suggests what to review.
 * No score. At the end the student sees a content summary (each knowledge point and its key points);
 * how each key point went is kept on the server for the study assistant, not shown here. When done, `finished` is emitted
 * (the server has already recorded the item as finished, so the next item unlocks).
 */
import { computed, nextTick, onMounted, ref } from 'vue'
import { feynmanReply, startFeynman } from '@/api/learn'

const props = defineProps({ itemId: { type: Number, required: true } })
const emit = defineEmits(['finished'])

const s = ref(null)
const text = ref('')
const sending = ref(false)
const box = ref(null)

const kp = computed(() => s.value?.kps[s.value.kpIdx])
const done = computed(() => s.value?.status === 'done')
const peer = computed(() => kp.value?.peer || '同学')

async function scroll() {
  await nextTick()
  if (box.value) box.value.scrollTop = box.value.scrollHeight
}

async function run(fn) {
  sending.value = true
  try {
    const before = s.value?.status
    s.value = await fn()
    if (s.value.status === 'done' && before !== 'done') emit('finished')
  } finally {
    sending.value = false
    scroll()
  }
}

async function send() {
  const t = text.value.trim()
  if (!t || sending.value) return
  text.value = ''
  s.value.messages.push({ role: 'student', content: t, kpIdx: s.value.kpIdx }) // show it right away
  scroll()
  await run(() => feynmanReply(s.value.id, t)).catch(() => {
    text.value = t // keep what was typed if it failed
  })
}


function onKey(e) {
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault()
    send()
  }
}

onMounted(() => run(() => startFeynman(props.itemId)))
</script>

<template>
  <div v-if="s" class="fc">
    <div class="bar">
      <span class="who"><span class="avatar">{{ peer.slice(-1) }}</span> {{ peer }} · 还没学过这一章的同学</span>
      <template v-if="kp && !done">
        <span class="sw-tag blue">知识点 {{ s.kpIdx + 1 }}/{{ s.kps.length }}：{{ kp.name }}</span>
        <span class="muted">第 {{ kp.turns }}/{{ kp.maxTurns }} 句</span>
      </template>
      <span v-else-if="done" class="sw-tag teal">对话完成</span>
    </div>

    <div ref="box" class="msgs">
      <template v-for="(m, i) in s.messages" :key="i">
        <div v-if="m.role === 'system'" class="sys">{{ m.content }}</div>
        <div v-else class="row" :class="m.role">
          <span v-if="m.role === 'ai'" class="avatar">{{ (s.kps[m.kpIdx]?.peer || '同').slice(-1) }}</span>
          <div class="bubble">{{ m.content }}</div>
        </div>
      </template>
      <div v-if="sending" class="row ai">
        <span class="avatar">{{ peer.slice(-1) }}</span>
        <div class="bubble thinking">{{ peer }}在想……</div>
      </div>
    </div>

    <div v-if="!done" class="input">
      <el-input
        v-model="text"
        type="textarea"
        :autosize="{ minRows: 2, maxRows: 5 }"
        maxlength="500"
        :placeholder="`用你自己的话讲给${peer}听（Enter 发送，Shift + Enter 换行）`"
        :disabled="sending"
        @keydown="onKey"
      />
      <el-button type="primary" :loading="sending" :disabled="!text.trim()" @click="send">发送</el-button>
    </div>
    <div v-else class="done">对话完成，下一项已解锁。</div>
  </div>
</template>

<style scoped>
.fc {
  display: grid;
  gap: 10px;
}
.bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  font-size: 13px;
}
.who {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 500;
}
.avatar {
  flex: none;
  display: inline-grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #eef0fe;
  color: #3946c9;
  font-size: 13px;
  font-weight: 600;
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.msgs {
  display: grid;
  gap: 10px;
  max-height: 480px;
  overflow-y: auto;
  padding: 12px;
  border-radius: 10px;
  background: var(--el-fill-color-lighter);
}
.row {
  display: flex;
  gap: 8px;
  align-items: flex-start;
}
.row.student {
  justify-content: flex-end;
}
.bubble {
  max-width: 78%;
  padding: 8px 12px;
  border-radius: 12px;
  background: #fff;
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-line;
}
.row.student .bubble {
  background: #5b6ef5;
  color: #fff;
}
.thinking {
  color: var(--el-text-color-secondary);
}
.sys {
  justify-self: center;
  max-width: 92%;
  padding: 8px 12px;
  border-radius: 8px;
  background: #fff;
  border: 1px dashed var(--el-border-color);
  color: var(--el-text-color-regular);
  font-size: 12px;
  line-height: 1.7;
  white-space: pre-line;
}
.input {
  display: flex;
  gap: 8px;
  align-items: flex-end;
}
.done {
  color: #2b9c72;
  font-size: 13px;
}
</style>
