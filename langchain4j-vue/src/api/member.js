import request from './request'

/** 查询成员 */
export const listMembers = (kbId) => request.get(`/kb/${kbId}/members`)

/** 添加成员 */
export const addMember = (kbId, data) => request.post(`/kb/${kbId}/members`, data)

/** 修改成员角色，OWNER 表示转让 */
export const updateMemberRole = (kbId, userId, memberRole) =>
  request.put(`/kb/${kbId}/members/${userId}`, { memberRole })

/** 移除成员 */
export const removeMember = (kbId, userId) => request.delete(`/kb/${kbId}/members/${userId}`)
