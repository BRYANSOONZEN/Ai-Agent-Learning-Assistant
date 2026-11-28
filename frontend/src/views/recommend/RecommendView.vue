<script setup>
/*
 * 学习推荐 = 小助手的延伸（backend: RecommendService）。要先经过小助手：
 *   - 系统补充：在小助手里点“出补练题”，大模型按自己的错题出题，点“去查看”到这里做；一律保存
 *     （知识点变色、补回来要有记录）；
 *   - 拓展：在小助手里点“来点更难的”，到这里做；做完自己选保存不保存，不保存的不再显示；
 *   - 老师推送：在小助手里点“接收”后保存到这里，在这里做老师的新题。
 * 做题用小助手原来的提交接口（RemedyBox / PushBox），判分和“补回来”的规则只有一套。
 * 练习里的题不在这里。
 */
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import RemedyBox from '@/components/RemedyBox.vue'
import PushBox from '@/components/PushBox.vue'
import { getRecommendations, saveExtend } from '@/api/recommend'

const route = useRoute()
const router = useRouter()

const data = ref(null)
const loading = ref(false)
const tab = ref(['remedy', 'extend', 'teacher'].includes(route.query.kind) ? route.query.kind : 'remedy')
const decide = ref({}) // 拓展刚做完、还没选保存不保存的：setId -> true

const TABS = [
  { key: 'remedy', label: '系统补充', hint: '小助手按你的错题出的补练题，全部保存：知识点补回来（变色）要靠这些记录。' },
  { key: 'extend', label: '拓展', hint: '掌握得好的知识点，小助手出的更难的题。做完你自己决定保存不保存。' },
  { key: 'teacher', label: '老师推送', hint: '老师看了全班的练习后补充的讲解和新题，在小助手里接收后保存到这里。' },
]
const countOf = (k) => (data.value?.sets || []).filter((s) => s.kind === k).length
const shown = computed(() => (data.value?.sets || []).filter((s) => s.kind === tab.value))
const hint = computed(() => TABS.find((t) => t.key === tab.value)?.hint)

async function load() {
  loading.value = true
  try {
    data.value = await getRecommendations()
  } finally {
    loading.value = false
  }
}
load()
watch(() => route.query.kind, (k) => {
  if (['remedy', 'extend', 'teacher'].includes(k)) tab.value = k
})

const focusKp = computed(() => Number(route.query.kp) || null)

function when(t) {
  return t ? String(t).replace('T', ' ').slice(5, 16) : ''
}
function statusTag(s) {
  if (s.status === 'open') return { type: 'warning', text: s.kind === 'teacher' ? '还没做' : '还没做完' }
  const pct = s.score == null ? '' : `，得分 ${Math.round(s.score * 100)}%`
  return { type: 'success', text: '做完了' + pct }
}
function title(s) {
  if (s.kind === 'remedy') return `补练第 ${s.round} 轮`
  if (s.kind === 'extend') return `拓展 · ${s.assessLabel || ''}`
  return `老师补充讲解 · ${s.assessLabel || ''}`
}
function tag(q) {
  if (q.correct) return { type: 'success', text: '答对' }
  if (q.score > 0) return { type: 'warning', text: `部分对，得 ${q.score} 分` }
  return { type: 'danger', text: '答错' }
}
const LETTERS = 'ABCDEF'

// 做完一组：补练、老师推送直接刷新；拓展先让学生选保存不保存
function finished(s) {
  if (s.kind === 'extend') decide.value = { ...decide.value, [s.id]: true }
  else load()
}
async function keep(s, saved) {
  await saveExtend(s.id, saved)
  ElMessage.success(saved ? '已保存到“拓展”' : '不保存，这组题不再显示')
  decide.value = { ...decide.value, [s.id]: false }
  load()
}
function openAssistant() {
  window.dispatchEvent(new Event('assistant-open'))
}
</script>

