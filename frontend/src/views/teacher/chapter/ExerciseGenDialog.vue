<script setup>
/*
 * Two ways to get questions into an exercise item (both end up as drafts the teacher confirms):
 *   AI 出题 — pick knowledge points, assessment types, question types and how many per point;
 *   上传已有题目 — a .txt / .md / .docx file or pasted text; the model labels knowledge point and type.
 * After running, shows per knowledge point what came out: created / dropped, key points covered,
 * misconceptions used, questions the AI could not solve without the answer (maybe ambiguous), time and cost.
 */
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { generateExercises, getKpStatus, importExercises } from '@/api/teacherCourse'
import { ASSESS_TYPES, QUESTION_TYPES } from './exerciseLabels'

const props = defineProps({
  modelValue: Boolean,
  mode: { type: String, default: 'ai' }, // 'ai' | 'upload'
  chapterId: { type: Number, required: true },
  itemId: { type: Number, required: true },
})
const emit = defineEmits(['update:modelValue', 'done'])

const visible = computed({ get: () => props.modelValue, set: (v) => emit('update:modelValue', v) })
const kps = ref([])
const kpStatus = ref('')
const picked = ref([])
const assess = ref(['concept', 'scenario'])
const types = ref(['single_choice', 'two_tier'])
const perKp = ref(3)
const file = ref(null)
const pasted = ref('')
const running = ref(false)
const result = ref(null)

watch(visible, async (v) => {
  if (!v) return
  result.value = null
  if (props.mode === 'ai') {
    const s = await getKpStatus(props.chapterId)
    kpStatus.value = s.status
    kps.value = s.kps || []
    if (!picked.value.length) picked.value = kps.value.map((k) => k.id)
  }
})

const estimate = computed(() => `${picked.value.length} 个知识点，约 ${picked.value.length * perKp.value} 道题，预计 ${Math.max(1, Math.round(picked.value.length * 0.5))}～${picked.value.length} 分钟`)

function onFile(e) {
  file.value = e.target.files?.[0] || null
}

async function run() {
  running.value = true
  try {
    if (props.mode === 'ai') {
      if (!picked.value.length || !assess.value.length || !types.value.length) {
        return ElMessage.warning('知识点、考查类型、题型都至少选 1 个')
      }
      result.value = await generateExercises({
        itemId: props.itemId,
        kpIds: picked.value,
        assessTypes: assess.value,
        types: types.value,
        perKp: perKp.value,
      })
    } else {
      if (!file.value && !pasted.value.trim()) return ElMessage.warning('上传文件或粘贴题目')
      result.value = await importExercises(props.itemId, { file: file.value, text: pasted.value })
    }
    ElMessage.success(`出了 ${result.value.created} 道草稿题，请检查后确认`)
    emit('done')
  } finally {
    running.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" :title="mode === 'ai' ? 'AI 出题' : '上传已有题目'" width="min(820px, 96vw)" top="5vh">
    <template v-if="!result">
      <template v-if="mode === 'ai'">
        <el-alert
          v-if="kpStatus !== 'confirmed'" type="warning" :closable="false" show-icon
          title="这一章的知识点还没确认：先在“知识点”里上传资料、确认知识点，AI 按知识点出题"
        />
        <h4>① 考哪些知识点</h4>
        <el-checkbox-group v-model="picked" class="grid">
          <el-checkbox v-for="(k, i) in kps" :key="k.id" :value="k.id">
            {{ i + 1 }}. {{ k.name }}<span v-if="k.importance === '重点'" class="key">重点</span>
          </el-checkbox>
        </el-checkbox-group>
        <h4>② 考查类型 <small>题目会分布在选中的类型里</small></h4>
        <el-checkbox-group v-model="assess" class="grid">
          <el-checkbox v-for="a in ASSESS_TYPES" :key="a.value" :value="a.value">
            {{ a.label }}<small>{{ a.hint }}</small>
          </el-checkbox>
        </el-checkbox-group>
        <h4>③ 题型</h4>
        <el-checkbox-group v-model="types" class="grid">
          <el-checkbox v-for="t in QUESTION_TYPES" :key="t.value" :value="t.value">
            {{ t.label }}<small>{{ t.hint }}</small>
          </el-checkbox>
        </el-checkbox-group>
        <h4>④ 每个知识点几道</h4>
        <el-input-number v-model="perKp" :min="1" :max="6" />
        <span class="muted">建议至少 3 道：掌握度不到 3 次作答可信度低</span>
        <p class="muted">{{ estimate }}。出的题先是草稿，学生看不到；AI 还会不看答案做一遍，做不对的标出来给你检查。</p>
      </template>
      <template v-else>
        <p class="muted">
          支持 .txt、.md、.docx 文件，也可以直接粘贴（PDF、Excel 里的题先复制出来）。AI 只整理格式、标上知识点和考查类型，不改题目内容；
          看不出答案或没把握的会标出来。
        </p>
        <input type="file" accept=".txt,.md,.docx" @change="onFile" />
        <el-input v-model="pasted" type="textarea" :rows="10" placeholder="或者把题目粘贴在这里，例如：1. ……（ ）A. … B. … 答案：B" class="paste" />
      </template>
    </template>

    <template v-else>
      <p>
        出了 <b>{{ result.created }}</b> 道草稿题 · 用时 {{ (result.ms / 1000).toFixed(1) }} 秒 · 调用 {{ result.calls }} 次 ·
        费用约 {{ result.costCny.toFixed(3) }} 元
      </p>
      <el-table :data="result.kps" size="small" border>
        <el-table-column prop="name" label="知识点" min-width="160" />
        <el-table-column prop="created" label="出了" width="60" />
        <el-table-column prop="dropped" label="丢掉" width="60" />
        <el-table-column v-if="mode === 'ai'" prop="points" label="要点覆盖" width="84" />
        <el-table-column v-if="mode === 'ai'" prop="misconceptions" label="误解用上" width="84" />
        <el-table-column v-if="mode === 'ai'" prop="flagged" label="可能有歧义" width="96" />
        <el-table-column label="说明" min-width="200">
          <template #default="{ row }">{{ row.notes.join('；') || '—' }}</template>
        </el-table-column>
      </el-table>
    </template>

    <template #footer>
      <el-button @click="visible = false">{{ result ? '去检查草稿' : '取消' }}</el-button>
      <el-button v-if="!result" type="primary" :loading="running" @click="run">
        {{ running ? (mode === 'ai' ? 'AI 正在出题…' : 'AI 正在识别…') : mode === 'ai' ? '开始出题' : '开始识别' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
h4 {
  margin: 14px 0 6px;
}
h4 small,
.grid small {
  margin-left: 6px;
  color: var(--el-text-color-secondary);
  font-weight: normal;
  font-size: 12px;
}
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 2px 12px;
}
.key {
  margin-left: 6px;
  padding: 0 5px;
  border-radius: 4px;
  background: #fdf0e3;
  color: #9a5a1c;
  font-size: 12px;
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  margin-left: 8px;
}
p.muted {
  margin-left: 0;
}
.paste {
  margin-top: 10px;
}
</style>
