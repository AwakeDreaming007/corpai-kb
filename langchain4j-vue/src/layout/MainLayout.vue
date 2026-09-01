<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const canManageUser = computed(() => userStore.hasPerm('sys:user:manage'))
const canManageRole = computed(() => userStore.hasPerm('sys:role:manage'))
const activeMenu = computed(() => {
  if (route.path.startsWith('/history')) return '/history'
  if (route.path.startsWith('/sys/users')) return '/sys/users'
  if (route.path.startsWith('/sys/roles')) return '/sys/roles'
  return '/kb'
})
const breadcrumb = computed(() => route.meta.title || '控制台')
const displayName = computed(() => userStore.user?.nickname || userStore.user?.username || '用户')

/** 退出登录并回到登录页 */
const handleLogout = () => {
  userStore.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

<template>
  <div class="console-layout">
    <aside class="console-sidebar">
      <div class="brand">
        <div class="brand-mark"><el-icon><Collection /></el-icon></div>
        <div>
          <div class="brand-title">AI 知识问答库</div>
          <div class="brand-subtitle">Enterprise Console</div>
        </div>
      </div>

      <el-menu :default-active="activeMenu" router class="console-menu" background-color="transparent">
        <el-menu-item index="/kb">
          <el-icon><Folder /></el-icon><span>知识库</span>
        </el-menu-item>
        <el-menu-item index="/history">
          <el-icon><Clock /></el-icon><span>问答历史</span>
        </el-menu-item>
        <template v-if="canManageUser || canManageRole">
          <div class="menu-section">系统管理</div>
          <el-menu-item v-if="canManageUser" index="/sys/users">
            <el-icon><User /></el-icon><span>用户管理</span>
          </el-menu-item>
          <el-menu-item v-if="canManageRole" index="/sys/roles">
            <el-icon><Setting /></el-icon><span>角色管理</span>
          </el-menu-item>
        </template>
      </el-menu>
    </aside>

    <div class="console-main">
      <header class="console-header">
        <div class="breadcrumb">
          <el-icon><Location /></el-icon>
          <span>{{ breadcrumb }}</span>
        </div>
        <el-dropdown trigger="click">
          <button class="user-button" type="button">
            <span class="avatar">{{ displayName.slice(0, 1).toUpperCase() }}</span>
            <span>{{ displayName }}</span>
            <el-icon><ArrowDown /></el-icon>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item disabled>{{ userStore.user?.username || '' }}</el-dropdown-item>
              <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </header>

      <main class="console-content">
        <router-view v-slot="{ Component }">
          <transition name="fade-slide" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<style scoped>
.console-layout {
  display: flex;
  min-height: 100vh;
  background: var(--surface-000);
}

.console-sidebar {
  width: 248px;
  flex-shrink: 0;
  padding: var(--space-3);
  background: linear-gradient(180deg, var(--sidebar-900), var(--sidebar-850));
  color: #e5edff;
}

.brand {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-2) var(--space-3);
}

.brand-mark {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  border-radius: var(--radius-md);
  color: #fff;
  background: linear-gradient(135deg, #3b82f6, #6366f1);
  box-shadow: 0 6px 18px rgba(59, 130, 246, 0.24);
}

.brand-title {
  font-weight: 700;
  letter-spacing: -0.02em;
}

.brand-subtitle {
  margin-top: 2px;
  font-size: 12px;
  color: rgba(226, 232, 240, 0.6);
}

.console-menu {
  border-right: 0;
}

.console-menu :deep(.el-menu-item) {
  position: relative;
  height: 44px;
  margin-bottom: 4px;
  border-radius: var(--radius-md);
  color: rgba(226, 232, 240, 0.72);
  transition: color 160ms ease-out, background-color 160ms ease-out;
}

.console-menu :deep(.el-menu-item:hover) {
  color: #fff;
  background: rgba(148, 163, 184, 0.12);
}

.console-menu :deep(.el-menu-item.is-active) {
  color: #fff;
  background: rgba(59, 130, 246, 0.18);
}

.console-menu :deep(.el-menu-item.is-active)::before {
  content: "";
  position: absolute;
  left: 0;
  top: 10px;
  bottom: 10px;
  width: 3px;
  border-radius: 999px;
  background: var(--primary-600);
}

.menu-section {
  padding: var(--space-3) var(--space-2) var(--space-1);
  font-size: 12px;
  color: rgba(148, 163, 184, 0.7);
}

.console-main {
  flex: 1;
  min-width: 0;
}

.console-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 64px;
  padding: 0 var(--space-4);
  background: rgba(255, 255, 255, 0.88);
  border-bottom: 1px solid var(--ink-200);
  backdrop-filter: blur(12px);
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  color: var(--ink-500);
}

.user-button {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: 4px 8px;
  border: 0;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--ink-700);
  cursor: pointer;
  transition: background-color 160ms ease-out;
}

.user-button:hover {
  background: var(--ink-100);
}

.avatar {
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  color: #fff;
  background: linear-gradient(135deg, var(--primary-600), #6366f1);
  font-size: 13px;
  font-weight: 700;
}

.console-content {
  padding: var(--space-4);
}
</style>
