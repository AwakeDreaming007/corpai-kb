<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createSession, listSessions } from '../api/kb'
import { listHistory, getHistory } from '../api/history'
import { streamChat } from '../api/chat'
import { renderMarkdown } from '../utils/markdown'
import { formatDate } from '../utils/format'

const route = useRoute()
const kbId = String(route.params.id)

const loading = ref(false)
const sending = ref(false)
const sessions = ref([])
const activeSession = ref('')
const question = ref('')
const inputRef = ref()
const messageListRef = ref()
const messages = ref([])
const sources = ref([])
const sourceDrawerVisible = ref(false)
const currentSources = ref([])
const stopFn = ref(null)
const streaming = ref(false)
const historyLoading = ref(false)

const assistantAnswer = computed(() => messages.value.filter((item) => item.role === 'assistant').pop())

/** 创建新会话并加载会话列表 */
const createNewSession = async () => {
  const sessionId = await createSession(kbId)
  activeSession.value = sessionId
  messages.value = []
  sources.value = []
  await loadSessions()
  return sessionId
}

/** 加载左侧会话列表 */
const loadSessions = async () => {
  loading.value = true
  try {
    const data = await listSessions(kbId, { page: 1, size: 50 })
    sessions.value = data.records || []
  } finally {
    loading.value = false
  }
}

/** 选择会话并加载该会话的历史问答记录 */
const selectSession = (session) => {
  if (sending.value) {
    ElMessage.warning('请等待当前回答完成后再切换会话')
    return
  }
  activeSession.value = session.id
  loadSessionHistory(session.id)
}

/**
 * 加载指定会话的历史消息：
 * 列表接口已裁剪 answer/sources 大字段，需逐条取详情拼装成问答对；
 * 按 id 升序（列表接口是倒序），历史消息标记 streaming=false 避免光标动画。
 */
const loadSessionHistory = async (sessionId) => {
  messages.value = []
  sources.value = []
  historyLoading.value = true
  try {
    const page = await listHistory({ sessionId, kbId, page: 1, size: 100 })
    const records = (page.records || []).slice().sort((a, b) => a.id - b.id)
    const details = await Promise.all(records.map((item) => getHistory(item.id)))
    const assembled = []
    for (const detail of details) {
      assembled.push({ role: 'user', content: detail.question || '' })
      assembled.push({
        role: 'assistant',
        content: detail.answer || '',
        sources: detail.sources || [],
        streaming: false,
      })
    }
    messages.value = assembled
    if (assembled.length) sources.value = assembled[assembled.length - 1].sources || []
    await scrollToBottom()
  } catch {
    // request.js 拦截器已弹出错误提示，这里仅保证界面可用
    messages.value = []
  } finally {
    historyLoading.value = false
  }
}

/** 滚动到消息底部 */
const scrollToBottom = async () => {
  await nextTick()
  if (messageListRef.value) messageListRef.value.scrollTop = messageListRef.value.scrollHeight
}

/** 结束流式状态（统一入口：done/error/aborted 均调用，幂等） */
const finishStreaming = (assistantMessage) => {
  assistantMessage.streaming = false
  sending.value = false
  streaming.value = false
  stopFn.value = null
}

/** 发起流式问答 */
const sendMessage = async () => {
  const text = question.value.trim()
  if (!text || sending.value) return
  if (!activeSession.value) await createNewSession()

  messages.value.push({ role: 'user', content: text })
  const assistantMessage = reactive({ role: 'assistant', content: '', sources: [], streaming: true })
  messages.value.push(assistantMessage)
  question.value = ''
  sending.value = true
  streaming.value = true
  await scrollToBottom()

  // streamChat 同步返回 abort 函数；流结束（done/error/aborted 事件）时才复位发送态
  stopFn.value = streamChat(kbId, { sessionId: activeSession.value, question: text }, (type, data) => {
    if (type === 'token') {
      assistantMessage.content += data.content || ''
      scrollToBottom()
    } else if (type === 'sources') {
      assistantMessage.sources = data || []
      sources.value = data || []
    } else if (type === 'error') {
      ElMessage.error(data?.message || '回答失败')
      finishStreaming(assistantMessage)
    } else if (type === 'aborted') {
      finishStreaming(assistantMessage)
    } else if (type === 'done') {
      if (data?.sessionId && data.sessionId !== activeSession.value) activeSession.value = data.sessionId
      // sse.js 在流读完还会补发一个空 done，用 stopFn 存在性去重，只刷新一次
      if (stopFn.value) {
        finishStreaming(assistantMessage)
        scrollToBottom()
      }
    }
  })
}

/** 停止当前请求并恢复输入 */
const stopStream = () => {
  if (stopFn.value) stopFn.value()
  sending.value = false
  streaming.value = false
  stopFn.value = null
}

/** 打开来源抽屉 */
const openSources = (message) => {
  currentSources.value = message.sources || sources.value || []
  sourceDrawerVisible.value = true
}

/** 输入框回车发送，Shift+Enter 换行 */
const handleKeydown = (event) => {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    sendMessage()
  }
}

onMounted(() => {
  loadSessions()
  inputRef.value?.focus?.()
})

onBeforeUnmount(() => stopStream())
</script>

