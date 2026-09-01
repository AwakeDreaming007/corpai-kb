<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listRoles, listUsers, updateUserRoles, updateUserStatus } from '../api/sys'

const loading = ref(false)
const records = ref([])
const total = ref(0)
const dialogVisible = ref(false)
const saving = ref(false)
const roleOptions = ref([])
const currentRow = ref(null)
const selectedRoles = ref([])

const query = reactive({ page: 1, size: 10, keyword: '' })

/** 角色编码 -> 角色名称映射（列表接口的 roles 是编码字符串数组） */
const roleNameByCode = computed(() => {
  const map = {}
  for (const role of roleOptions.value) map[role.roleCode] = role.roleName
  return map
})

/** 加载用户分页列表 */
const loadUsers = async () => {
  loading.value = true
  try {
    const data = await listUsers(query)
    records.value = data.records || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

/** 切换启用/禁用：switch 回传目标状态；禁用前二次确认 */
const changeStatus = async (row, targetStatus) => {
  if (targetStatus === 0) {
    try {
      await ElMessageBox.confirm(`确定禁用用户“${row.username}”吗？`, '禁用确认', { type: 'warning' })
    } catch {
      await loadUsers() // 取消确认：刷新以回滚开关显示
      return
    }
  }
  await updateUserStatus(row.id, targetStatus)
  ElMessage.success(targetStatus === 1 ? '已启用' : '已禁用')
  await loadUsers()
}

/** 打开角色分配弹窗（列表 roles 为角色编码，需映射回角色 id 供多选框回显） */
const openRoleDialog = (row) => {
  currentRow.value = row
  selectedRoles.value = (row.roles || [])
    .map((code) => roleOptions.value.find((item) => item.roleCode === code)?.id)
    .filter((id) => id != null)
  dialogVisible.value = true
}

/** 保存角色分配 */
const saveRoles = async () => {
  saving.value = true
  try {
    await updateUserRoles(currentRow.value.id, selectedRoles.value)
    ElMessage.success('角色已分配')
    dialogVisible.value = false
    await loadUsers()
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  roleOptions.value = await listRoles()
  await loadUsers()
})
</script>

<template>
  <div class="page-container">
    <div class="page-head">
      <div>
        <h1 class="page-title">用户管理</h1>
        <p class="page-subtitle">控制账号状态与角色授权</p>
      </div>
      <el-input v-model="query.keyword" clearable placeholder="搜索用户名/昵称" style="width: 280px" @keyup.enter="loadUsers" />
    </div>

    <el-table v-loading="loading" :data="records">
      <el-table-column prop="username" label="用户名" min-width="150" />
      <el-table-column prop="nickname" label="昵称" min-width="150" />
      <el-table-column label="角色" min-width="220">
        <template #default="{ row }">
          <el-tag v-for="code in row.roles" :key="code" class="role-tag" :type="code === 'ADMIN' ? 'danger' : 'primary'" effect="light">
            {{ roleNameByCode[code] || code }}
          </el-tag>
          <span v-if="!row.roles?.length" class="muted">未分配</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="150" align="center">
        <template #default="{ row }">
          <el-switch
            :model-value="row.status"
            :active-value="1"
            :inactive-value="0"
            inline-prompt
            active-text="启用"
            inactive-text="禁用"
            @change="(val) => changeStatus(row, val)"
          />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="130">
        <template #default="{ row }">
          <el-button text type="primary" @click="openRoleDialog(row)">分配角色</el-button>
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
        @size-change="loadUsers"
        @current-change="loadUsers"
      />
    </div>

    <el-dialog v-model="dialogVisible" :title="`分配角色 · ${currentRow?.username || ''}`" width="480px">
      <el-select v-model="selectedRoles" multiple class="full" placeholder="选择角色">
        <el-option v-for="role in roleOptions" :key="role.id" :label="role.roleName" :value="role.id" />
      </el-select>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveRoles">保存</el-button>
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

.role-tag {
  margin-right: 6px;
}

.muted {
  color: var(--ink-500);
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--space-4);
}

.full {
  width: 100%;
}
</style>
