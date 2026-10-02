import axios, { type AxiosError, type AxiosRequestConfig, type AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

/** 后端统一响应包 */
interface RawResult<T> {
  code: number
  message: string
  data: T
}

/** 出错时响应体里的业务提示（后端统一响应包） */
interface ErrorBody {
  message?: string
}

const service = axios.create({
  baseURL: '/',
  timeout: 30000,
})

// 请求拦截：统一加 Authorization 与 X-Site-Id 头
service.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  const siteId = localStorage.getItem('siteId')
  if (siteId) {
    config.headers['X-Site-Id'] = siteId
  }
  return config
})

/**
 * 解包 { code, message, data }。axios 的拦截器签名要求返回 AxiosResponse，
 * 而这里要交出去的是解包后的业务数据，所以按泛型 T 标注（调用方由 http.get<T> 等指定），
 * 注册时顺着签名做一次类型断言。
 */
function unwrap<T>(response: AxiosResponse): T {
  const res = response.data as RawResult<unknown> | unknown
  if (
    res !== null &&
    typeof res === 'object' &&
    'code' in (res as object) &&
    'data' in (res as object)
  ) {
    const wrapped = res as RawResult<unknown>
    if (wrapped.code === 0) {
      return wrapped.data as T
    }
    ElMessage.error(wrapped.message || '请求失败')
    // 业务错误：交一个 rejected Promise 给调用方（泛型对齐 unknown，注册时再断言成 AxiosResponse）
    return Promise.reject(new Error(wrapped.message || '请求失败')) as unknown as T
  }
  return res as T
}

/** 401 处理去重标记：并发请求同时失效时只提示并跳转一次 */
let unauthorizedHandled = false

// 响应拦截：解包 { code, message, data }
service.interceptors.response.use(
  unwrap as unknown as (response: AxiosResponse) => AxiosResponse,
  (error: AxiosError): Promise<never> => {
    const status: number | undefined = error.response?.status
    const body = error.response?.data as ErrorBody | undefined
    const message: string = body?.message || error.message || '网络错误'
    if (status === 401) {
      try {
        localStorage.removeItem('token')
        localStorage.removeItem('user')
      } catch {
        // 存储不可用时忽略，跳转登录页仍能继续
      }
      // 并发 401 只提示 / 跳转一次；已经停在登录页时不再跳，否则会把自己的 redirect 覆盖成 /login
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
    } else if (status === 403) {
      ElMessage.error('没有权限执行此操作')
    } else {
      ElMessage.error(message)
    }
    return Promise.reject(error)
  },
)

/** 过滤掉空查询参数（筛选框留空时不传该参数） */
function cleanParams(params?: object): Record<string, unknown> | undefined {
  if (!params) return undefined
  const result: Record<string, unknown> = {}
  for (const [key, value] of Object.entries(params)) {
    // 空串 / 空数组 / 空对象都不发，避免污染后端的分页与筛选解析；0 与 false 是有效取值，保留
    if (value === '' || value === undefined || value === null) continue
    if (Array.isArray(value) && value.length === 0) continue
    if (typeof value === 'object' && !Array.isArray(value) && Object.keys(value).length === 0) continue
    result[key] = value
  }
  return result
}

export const http = {
  get<T>(url: string, params?: object, config?: AxiosRequestConfig): Promise<T> {
    return service.get(url, { params: cleanParams(params), ...config }) as unknown as Promise<T>
  },
  post<T>(url: string, data?: object, config?: AxiosRequestConfig): Promise<T> {
    return service.post(url, data, config) as unknown as Promise<T>
  },
  put<T>(url: string, data?: object, config?: AxiosRequestConfig): Promise<T> {
    return service.put(url, data, config) as unknown as Promise<T>
  },
  delete<T>(url: string, params?: object, config?: AxiosRequestConfig): Promise<T> {
    return service.delete(url, { params: cleanParams(params), ...config }) as unknown as Promise<T>
  },
}

export default service
