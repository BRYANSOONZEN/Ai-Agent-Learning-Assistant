<script setup>
/*
 * 资料 → 知识点 (teacher, top of the chapter's 知识点 view).
 * Every uploaded video / PDF is turned into text right away (shown per material below).
 * 上传完毕 tells the server the chapter's materials are complete: as soon as all are processed,
 * knowledge points are recognized automatically. The draft can be edited, then 确认 makes the
 * knowledge point map visible to students. 重新识别 keeps what the teacher edited.
 * While anything is still running the status is polled every 4 seconds.
 */
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import KpMap from '@/components/KpMap.vue'
import {
  createChapterKp,
  deleteChapterKp,
  getKpStatus,
  getTeacherKpMap,
  kpConfirm,
  kpRerun,
  kpUploadDone,
  updateChapterKp,
} from '@/api/teacherCourse'

const props = defineProps({ chapter: { type: Object, required: true } })
const emit = defineEmits(['changed', 'go'])

const st = ref(null)
const map = ref(null)
const busy = ref(false)
let timer = null

const STATUS = {
  none: { label: '等待上传完毕', tone: 'info' },
  waiting: { label: '处理资料中', tone: 'warning' },
  recognizing: { label: '识别中', tone: 'warning' },
  draft: { label: '待确认', tone: 'danger' },
  confirmed: { label: '已确认', tone: 'success' },
  failed: { label: '识别失败', tone: 'danger' },
}
const JOB = {
  none: { label: '未上传', cls: 'gray' },
  queued: { label: '排队中', cls: 'orange' },
  processing: { label: '处理中', cls: 'orange' },
  done: { label: '已转成文字', cls: 'teal' },
  failed: { label: '失败', cls: 'red' },
}

const running = computed(
  () =>
    !!st.value &&
    (['waiting', 'recognizing'].includes(st.value.status) ||
      st.value.materials.some((m) => ['queued', 'processing'].includes(m.status))),
)

const hint = computed(() => {
  const s = st.value
  if (!s) return ''
  if (s.message) return s.message
  if (s.status === 'none') {
    return s.total
      ? `已处理 ${s.processed}/${s.total} 份资料。资料传齐后请点“上传完毕”，系统会自动识别知识点`
      : '先在左边给视频、文档上传文件，上传后会马上转成文字'
  }
  return ''
})

async function load() {
  const before = st.value?.status
  st.value = await getKpStatus(props.chapter.id)
  if (st.value.kps.length) map.value = await getTeacherKpMap(props.chapter.id)
  else map.value = null
  if (before && before !== st.value.status) {
    emit('changed')
    if (st.value.status === 'draft') ElMessage.success(`识别完成：${st.value.kps.length} 个知识点，待你确认`)
  }
  schedule()
}

function schedule() {
  clearTimeout(timer)
  if (running.value) timer = setTimeout(() => load().catch(() => {}), 4000)
}

async function act(fn, ok) {
  busy.value = true
  try {
    st.value = await fn(props.chapter.id)
    if (ok) ElMessage.success(ok)
    await load()
    emit('changed')
  } finally {
    busy.value = false
  }
}

const uploadDone = () => act(kpUploadDone)
const rerun = () => act(kpRerun, '已开始重新识别')
const confirm = () => act(kpConfirm, '已确认，学生可以看到知识点地图了')

// ---------- edit ----------
const dialog = ref(false)
const editing = ref(null) // kp being edited, null = new
const form = reactive({ name: '', summary: '', importance: '重点', points: [], misconceptions: '' })

function openEdit(kp) {
  editing.value = kp
  form.name = kp?.name || ''
  form.summary = kp?.summary || ''
  form.importance = kp?.importance || '重点'
  form.points = (kp?.keyPoints || []).map((p) => ({ text: p.text, sources: p.sources }))
  form.misconceptions = (kp?.misconceptions || []).join('\n')
  dialog.value = true
}

