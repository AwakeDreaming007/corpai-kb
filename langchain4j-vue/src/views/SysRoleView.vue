<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createRole, deleteRole, listPermissions, listRoles, updateRole, updateRolePermissions } from '../api/sys'

const loading = ref(false)
const records = ref([])
const permissions = ref([])
const dialogVisible = ref(false)
const permDialogVisible = ref(false)
const saving = ref(false)
const formRef = ref()
const currentRole = ref(null)
const selectedPermissions = ref([])
const permKeyword = ref('')

const form = reactive({ id: '', roleCode: '', roleName: '', description: '', builtIn: false })
const rules = {
  roleCode: [
    { required: true, message: '请输入角色编码', trigger: 'blur' },
    { pattern: /^[a-z][a-z0-9:_-]*$/, message: '仅支持小写字母、数字、冒号、下划线和横线', trigger: 'blur' },
  ],
  roleName: [
    { required: true, message: '请输入角色名称', trigger: 'blur' },
    { min: 2, max: 30, message: '名称长度 2-30 个字符', trigger: 'blur' },
  ],
}

const isEdit = computed(() => !!form.id)

const permNameByCode = computed(() => {
  const map = {}
  for (const p of permissions.value) map[p.permCode] = p.permName
  return map
})

/** 加载角色与权限基础数据 */
const loadRoles = async () => {
  loading.value = true
  try {
    records.value = await listRoles() || []
  } finally {
    loading.value = false
  }
}

/** 打开新建/编辑弹窗，内置角色编码只读 */
const openDialog = (role) => {
  Object.assign(form, role
    ? { id: role.id, roleCode: role.roleCode, roleName: role.roleName, description: role.description, builtIn: !!role.builtIn }
    : { id: '', roleCode: '', roleName: '', description: '', builtIn: false })
  dialogVisible.value = true
}

/** 保存角色信息 */
const submitRole = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (form.id) await updateRole(form.id, form)
    else await createRole(form)
    ElMessage.success('角色已保存')
    dialogVisible.value = false
    await loadRoles()
  } finally {
    saving.value = false
  }
}

/** 按模块分组 + 关键词过滤的权限 */
const filteredPermissions = computed(() => {
  const kw = permKeyword.value.trim().toLowerCase()
  if (!kw) return permissions.value
  return permissions.value.filter(
    (p) => p.permName.toLowerCase().includes(kw) || p.permCode.toLowerCase().includes(kw)
  )
})

const groupedPermissions = computed(() => {
  const map = new Map()
  for (const p of filteredPermissions.value) {
    const seg = (p.permCode || '').split(':')[0].toLowerCase() || 'common'
    const label = seg === 'common' ? '通用' : seg.toUpperCase()
    if (!map.has(seg)) map.set(seg, { key: seg, label, items: [] })
    map.get(seg).items.push(p)
  }
  return Array.from(map.values())
})

/** 打开权限分配弹窗：role.permissions 为权限编码数组，映射回权限 id 供复选框回显 */
const openPermDialog = (role) => {
  currentRole.value = role
  permKeyword.value = ''
  selectedPermissions.value = (role.permissions || [])
    .map((code) => permissions.value.find((item) => item.permCode === code)?.id)
    .filter((id) => id != null)
  permDialogVisible.value = true
}

/** 全选 / 清空（仅作用于当前筛选结果，全选为并集不清空隐藏项） */
const selectAllVisible = () => {
  for (const p of filteredPermissions.value) {
    if (!selectedPermissions.value.includes(p.id)) selectedPermissions.value.push(p.id)
  }
}
const clearAll = () => { selectedPermissions.value = [] }

/** 保存权限分配 */
const savePermissions = async () => {
  saving.value = true
  try {
    await updateRolePermissions(currentRole.value.id, selectedPermissions.value)
    ElMessage.success('权限已更新，相关用户重新登录后生效')
    permDialogVisible.value = false
    await loadRoles()
  } finally {
    saving.value = false
  }
}

