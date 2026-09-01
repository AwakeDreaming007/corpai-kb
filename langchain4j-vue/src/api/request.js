import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

// 统一 axios 实例：带上认证头，处理后端标准响应结构与 401
const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('kb_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

request.interceptors.response.use(
  (response) => {
    const payload = response.data
    if (payload && typeof payload === 'object' && 'code' in payload) {
      if (payload.code !== 200) {
        ElMessage.error(payload.message || '请求失败')
        return Promise.reject(new Error(payload.message || '请求失败'))
      }
      return payload.data
    }
    return payload
  },
  (error) => {
    const status = error.response?.status
    const message = error.response?.data?.message || error.message || '网络异常'
    if (status === 401) {
      localStorage.removeItem('kb_token')
      localStorage.removeItem('kb_user')
      router.push('/login')
    }
    ElMessage.error(message)
    return Promise.reject(error)
  }
)

export default request
