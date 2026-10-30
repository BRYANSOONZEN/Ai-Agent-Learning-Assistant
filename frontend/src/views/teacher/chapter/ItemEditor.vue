<script setup>
/*
 * Edit one chapter item. Common part: title + description.
 *   video    — one video file (upload / replace / remove), with a player
 *   document — one file, or no file and the description is the reading text
 *   exercise — the questions of this exercise (ChapterExercises)
 *   dialogue — the topics of this AI dialogue (TopicManage), and a 学生视角试一试 tab
 *              that runs the real dialogue (AiDialogue, the same component students use)
 */
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { updateItem, uploadItemFile, removeItemFile } from '@/api/teacherCourse'
import ChapterExercises from './ChapterExercises.vue'
import TopicManage from '../TopicManage.vue'
import AiDialogue from '@/components/AiDialogue.vue'

const props = defineProps({
  chapter: { type: Object, required: true },
  item: { type: Object, required: true },
})
const emit = defineEmits(['changed', 'go'])

const form = reactive({ title: '', description: '' })
const saving = ref(false)
const uploading = ref(null) // { name, percent } while a file is being sent
const dialogueTab = ref('topics') // AI 对话 item: 'topics' | 'try'

watch(
  () => props.item,
  (it) => Object.assign(form, { title: it.title, description: it.description || '' }),
  { immediate: true },
)

const isVideo = computed(() => props.item.type === 'video')
const hasFile = computed(() => props.item.type === 'video' || props.item.type === 'document')
const ACCEPT = computed(() =>
  isVideo.value ? '.mp4,.webm' : '.pdf,.ppt,.pptx,.doc,.docx,.md,.txt,.png,.jpg,.jpeg',
)
const dirty = computed(
  () => form.title !== props.item.title || form.description !== (props.item.description || ''),
)

const descLabel = computed(
  () =>
    ({
      video: '视频说明（选填）：学生看视频前会看到',
      document: '正文：可以只上传文件，也可以不传文件、直接在这里写阅读内容',
      exercise: '练习说明（选填）',
      dialogue: '对话说明（选填）：学生开始对话前会看到',
    })[props.item.type],
)

async function save() {
  if (!form.title.trim()) return ElMessage.warning('标题不能为空')
  saving.value = true
  try {
    await updateItem(props.item.id, form)
    ElMessage.success('已保存')
    emit('changed')
  } finally {
    saving.value = false
  }
}

// el-upload calls this instead of its own XHR
async function doUpload({ file }) {
  uploading.value = { name: file.name, percent: 0 }
  try {
    await uploadItemFile(props.item.id, file, (p) => (uploading.value.percent = p))
    ElMessage.success(`已上传：${file.name}`)
    emit('changed')
  } finally {
    uploading.value = null
  }
}

async function removeFile() {
  await ElMessageBox.confirm('确定删除这个文件吗？', '提示', { type: 'warning' })
  await removeItemFile(props.item.id)
  ElMessage.success('已删除文件')
  emit('changed')
}

function size(bytes) {
  if (bytes > 1024 * 1024) return (bytes / 1024 / 1024).toFixed(1) + ' MB'
  return Math.max(1, Math.round(bytes / 1024)) + ' KB'
}
</script>

<template>
  <div class="editor">
    <el-form label-position="top" class="basic">
      <el-form-item label="标题">
        <el-input v-model="form.title" maxlength="100" />
      </el-form-item>
      <el-form-item :label="descLabel">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="item.type === 'document' ? 8 : 2"
          :placeholder="item.type === 'document' ? '例：1. 大量错误发生在边界上……' : ''"
        />
      </el-form-item>
      <el-button type="primary" :disabled="!dirty" :loading="saving" @click="save">保存</el-button>
    </el-form>

    <!-- video / document: one file -->
    <section v-if="hasFile" class="block">
      <h3>{{ isVideo ? '视频文件' : '附件' }}</h3>
      <template v-if="item.material">
        <video v-if="isVideo" :src="item.material.url" controls class="player" />
        <div class="file">
          <el-icon><component :is="isVideo ? 'VideoCamera' : 'Document'" /></el-icon>
          <el-link :href="item.material.url" target="_blank" type="primary">{{ item.material.originalName }}</el-link>
          <span class="muted">{{ size(item.material.fileSize) }}</span>
          <el-upload :show-file-list="false" :accept="ACCEPT" :http-request="doUpload">
            <el-button link type="primary">替换</el-button>
          </el-upload>
          <el-button link type="danger" @click="removeFile">删除文件</el-button>
        </div>
      </template>
      <el-upload v-else drag :show-file-list="false" :accept="ACCEPT" :http-request="doUpload">
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">把{{ isVideo ? '视频' : '文件' }}拖到这里，或<em>点击选择</em></div>
        <template #tip>
          <div class="el-upload__tip">
            {{ isVideo ? 'mp4 / webm' : 'pdf ppt pptx doc docx md txt png jpg' }}，不超过 200 MB
          </div>
        </template>
      </el-upload>
      <div v-if="uploading" class="progress">
        <span>{{ uploading.name }}</span>
        <el-progress :percentage="uploading.percent" />
      </div>
    </section>

    <section v-else-if="item.type === 'exercise'" class="block">
      <h3>题目</h3>
      <ChapterExercises :chapter="chapter" :item-id="item.id" @changed="emit('changed')" @go="emit('go', $event)" />
    </section>

    <section v-else-if="item.type === 'dialogue'" class="block">
      <el-tabs v-model="dialogueTab">
        <el-tab-pane :label="`对话话题（${item.topicCount}）`" name="topics">
          <TopicManage :chapter-id="chapter.id" :item-id="item.id" embedded @changed="emit('changed')" />
        </el-tab-pane>
        <el-tab-pane label="学生视角试一试" name="try" lazy>
          <el-alert
            type="info"
            show-icon
            :closable="false"
            class="try-tip"
            title="和学生看到的一模一样，会真实调用 AI。改了话题后点「重新开始」再试。"
          />
          <!-- key: start fresh when the topic count changes -->
          <AiDialogue :key="item.topicCount" :chapter-id="chapter.id" :item-id="item.id" compact />
        </el-tab-pane>
      </el-tabs>
    </section>
  </div>
</template>

<style scoped>
.basic {
  max-width: 760px;
}
.block {
  margin-top: 28px;
  padding-top: 20px;
  border-top: 1px solid var(--el-border-color-lighter);
}
h3 {
  margin: 0 0 12px;
  font-size: 15px;
}
.player {
  width: 100%;
  max-width: 760px;
  max-height: 420px;
  background: #000;
  border-radius: 6px;
}
.file {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 8px;
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.try-tip {
  margin-bottom: 12px;
}
.progress {
  margin-top: 10px;
  max-width: 480px;
  font-size: 13px;
}
</style>
