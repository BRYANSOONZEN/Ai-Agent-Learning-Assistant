<script setup>
/*
 * AI call log: every model call (teacher drafts and student dialogue turns)
 * with tokens, total duration (including retries), cost and whether it succeeded.
 */
import { onMounted, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import { getAiLogs } from '@/api/aiLog'

const totals = ref(null)
const records = ref([])
const loading = ref(false)

const taskLabels = { TOPIC_DRAFT: '老师拆要点', DIALOGUE_TURN: '学生对话' }

async function load() {
  loading.value = true
  try {
    const data = await getAiLogs(200)
    totals.value = data.totals
    records.value = data.records
  } finally {
    loading.value = false
  }
}

const seconds = (ms) => (ms == null ? '-' : (ms / 1000).toFixed(1) + ' 秒')

onMounted(load)
</script>

<template>
  <div>
    <PageHeader title="AI 调用记录" description="每次调用大模型都会记一条：token、总耗时（含重试）、费用、是否成功。">
      <el-button icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </PageHeader>

    <div v-if="totals" class="stats">
      <div class="app-card stat">
        <span class="label">调用次数</span>
        <strong>{{ totals.calls }}</strong>
        <span class="sub">失败 {{ totals.failures }} 次</span>
      </div>
      <div class="app-card stat">
        <span class="label">输入 token</span>
        <strong>{{ totals.inputTokens.toLocaleString() }}</strong>
      </div>
      <div class="app-card stat">
        <span class="label">输出 token</span>
        <strong>{{ totals.outputTokens.toLocaleString() }}</strong>
      </div>
      <div class="app-card stat">
        <span class="label">总耗时</span>
        <strong>{{ seconds(totals.totalLatencyMs) }}</strong>
        <span class="sub">平均 {{ seconds(totals.avgLatencyMs) }}</span>
      </div>
      <div class="app-card stat">
        <span class="label">费用</span>
        <strong>{{ Number(totals.costCny).toFixed(4) }} 元</strong>
      </div>
    </div>

    <div class="app-card">
      <el-table v-loading="loading" :data="records" stripe>
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="detail">
              <h4>模型返回</h4>
              <pre>{{ row.responseJson || '（无）' }}</pre>
              <h4>发送的请求</h4>
              <pre>{{ row.requestJson }}</pre>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="id" label="#" width="60" />
        <el-table-column prop="createdAt" label="时间" width="170" />
        <el-table-column label="任务" width="110">
          <template #default="{ row }">{{ taskLabels[row.task] || row.task }}</template>
        </el-table-column>
        <el-table-column prop="model" label="模型" min-width="150" />
        <el-table-column prop="inputTokens" label="输入 token" width="100" align="right" />
        <el-table-column prop="outputTokens" label="输出 token" width="100" align="right" />
        <el-table-column label="耗时" width="90" align="right">
          <template #default="{ row }">{{ seconds(row.latencyMs) }}</template>
        </el-table-column>
        <el-table-column label="结果" width="120">
          <template #default="{ row }">
            <el-tag v-if="!row.success" type="danger" size="small">调用失败</el-tag>
            <el-tag v-else-if="row.passedCheck === false" type="warning" size="small">未通过检查</el-tag>
            <el-tag v-else type="success" size="small">成功</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="error" label="原因" min-width="180" show-overflow-tooltip />
        <template #empty><el-empty description="还没有调用记录" /></template>
      </el-table>
    </div>
  </div>
</template>

<style scoped>
.stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: var(--app-space-md);
  margin-bottom: var(--app-space-md);
}
.stat {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.stat .label,
.stat .sub {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.stat strong {
  font-size: 22px;
}
.detail {
  padding: 0 16px;
}
.detail h4 {
  margin: 8px 0 4px;
}
.detail pre {
  white-space: pre-wrap;
  word-break: break-all;
  background: var(--el-fill-color-light);
  padding: 8px;
  border-radius: 6px;
  max-height: 240px;
  overflow: auto;
  margin: 0;
}
</style>
