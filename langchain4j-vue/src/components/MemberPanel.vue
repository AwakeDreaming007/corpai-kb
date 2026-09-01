<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { addMember, listMembers, removeMember, updateMemberRole } from '../api/member'
import { roleText } from '../utils/format'

const props = defineProps({
  kbId: { type: String, required: true },
})

const loading = ref(false)
const members = ref([])
const dialogVisible = ref(false)
const submitLoading = ref(false)
const formRef = ref()

const form = reactive({ username: '', memberRole: 'VIEWER' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  memberRole: [{ required: true, message: '请选择角色', trigger: 'change' }],
}

/** 加载成员列表 */
const loadMembers = async () => {
  loading.value = true
  try {
    members.value = await listMembers(props.kbId) || []
  } finally {
    loading.value = false
  }
}

/** 打开添加成员弹窗 */
const openDialog = () => {
  Object.assign(form, { username: '', memberRole: 'VIEWER' })
  dialogVisible.value = true
}

/** 提交新增成员 */
const submitMember = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitLoading.value = true
  try {
    await addMember(props.kbId, form)
    ElMessage.success('成员已添加')
    dialogVisible.value = false
    await loadMembers()
  } finally {
    submitLoading.value = false
  }
}

/** 修改成员角色；OWNER 需要再次确认转让 */
const changeRole = async (row, role) => {
  if (role === 'OWNER') {
    await ElMessageBox.confirm(`确定将知识库转让给“${row.nickname || row.username}”吗？`, '转让确认', { type: 'warning' })
  }
  await updateMemberRole(props.kbId, row.userId, role)
  ElMessage.success('角色已更新')
  await loadMembers()
}

/** 移除成员前二次确认 */
const deleteMember = async (row) => {
  await ElMessageBox.confirm(`确定移除成员“${row.nickname || row.username}”吗？`, '移除确认', { type: 'warning' })
  await removeMember(props.kbId, row.userId)
  ElMessage.success('成员已移除')
  await loadMembers()
}

onMounted(loadMembers)
</script>

<template>
  <div>
    <div class="panel-head">
      <h3>成员管理</h3>
      <el-button type="primary" @click="openDialog"><el-icon><Plus /></el-icon>添加成员</el-button>
    </div>

    <el-table v-loading="loading" :data="members" row-key="id">
      <el-table-column prop="nickname" label="昵称" min-width="120" />
      <el-table-column prop="username" label="用户名" min-width="120" />
      <el-table-column prop="memberRole" label="角色" width="140">
        <template #default="{ row }">
          <el-select :model-value="row.memberRole" size="small" @change="(value) => changeRole(row, value)">
            <el-option label="所有者" value="OWNER" />
            <el-option label="编辑者" value="EDITOR" />
            <el-option label="查看者" value="VIEWER" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="加入时间" min-width="150" />
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button text type="danger" @click="deleteMember(row)">移除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" title="添加成员" width="460px">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入对方登录用户名" />
        </el-form-item>
        <el-form-item label="角色" prop="memberRole">
          <el-select v-model="form.memberRole" class="full">
            <el-option label="编辑者" value="EDITOR" />
            <el-option label="查看者" value="VIEWER" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitMember">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-3);
}

.panel-head h3 {
  margin: 0;
  font-size: 18px;
}

.full {
  width: 100%;
}
</style>
