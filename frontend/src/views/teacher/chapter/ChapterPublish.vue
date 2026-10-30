<script setup>
/*
 * Last step: check the chapter, preview what students will see (the items in learning order), then publish.
 * Required before publishing: an intro, at least one item, and every item complete
 * (video has a file, document has a file or text, exercise has questions, dialogue has topics).
 */
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { patchChapter } from '@/api/teacherCourse'
import { itemType, itemSummary } from '../courseModules'

const props = defineProps({
  chapter: { type: Object, required: true },
  items: { type: Array, required: true },
})
const emit = defineEmits(['changed', 'go'])

const saving = ref(false)

const checks = computed(() => [
  { label: '已写章节介绍', ok: !!props.chapter.intro?.trim(), go: 'intro' },
  { label: '至少有一项内容', ok: props.items.length > 0, go: null },
  ...props.items.map((it) => ({
    label: `${itemType(it.type)?.label}「${it.title}」：${itemSummary(it)}`,
    ok: it.ready,
    go: 'item:' + it.id,
  })),
])
const ready = computed(() => checks.value.every((c) => c.ok))
const published = computed(() => props.chapter.status === 'published')

async function setStatus(status) {
  saving.value = true
  try {
    await patchChapter(props.chapter, { status })
    ElMessage.success(status === 'published' ? '已发布，学生可以看到本章' : '已撤回为草稿')
    emit('changed')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="publish">
    <section>
      <h3>发布检查</h3>
      <div v-for="c in checks" :key="c.label" class="check">
        <el-icon :color="c.ok ? 'var(--el-color-success)' : 'var(--el-color-danger)'">
          <CircleCheck v-if="c.ok" />
          <CircleClose v-else />
        </el-icon>
        <span class="check-label">{{ c.label }}</span>
        <el-button v-if="!c.ok && c.go" link type="primary" @click="emit('go', c.go)">去完成</el-button>
      </div>

      <div class="actions">
        <el-tag :type="published ? 'success' : 'info'">当前：{{ published ? '已发布' : '草稿' }}</el-tag>
        <el-button v-if="!published" type="primary" :disabled="!ready" :loading="saving" @click="setStatus('published')">
          发布本章
        </el-button>
        <el-button v-else :loading="saving" @click="setStatus('draft')">撤回为草稿</el-button>
      </div>
    </section>

    <section class="preview">
      <h3>学生看到的样子（预览）</h3>
      <div class="block">
        <div class="block-title">{{ chapter.title }}</div>
        <p class="pre">{{ chapter.intro || '（还没写章节介绍）' }}</p>
      </div>
      <ol class="path">
        <li v-for="it in items" :key="it.id">
          <el-icon><component :is="itemType(it.type)?.icon" /></el-icon>
          <span class="path-title">{{ it.title }}</span>
          <span class="muted">{{ itemSummary(it) }}</span>
        </li>
      </ol>
      <span v-if="!items.length" class="muted">（还没有内容）</span>
    </section>
  </div>
</template>

<style scoped>
.publish {
  display: grid;
  /* two columns when there is room, otherwise the preview goes below the checklist */
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 24px;
}
h3 {
  margin: 0 0 12px;
  font-size: 15px;
}
.check {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
}
.check-label {
  min-width: 0;
}
.actions {
  margin-top: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.block {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 10px 12px;
  margin-bottom: 10px;
}
.block-title {
  font-weight: 600;
  margin-bottom: 6px;
}
.pre {
  white-space: pre-wrap;
  margin: 0;
  line-height: 1.6;
}
.path {
  margin: 0;
  padding-left: 22px;
}
.path li {
  padding: 6px 0;
}
.path li :deep(.el-icon) {
  vertical-align: -2px;
  margin-right: 6px;
}
.path-title {
  margin-right: 8px;
}
</style>