<template>
  <div class="page-container chat-page">
    <aside class="session-panel">
      <el-button class="new-session" type="primary" @click="createNewSession"><el-icon><Plus /></el-icon>新建会话</el-button>
      <el-skeleton v-if="loading" :rows="6" animated />
      <div v-else class="session-list">
        <div
          v-for="(session, index) in sessions"
          :key="session.id"
          class="session-item"
          :style="{ '--session-index': index }"
          :class="{ active: session.id === activeSession }"
          @click="selectSession(session)"
        >
          <div class="session-title">{{ session.title || '未命名会话' }}</div>
          <div class="session-time">{{ formatDate(session.lastActiveAt || session.createdAt) }}</div>
        </div>
        <el-empty v-if="!sessions.length" description="暂无会话" :image-size="70" />
      </div>
    </aside>

    <section class="chat-panel soft-card">
      <div ref="messageListRef" class="message-list">
        <el-empty v-if="!messages.length" description="开始一次企业知识检索吧" />
        <div v-for="(message, index) in messages" :key="index" class="message-row" :class="message.role" :style="{ '--message-index': index }">
          <div class="bubble" :class="message.role">
            <div v-if="message.role === 'assistant'" class="markdown" v-html="renderMarkdown(message.content)"></div>
            <div v-else class="plain">{{ message.content }}</div>
            <div v-if="message.streaming" class="cursor"></div>
          </div>
          <div v-if="message.role === 'assistant' && message.sources?.length" class="source-entry">
            <el-button text type="primary" @click="openSources(message)">
              <el-icon><Link /></el-icon>参考来源 {{ message.sources.map((item) => `[${item.seq}]`).join(' ') }}
            </el-button>
          </div>
        </div>
      </div>

      <div class="input-area">
        <textarea
          ref="inputRef"
          v-model="question"
          class="chat-input"
          rows="3"
          placeholder="输入问题，Enter 发送，Shift + Enter 换行"
          :disabled="sending"
          @keydown="handleKeydown"
        ></textarea>
        <div class="input-actions">
          <span class="input-tip">回答内容基于已上传文档生成</span>
          <el-button v-if="sending" type="danger" @click="stopStream"><el-icon><CircleClose /></el-icon>停止</el-button>
          <el-button v-else type="primary" @click="sendMessage"><el-icon><Promotion /></el-icon>发送</el-button>
        </div>
      </div>
    </section>

    <el-drawer v-model="sourceDrawerVisible" title="参考来源" size="420px">
      <div class="source-list">
        <div v-for="source in currentSources" :key="source.seq" class="source-item soft-card">
          <div class="source-head">
            <el-tag effect="light" round>{{ source.seq }}</el-tag>
            <span class="doc-name">{{ source.docName }}</span>
          </div>
          <p class="snippet">{{ source.snippet }}</p>
          <div class="score">相似度：{{ Number(source.score || 0).toFixed(4) }}</div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.chat-page {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: var(--space-3);
  height: calc(100vh - 112px);
}

.session-panel {
  background: var(--surface-100);
  border-radius: var(--radius-lg);
  padding: var(--space-3);
  box-shadow: var(--shadow-1);
  overflow: hidden;
}

.new-session {
  width: 100%;
  margin-bottom: var(--space-3);
}

.session-list {
  height: calc(100% - 64px);
  overflow: auto;
}

.session-item {
  position: relative;
  padding: var(--space-2) var(--space-2) var(--space-2) var(--space-3);
  border-radius: var(--radius-md);
  cursor: pointer;
  animation: session-enter 280ms ease-out both;
  animation-delay: calc(var(--session-index, 0) * 35ms);
  transition: background-color 160ms ease-out, transform 160ms ease-out;
}

.session-item:hover {
  transform: translateX(3px);
}

@keyframes session-enter {
  from {
    opacity: 0;
    transform: translateX(-6px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.session-item:hover,
.session-item.active {
  background: var(--primary-050);
}

.session-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
}

.session-time {
  margin-top: 4px;
  font-size: 12px;
  color: var(--ink-500);
}

.chat-panel {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-4);
}

.message-row {
  display: flex;
  margin-bottom: var(--space-3);
  animation: message-enter 240ms ease-out both;
  animation-delay: calc(var(--message-index, 0) * 25ms);
}

@keyframes message-enter {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.message-row.user {
  justify-content: flex-end;
}

.bubble {
  max-width: min(720px, 82%);
  padding: 12px 16px;
  border-radius: 16px;
  line-height: 1.7;
}

.bubble.user {
  color: #fff;
  background: linear-gradient(135deg, var(--primary-600), #4f46e5);
}

.bubble.assistant {
  background: var(--surface-050);
  border: 1px solid var(--ink-200);
}

.plain {
  white-space: pre-wrap;
}

.cursor {
  display: inline-block;
  width: 2px;
  height: 18px;
  margin-left: 2px;
  vertical-align: text-bottom;
  background: var(--primary-600);
  animation: blink 1s steps(2, start) infinite;
}

@keyframes blink {
  to {
    visibility: hidden;
  }
}

.source-entry {
  margin-top: 6px;
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

.input-area {
  border-top: 1px solid var(--ink-200);
  padding: var(--space-3);
  background: rgba(255, 255, 255, 0.96);
}

.chat-input {
  width: 100%;
  resize: none;
  min-height: 84px;
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-md);
  border: 1px solid var(--ink-200);
  outline: none;
  background: var(--surface-100);
  color: var(--ink-900);
  transition: border-color 160ms ease-out, box-shadow 160ms ease-out;
}

.chat-input:focus {
  border-color: var(--primary-600);
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12);
}

.input-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: var(--space-2);
}

.input-tip {
  font-size: 12px;
  color: var(--ink-500);
}

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
</style>
