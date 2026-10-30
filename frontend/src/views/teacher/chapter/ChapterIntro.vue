<script setup>
/*
 * Chapter introduction — what students read first when they open this chapter.
 */
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { patchChapter } from '@/api/teacherCourse'

const props = defineProps({ chapter: { type: Object, required: true } })
const emit = defineEmits(['changed'])

const intro = ref(props.chapter.intro || '')
const saving = ref(false)

const TEMPLATE = '学习目标：学完本章，你能……\n\n概要：本章介绍……'

async function save() {
  saving.value = true
  try {
    await patchChapter(props.chapter, { intro: intro.value })
    ElMessage.success('已保存')
    emit('changed')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="intro">
    <el-input v-model="intro" type="textarea" :rows="10" :placeholder="TEMPLATE" />
    <div class="actions">
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      <el-button v-if="!intro" link type="primary" @click="intro = TEMPLATE">插入模板</el-button>
    </div>
  </div>
</template>

<style scoped>
.intro {
  max-width: 760px;
}
.actions {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 12px;
}
</style>
