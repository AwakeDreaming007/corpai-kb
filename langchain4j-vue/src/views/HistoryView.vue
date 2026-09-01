<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listKb } from '../api/kb'
import { deleteHistory, getHistory, listHistory } from '../api/history'
import { getFeedback, submitFeedback } from '../api/feedback'
import { renderMarkdown } from '../utils/markdown'
import { formatLatency, formatDate } from '../utils/format'

const loading = ref(false)
const records = ref([])
const total = ref(0)
const drawerVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)
const feedback = ref({ rating: 0, reason: '' })
const feedbackLoading = ref(false)

const query = reactive({ page: 1, size: 10, kbId: '' })
const kbOptions = ref([])

/** 拉取问答历史列表（后端接口按库查询，未选知识库时不请求） */
const loadHistory = async () => {
  if (query.kbId === '' || query.kbId == null) {
    records.value = []
    total.value = 0
    return
  }
  loading.value = true
  try {
    const data = await listHistory(query)
    records.value = data.records || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

/** 打开详情抽屉并加载回答、来源与反馈 */
const openDetail = async (row) => {
  drawerVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getHistory(row.id)
    const data = await getFeedback(row.id)
    feedback.value = data || { rating: 0, reason: '' }
  } finally {
    feedbackLoading.value = false
    detailLoading.value = false
  }
}

/** 点赞或点踩，点踩时要求填写原因 */
const rateAnswer = async (rating) => {
  if (!detail.value) return
  if (rating === -1 && !feedback.value.reason) {
    const { value } = await ElMessageBox.prompt('请填写点踩原因，便于后续优化', '反馈原因', {
      inputPlaceholder: '例如：答案与文档不一致',
      inputValidator: (value) => !!value?.trim() || '原因不能为空',
    })
    feedback.value.reason = value
  }
  feedback.value.rating = rating
  await submitFeedback({ historyId: detail.value.id, rating, reason: feedback.value.reason })
  ElMessage.success('反馈已提交')
}

/** 删除历史记录前二次确认 */
const removeHistory = async (row) => {
  await ElMessageBox.confirm('确定删除这条问答历史吗？', '删除确认', { type: 'warning' })
  await deleteHistory(row.id)
  ElMessage.success('已删除')
  await loadHistory()
}

const kbLabel = computed(() => (kbOptions.value.find((item) => String(item.id) === String(query.kbId))?.name || ''))

onMounted(async () => {
  const page = await listKb({ page: 1, size: 100 })
  kbOptions.value = page.records || []
  // 默认选中第一个知识库并加载其历史（后端历史接口按库查询）
  if (!query.kbId && kbOptions.value.length) query.kbId = kbOptions.value[0].id
  await loadHistory()
})
</script>

<template>
  <div class="page-container">
    <div class="page-head">
      <div>
        <h1 class="page-title">问答历史</h1>
        <p class="page-subtitle">回看检索过程与模型回答质量</p>
      </div>
      <el-select v-model="query.kbId" clearable filterable placeholder="按知识库过滤" style="width: 220px" @change="loadHistory">
        <el-option v-for="item in kbOptions" :key="item.id" :label="item.name" :value="item.id" />
      </el-select>
    </div>

    <el-table v-loading="loading" :data="records" class="history-table" @row-click="openDetail">
      <el-table-column prop="question" label="问题" min-width="300" show-overflow-tooltip />
      <el-table-column prop="model" label="模型" width="160" />
      <el-table-column prop="latencyMs" label="耗时" width="110">
        <template #default="{ row }">{{ formatLatency(row.latencyMs) }}</template>
      </el-table-column>
      <el-table-column prop="createdAt" label="时间" min-width="170">
        <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button text type="danger" @click.stop="removeHistory(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @size-change="loadHistory"
        @current-change="loadHistory"
      />
    </div>

    <el-drawer v-model="drawerVisible" title="问答详情" size="620px">
      <div v-loading="detailLoading">
        <div v-if="detail" class="history-detail">
          <section class="detail-block">
            <h3>问题</h3>
            <p>{{ detail.question }}</p>
          </section>
          <section class="detail-block">
            <h3>回答</h3>
            <div class="markdown" v-html="renderMarkdown(detail.answer)"></div>
          </section>
          <section class="detail-block">
            <h3>来源</h3>
            <div v-if="detail.sources?.length" class="source-list">
              <div v-for="source in detail.sources" :key="source.seq" class="source-item soft-card">
                <div class="source-head">
                  <el-tag effect="light" round>{{ source.seq }}</el-tag>
                  <span class="doc-name">{{ source.docName }}</span>
                </div>
                <p class="snippet">{{ source.snippet }}</p>
                <div class="score">相似度：{{ Number(source.score || 0).toFixed(4) }}</div>
              </div>
            </div>
            <el-empty v-else description="没有引用文档" :image-size="70" />
          </section>
          <section class="detail-block">
            <h3>反馈</h3>
            <div class="feedback-row">
              <el-button :type="feedback.rating === 1 ? 'primary' : 'default'" @click="rateAnswer(1)">点赞</el-button>
              <el-button :type="feedback.rating === -1 ? 'danger' : 'default'" @click="rateAnswer(-1)">点踩</el-button>
              <el-button v-if="feedback.rating === 0" @click="rateAnswer(0)">清除</el-button>
            </div>
            <p v-if="feedback.reason" class="feedback-reason">原因：{{ feedback.reason }}</p>
          </section>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-3);
}

.history-table {
  cursor: pointer;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--space-4);
}

.history-detail {
  display: grid;
  gap: var(--space-4);
}

.detail-block h3 {
  margin: 0 0 var(--space-2);
  font-size: 16px;
}

.detail-block p {
  margin: 0;
  color: var(--ink-700);
  line-height: 1.7;
}

.markdown :deep(p) {
  margin: 0 0 10px;
}

.markdown :deep(pre.md-code) {
  overflow: auto;
  padding: var(--space-3);
  border-radius: var(--radius-md);
  color: #e6edf3;
  background: #0b1020;
  border: 1px solid rgba(148, 163, 184, 0.28);
}

.markdown :deep(code.hljs) {
  font-family: "JetBrains Mono", "SFMono-Regular", Consolas, monospace;
  font-size: 13px;
}

.markdown :deep(.token-keyword) { color: #c792ea; }
.markdown :deep(.token-string) { color: #7ee787; }
.markdown :deep(.token-comment) { color: #8b949e; }

.source-list {
  display: grid;
  gap: var(--space-3);
}

.source-item {
  padding: var(--space-3);
}

.source-head {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-2);
}

.doc-name {
  font-weight: 700;
}

.snippet {
  margin: 0 0 var(--space-2);
  color: var(--ink-700);
  line-height: 1.7;
}

.score {
  font-size: 12px;
  color: var(--ink-500);
}

.feedback-row {
  display: flex;
  gap: var(--space-2);
}

.feedback-reason {
  margin-top: var(--space-2);
  color: var(--ink-500);
}
</style>
