/** 格式化日期；空值返回占位符 */
export const formatDate = (value) => {
  if (!value) return '-'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '-' : date.toLocaleString('zh-CN', { hour12: false })
}

/** 文件大小人性化展示 */
export const formatSize = (value) => {
  if (value == null) return '-'
  const units = ['B', 'KB', 'MB', 'GB']
  let size = Number(value)
  let index = 0
  while (size >= 1024 && index < units.length - 1) {
    size /= 1024
    index += 1
  }
  return `${size.toFixed(size >= 10 || index === 0 ? 0 : 1)} ${units[index]}`
}

/** 耗时格式化 */
export const formatLatency = (value) => (value == null ? '-' : `${value} ms`)

/** 角色展示文案 */
export const roleText = (role) => ({ OWNER: '所有者', EDITOR: '编辑者', VIEWER: '查看者' }[role] || role)
