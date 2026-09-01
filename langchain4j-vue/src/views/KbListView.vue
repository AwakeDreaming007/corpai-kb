<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createKb, deleteKb, listKb, updateKb } from '../api/kb'
import { useUserStore } from '../stores/user'
import { roleText } from '../utils/format'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const records = ref([])
const total = ref(0)
const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref()

const query = reactive({ page: 1, size: 12, keyword: '' })
const form = reactive({ id: '', name: '', description: '' })

const rules = {
  name: [
    { required: true, message: '请输入知识库名称', trigger: 'blur' },
    { min: 2, max: 40, message: '名称长度 2-40 个字符', trigger: 'blur' },
  ],
  description: [{ max: 120, message: '描述不能超过 120 个字符', trigger: 'blur' }],
}

/** 拉取知识库分页列表 */
const loadList = async () => {
  loading.value = true
  try {
    const data = await listKb(query)
    records.value = data.records || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

/** 打开新建/编辑弹窗 */
const openDialog = (item) => {
  Object.assign(form, item ? { id: item.id, name: item.name, description: item.description } : { id: '', name: '', description: '' })
  dialogVisible.value = true
}

/** 保存知识库信息 */
const submitForm = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (form.id) await updateKb(form.id, form)
    else await createKb(form)
    ElMessage.success(form.id ? '知识库已更新' : '知识库已创建')
    dialogVisible.value = false
    await loadList()
  } finally {
    submitting.value = false
  }
}

/** 删除前二次确认 */
const removeKb = async (item) => {
  await ElMessageBox.confirm(`确定删除“${item.name}”吗？删除后不可恢复。`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  await deleteKb(item.id)
  ElMessage.success('已删除')
  await loadList()
}

const roleTagType = (role) => ({ OWNER: 'warning', EDITOR: 'primary', VIEWER: 'info' }[role] || 'info')

onMounted(loadList)
</script>

<template>
  <div class="page-container">
    <div class="page-head">
      <div>
        <h1 class="page-title">知识库</h1>
        <p class="page-subtitle">沉淀文档资产，让团队检索更高效</p>
      </div>
      <el-button v-perm="'kb:create'" type="primary" @click="openDialog()">
        <el-icon><Plus /></el-icon>新建知识库
      </el-button>
    </div>

    <div class="toolbar">
      <el-input v-model="query.keyword" placeholder="搜索知识库名称" clearable class="search-input" @keyup.enter="loadList">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-button @click="loadList">搜索</el-button>
    </div>

    <el-skeleton v-if="loading" :rows="6" animated />
    <el-empty v-else-if="!records.length" description="暂无知识库，先创建一个吧" />

    <div v-else class="kb-grid">
      <article
        v-for="(item, index) in records"
        :key="item.id"
        class="kb-card soft-card"
        :style="{ '--card-index': index }"
        @click="router.push(`/kb/${item.id}`)"
      >
        <div class="card-top" :class="item.ownedByMe ? 'owned' : 'shared'"></div>
        <div class="card-body">
          <div class="card-head">
            <h3>{{ item.name }}</h3>
            <el-tag :type="roleTagType(item.myRole)" effect="light" round>{{ roleText(item.myRole) }}</el-tag>
          </div>
          <p class="card-desc">{{ item.description || '暂无描述' }}</p>
          <div class="card-foot">
            <span class="owner">{{ item.ownedByMe ? '我创建的' : `共享 · ${item.ownerName || '-'}` }}</span>
            <span class="time">{{ item.createdAt || '-' }}</span>
          </div>
          <div class="card-actions">
            <el-button text type="primary" @click.stop="router.push(`/kb/${item.id}`)">管理</el-button>
            <el-button v-if="item.myRole !== 'VIEWER'" text @click.stop="router.push(`/kb/${item.id}/chat`)">问答</el-button>
            <el-button v-if="item.ownedByMe" text type="danger" @click.stop="removeKb(item)">删除</el-button>
          </div>
        </div>
      </article>
    </div>

    <div class="pagination">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[12, 24, 48]"
        layout="total, sizes, prev, pager, next"
        @size-change="loadList"
        @current-change="loadList"
      />
    </div>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑知识库' : '新建知识库'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" maxlength="40" show-word-limit placeholder="例如：产品资料库" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="4" maxlength="120" show-word-limit placeholder="简要说明知识库用途" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-3);
}

.toolbar {
  display: flex;
  gap: var(--space-2);
  margin-bottom: var(--space-4);
}

.search-input {
  width: min(320px, 100%);
}

.kb-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--space-3);
}

.kb-card {
  overflow: hidden;
  cursor: pointer;
  animation: card-enter 360ms cubic-bezier(0.22, 1, 0.36, 1) both;
  animation-delay: calc(var(--card-index, 0) * 40ms);
}

.kb-card:hover {
  transform: translateY(-4px);
}

@keyframes card-enter {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.card-top {
  height: 6px;
}

.card-top.owned {
  background: linear-gradient(90deg, #3b82f6, #6366f1);
}

.card-top.shared {
  background: linear-gradient(90deg, #22d3ee, #0ea5e9);
}

.card-body {
  padding: var(--space-3);
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.card-head h3 {
  margin: 0;
  font-size: 17px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-desc {
  min-height: 42px;
  margin: var(--space-2) 0;
  color: var(--ink-500);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-foot {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--ink-500);
}

.card-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--space-2);
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--space-4);
}
</style>
