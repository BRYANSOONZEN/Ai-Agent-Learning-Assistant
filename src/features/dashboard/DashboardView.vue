<script setup>
import AppIcon from '../../components/AppIcon.vue'
import StatCard from '../../components/StatCard.vue'

const emit = defineEmits(['navigate'])
</script>

<template>
  <div class="view-stack dashboard-view">
    <section class="welcome-banner">
      <div class="welcome-copy">
        <span class="eyebrow">WEDNESDAY · OCT 22, 2026</span>
        <h2>早上好，林晓雨 <span>✦</span></h2>
        <p>今天继续探索「边界值分析」吧。你的学习助手已经为你准备好了下一步。</p>
        <button class="button button-light" @click="emit('navigate', 'learn')">继续学习 <AppIcon name="arrow" :size="16" /></button>
      </div>
      <div class="banner-illustration" aria-hidden="true">
        <div class="orbit orbit-one"></div><div class="orbit orbit-two"></div><div class="planet planet-one"></div><div class="planet planet-two"></div>
        <div class="illustration-card"><span class="mini-spark">✦</span><strong>68%</strong><small>本周掌握度</small></div>
      </div>
    </section>

    <section class="stats-grid">
      <StatCard title="本周学习时间" value="2h 45m" detail="+18%" icon="clock" tone="blue" />
      <StatCard title="已掌握知识点" value="12 / 18" detail="+2" icon="check" tone="green" />
      <StatCard title="待处理练习" value="03" detail="今天" icon="book-open" tone="orange" />
    </section>

    <section class="content-grid two-columns">
      <article class="card continue-card">
        <div class="section-heading"><div><span class="eyebrow dark-eyebrow">CONTINUE LEARNING</span><h3>接着上次学习</h3></div><button class="text-button" @click="emit('navigate', 'learn')">查看课程 <AppIcon name="arrow" :size="15" /></button></div>
        <div class="lesson-row">
          <div class="lesson-icon"><AppIcon name="spark" :size="23" /></div>
          <div class="lesson-info"><strong>边界值分析</strong><span>软件测试 · 第 2 章</span><div class="progress-line"><span style="width: 68%"></span></div><small>已完成 68%</small></div>
          <span class="lesson-time">约 12 分钟</span>
        </div>
        <div class="stage-mini"><span class="done"><i></i>引入</span><span class="done"><i></i>探究</span><span class="current"><i></i>解释</span><span><i></i>迁移</span><span><i></i>评价</span></div>
      </article>

      <article class="card activity-card">
        <div class="section-heading"><div><span class="eyebrow dark-eyebrow">RECENT ACTIVITY</span><h3>最近活动</h3></div><button class="more-button"><AppIcon name="dots" :size="19" /></button></div>
        <div class="activity-list">
          <div class="activity-item"><span class="activity-icon activity-blue"><AppIcon name="spark" :size="15" /></span><div><strong>完成了一个引导提示</strong><small>边界值分析 · 10 分钟前</small></div><span class="activity-score">+5 XP</span></div>
          <div class="activity-item"><span class="activity-icon activity-green"><AppIcon name="check" :size="15" /></span><div><strong>掌握度提升了 8%</strong><small>等价类划分 · 昨天</small></div><span class="activity-score">+8%</span></div>
          <div class="activity-item"><span class="activity-icon activity-orange"><AppIcon name="book" :size="15" /></span><div><strong>保存了一份学习笔记</strong><small>测试设计基础 · 昨天</small></div><span class="activity-score">查看</span></div>
        </div>
      </article>
    </section>

    <section class="card recommended-card">
      <div class="section-heading"><div><span class="eyebrow dark-eyebrow">RECOMMENDED FOR YOU</span><h3>为你推荐</h3></div><button class="text-button">刷新推荐 <AppIcon name="arrow" :size="15" /></button></div>
      <div class="recommend-grid">
        <div class="recommend-item"><span class="recommend-number">01</span><div><strong>边界值 · 迁移练习</strong><p>把刚刚的概念用到一个新的输入范围。</p></div><span class="tag tag-purple">8 分钟</span></div>
        <div class="recommend-item"><span class="recommend-number">02</span><div><strong>理解错误：范围内 vs 范围外</strong><p>针对你刚才的答案，补一条原理句。</p></div><span class="tag tag-teal">复习</span></div>
        <div class="recommend-item"><span class="recommend-number">03</span><div><strong>查看本章学习路径</strong><p>了解边界值之后的下一块知识点。</p></div><span class="tag tag-gray">浏览</span></div>
      </div>
    </section>
  </div>
</template>
