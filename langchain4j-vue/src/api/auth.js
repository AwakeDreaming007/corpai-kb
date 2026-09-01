import request from './request'

/** 登录并返回 token 与用户信息 */
export const login = (data) => request.post('/auth/login', data)

/** 注册账号 */
export const register = (data) => request.post('/auth/register', data)

/** 获取当前登录用户 */
export const getMe = () => request.get('/auth/me')
