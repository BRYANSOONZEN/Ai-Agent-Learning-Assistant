<script setup>
/*
 * AI consolidation dialogue for one chapter, or for one AI 对话 item of a chapter.
 * Three states: opening (topic list + start) -> chat -> summary.
 * The backend returns the whole dialogue after every action, so this component only renders it.
 *
 * Used by: the AI 对话 items of a chapter — students in LearnCourse, teachers in CourseBuilder
 * (preview with the real AI); and the standalone demo page DialogueView (whole chapter 1).
 */
import { computed, nextTick, onMounted, ref } from 'vue'
import {
  getOpening,
  startDialogue,
  getDialogue,
  sendMessage,
  sendStuck,
  resumeDialogue,
} from '@/api/dialogue'

const props = defineProps({
  chapterId: { type: Number, required: true },
  itemId: { type: Number, default: undefined }, // only this AI 对话 item's topics; none = whole chapter
  compact: { type: Boolean, default: false }, // smaller chat box, for use inside another page
})
const emit = defineEmits(['finished']) // all topics done: the chapter page marks the item as learned

// one saved session per chapter / item, so a refresh continues where the student was
const storageKey = computed(
  () => `se-platform-dialogue-session:${props.chapterId}:${props.itemId ?? 'chapter'}`,
)

const opening = ref(null)
const dialogue = ref(null)
const input = ref('')
const pendingText = ref('') // student's message shown while waiting for the reply
const busy = ref(false)
const listEl = ref(null)

const resultLabels = {
  SELF_CORRECT: { text: '自己答对', type: 'success' },
  HINT_CORRECT: { text: '提示后答对', type: 'success' },
  REVEALED_APPLIED: { text: '讲解后会用', type: 'warning' },
  REVEALED: { text: '讲解后仍不会', type: 'danger' },
  SKIPPED_IRRELEVANT: { text: '已跳过', type: 'info' },
}

const status = computed(() => dialogue.value?.status)
const canTalk = computed(() => status.value === 'OPEN' && !busy.value)

function remember(view) {
  dialogue.value = view
  if (view.status === 'FINISHED') emit('finished')
  try {
    localStorage.setItem(storageKey.value, String(view.sessionId))
  } catch {
    /* storage unavailable: refresh will just show the opening page */
  }
  scrollToBottom()
}

async function scrollToBottom() {
  await nextTick()
  if (listEl.value) listEl.value.scrollTop = listEl.value.scrollHeight
}

async function run(action) {
  busy.value = true
  try {
    remember(await action())
  } finally {
    busy.value = false
    pendingText.value = ''
  }
}

function start() {
  run(() => startDialogue(props.chapterId, props.itemId))
}

function send() {
  const text = input.value.trim()
  if (!text || !canTalk.value) return
  pendingText.value = text
  input.value = ''
  scrollToBottom()
  run(() => sendMessage(dialogue.value.sessionId, text))
}

function stuck() {
  if (!canTalk.value) return
  pendingText.value = '（我卡住了）'
  run(() => sendStuck(dialogue.value.sessionId))
}

function resume() {
  run(() => resumeDialogue(dialogue.value.sessionId))
}

async function restart() {
  try {
    localStorage.removeItem(storageKey.value)
  } catch {
    /* ignore */
  }
  dialogue.value = null
  opening.value = await getOpening(props.chapterId, props.itemId) // topics may have changed meanwhile
}

function onKeydown(e) {
  // Enter sends, Shift+Enter makes a new line
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault()
    send()
  }
}

onMounted(async () => {
  opening.value = await getOpening(props.chapterId, props.itemId)
  let saved = null
  try {
    saved = localStorage.getItem(storageKey.value)
  } catch {
    /* ignore */
  }
  if (saved) {
    try {
      remember(await getDialogue(saved))
    } catch {
      restart() // backend restarted (in-memory DB), the old session is gone
    }
  }
})
</script>

