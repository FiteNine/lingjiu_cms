import { ElMessage } from 'element-plus'
import router from '@/router'

export interface SseHandlers {
  onEvent: (event: string, data: unknown) => void
  onError?: (error: Error) => void
  onClose?: () => void
}

export interface SseHandle {
  abort: () => void
}

/** 401 处理去重标记：与 api/request.ts 同源，并发流同时失效时只提示 / 跳转一次 */
let unauthorizedHandled = false

/** localStorage 在隐私模式 / 配额耗尽时会抛异常，读取要兜住 */
function readStorage(key: string): string | null {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

/** 复刻 api/request.ts 响应拦截器里的 401 处理（fetch 绕过了 axios） */
function handleUnauthorized() {
  try {
    localStorage.removeItem('token')
    localStorage.removeItem('user')
  } catch {
    // 存储不可用时忽略，跳转登录页仍能继续
  }
  // 已经停在登录页时不再跳，否则会把自己的 redirect 覆盖成 /login
  if (!unauthorizedHandled && router.currentRoute.value.path !== '/login') {
    unauthorizedHandled = true
    ElMessage.error('登录已失效，请重新登录')
    const current = router.currentRoute.value
    void router
      .push({ path: '/login', query: { redirect: current.fullPath } })
      .finally(() => {
        unauthorizedHandled = false
      })
  }
}

/**
 * POST + text/event-stream 请求。
 * 必须用 fetch 手写：EventSource 不能带 Authorization 头，而 JWT 只能在头里传；
 * axios 又会整体缓冲响应，拿不到流式增量。
 */
export function sseRequest(url: string, body: unknown, handlers: SseHandlers): SseHandle {
  const controller = new AbortController()

  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  const token = readStorage('token')
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  const siteId = readStorage('siteId')
  if (siteId) {
    headers['X-Site-Id'] = siteId
  }

  void run()

  async function run() {
    try {
      const response = await fetch(url, {
        method: 'POST',
        headers,
        body: JSON.stringify(body),
        signal: controller.signal,
      })

      if (!response.ok) {
        if (response.status === 401) {
          handleUnauthorized()
        } else {
          let message = `请求失败（${response.status}）`
          try {
            const text = await response.text()
            if (text) {
              const parsed = JSON.parse(text) as { message?: unknown }
              if (parsed && typeof parsed.message === 'string' && parsed.message) {
                message = parsed.message
              }
            }
          } catch {
            // 响应体不是 JSON（网关页面等），保留默认文案
          }
          ElMessage.error(message)
        }
        handlers.onError?.(new Error('流式请求失败'))
        return
      }

      const reader = response.body?.getReader()
      if (!reader) {
        throw new Error('当前浏览器不支持流式响应')
      }

      const decoder = new TextDecoder()
      let buffer = ''
      let eventName = ''
      const dataLines: string[] = []

      // 一帧结束（空行）时派发；只有 data 行才派发，注释帧（心跳）与空帧到此为止
      const dispatch = () => {
        if (dataLines.length === 0) {
          eventName = ''
          return
        }
        const raw = dataLines.join('\n')
        dataLines.length = 0
        const name = eventName || 'message'
        eventName = ''
        let parsed: unknown = raw
        try {
          parsed = JSON.parse(raw)
        } catch {
          // 非 JSON 的 data 原样交出，交给上层判断
          parsed = raw
        }
        handlers.onEvent(name, parsed)
      }

      const handleLine = (line: string) => {
        if (line === '') {
          dispatch()
          return
        }
        if (line.startsWith(':')) return
        const colon = line.indexOf(':')
        const field = colon === -1 ? line : line.slice(0, colon)
        let value = colon === -1 ? '' : line.slice(colon + 1)
        if (value.startsWith(' ')) {
          value = value.slice(1)
        }
        if (field === 'event') {
          eventName = value
        } else if (field === 'data') {
          dataLines.push(value)
        }
      }

      // 分块边界可能把一帧切成两半，必须保留跨 chunk 的缓冲，只按 \n 切完整的行
      for (;;) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        let index = buffer.indexOf('\n')
        while (index !== -1) {
          let line = buffer.slice(0, index)
          if (line.endsWith('\r')) {
            line = line.slice(0, -1)
          }
          buffer = buffer.slice(index + 1)
          handleLine(line)
          index = buffer.indexOf('\n')
        }
      }

      // 收尾：补上最后一个无换行的行与仍未派发的一帧
      buffer += decoder.decode()
      if (buffer) {
        let line = buffer
        if (line.endsWith('\r')) {
          line = line.slice(0, -1)
        }
        handleLine(line)
      }
      dispatch()
      handlers.onClose?.()
    } catch (error) {
      if (controller.signal.aborted) {
        // 用户主动取消：不是错误，只通知关闭（不调 onError）
        handlers.onClose?.()
        return
      }
      handlers.onError?.(error instanceof Error ? error : new Error('流式请求失败'))
    }
  }

  return {
    abort: () => controller.abort(),
  }
}