/** 删除角色；内置角色按钮禁用；取消确认直接返回，避免未捕获拒绝 */
const removeRole = async (role) => {
  try {
    await ElMessageBox.confirm(`确定删除角色“${role.roleName}”吗？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  await deleteRole(role.id)
  ElMessage.success('角色已删除')
  await loadRoles()
}

onMounted(async () => {
  permissions.value = await listPermissions() || []
  await loadRoles()
})
</script>

<template>
  <div class="page-container">
    <div class="page-head">
      <div>
        <h1 class="page-title">角色管理</h1>
        <p class="page-subtitle">维护角色与权限边界</p>
      </div>
      <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新建角色</el-button>
    </div>

    <div class="table-wrap">
      <el-table v-loading="loading" :data="records">
        <el-table-column prop="roleName" label="名称" min-width="150" />
        <el-table-column prop="roleCode" label="编码" min-width="180" />
        <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip />
        <el-table-column label="内置" width="100">
          <template #default="{ row }">
            <el-tag :type="row.builtIn ? 'warning' : 'info'" effect="light">{{ row.builtIn ? '内置' : '自定义' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="权限" min-width="300">
          <template #default="{ row }">
            <el-tag v-for="perm in row.permissions" :key="perm" class="perm-tag" effect="plain">{{ permNameByCode[perm] || perm }}</el-tag>
            <span v-if="!row.permissions?.length" class="muted">未分配</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="openPermDialog(row)">权限</el-button>
            <el-button text :disabled="row.builtIn" @click="openDialog(row)">编辑</el-button>
            <el-button text type="danger" :disabled="row.builtIn" @click="removeRole(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑角色' : '新建角色'" width="520px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="角色编码" prop="roleCode">
          <el-input v-model="form.roleCode" :disabled="form.builtIn" placeholder="例如 kb:create" />
        </el-form-item>
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="form.roleName" :disabled="form.builtIn" placeholder="例如 知识库管理员" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="简要说明角色职责" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitRole">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="permDialogVisible"
      :title="`分配权限 · ${currentRole?.roleName || ''}`"
      width="min(720px, 94vw)"
      :close-on-click-modal="false"
    >
      <div class="perm-toolbar">
        <el-input
          v-model="permKeyword"
          :prefix-icon="Search"
          placeholder="搜索权限名称或编码"
          clearable
        />
        <div class="perm-toolbar__right">
          <span class="perm-count" role="status" aria-live="polite">
            已选 {{ selectedPermissions.length }} / 共 {{ permissions.length }} 项
          </span>
          <el-button text type="primary" @click="selectAllVisible">全选</el-button>
          <el-button text type="primary" @click="clearAll">清空</el-button>
        </div>
      </div>

      <div class="perm-scroll">
        <template v-for="group in groupedPermissions" :key="group.key">
          <div class="perm-group-head">
            <span>{{ group.label }}</span>
            <span class="perm-group-count">{{ group.items.length }} 项</span>
          </div>
          <el-checkbox-group v-model="selectedPermissions" class="perm-grid">
            <el-checkbox v-for="perm in group.items" :key="perm.id" :value="perm.id" class="perm-card">
              <span class="perm-card__name">{{ perm.permName }}</span>
              <span class="perm-card__code">{{ perm.permCode }}</span>
            </el-checkbox>
          </el-checkbox-group>
        </template>
        <el-empty v-if="filteredPermissions.length === 0" description="没有匹配的权限" :image-size="80" />
      </div>

      <template #footer>
        <el-button @click="permDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="savePermissions">保存</el-button>
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

.table-wrap {
  overflow-x: auto;
}

.perm-tag {
  margin: 0 6px 6px 0;
}

.muted {
  color: var(--ink-500);
}

.perm-toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
}

.perm-toolbar__right {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.perm-count {
  font-size: 13px;
  color: var(--ink-500);
  white-space: nowrap;
}

.perm-scroll {
  max-height: 56vh;
  overflow-y: auto;
  padding-right: var(--space-1);
}

.perm-group-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: var(--space-2) 0;
  margin-bottom: var(--space-2);
  border-bottom: 1px solid var(--ink-200);
  font-size: 14px;
  font-weight: 700;
  color: var(--ink-900);
}

.perm-group-count {
  font-size: 12px;
  font-weight: 400;
  color: var(--ink-500);
}

.perm-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: var(--space-2);
  margin-bottom: var(--space-3);
}

.perm-card {
  height: auto;
  margin-right: 0;
  padding: 10px 12px;
  border: 1px solid var(--ink-200);
  border-radius: var(--radius-md);
  background: var(--surface-050);
  transition: border-color 180ms ease-out, background 180ms ease-out;
}

.perm-card :deep(.el-checkbox__label) {
  display: block;
  min-width: 0;
  padding-left: 8px;
  white-space: normal;
  overflow: visible;
  text-overflow: clip;
  color: var(--ink-900);
}

.perm-card :deep(.el-checkbox__input) {
  align-self: flex-start;
  margin-top: 2px;
}

.perm-card.is-checked {
  border-color: var(--primary-200);
  background: var(--primary-050);
}

.perm-card__name {
  display: block;
  overflow-wrap: anywhere;
  font-size: 13px;
  color: var(--ink-900);
}

.perm-card__code {
  display: block;
  margin-top: 2px;
  overflow-wrap: anywhere;
  font-family: ui-monospace, "SFMono-Regular", Menlo, Consolas, monospace;
  font-size: 12px;
  color: var(--ink-500);
}
</style>