<template>
  <div class="ai-dialogue" :class="{ compact }">
    <!-- 开场页 -->
    <div v-if="!dialogue" class="app-card opening">
      <h3>本次对话会讨论 {{ opening?.topicTitles.length || 0 }} 个话题</h3>
      <ol>
        <li v-for="t in opening?.topicTitles || []" :key="t">{{ t }}</li>
      </ol>
      <p class="muted">
        用自己的话回答就好，不需要很完整，说错也没关系。AI 会一步步提示，不会直接给答案；想不出来可以点"我卡住了"。
      </p>
      <el-button
        type="primary"
        size="large"
        :loading="busy"
        :disabled="!opening?.topicTitles.length"
        @click="start"
      >
        开始对话
      </el-button>
    </div>

    <!-- 对话 -->
    <div v-else class="layout">
      <aside class="app-card progress">
        <div class="progress-head">
          <h4>话题进度</h4>
          <el-button link type="primary" icon="RefreshRight" @click="restart">重新开始</el-button>
        </div>
        <div
          v-for="(t, i) in dialogue.topics"
          :key="i"
          class="topic"
          :class="{ current: i === dialogue.currentIdx && status !== 'FINISHED' }"
        >
          <span class="num">{{ i + 1 }}</span>
          <span class="title">{{ t.title }}</span>
          <el-tag v-if="t.result" size="small" :type="resultLabels[t.result].type">
            {{ resultLabels[t.result].text }}
          </el-tag>
          <el-tag v-else-if="i === dialogue.currentIdx && status !== 'FINISHED'" size="small">
            {{ t.transfer ? '换个情境试试' : '进行中' }}
          </el-tag>
        </div>
      </aside>

      <section class="app-card chat">
        <!-- who is talking: the assistant, and whether it is still going -->
        <div class="chat-head">
          <div class="agent">
            <span class="agent-avatar">✦</span>
            <div>
              <strong>AI 学习助手</strong>
              <small>先自己想，再看讲解 · 不直接给答案</small>
            </div>
          </div>
          <span class="live" :class="{ off: status !== 'OPEN' }">
            <i />{{ status === 'OPEN' ? (busy ? '正在思考' : '对话中') : status === 'PAUSED' ? '已暂停' : '已结束' }}
          </span>
        </div>

        <div ref="listEl" class="messages">
          <div
            v-for="(m, i) in dialogue.messages"
            :key="i"
            class="msg"
            :class="m.role === 'STUDENT' ? 'me' : 'ai'"
          >
            <span v-if="m.role !== 'STUDENT'" class="msg-avatar">✦</span>
            <div class="bubble">{{ m.content }}</div>
          </div>
          <div v-if="pendingText" class="msg me">
            <div class="bubble">{{ pendingText }}</div>
          </div>
          <div v-if="busy && pendingText" class="msg ai">
            <span class="msg-avatar">✦</span>
            <div class="bubble thinking">正在思考…</div>
          </div>
        </div>

        <div v-if="status === 'OPEN'" class="composer-wrap">
          <div class="hint-row">
            <button type="button" class="hint-button" :disabled="!canTalk" @click="stuck">
              <span>✦</span> 我卡住了，给点提示
            </button>
            <span class="hint-note">答错了 AI 会一步步提示；试过变式题后才显示完整讲解</span>
          </div>
          <div class="composer">
            <textarea
              v-model="input"
              rows="2"
              placeholder="写下你的想法……用自己的话回答就好"
              :disabled="busy"
              @keydown="onKeydown"
            />
            <div class="composer-foot">
              <span>Enter 发送 · Shift + Enter 换行</span>
              <el-button type="primary" size="small" :loading="busy" :disabled="!canTalk || !input.trim()" @click="send">
                <el-icon class="el-icon--left"><Promotion /></el-icon>发送
              </el-button>
            </div>
          </div>
        </div>
        <div v-else-if="status === 'PAUSED'" class="composer paused">
          <span>对话已暂停</span>
          <el-button type="primary" :loading="busy" @click="resume">继续对话</el-button>
        </div>
      </section>
    </div>

    <!-- 总结 -->
    <div v-if="dialogue?.summary" class="app-card summary">
      <h3>本次对话总结</h3>
      <p class="big">完成 {{ dialogue.summary.completed }} / {{ dialogue.summary.total }} 个话题</p>
      <div v-if="dialogue.summary.good.length" class="group">
        <h4>做得好的</h4>
        <ul>
          <li v-for="g in dialogue.summary.good" :key="g">{{ g }}</li>
        </ul>
      </div>
      <div v-if="dialogue.summary.improve.length" class="group">
        <h4>需要再看看的</h4>
        <ul>
          <li v-for="g in dialogue.summary.improve" :key="g">{{ g }}</li>
        </ul>
      </div>
      <div v-if="dialogue.summary.skipped.length" class="group">
        <h4>没有作答的</h4>
        <ul>
          <li v-for="g in dialogue.summary.skipped" :key="g">{{ g }}</li>
        </ul>
      </div>
    </div>
  </div>
