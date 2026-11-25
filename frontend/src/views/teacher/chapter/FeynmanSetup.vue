<script setup>
/*
 * 费曼式 AI 对话 (teacher): tick the knowledge points students should explain to an AI classmate (at most 2, pick the hard ones),
 * let the model draft one card per point, check it, then confirm. Students use it once confirmed.
 * Each card: the AI classmate (name and persona), scene, opening line, the misconception the classmate plays,
 * per key point the "explained" / "partial" criteria and the three hint levels (L1 → L2 → L3, then point to the material).
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { confirmFeynman, draftFeynman, getFeynmanSetup } from '@/api/teacherCourse'

const props = defineProps({ item: { type: Object, required: true } })

const setup = ref(null)
const picked = ref([])
const drafting = ref(false)

const STATUS = { none: '还没生成', draft: '待确认', confirmed: '已确认，学生可以用' }
const changed = computed(() => setup.value && picked.value.join(',') !== setup.value.kpIds.join(','))
// 知识点要老师先确认：对话里的标准答案就是确认过的要点原文
const kpReady = computed(() => setup.value?.kpStatus === 'confirmed')
const stale = computed(() => setup.value?.stale || [])

async function load() {
  setup.value = await getFeynmanSetup(props.item.id)
  picked.value = [...setup.value.kpIds]
}

function toggle(id) {
  if (picked.value.includes(id)) picked.value = picked.value.filter((x) => x !== id)
  else if (picked.value.length < setup.value.maxKps) picked.value = [...picked.value, id]
  else ElMessage.warning(`一次对话最多 ${setup.value.maxKps} 个知识点，选最难理解的`)
}

async function draft() {
  if (!picked.value.length) {
    ElMessage.warning('先选知识点')
    return
  }
  drafting.value = true
  try {
    setup.value = await draftFeynman(props.item.id, picked.value)
    ElMessage.success('草稿已生成，请检查后确认')
  } finally {
    drafting.value = false
  }
}

async function confirm() {
  setup.value = await confirmFeynman(props.item.id)
  ElMessage.success('已确认，学生打开这一项就是和 AI 同学对话')
}

onMounted(load)
</script>

<template>
  <div v-if="setup" class="fs">
    <div class="block-head">
      <strong>① 选这次对话要讲的知识点</strong>
      <span class="muted">最多 {{ setup.maxKps }} 个，选学生最难理解的</span>
    </div>
    <el-empty
      v-if="!setup.candidates.length"
      description="这一章还没有识别出知识点：先在“知识点”里上传资料、确认知识点"
      :image-size="60"
    />
    <el-alert
      v-if="setup.candidates.length && !kpReady" type="warning" :closable="false" show-icon
      title="这一章的知识点还没确认（或改过之后还没再确认）：先到“知识点”里确认，再生成对话草稿。对话里判断学生、最后补充给学生的标准答案，就是确认过的要点原文。"
    />
    <div v-if="setup.candidates.length" class="kps">
      <button
        v-for="(c, i) in setup.candidates"
        :key="c.id"
        type="button"
        class="kp"
        :class="{ on: picked.includes(c.id) }"
        @click="toggle(c.id)"
      >
        <el-icon v-if="picked.includes(c.id)"><Select /></el-icon>
        <span>{{ i + 1 }}. {{ c.name }}</span>
        <span v-if="c.importance === '重点'" class="sw-tag orange">重点</span>
      </button>
    </div>
    <div class="actions">
      <el-button type="primary" :loading="drafting" :disabled="!picked.length || !kpReady" @click="draft">
        {{ setup.drafts.length ? '重新生成草稿' : '生成对话草稿' }}
      </el-button>
      <span class="muted">{{ drafting ? 'AI 正在起草，每个知识点约 15 秒…' : '每个知识点约 15 秒' }}</span>
    </div>

    <template v-if="setup.drafts.length">
      <div class="block-head">
        <strong>② 草稿</strong>
        <span class="sw-tag" :class="setup.status === 'confirmed' ? 'teal' : 'orange'">{{ STATUS[setup.status] }}</span>
        <span v-if="changed" class="muted">选的知识点改了，请重新生成</span>
      </div>
      <el-alert
        v-if="stale.length" type="error" :closable="false" show-icon
        :title="`草稿生成之后知识点改过了：${stale.join('；')}。草稿里还是改之前的内容，请先确认知识点，再重新生成草稿${setup.status === 'confirmed' ? '（学生现在用的还是旧草稿）' : ''}。`"
      />
      <article v-for="d in setup.drafts" :key="d.kpId" class="card">
        <header>
          <strong>{{ d.name }}</strong>
          <span v-for="c in d.checks" :key="c.name" class="sw-tag" :class="c.ok ? 'teal' : 'red'" :title="c.detail">
            {{ c.ok ? '✓' : '!' }} {{ c.name }}
          </span>
        </header>
        <dl>
          <dt>AI 同学</dt>
          <dd>{{ d.draft.scene.name || '小林' }}：{{ d.draft.scene.persona }}</dd>
          <dt>场景（{{ d.draft.scene.type }}）</dt>
          <dd>{{ d.draft.scene.setting }}</dd>
          <dt>开场白</dt>
          <dd class="say">{{ d.draft.scene.opening }}</dd>
          <dt>带着的误解</dt>
          <dd class="say">{{ d.draft.misconception_probe.line }}</dd>
        </dl>
        <div v-for="p in d.draft.key_points" :key="p.no" class="point">
          <div class="point-title">要点 {{ p.no }}：{{ d.keyPoints[p.no - 1] }}</div>
          <div class="grid">
            <span class="sw-tag teal">讲清</span><span>{{ p.understood_if }}</span>
            <span class="sw-tag gray">不完整</span><span>{{ p.partial_if }}</span>
            <b>L1</b><span>{{ p.L1 }}</span>
            <b>L2</b><span>{{ p.L2 }}</span>
            <b>L3</b><span>{{ p.L3 }}</span>
            <b>之后</b><span>不给答案，指向：{{ p.pointer }}</span>
          </div>
        </div>
      </article>
      <div class="actions">
        <el-button v-if="setup.status !== 'confirmed'" type="primary" :disabled="changed || stale.length > 0" @click="confirm">
          确认草稿
        </el-button>
        <span class="muted">规则：每个知识点学生最多说 10 句，自动结束；认真尝试过但没讲清的，AI 同学会补充并推荐复习；对话结果只给学生看</span>
      </div>
    </template>
  </div>
</template>

<style scoped>
.fs {
  display: grid;
  gap: 12px;
}
.block-head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 6px;
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.kps {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 8px;
}
.kp {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  background: #fff;
  text-align: left;
  font-size: 13px;
  cursor: pointer;
}
.kp.on {
  border-color: #5b6ef5;
  background: #eef0fe;
  color: #3946c9;
}
.actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.card {
  padding: 12px 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  display: grid;
  gap: 8px;
}
.card header {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
dl {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr);
  gap: 6px 10px;
  margin: 0;
  font-size: 13px;
}
dt {
  color: var(--el-text-color-secondary);
}
dd {
  margin: 0;
}
.say {
  padding: 4px 8px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
}
.point {
  padding: 8px 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}
.point-title {
  font-weight: 500;
  font-size: 13px;
}
.grid {
  display: grid;
  grid-template-columns: 56px minmax(0, 1fr);
  gap: 4px 8px;
  margin-top: 6px;
  font-size: 12px;
  align-items: start;
}
.grid b {
  color: #3946c9;
}
</style>
