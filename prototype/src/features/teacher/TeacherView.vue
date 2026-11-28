<script setup>
import { computed, ref } from 'vue'
import AppIcon from '../../components/AppIcon.vue'
import { reviewItems } from '../../data/demo'

const selectedId = ref(reviewItems[0].id)
const selected = computed(() => reviewItems.find(item => item.id === selectedId.value))
const reviewed = ref(false)
function markReviewed() { reviewed.value = true }
</script>

<template>
  <div class="view-stack">
    <section class="page-intro"><div><span class="eyebrow dark-eyebrow">M10 · HUMAN REVIEW</span><h2>教师审核</h2><p>查看学生的作答证据与系统判定，在需要时修改分类或调整提示配置。</p></div><div class="teacher-date"><span class="live-status"><i></i> 今日课堂运行中</span><strong>第 2 章 · 软件测试</strong></div></section>
    <section class="teacher-summary"><div class="teacher-summary-main"><span class="eyebrow">TODAY'S OVERVIEW</span><h3>3 条记录需要你的判断</h3><p>系统不会自动修改成绩，所有干预都需要教师确认后才会生效。</p></div><div class="teacher-summary-stat"><strong>86%</strong><span>判定一致率</span></div><div class="teacher-summary-stat"><strong>12m</strong><span>平均审核时长</span></div></section>
    <section class="review-layout"><div class="review-list card"><div class="review-list-heading"><h3>待审核记录</h3><span class="source-count">2 待处理</span></div><button v-for="item in reviewItems" :key="item.id" class="review-list-item" :class="{ active: selectedId === item.id }" @click="selectedId = item.id; reviewed = false"><span class="avatar avatar-review">{{ item.avatar }}</span><div><strong>{{ item.student }}</strong><p>{{ item.issue }}</p><small>{{ item.time }}</small></div><span class="review-status" :class="item.status === '已处理' ? 'done' : ''">{{ item.status }}</span></button></div><div class="review-detail card"><div class="detail-heading"><div><span class="eyebrow dark-eyebrow">{{ selected.id }} · STUDENT EVENT</span><h3>{{ selected.student }} 的作答判定</h3></div><span class="status-chip" :class="{ done: reviewed || selected.status === '已处理' }">{{ reviewed || selected.status === '已处理' ? '已审核' : '待审核' }}</span></div><div class="student-answer"><div class="answer-label"><span>学生作答</span><small>今天 09:36 · 评价阶段</small></div><blockquote>“我会选择范围内的两个值和一个边界外的值，因为这样可以检查系统是否接受合法输入。”</blockquote></div><div class="evidence-box"><div class="evidence-heading"><span class="evidence-mark">✦</span><div><strong>系统判定</strong><small>由 E1 + M5 生成 · 置信度 0.86</small></div><span class="level-chip">{{ selected.level }} 提示后</span></div><p>学生已经覆盖了边界上的检查点，并说明了预期结果。建议将该作答标记为“理解基本概念”，下一次进入迁移练习。</p><div class="evidence-source"><span>依据</span><span>S1 · 原理句</span><span>S4 · 检查清单</span></div></div><div class="review-form"><label>修改判定（可选）<select><option>理解基本概念</option><option>需要补充边界外值</option><option>证据不足，暂不判断</option></select></label><label>教师备注（可选）<textarea placeholder="留下给学生或下次提示使用的备注……"></textarea></label></div><div class="detail-actions"><button class="outline-button">退回补充证据</button><button class="button button-primary" @click="markReviewed"><AppIcon name="check" :size="16" /> 确认审核</button></div></div></section>
  </div>
</template>
