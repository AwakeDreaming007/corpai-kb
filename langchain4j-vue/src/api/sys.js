import request from './request'

/** 分页查询系统用户 */
export const listUsers = (params) => request.get('/sys/users', { params })

/** 修改用户状态，0 禁用，1 启用 */
export const updateUserStatus = (id, status) => request.put(`/sys/users/${id}/status`, { status })

/** 分配用户角色 */
export const updateUserRoles = (id, roleIds) => request.put(`/sys/users/${id}/roles`, { roleIds })

/** 查询角色列表 */
export const listRoles = () => request.get('/sys/roles')

/** 创建角色 */
export const createRole = (data) => request.post('/sys/roles', data)

/** 更新角色 */
export const updateRole = (id, data) => request.put(`/sys/roles/${id}`, data)

/** 删除角色 */
export const deleteRole = (id) => request.delete(`/sys/roles/${id}`)

/** 分配角色权限 */
export const updateRolePermissions = (id, permIds) =>
  request.put(`/sys/roles/${id}/permissions`, { permIds })

/** 查询全量权限编码 */
export const listPermissions = () => request.get('/sys/permissions')
