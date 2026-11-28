<script setup>
import { computed, ref } from 'vue'
import AppIcon from '../../components/AppIcon.vue'
import StageRail from './StageRail.vue'
import { materials } from '../../data/demo'

const hintLevel = ref(1)
const answer = ref('')
const submitted = ref(false)
const activeTab = ref('lesson')
const messages = ref([
  { from: 'agent', text: '我们先从一个具体问题开始：如果一个输入框要求年龄在某个范围内，你会优先检查哪些位置？', time: '09:41' },
  { from: 'student', text: '我会检查范围的最小值和最大值。', time: '09:42' },
  { from: 'agent', text: '很好，你已经找到两个关键边界。再想一步：除了边界上的值，还需要关注边界附近的哪些情况？', time: '09:42', level: 'L1' },
])

const currentStage = computed(() => submitted.value ? 'evaluate' : hintLevel.value >= 2 ? 'explain' : 'explore')
const visibleMaterials = computed(() => materials.filter(item => item.visibleToStudent))

function askHint() {
  if (hintLevel.value < 3) hintLevel.value += 1
  messages.value.push({
    from: 'agent',
    text: hintLevel.value === 2
      ? '提示 L2：把测试点分成三类来想——边界内、边界上、边界外。你能为每一类写出一个例子吗？'
      : '提示 L3：检查最小边界、最大边界，以及紧挨着它们的值；同时写清楚每个值应该被接受还是拒绝。',
    time: '09:43', level: `L${hintLevel.value}`,
  })
}

function submitAnswer() {
  if (!answer.value.trim()) return
  submitted.value = true
  messages.value.push({ from: 'student', text: answer.value, time: '09:44' })
  messages.value.push({ from: 'agent', text: '你的答案覆盖了边界上的检查点，也写出了预期结果。下一步可以尝试一个不同范围的题目，看看这个方法能否迁移。', time: '09:44', level: '判定完成' })
}

function resetLesson() {
  hintLevel.value = 1
  answer.value = ''
  submitted.value = false
  activeTab.value = 'lesson'
}
</script>

<template>
  <div class="learning-layout">
    <section class="learning-main">
      <div class="learning-heading">
        <div><span class="eyebrow dark-eyebrow">SOFTWARE TESTING · CHAPTER 02</span><h2>边界值分析</h2><p>用 5E 学习路径理解：为什么边界附近最容易发现问题？</p></div>
        <button class="outline-button" @click="resetLesson"><AppIcon name="book-open" :size="16" /> 学习笔记</button>
      </div>
      <StageRail :current-stage="currentStage" />

      <div class="lesson-tabs"><button :class="{ active: activeTab === 'lesson' }" @click="activeTab = 'lesson'">学习空间</button><button :class="{ active: activeTab === 'sources' }" @click="activeTab = 'sources'">可引用资料 <span>{{ visibleMaterials.length }}</span></button></div>

      <div v-if="activeTab === 'lesson'" class="lesson-workspace">
        <div class="concept-card card"><div class="concept-header"><span class="concept-label">当前探究问题</span><span class="soft-tag">探索阶段</span></div><h3>你会怎样设计「年龄输入」的边界测试？</h3><p>先不要查答案。试着说说你选择这些测试值的理由，学习助手会根据你的作答给下一步提示。</p><div class="concept-foot"><span><AppIcon name="clock" :size="15" /> 预计 8 分钟</span><span><AppIcon name="spark" :size="15" /> 引导模式已开启</span></div></div>

        <div class="conversation card">
          <div class="conversation-header"><div class="agent-identity"><div class="agent-avatar"><span>✦</span></div><div><strong>Studywise 引导助手</strong><small>5E · {{ currentStage === 'explore' ? '探究' : currentStage === 'explain' ? '解释' : '评价' }}阶段</small></div></div><span class="live-status"><i></i>在线</span></div>
          <div class="message-list">
            <div v-for="(message, index) in messages" :key="index" class="message-row" :class="message.from">
              <div v-if="message.from === 'agent'" class="message-avatar">✦</div>
              <div class="message-bubble"><p>{{ message.text }}</p><div class="message-meta"><span>{{ message.time }}</span><span v-if="message.level" class="message-level">{{ message.level }}</span></div></div>
            </div>
          </div>
          <div v-if="!submitted" class="hint-controls"><button class="hint-button" @click="askHint"><span class="hint-icon">✦</span> 再给我一点提示 <span class="hint-count">L{{ hintLevel }}/L3</span></button><span class="hint-note">先引导，再解释 · 不直接展示答案</span></div>
          <div v-else class="success-callout"><span class="success-icon">✓</span><div><strong>这次作答已记录</strong><p>掌握度证据已更新，下一步可以尝试迁移练习。</p></div><button class="text-button">查看证据 <AppIcon name="arrow" :size="15" /></button></div>
          <div class="answer-composer"><textarea v-model="answer" :disabled="submitted" placeholder="写下你的想法……例如：我会选择哪些值，为什么？" @keydown.meta.enter="submitAnswer" @keydown.ctrl.enter="submitAnswer"></textarea><div class="composer-footer"><span>⌘ + Enter 提交</span><button class="send-button" :disabled="submitted || !answer.trim()" @click="submitAnswer"><AppIcon name="send" :size="16" /> 提交作答</button></div></div>
        </div>
      </div>

      <div v-else class="source-grid">
        <article v-for="material in visibleMaterials" :key="material.id" class="source-card card"><div class="source-card-top"><span class="source-id">{{ material.id }}</span><span class="tag tag-teal">{{ material.typeLabel }}</span></div><h3>{{ material.title }}</h3><p>{{ material.detail }}</p><footer><span>{{ material.source }}</span><button class="icon-button"><AppIcon name="arrow" :size="15" /></button></footer></article>
      </div>
    </section>

    <aside class="learning-sidebar">
      <div class="side-card card session-card"><div class="side-card-title"><span>本次学习</span><AppIcon name="dots" :size="18" /></div><div class="session-progress"><div class="progress-ring"><span>68<small>%</small></span></div><div><strong>学习进度</strong><p>已完成 3 / 5 个阶段</p><span class="small-muted">预计还需 12 分钟</span></div></div><div class="session-divider"></div><div class="session-row"><span>连续学习</span><strong>5 天 <span class="fire">✦</span></strong></div><div class="session-row"><span>获得经验</span><strong>+125 XP</strong></div></div>
      <div class="side-card card source-side-card"><div class="side-card-title"><span>本次可引用资料</span><span class="source-count">{{ visibleMaterials.length }}</span></div><p class="side-help">只展示原理句，含答案的例题会在作答后由教师审核。</p><div v-for="material in visibleMaterials.slice(0, 3)" :key="material.id" class="source-mini"><span class="source-mini-id">{{ material.id }}</span><div><strong>{{ material.title }}</strong><small>{{ material.source }}</small></div></div><button class="full-width-button" @click="activeTab = 'sources'">查看全部资料 <AppIcon name="arrow" :size="15" /></button></div>
      <div class="privacy-note"><span>◈</span><p>你的学习记录仅用于生成掌握度证据，答案不会被直接展示。</p></div>
    </aside>
  </div>
</template>
