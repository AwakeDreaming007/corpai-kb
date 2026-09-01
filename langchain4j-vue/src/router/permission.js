import { ElMessage } from 'element-plus'
import { useUserStore } from '../stores/user'

/** 注册全局路由守卫，处理登录态与权限码 */
export function setupPermission(router) {
  router.beforeEach(async (to) => {
    const userStore = useUserStore()
    if (to.path === '/login') return true
    if (!userStore.token) return { path: '/login', query: { redirect: to.fullPath } }

    try {
      if (!userStore.user) await userStore.fetchMe()
      if (to.meta.perm && !userStore.hasPerm(to.meta.perm)) {
        ElMessage.warning('暂无访问权限')
        return { path: '/kb' }
      }
      return true
    } catch {
      userStore.logout()
      return { path: '/login', query: { redirect: to.fullPath } }
    }
  })

  router.afterEach((to) => {
    document.title = to.meta.title ? `${to.meta.title} · 企业 AI 知识问答库` : '企业 AI 知识问答库'
  })
}
