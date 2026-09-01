import request from './request'

/** 分页查询知识库 */
export const listKb = (params) => request.get('/kb', { params })

/** 创建知识库 */
export const createKb = (data) => request.post('/kb', data)

/** 更新知识库 */
export const updateKb = (kbId, data) => request.put(`/kb/${kbId}`, data)

/** 删除知识库 */
export const deleteKb = (kbId) => request.delete(`/kb/${kbId}`)

/** 创建会话 */
export const createSession = (kbId) => request.post(`/kb/${kbId}/sessions`)

/** 分页查询知识库下的会话 */
export const listSessions = (kbId, params) => request.get(`/kb/${kbId}/sessions`, { params })
