<script setup>
/*
 * Student page: AI consolidation dialogue for chapter 1 (all its topics).
 * The dialogue itself is components/AiDialogue.vue, also used by the AI 对话 items in CourseBuilder.
 */
import PageHeader from '@/components/PageHeader.vue'
import AiDialogue from '@/components/AiDialogue.vue'

const CHAPTER_ID = 1
</script>

<template>
  <div>
    <PageHeader title="AI 巩固对话" description="第 1 章 · 边界值分析。AI 会提问并根据你的回答给提示。" />
    <AiDialogue :chapter-id="CHAPTER_ID" />
  </div>
</template>
