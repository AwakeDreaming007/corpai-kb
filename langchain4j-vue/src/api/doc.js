import request from './request'

/** 分页查询文档 */
export const listDocs = (kbId, params) => request.get(`/kb/${kbId}/docs`, { params })

/** 上传文档 */
export const uploadDoc = (kbId, file) => {
  const formData = new FormData()
  formData.append('file', file)
  return request.post(`/kb/${kbId}/docs`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/** 删除文档 */
export const deleteDoc = (kbId, docId) => request.delete(`/kb/${kbId}/docs/${docId}`)

/** 重建文档索引 */
export const reindexDoc = (kbId, docId) => request.post(`/kb/${kbId}/docs/${docId}/reindex`)