</template>

<style scoped>
.opening h3 {
  margin: 0 0 8px;
}
.opening ol {
  margin: 0 0 12px;
  padding-left: 20px;
  line-height: 2;
}
.muted {
  color: var(--el-text-color-secondary);
  margin-bottom: 16px;
}
.ai-dialogue {
  container-type: inline-size; /* stack by the space it gets: the learning page's middle column can be narrow */
}
.layout {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  gap: var(--app-space-md);
  align-items: start;
}
@container (max-width: 640px) {
  .layout {
    grid-template-columns: minmax(0, 1fr);
  }
}
/* the global .app-card + .app-card top margin is for stacked cards; these sit side by side */
.layout > .app-card {
  margin-top: 0;
}
.progress-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.progress-head h4 {
  margin: 0;
}
.topic {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px;
  border-radius: 6px;
  flex-wrap: wrap;
}
.topic.current {
  background: var(--el-color-primary-light-9);
}
.topic .num {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--el-fill-color);
  font-size: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.topic .title {
  flex: 1;
  min-width: 0;
}
.chat {
  display: flex;
  flex-direction: column;
  height: min(72vh, 660px);
  padding: 0;
  overflow: hidden;
}
.compact .chat {
  height: min(64vh, 540px);
}
/* inside another card: drop the card shadow so it does not look boxed twice */
.compact .app-card {
  box-shadow: none;
}
.chat-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 15px 20px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.agent {
  display: flex;
  align-items: center;
  gap: 10px;
}
.agent-avatar,
.msg-avatar {
  display: grid;
  place-items: center;
  color: #6578ed;
  background: #eef0ff;
}
.agent-avatar {
  width: 32px;
  height: 32px;
  border-radius: 9px;
}
.agent strong,
.agent small {
  display: block;
}
.agent strong {
  font-size: 13px;
}
.agent small {
  margin-top: 2px;
  color: var(--app-text-muted);
  font-size: 11px;
}
.live {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #38a48f;
  font-size: 12px;
}
.live i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #50c3a9;
  box-shadow: 0 0 0 3px #e4f7f2;
}
.live.off {
  color: var(--app-text-muted);
}
.live.off i {
  background: #b9c5d0;
  box-shadow: 0 0 0 3px #eef2f6;
}
.messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.msg {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  max-width: 84%;
}
.msg.me {
  margin-left: auto;
  justify-content: flex-end;
}
.msg-avatar {
  flex: none;
  width: 26px;
  height: 26px;
  border-radius: 7px;
  font-size: 12px;
}
.bubble {
  padding: 10px 13px;
  border-radius: 4px 12px 12px 12px;
  background: #f2f6fa;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}
.me .bubble {
  border-radius: 12px 4px 12px 12px;
  background: #5f76e9;
  color: #fff;
}
.thinking {
  color: var(--app-text-muted);
  font-style: italic;
}
.composer-wrap {
  padding: 4px 16px 16px;
}
.hint-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  padding: 6px 4px 12px;
}
.hint-button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 31px;
  padding: 0 11px;
  border: 1px solid #dedcff;
  border-radius: 7px;
  background: #f8f7ff;
  color: #6973d1;
  font: inherit;
  font-size: 12px;
  cursor: pointer;
}
.hint-button:disabled {
  opacity: 0.5;
  cursor: default;
}
.hint-note {
  color: var(--app-text-muted);
  font-size: 11px;
}
.composer {
  border: 1px solid #dfe7ee;
  border-radius: 10px;
  overflow: hidden;
}
.composer:focus-within {
  border-color: var(--el-color-primary-light-5);
}
.composer textarea {
  display: block;
  width: 100%;
  min-height: 60px;
  padding: 12px;
  border: 0;
  outline: 0;
  resize: vertical;
  font: inherit;
  color: var(--app-text);
  background: transparent;
}
.composer textarea::placeholder {
  color: #b4bfca;
}
.composer-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 9px 8px 12px;
  color: #abb6c1;
  font-size: 11px;
}
.composer.paused {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 0 16px 16px;
  padding: 12px;
}
.summary {
  margin-top: var(--app-space-md);
}
.summary h3 {
  margin: 0 0 8px;
}
.summary .big {
  font-size: 18px;
  font-weight: 600;
  margin: 0 0 12px;
}
.summary .group h4 {
  margin: 12px 0 4px;
}
.summary ul {
  margin: 0;
  padding-left: 20px;
  line-height: 1.8;
}
</style>
