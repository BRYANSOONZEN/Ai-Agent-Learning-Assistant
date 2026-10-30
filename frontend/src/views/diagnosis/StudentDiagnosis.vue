<script setup>
/*
 * 学情诊断, student side: how well each knowledge point is mastered, worked out from the answer records.
 * Weak points come first, each with why (recent answers, the misconception behind the last wrong answer)
 * and what to do (redo the exercise). Then every knowledge point by course / chapter, then how the
 * AI dialogue topics went.
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import MasteryMeter from './MasteryMeter.vue'
import { LEVELS, TOPIC_RESULTS } from './levels'
import { getMyDiagnosis } from '@/api/diagnosis'

const router = useRouter()
const data = ref(null)
const loading = ref(false)

const s = computed(() => data.value?.summary)

const headline = computed(() => {
  if (!s.value?.total) return '还没有可以诊断的内容'
  if (s.value.weak) return `有 ${s.value.weak} 个知识点还不牢，先补这几个`
  if (s.value.none === s.value.total) return '知识点都还没练过'
  if (s.value.medium) return '没有薄弱的知识点，还有几个可以再巩固'
  return '练过的知识点都掌握了'
})

function practice(p) {
  router.push({
    name: 'course-detail',
    params: { id: p.courseId },
    query: { chapter: p.chapterId, view: 'item:' + p.practiceItemId },
  })
}

const shortTime = (t) => `${Number(t.slice(5, 7))}月${Number(t.slice(8, 10))}日`

async function load() {
  loading.value = true
  try {
    data.value = await getMyDiagnosis()
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="diagnosis">
    <template v-if="data">
      <!-- conclusion first: one sentence, then the count per level -->
      <section class="sw-summary">
        <div class="sw-summary-main">
          <span class="sw-eyebrow on-dark">EVIDENCE-BASED DIAGNOSIS</span>
          <h2>{{ headline }}</h2>
          <p>
            根据你的 {{ data.courses.length }} 门课的答题记录计算。越近的作答权重越大，做题少于 3 次的会打折，所以多练几次才会准。
          </p>
        </div>
        <div v-for="key in ['weak', 'medium', 'good', 'none']" :key="key" class="sw-summary-stat level-stat">
          <strong>{{ s[key] }}</strong>
          <span><i class="swatch" :class="key" />{{ LEVELS[key].label }}</span>
        </div>
      </section>

      <!-- ============ weak points: why + what to do ============ -->
      <section v-if="data.weakPoints.length" class="block">
        <div class="sw-heading card-head">
          <div>
            <span class="sw-eyebrow">WEAK POINTS</span>
            <h3>需要先补的知识点</h3>
          </div>
          <router-link :to="{ name: 'recommend' }" class="sw-link">去学习推荐<el-icon><Right /></el-icon></router-link>
        </div>
        <div class="weak-list">
          <article v-for="p in data.weakPoints" :key="p.knowledgePointId" class="weak-card">
            <div class="weak-head">
              <div>
                <strong>{{ p.name }}</strong>
                <small>{{ p.chapterTitle }}</small>
              </div>
              <span v-if="p.key" class="flag">重点</span>
              <span v-if="p.difficult" class="flag">难点</span>
            </div>
            <MasteryMeter :value="p.mastery" :level="p.level" />
            <div class="recent">
              <span class="recent-label">最近 {{ p.recent.length }} 次</span>
              <span
                v-for="(ok, i) in p.recent"
                :key="i"
                class="attempt"
                :class="ok ? 'ok' : 'no'"
                :title="ok ? '答对' : '答错'"
              >
                <el-icon><component :is="ok ? 'Check' : 'Close'" /></el-icon>
              </span>
              <span class="recent-note">共答 {{ p.attemptCount }} 次，对 {{ p.correctCount }} 次</span>
            </div>
            <p v-if="p.misconception" class="why">
              <b>上次错在</b>{{ p.misconception }}
            </p>
            <el-button v-if="p.practiceItemId" type="primary" plain size="small" @click="practice(p)">
              回去重做这组练习
            </el-button>
          </article>
        </div>
      </section>

      <!-- ============ every knowledge point ============ -->
      <section v-for="c in data.courses" :key="c.courseId" class="block">
        <div class="sw-heading card-head">
          <div>
            <span class="sw-eyebrow">ALL KNOWLEDGE POINTS</span>
            <h3>{{ c.courseName }}</h3>
          </div>
        </div>
        <div class="app-card points">
          <template v-for="ch in c.chapters" :key="ch.chapterId">
            <div class="chapter">{{ ch.title }}</div>
            <div v-for="p in ch.points" :key="p.knowledgePointId" class="point">
              <div class="point-name">
                <span>{{ p.name }}</span>
                <span v-if="p.key" class="flag">重点</span>
                <span v-if="p.difficult" class="flag">难点</span>
              </div>
              <MasteryMeter :value="p.mastery" :level="p.level" />
              <span class="point-count">
                {{ p.attemptCount ? `对 ${p.correctCount} / ${p.attemptCount}` : '没有作答' }}
              </span>
            </div>
          </template>
          <el-empty v-if="!c.chapters.length" :image-size="50" description="这门课还没有知识点" />
        </div>
      </section>

      <!-- ============ AI dialogue ============ -->
      <section class="block">
        <div class="sw-heading card-head">
          <div>
            <span class="sw-eyebrow">AI DIALOGUE</span>
            <h3>AI 对话里的理解情况</h3>
          </div>
        </div>
        <div class="app-card">
          <p class="basis small">
            对话不算进掌握度，但能看出是自己想明白的，还是靠提示、看讲解才懂的。
          </p>
          <el-empty
            v-if="!data.dialogueTopics.length"
            :image-size="50"
            description="还没有完成的对话话题。学完章节后去和 AI 对话吧"
          />
          <ul class="topics">
            <li v-for="(t, i) in data.dialogueTopics" :key="i">
              <span class="result" :class="TOPIC_RESULTS[t.result]?.cls">{{ TOPIC_RESULTS[t.result]?.label }}</span>
              <span class="topic-main">
                <strong>{{ t.topicTitle }}</strong>
                <small>{{ t.itemTitle }}，{{ shortTime(t.time) }}</small>
              </span>
              <span class="topic-count">
                {{ t.wrongCount ? `答错 ${t.wrongCount} 次` : '' }}{{ t.wrongCount && t.stuckCount ? '，' : '' }}{{ t.stuckCount ? `卡住 ${t.stuckCount} 次` : '' }}
              </span>
            </li>
          </ul>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.diagnosis {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 26px;
}
.diagnosis .app-card {
  margin-top: 0;
}

.basis {
  max-width: 46em;
  margin: 0;
  color: var(--app-text-secondary);
  line-height: 1.6;
}
.basis.small {
  margin-bottom: 8px;
  font-size: 13px;
}

.card-head {
  margin-bottom: 12px;
}
.level-stat span {
  display: flex;
  align-items: center;
  gap: 6px;
}
.swatch {
  width: 8px;
  height: 8px;
  border-radius: 2px;
  background: #8aa0b6;
}
.swatch.good {
  background: #6ac8b6;
}
.swatch.medium {
  background: #f4a55a;
}
.swatch.weak {
  background: #ef8c73;
}

/* ---------- weak points ---------- */
.weak-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(340px, 100%), 1fr));
  gap: 14px;
}
.weak-card {
  min-width: 0;
  display: grid;
  gap: 14px;
  justify-items: start;
  padding: 20px;
  border: 1px solid var(--app-border);
  border-left: 3px solid var(--app-danger);
  border-radius: 14px;
  background: var(--app-surface);
}
.weak-card > * {
  width: 100%;
}
.weak-card > .el-button {
  width: auto;
}
.weak-head {
  display: flex;
  align-items: flex-start;
  gap: 6px;
}
.weak-head > div {
  flex: 1;
  display: grid;
  gap: 3px;
}
.weak-head strong {
  font-size: 16px;
}
.weak-head small {
  color: var(--app-text-muted);
}
.flag {
  flex: none;
  padding: 1px 6px;
  border: 1px solid var(--el-border-color);
  border-radius: 5px;
  color: var(--app-text-secondary);
  font-size: 11px;
}
.recent {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  font-size: 12px;
}
.recent-label {
  color: var(--app-text-muted);
  margin-right: 2px;
}
.attempt {
  width: 20px;
  height: 20px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  font-size: 12px;
}
.attempt.ok {
  background: var(--el-color-success-light-9);
  color: #1f7a69;
}
.attempt.no {
  background: var(--el-color-danger-light-9);
  color: #b23b2f;
}
.recent-note {
  margin-left: auto;
  color: var(--app-text-muted);
}
.why {
  margin: 0;
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  line-height: 1.6;
  font-size: 13px;
}
.why b {
  margin-right: 8px;
  color: var(--app-text-secondary);
  font-weight: 600;
}