<template>
  <div v-loading="loading" class="recommend">
    <PageHeader
      title="学习推荐"
      description="小助手的延伸：小助手给你出的补练题、拓展题，以及老师推送给你的讲解和新题，都保存在这里，在这里做、回看。"
    />

    <div class="tabs">
      <button v-for="t in TABS" :key="t.key" type="button" :class="{ active: tab === t.key }" @click="tab = t.key">
        {{ t.label }}<span class="count">{{ countOf(t.key) }}</span>
      </button>
    </div>
    <p class="hint">{{ hint }}</p>

    <el-empty v-if="data && !shown.length" :description="tab === 'teacher' ? '还没有接收老师的推送' : '这里还没有题'">
      <p class="muted">要先经过小助手：交卷后打开右下角的小助手，{{ tab === 'remedy' ? '在没掌握的知识点下点“出补练题”' : tab === 'extend' ? '在掌握得好的知识点下点“来点更难的”' : '在“老师给你的”里点“接收”' }}。</p>
      <el-button type="primary" @click="openAssistant">打开小助手</el-button>
    </el-empty>

    <article v-for="s in shown" :key="s.kind + s.id" class="set app-card" :class="{ focus: focusKp === s.knowledgePointId && s.status === 'open' }">
      <header class="set-head">
        <div>
          <span class="sw-eyebrow">{{ s.courseName }} · {{ s.chapterTitle }}</span>
          <h3>「{{ s.knowledgePointName }}」{{ title(s) }}</h3>
          <small>{{ s.kind === 'teacher' ? '推送' : '出题' }}时间 {{ when(s.at) }}<template v-if="s.dueAt"> · 期限 {{ when(s.dueAt) }}</template></small>
        </div>
        <el-tag :type="statusTag(s).type">{{ statusTag(s).text }}</el-tag>
      </header>

      <p v-if="s.explanation" class="explain">{{ s.explanation }}</p>

      <!-- 还没做：在这里做（调小助手原来的接口） -->
      <template v-if="s.status === 'open'">
        <RemedyBox v-if="s.kind !== 'teacher'" :kp-id="s.knowledgePointId" :mode="s.kind" :assess-type="s.assessType || 'scenario'" no-continue
          @changed="finished(s)" />
        <PushBox v-else :push="{ id: s.id, explanation: '', done: false, questions: s.questions }" @changed="finished(s)" />
        <div v-if="decide[s.id]" class="decide">
          <span>这组拓展题要保存到学习推荐吗？</span>
          <el-button size="small" type="primary" @click="keep(s, true)">保存</el-button>
          <el-button size="small" @click="keep(s, false)">不保存</el-button>
        </div>
      </template>

      <!-- 做完了：回看 -->
      <ol v-else class="review">
        <li v-for="q in s.questions" :key="q.id">
          <p class="stem">{{ q.stem }} <el-tag size="small" :type="tag(q).type">{{ tag(q).text }}</el-tag></p>
          <ul v-if="q.options?.length" class="opts">
            <li v-for="(o, k) in q.options" :key="k"
              :class="{ right: LETTERS[k] === q.answer, mine: LETTERS[k] === q.chosen && LETTERS[k] !== q.answer }">
              {{ LETTERS[k] }}. {{ o }}
            </li>
          </ul>
          <p v-if="q.type === 'two_tier' && q.reasonChosen" class="muted">
            理由：你选 {{ q.reasonChosen || '—' }}，正确 {{ q.reasonAnswer }}
          </p>
          <p v-if="!q.options?.length" class="muted">你的回答：{{ q.chosen }}</p>
          <p v-if="!q.correct && q.note" class="note">你可能是这样想的：{{ q.note }}</p>
          <p v-if="q.analysis" class="muted">解析：{{ q.analysis }}</p>
        </li>
      </ol>
    </article>
  </div>
</template>

<style scoped>
.tabs {
  display: flex;
  gap: 8px;
  margin: 4px 0 8px;
}
.tabs button {
  border: 1px solid var(--el-border-color);
  background: var(--el-bg-color);
  border-radius: 999px;
  padding: 7px 16px;
  font-size: 14px;
  cursor: pointer;
  color: var(--el-text-color-regular);
}
.tabs button.active {
  background: var(--app-primary, var(--el-color-primary));
  border-color: transparent;
  color: #fff;
}
.count {
  margin-left: 6px;
  font-weight: 600;
}
.hint {
  color: var(--app-text-muted, var(--el-text-color-secondary));
  margin: 0 0 14px;
  font-size: 13px;
}
.set {
  padding: 18px 20px;
  margin-bottom: 14px;
}
.set.focus {
  outline: 2px solid var(--el-color-primary-light-5);
}
.set-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  margin-bottom: 12px;
}
.set-head h3 {
  margin: 4px 0;
  font-size: 16px;
}
.set-head small,
.muted {
  color: var(--app-text-muted, var(--el-text-color-secondary));
}
.explain {
  background: var(--el-fill-color-light);
  border-radius: 8px;
  padding: 10px 12px;
  white-space: pre-wrap;
}
.decide {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--el-color-primary-light-9);
}
.review {
  padding-left: 20px;
  margin: 0;
}
.review > li + li {
  margin-top: 14px;
}
.stem {
  margin: 0 0 6px;
  font-weight: 500;
}
.opts {
  list-style: none;
  padding: 0;
  margin: 0 0 6px;
}
.opts li {
  padding: 3px 8px;
  border-radius: 6px;
}
.opts li.right {
  background: var(--el-color-success-light-9);
}
.opts li.mine {
  background: var(--el-color-danger-light-9);
}
.note {
  color: var(--el-color-warning-dark-2);
}
</style>