async function save() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写知识点名称')
    return
  }
  const data = {
    name: form.name,
    summary: form.summary,
    importance: form.importance,
    keyPoints: form.points.filter((p) => p.text.trim()),
    misconceptions: form.misconceptions.split('\n').map((s) => s.trim()).filter(Boolean),
  }
  if (editing.value) await updateChapterKp(editing.value.id, data)
  else await createChapterKp(props.chapter.id, data)
  dialog.value = false
  ElMessage.success('已保存')
  await load()
  emit('changed')
}

async function remove(kp) {
  await ElMessageBox.confirm(`确定删除知识点「${kp.name}」吗？`, '提示', { type: 'warning' })
  await deleteChapterKp(kp.id)
  await load()
  emit('changed')
}

function sourceLabel(key) {
  // D1-P4 → 文档1 第4页；D1-P13-图1 → 文档1 第13页的图；V2-03 → 视频2 第3段
  let m = key.match(/^D(\d+)-P(\d+)-图(\d+)$/)
  if (m) return `文档${m[1]} 第${m[2]}页的图`
  m = key.match(/^D(\d+)-P(\d+)$/)
  if (m) return `文档${m[1]} 第${m[2]}页`
  m = key.match(/^D(\d+)-T\d+$/)
  if (m) return `文档${m[1]} 正文`
  m = key.match(/^V(\d+)-(\d+)$/)
  if (m) return `视频${m[1]} 第${Number(m[2])}段`
  return key
}

onMounted(() => load().catch(() => {}))
onBeforeUnmount(() => clearTimeout(timer))
</script>