/* ---------- all points ---------- */
.points {
  padding: 8px 24px 12px;
}
.chapter {
  padding: 14px 0 6px;
  color: var(--app-text-muted);
  font-size: 12px;
  font-weight: 600;
}
.point {
  display: grid;
  grid-template-columns: minmax(160px, 1fr) minmax(220px, 1.4fr) 90px;
  align-items: center;
  gap: 20px;
  padding: 12px 0;
  border-top: 1px solid var(--el-border-color-lighter);
}
.point-name {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  font-weight: 600;
}
.point-count {
  text-align: right;
  color: var(--app-text-muted);
  font-size: 12px;
}

/* ---------- dialogue topics ---------- */
.topics {
  margin: 0;
  padding: 0;
  list-style: none;
}
.topics li {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 0;
  border-top: 1px solid var(--el-border-color-lighter);
}
.result {
  flex: none;
  width: 78px;
  padding: 3px 0;
  border-radius: 6px;
  text-align: center;
  font-size: 12px;
  font-weight: 600;
  background: var(--el-fill-color);
  color: var(--app-text-secondary);
}
.result.good {
  background: var(--el-color-success-light-9);
  color: #1f7a69;
}
.result.medium {
  background: var(--el-color-warning-light-9);
  color: #9a5a1c;
}
.result.weak {
  background: var(--el-color-danger-light-9);
  color: #b23b2f;
}
.topic-main {
  flex: 1;
  display: grid;
  gap: 2px;
}
.topic-main small,
.topic-count {
  color: var(--app-text-muted);
  font-size: 12px;
}

@media (max-width: 760px) {
  .point {
    grid-template-columns: 1fr;
    gap: 8px;
  }
  .point-count {
    text-align: left;
  }
  .weak-list {
    grid-template-columns: 1fr;
  }
}
</style>
