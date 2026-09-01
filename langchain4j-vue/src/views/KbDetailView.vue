<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteDoc, listDocs, reindexDoc, uploadDoc } from '../api/doc'
import { listKb } from '../api/kb'
import MemberPanel from '../components/MemberPanel.vue'
import { formatDate, formatSize, roleText } from '../utils/format'
import { useUserStore } from '../stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const kbId = String(route.params.id)

const loading = ref(true)
const kb = ref(null)
const activeTab = ref('docs')
const docs = ref([])
const docTotal = ref(0)
const docLoading = ref(false)
const processingTimer = ref(null)
const uploadRef = ref()
const fileList = ref([])
const uploading = ref(false)

const docQuery = reactive({ page: 1, size: 10, status: '' })
const canEdit = computed(() => kb.value?.myRole === 'OWNER' || kb.value?.myRole === 'EDITOR')

/** 文件类型与大小校验 */
const beforeUpload = (file) => {
  const allowed = ['pdf', 'doc', 'docx']
  const ext = file.name.split('.').pop()?.toLowerCase()
  if (!allowed.includes(ext)) {
    ElMessage.error('仅支持 PDF / DOC / DOCX 文件')
    return false
  }
  if (file.size > 20 * 1024 * 1024) {
    ElMessage.error('文件大小不能超过 20MB')
    return false
  }
  uploading.value = true
  return true
}

/** 自定义上传，便于携带 token 并刷新列表 */
const uploadFile = async ({ file }) => {
  try {
    await uploadDoc(kbId, file)
    ElMessage.success('文件已提交处理')
    fileList.value = []
    await loadDocs()
  } finally {
    uploading.value = false
  }
}

/** 加载知识库基础信息（后端无详情路由，从当前用户可见库列表中定位；size 取满避免深分页库找不到） */
const loadKb = async () => {
  const data = await listKb({ page: 1, size: 100, keyword: '' })
  kb.value = (data.records || []).find((item) => String(item.id) === kbId) || null
  loading.value = false
}

/** 加载文档列表 */
const loadDocs = async () => {
  docLoading.value = true
  try {
    const data = await listDocs(kbId, docQuery)
    docs.value = data.records || []
    docTotal.value = data.total || 0
    setupProcessingTimer()
  } finally {
    docLoading.value = false
  }
}

/** 存在处理中文档时启动 3 秒轮询 */
const setupProcessingTimer = () => {
  const hasProcessing = docs.value.some((doc) => doc.status === 0)
  if (hasProcessing && !processingTimer.value) {
    processingTimer.value = setInterval(loadDocs, 3000)
  }
  if (!hasProcessing && processingTimer.value) {
    clearInterval(processingTimer.value)
    processingTimer.value = null
  }
}

/** 删除文档前二次确认 */
const removeDoc = async (row) => {
  await ElMessageBox.confirm(`确定删除文档“${row.docName}”吗？`, '删除确认', { type: 'warning' })
  await deleteDoc(kbId, row.id)
  ElMessage.success('文档已删除')
  await loadDocs()
}

/** 重建索引 */
const rebuildIndex = async (row) => {
  await reindexDoc(kbId, row.id)
  ElMessage.success('已提交重建索引')
  await loadDocs()
}

const statusTag = (status) => ({ 0: { label: '处理中', type: 'primary' }, 1: { label: '成功', type: 'success' }, 2: { label: '失败', type: 'danger' } }[status] || { label: '未知', type: 'info' })

onMounted(async () => {
  await loadKb()
  await loadDocs()
})

onBeforeUnmount(() => {
  if (processingTimer.value) clearInterval(processingTimer.value)
})
</script>

<template>
  <div class="page-container">
    <div v-loading="loading">
      <div class="detail-head soft-card page-motion">
        <div class="head-main">
          <el-button text @click="router.push('/kb')"><el-icon><ArrowLeft /></el-icon>返回</el-button>
          <div class="head-info">
            <h1>{{ kb?.name || '知识库详情' }}</h1>
            <p>{{ kb?.description || '暂无描述' }}</p>
            <div class="head-tags">
              <el-tag effect="light" round>{{ roleText(kb?.myRole) }}</el-tag>
              <el-tag v-if="kb?.ownedByMe" type="warning" effect="light" round>我创建的</el-tag>
            </div>
          </div>
        </div>
        <el-button v-if="kb?.myRole !== 'VIEWER'" type="primary" size="large" @click="router.push(`/kb/${kbId}/chat`)">
          <el-icon><ChatDotRound /></el-icon>进入问答
        </el-button>
      </div>

      <el-tabs v-model="activeTab" class="detail-tabs">
        <el-tab-pane label="文档管理" name="docs">
          <div class="toolbar">
            <el-select v-model="docQuery.status" clearable placeholder="全部状态" style="width: 140px" @change="loadDocs">
              <el-option label="处理中" :value="0" />
              <el-option label="成功" :value="1" />
              <el-option label="失败" :value="2" />
            </el-select>
            <el-upload
              v-if="canEdit"
              ref="uploadRef"
              v-model:file-list="fileList"
              :show-file-list="false"
              :auto-upload="true"
              :http-request="uploadFile"
              :before-upload="beforeUpload"
              accept=".pdf,.doc,.docx"
            >
              <el-button type="primary" :loading="uploading"><el-icon><Upload /></el-icon>上传文档</el-button>
            </el-upload>
            <span class="upload-tip">支持 PDF、DOC、DOCX，最大 20MB</span>
          </div>

          <el-table v-loading="docLoading" :data="docs">
            <el-table-column prop="docName" label="文档名" min-width="220" show-overflow-tooltip />
            <el-table-column label="状态" width="120">
              <template #default="{ row }">
                <el-tooltip :disabled="!row.errorMsg" :content="row.errorMsg || ''" placement="top">
                  <el-tag :type="statusTag(row.status).type">{{ statusTag(row.status).label }}</el-tag>
                </el-tooltip>
              </template>
            </el-table-column>
            <el-table-column prop="segmentCount" label="分段数" width="100" />
            <el-table-column prop="fileSize" label="大小" width="100">
              <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
            </el-table-column>
            <el-table-column prop="fileType" label="类型" width="90" />
            <el-table-column prop="uploadUserName" label="上传人" width="130" />
            <el-table-column prop="createdAt" label="上传时间" min-width="160">
              <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column v-if="canEdit" label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button text type="primary" @click="rebuildIndex(row)">重建</el-button>
                <el-button text type="danger" @click="removeDoc(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane v-if="kb?.myRole === 'OWNER'" label="成员管理" name="members">
          <MemberPanel :kb-id="kbId" />
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<style scoped>
.detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-3);
  margin-bottom: var(--space-4);
}

.page-motion {
  animation: page-content-enter 360ms cubic-bezier(0.22, 1, 0.36, 1) both;
}

.detail-tabs {
  animation: page-content-enter 420ms cubic-bezier(0.22, 1, 0.36, 1) 60ms both;
}

@keyframes page-content-enter {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.head-main {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.head-info h1 {
  margin: 0;
  font-size: 24px;
  letter-spacing: -0.02em;
}

.head-info p {
  margin: 6px 0 10px;
  color: var(--ink-500);
}

.head-tags {
  display: flex;
  gap: var(--space-2);
}

.detail-tabs {
  background: var(--surface-100);
  border-radius: var(--radius-lg);
  padding: var(--space-3);
  box-shadow: var(--shadow-1);
}

.toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-3);
}

.upload-tip {
  color: var(--ink-500);
  font-size: 12px;
}
</style>
