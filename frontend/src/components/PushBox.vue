<script setup>
/*
 * 老师补充讲解 in the student's study assistant (docs/模块说明/老师干预-全班分析.md section 5):
 * the explanation the teacher confirmed, then the new questions, graded right away. Counts as 补练
 * (red -> orange when recovered). Only the first submission counts.
 */
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { submitPush } from '@/api/assistant'

const props = defineProps({ push: { type: Object, required: true } })
const emit = defineEmits(['changed'])

const choice = reactive({})
const multi = reactive({})
const result = ref(null)
const loading = ref(false)
const LETTERS = 'ABCDEF'

function answerOf(q) {
  return q.type === 'multiple' ? (multi[q.id] || []).slice().sort().join('') : choice[q.id] || ''
}
function toggleMulti(q, l) {
  const list = multi[q.id] || []
  multi[q.id] = list.includes(l) ? list.filter((x) => x !== l) : [...list, l]
}
const byId = computed(() => Object.fromEntries((result.value?.results || []).map((r) => [r.id, r])))

async function submit() {
  if (props.push.questions.some((q) => !answerOf(q))) return ElMessage.warning('每道题都要作答')
  loading.value = true
  try {
    result.value = await submitPush(props.push.id, Object.fromEntries(props.push.questions.map((q) => [q.id, answerOf(q)])))
    emit('changed')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div v-loading="loading" class="push">
    <p class="explain">{{ push.explanation }}</p>
    <template v-if="!push.done || result">
      <div v-for="(q, i) in push.questions" :key="q.id" class="q">
        <p class="stem">{{ i + 1 }}. {{ q.stem }}</p>
        <label v-for="(o, k) in q.options" :key="k" class="opt">
          <input v-if="q.type === 'multiple'" type="checkbox" :disabled="!!result" @change="toggleMulti(q, LETTERS[k])" />
          <input v-else v-model="choice[q.id]" type="radio" :name="'p' + q.id" :value="LETTERS[k]" :disabled="!!result" />
          {{ LETTERS[k] }}. {{ o }}
        </label>
        <div v-if="byId[q.id]" class="res">
          <el-tag :type="byId[q.id].correct ? 'success' : byId[q.id].score > 0 ? 'warning' : 'danger'" size="small">
            {{ byId[q.id].correct ? '答对' : byId[q.id].score > 0 ? '少选' : '答错' }}
          </el-tag>
          <span v-if="!byId[q.id].correct">正确答案 {{ byId[q.id].answer }}</span>
          <p class="muted">{{ byId[q.id].analysis }}</p>
        </div>
      </div>
      <el-button v-if="!result" size="small" type="primary" @click="submit">交题</el-button>
      <p v-else class="muted">得分 {{ Math.round(result.score * 100) }}%。</p>
    </template>
    <p v-else class="muted">这组题你已经做过了。</p>
  </div>
</template>

<style scoped>
.push {
  margin-top: 6px;
}
.explain {
  white-space: pre-wrap;
  font-size: 13px;
  line-height: 1.6;
  padding: 8px 10px;
  border-radius: 8px;
  background: var(--el-color-primary-light-9);
}
.q {
  margin-top: 8px;
}
.stem {
  margin: 0 0 4px;
  font-weight: 600;
}
.opt {
  display: block;
  font-size: 13px;
  margin: 2px 0;
}
.res {
  font-size: 12px;
  margin-top: 4px;
}
.res span {
  margin-left: 6px;
}
.muted {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin: 4px 0;
}
</style>
