/**
 * 基于 fetch + ReadableStream 的 SSE 客户端。
 * @param {object} options 请求配置
 * @param {string} options.url 请求地址
 * @param {string} [options.method] HTTP 方法
 * @param {object} [options.body] JSON 请求体
 * @param {object} [options.headers] 额外请求头
 * @param {(type: string, data: any) => void} options.onEvent 事件回调
 * @returns {() => void} abort 函数，用于停止请求
 */
export function connectSSE({ url, method = 'POST', body, headers = {}, onEvent }) {
  const controller = new AbortController()
  const token = localStorage.getItem('kb_token') || ''

  ;(async () => {
    try {
      const response = await fetch(url, {
        method,
        signal: controller.signal,
        headers: {
          'Content-Type': 'application/json',
          Accept: 'text/event-stream',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
          ...headers,
        },
        ...(body ? { body: JSON.stringify(body) } : {}),
      })

      if (!response.ok || !response.body) {
        onEvent('error', { message: `连接失败（${response.status}）` })
        return
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })

        // SSE 以空行分隔事件，避免半包导致解析错位
        const frames = buffer.split('\n\n')
        buffer = frames.pop() || ''

        frames.forEach((frame) => {
          const lines = frame.split('\n')
          const dataLines = []
          let eventType = 'message'
          lines.forEach((line) => {
            if (line.startsWith('event:')) eventType = line.slice(6).trim()
            if (line.startsWith('data:')) dataLines.push(line.slice(5).trimStart())
          })
          if (!dataLines.length) return
          const rawData = dataLines.join('\n')
          let parsedData = rawData
          try {
            parsedData = JSON.parse(rawData)
          } catch {
            // 后端若返回纯文本，保持原样透传
          }
          onEvent(eventType, parsedData)
        })
      }
      onEvent('done', {})
    } catch (error) {
      if (error.name !== 'AbortError') {
        onEvent('error', { message: error.message || '连接中断' })
      }
      onEvent('aborted', {})
    }
  })()

  return () => controller.abort()
}
