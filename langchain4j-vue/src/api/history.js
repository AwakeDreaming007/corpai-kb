import request from './request'

/** 分页查询问答历史 */
export const listHistory = (params) => request.get('/history', { params })

/** 查询历史详情 */
export const getHistory = (id) => request.get(`/history/${id}`)

/** 删除历史记录 */
export const deleteHistory = (id) => request.delete(`/history/${id}`)
