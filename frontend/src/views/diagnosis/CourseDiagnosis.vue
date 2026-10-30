<script setup>
/*
 * 学情诊断, teacher side: pick one of my courses, see every knowledge point across all students —
 * average mastery, how many practised, how many are weak, and the most common misconceptions
 * (from the wrong options students chose). Weakest points first, so the teacher knows what to re-teach.
 */
import { computed, onMounted, ref, watch } from 'vue'
import MasteryMeter from './MasteryMeter.vue'
import { getCourseDiagnosis } from '@/api/diagnosis'
import { getCourses } from '@/api/teacherCourse'

const courses = ref([])
const courseId = ref(null)
const data = ref(null)
const loading = ref(false)
const order = ref('weak') // 'weak' = weakest first, 'outline' = course order

const points = computed(() => {
  const list = [...(data.value?.points || [])]
  if (order.value === 'weak') {
    // practised points by average mastery, then the ones nobody has practised
    list.sort((a, b) => (a.avgMastery ?? 2) - (b.avgMastery ?? 2))
  }
  return list
})
const weakCount = computed(() => (data.value?.points || []).filter((p) => p.level === 'weak').length)

async function load() {
  if (!courseId.value) return
  loading.value = true
  try {
    data.value = await getCourseDiagnosis(courseId.value)
  } finally {
    loading.value = false
  }
}

watch(courseId, load)

onMounted(async () => {
  courses.value = (await getCourses({ page: 1, size: 50 })).records
  // start on a published course that has content: drafts and empty courses have no students yet
  const start =
    courses.value.find((c) => c.status === 'published' && c.chapterCount) ||
    courses.value.find((c) => c.status === 'published') ||
    courses.value[0]
  courseId.value = start?.id ?? null
})
</script>

<template>
  <div class="course-diagnosis">
    <div class="toolbar">
      <el-select v-model="courseId" placeholder="选择课程" style="width: 240px">
        <el-option v-for="c in courses" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-radio-group v-model="order" size="default">
        <el-radio-button value="weak">最弱的在前</el-radio-button>
        <el-radio-button value="outline">按章节顺序</el-radio-button>
      </el-radio-group>
    </div>

    <div v-loading="loading">
      <template v-if="data">
        <section class="sw-summary conclusion">
          <div class="sw-summary-main">
            <span class="sw-eyebrow on-dark">CLASS OVERVIEW</span>
            <h2 v-if="!data.studentCount">还没有学生做过这门课的练习</h2>
            <h2 v-else-if="weakCount">{{ weakCount }} 个知识点全班掌握得不好，值得再讲一次</h2>
            <h2 v-else>没有全班薄弱的知识点</h2>
            <p>
              平均掌握度按每个学生的掌握度取平均；常见误解来自学生答错时选的选项，是你出题时给错误选项写的说明。
            </p>
          </div>
          <div class="sw-summary-stat">
            <strong>{{ data.studentCount }}</strong>
            <span>名学生做过练习</span>
          </div>
          <div class="sw-summary-stat">
            <strong>{{ data.points.length }}</strong>
            <span>个知识点</span>
          </div>
          <div class="sw-summary-stat">
            <strong>{{ weakCount }}</strong>
            <span>个全班薄弱</span>
          </div>
        </section>

        <div class="app-card table">
          <div v-if="points.length" class="row head">
            <span>知识点</span>
            <span>平均掌握度</span>
            <span class="num">练过</span>
            <span class="num">薄弱</span>
            <span>常见误解</span>
          </div>
          <div v-for="p in points" :key="p.knowledgePointId" class="row">
            <span class="name">
              <strong>{{ p.name }}</strong>
              <small>
                {{ p.chapterTitle }}
                <span v-if="p.key" class="flag">重点</span>
                <span v-if="p.difficult" class="flag">难点</span>
              </small>
            </span>
            <MasteryMeter :value="p.avgMastery" :level="p.level" />
            <span class="num">{{ p.practicedStudents }} 人</span>
            <span class="num" :class="{ alert: p.weakStudents }">{{ p.weakStudents }} 人</span>
            <span class="mis">
              <span v-if="!p.misconceptions.length" class="muted">没有</span>
              <span v-for="(m, i) in p.misconceptions" :key="i" class="mis-item">
                <b>{{ m.count }} 次</b>{{ m.note }}
              </span>
            </span>
          </div>
          <el-empty v-if="!points.length" :image-size="50" description="这门课还没有知识点，先在建课页的「知识点」里添加" />
        </div>
      </template>
      <el-empty v-else-if="!courses.length" description="你还没有课程" />
    </div>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  justify-content: space-between;
  margin-bottom: 18px;
}
.conclusion {
  margin-bottom: 18px;
}
.table {
  padding: 6px 24px;
}
.row {
  display: grid;
  grid-template-columns: minmax(150px, 1fr) minmax(230px, 1.3fr) 56px 56px minmax(200px, 1.4fr);
  align-items: center;
  gap: 20px;
  padding: 14px 0;
  border-top: 1px solid var(--el-border-color-lighter);
}
.row.head {
  border-top: 0;
  padding: 10px 0;
  color: var(--app-text-muted);
  font-size: 12px;
}
.name {
  display: grid;
  gap: 3px;
}
.name small {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  color: var(--app-text-muted);
  font-size: 12px;
}
.flag {
  padding: 0 5px;
  border: 1px solid var(--el-border-color);
  border-radius: 5px;
  font-size: 11px;
}
.num {
  text-align: right;
  font-variant-numeric: tabular-nums;
}
.num.alert {
  color: #b23b2f;
  font-weight: 700;
}
.mis {
  display: grid;
  gap: 6px;
  font-size: 13px;
  line-height: 1.5;
}
.mis-item b {
  margin-right: 6px;
  color: var(--app-text-secondary);
}
.muted {
  color: var(--app-text-muted);
}
@media (max-width: 1100px) {
  .row {
    grid-template-columns: 1fr 1fr;
  }
  .row.head {
    display: none;
  }
  .num {
    text-align: left;
  }
}
</style>
