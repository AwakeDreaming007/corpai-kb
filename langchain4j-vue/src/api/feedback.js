import request from './request'

/** 提交反馈，rating 为 1、-1 或 0 */
export const submitFeedback = (data) => request.post('/feedback', data)

/** 查询历史记录反馈 */
export const getFeedback = (historyId) => request.get(`/history/${historyId}/feedback`)
