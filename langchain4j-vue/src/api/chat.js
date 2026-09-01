import { connectSSE } from '../utils/sse'

/**
 * 发起知识库流式问答。
 * @param {string} kbId 知识库 ID
 * @param {{sessionId:string,question:string}} body 请求体
 * @param {(type:string,data:any)=>void} onEvent SSE 事件回调
 * @returns {()=>void} 中止函数
 */
export const streamChat = (kbId, body, onEvent) =>
  connectSSE({ url: `/api/kb/${kbId}/chat/stream`, body, onEvent })
