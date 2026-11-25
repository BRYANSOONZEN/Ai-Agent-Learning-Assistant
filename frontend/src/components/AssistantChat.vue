<script setup>
/*
 * Free chat with the study assistant (docs/模块说明/小助手-自动干预与聊天.md section 3).
 * Two stages per chapter: before the first quiz submission it only explains (no questions at all);
 * after, it also knows this student's weak points. It never gives the answer to a quiz question —
 * the backend checks every reply and replaces it with a hint if it did.
 */
import { nextTick, ref, watch } from 'vue'
import { getChat, sendChat } from '@/api/assistant'

const props = defineProps({ chapterId: { type: Number, required: true }, chapterTitle: { type: String, default: '' } })

const lines = ref([])
const text = ref('')
const sending = ref(false)
const box = ref(null)

async function load() {
  try {
    lines.value = await getChat(props.chapterId)
  } catch {
    lines.value = []
  }
  scroll()
}
watch(() => props.chapterId, load, { immediate: true })

function scroll() {
  nextTick(() => {
    if (box.value) box.value.scrollTop = box.value.scrollHeight
  })
}

async function send() {
  const msg = text.value.trim()
  if (!msg || sending.value) return
  lines.value.push({ role: 'student', content: msg })
  text.value = ''
  sending.value = true
  scroll()
  try {
    const r = await sendChat(props.chapterId, msg)
    lines.value.push({ role: 'assistant', content: r.reply })
  } catch {
    lines.value.push({ role: 'assistant', content: '我这边出了点问题，稍后再问我一次吧。' })
  } finally {
    sending.value = false
    scroll()
  }
}
</script>

<template>
  <div class="chat">
    <div ref="box" class="lines">
      <p v-if="!lines.length" class="hint">
        学「{{ chapterTitle }}」时有不懂的地方，直接问我。我会告诉你资料在哪一页、视频第几分钟，但不会直接给练习题的答案。
      </p>
      <div v-for="(l, i) in lines" :key="i" class="line" :class="l.role">
        <span>{{ l.content }}</span>
      </div>
      <div v-if="sending" class="line assistant"><span class="typing">正在想……</span></div>
    </div>
    <div class="input">
      <el-input
        v-model="text" type="textarea" :autosize="{ minRows: 1, maxRows: 4 }" maxlength="500"
        placeholder="问点什么，回车发送" @keydown.enter.exact.prevent="send"
      />
      <el-button type="primary" size="small" :loading="sending" @click="send">发送</el-button>
    </div>
  </div>
</template>

<style scoped>
.chat {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.lines {
  max-height: 340px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.hint {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.line {
  display: flex;
}
.line span {
  max-width: 85%;
  padding: 6px 10px;
  border-radius: 10px;
  font-size: 13px;
  line-height: 1.5;
  white-space: pre-wrap;
}
.line.student {
  justify-content: flex-end;
}
.line.student span {
  background: var(--el-color-primary);
  color: #fff;
}
.line.assistant span {
  background: var(--el-fill-color);
}
.typing {
  color: var(--el-text-color-secondary);
}
.input {
  display: flex;
  gap: 6px;
  align-items: flex-end;
}
</style>