<template>
  <section v-if="st" class="kp-rec">
    <!-- status -->
    <div class="status" :class="'tone-' + STATUS[st.status]?.tone">
      <div class="status-main">
        <span class="label">
          <el-icon v-if="running" class="is-loading"><Loading /></el-icon>
          AI 识别知识点 · {{ STATUS[st.status]?.label }}
        </span>
        <span class="msg">{{ hint }}</span>
      </div>
      <div class="actions">
        <el-button
          v-if="!st.uploadDone || st.status === 'none'"
          type="primary"
          :loading="busy"
          :disabled="!st.materials.some((m) => m.status !== 'none')"
          @click="uploadDone"
        >
          上传完毕
        </el-button>
        <el-button
          v-if="['draft', 'confirmed', 'failed'].includes(st.status)"
          :loading="busy"
          @click="rerun"
        >
          重新识别
        </el-button>
        <el-button v-if="st.status === 'draft'" type="primary" :loading="busy" @click="confirm">
          确认这 {{ st.kps.length }} 个知识点
        </el-button>
      </div>
    </div>

    <!-- materials -->
    <ul class="materials">
      <li v-for="m in st.materials" :key="m.itemId">
        <el-icon><component :is="m.type === 'video' ? 'VideoPlay' : 'Document'" /></el-icon>
        <span class="title">{{ m.title }}</span>
        <span class="sw-tag" :class="JOB[m.status]?.cls">
          <el-icon v-if="m.status === 'processing'" class="is-loading"><Loading /></el-icon>
          {{ JOB[m.status]?.label }}
        </span>
        <span class="muted">{{ m.message }}</span>
      </li>
      <li v-if="!st.materials.length" class="muted">这一章还没有视频或文档</li>
    </ul>

    <!-- draft -->
    <template v-if="st.kps.length">
      <div class="sub-head">
        <h4>知识点（{{ st.kps.length }} 个）</h4>
        <el-button link type="primary" icon="Plus" @click="openEdit(null)">添加知识点</el-button>
      </div>
      <div class="kps">
        <article v-for="(k, i) in st.kps" :key="k.id" class="kp">
          <header>
            <strong>{{ i + 1 }}. {{ k.name }}</strong>
            <span class="sw-tag" :class="k.importance === '重点' ? 'orange' : 'gray'">{{ k.importance }}</span>
            <span v-if="k.edited" class="sw-tag blue">老师改过</span>
            <span class="grow" />
            <el-button link type="primary" @click="openEdit(k)">编辑</el-button>
            <el-button link type="danger" @click="remove(k)">删除</el-button>
          </header>
          <p v-if="k.summary" class="summary">{{ k.summary }}</p>
          <ul class="points">
            <li v-for="(p, j) in k.keyPoints" :key="j">
              {{ p.text }}
              <span v-for="s in p.sources" :key="s" class="src">{{ sourceLabel(s) }}</span>
            </li>
          </ul>
          <p v-if="k.misconceptions.length" class="mis">
            常见误解：{{ k.misconceptions.join('；') }}
          </p>
        </article>
      </div>

      <div v-if="map && map.rows.length" class="sub-head">
        <h4>知识点地图</h4>
        <span class="muted">每个知识点在哪份资料、哪一页、哪一分钟讲到；确认后学生也能看到</span>
      </div>
      <KpMap v-if="map && map.rows.length" :map="map" @jump="(j) => emit('go', 'item:' + j.itemId)" />
    </template>

    <el-dialog v-model="dialog" :title="editing ? '编辑知识点' : '添加知识点'" width="min(620px, 94vw)">
      <el-form label-position="top">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" maxlength="100" />
        </el-form-item>
        <el-form-item label="一句话说明">
          <el-input v-model="form.summary" maxlength="500" />
        </el-form-item>
        <el-form-item label="重要性">
          <el-radio-group v-model="form.importance">
            <el-radio value="重点">重点</el-radio>
            <el-radio value="一般">一般</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="要点（学生应该能用自己的话讲出来的内容）">
          <div class="point-edit">
            <div v-for="(p, j) in form.points" :key="j" class="point-row">
              <el-input v-model="p.text" type="textarea" autosize />
              <el-button link type="danger" icon="Delete" @click="form.points.splice(j, 1)" />
            </div>
            <el-button link type="primary" icon="Plus" @click="form.points.push({ text: '', sources: [] })">
              加一条要点
            </el-button>
          </div>
        </el-form-item>
        <el-form-item label="常见误解（一行一个）">
          <el-input v-model="form.misconceptions" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.kp-rec {
  display: grid;
  /* minmax(0, 1fr): the wide 知识点地图 table scrolls inside instead of stretching the whole panel */
  grid-template-columns: minmax(0, 1fr);
  min-width: 0;
  gap: 14px;
  margin-bottom: 24px;
}
.status {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  padding: 12px 14px;
  border-radius: 10px;
  background: var(--el-fill-color-light);
}
.status.tone-warning {
  background: #fdf6ec;
}
.status.tone-danger {
  background: #fef0f0;
}
.status.tone-success {
  background: #f0f9eb;
}
.status-main {
  display: grid;
  gap: 4px;
}
.label {
  font-weight: 600;
}
.msg,
.muted {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.actions {
  display: flex;
  gap: 8px;
}
.materials {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 6px;
}
.materials li {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  font-size: 13px;
}
.materials .title {
  font-weight: 500;
}
.sub-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  flex-wrap: wrap;
}
.sub-head h4 {
  margin: 6px 0 0;
}
.kps {
  display: grid;
  gap: 10px;
}
.kp {
  padding: 12px 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
}
.kp header {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.grow {
  flex: 1;
}
.summary {
  margin: 6px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.points {
  margin: 8px 0 0;
  padding-left: 18px;
  font-size: 13px;
  display: grid;
  gap: 4px;
}
.src {
  margin-left: 6px;
  padding: 0 6px;
  border-radius: 4px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-secondary);
  font-size: 11px;
}
.mis {
  margin: 8px 0 0;
  font-size: 12px;
  color: #b25d00;
}
.point-edit {
  width: 100%;
  display: grid;
  gap: 6px;
}
.point-row {
  display: flex;
  gap: 6px;
  align-items: flex-start;
}
</style>
