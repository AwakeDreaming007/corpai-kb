import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '../layout/MainLayout.vue'

/** 集中声明路由；meta.perm 为进入页面所需权限码 */
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('../views/LoginView.vue'), meta: { title: '登录' } },
    {
      path: '/',
      component: MainLayout,
      children: [
        { path: '', redirect: '/kb' },
        { path: 'kb', name: 'kb', component: () => import('../views/KbListView.vue'), meta: { title: '知识库' } },
        { path: 'kb/:id', name: 'kb-detail', component: () => import('../views/KbDetailView.vue'), meta: { title: '知识库详情' } },
        { path: 'kb/:id/chat', name: 'kb-chat', component: () => import('../views/ChatView.vue'), meta: { title: '智能问答' } },
        { path: 'history', name: 'history', component: () => import('../views/HistoryView.vue'), meta: { title: '问答历史' } },
        { path: 'sys/users', name: 'sys-users', component: () => import('../views/SysUserView.vue'), meta: { title: '用户管理', perm: 'sys:user:manage' } },
        { path: 'sys/roles', name: 'sys-roles', component: () => import('../views/SysRoleView.vue'), meta: { title: '角色管理', perm: 'sys:role:manage' } },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

export default router
