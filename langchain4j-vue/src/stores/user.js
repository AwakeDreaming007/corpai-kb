import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getMe, login as loginApi } from '../api/auth'

/** 用户信息与权限缓存 */
export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('kb_token') || '')
  const user = ref(JSON.parse(localStorage.getItem('kb_user') || 'null'))
  const roles = computed(() => user.value?.roles || [])
  const permissions = computed(() => user.value?.permissions || [])

  /** 判断当前用户是否持有指定权限码 */
  const hasPerm = (code) => !code || permissions.value.includes(code)

  /** 登录并缓存 token 与用户信息 */
  const login = async (form) => {
    const data = await loginApi(form)
    token.value = data.token
    user.value = data
    localStorage.setItem('kb_token', data.token)
    localStorage.setItem('kb_user', JSON.stringify(data))
    return data
  }

  /** 拉取并刷新当前用户，供路由守卫使用 */
  const fetchMe = async () => {
    const data = await getMe()
    user.value = data
    localStorage.setItem('kb_user', JSON.stringify(data))
    return data
  }

  /** 退出并清空本地认证数据 */
  const logout = () => {
    token.value = ''
    user.value = null
    localStorage.removeItem('kb_token')
    localStorage.removeItem('kb_user')
  }

  return { token, user, roles, permissions, hasPerm, login, fetchMe, logout }
})
