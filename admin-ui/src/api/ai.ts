import { http } from './request'
import type {
  AiAgent,
  AiChatMessage,
  AiChatResult,
  AiProtocol,
  AiProvider,
  PageResult,
} from '@/types'

/* ---------------- AI 服务商 ---------------- */

const PROVIDER_BASE = '/api/ai/providers'
const AGENT_BASE = '/api/ai/agents'

/** 后端调服务商的读超时是 5 分钟，前端多留 30 秒，保证看到的是后端真实报错而不是前端超时 */
const AI_TIMEOUT = 330000

export interface ProviderQuery {
  page: number
  size: number
  keyword?: string
  protocol?: AiProtocol | ''
}

export interface ProviderBody {
  name: string
  code: string
  protocol: AiProtocol
  baseUrl: string
  /** 留空表示不修改（后端只在非空时更新） */
  apiKey?: string
  status: number
  remark?: string | null
}

export function listProviders(params: ProviderQuery) {
  return http.get<PageResult<AiProvider>>(PROVIDER_BASE, params)
}

/** 全部服务商（含停用），供智能体表单下拉；停用项由 providerLabel 追加「（已停用）」标注 */
export function providerOptions() {
  return http.get<AiProvider[]>(`${PROVIDER_BASE}/options`)
}

export function createProvider(data: ProviderBody) {
  return http.post<null>(PROVIDER_BASE, data)
}

export function updateProvider(id: number, data: ProviderBody) {
  return http.put<null>(`${PROVIDER_BASE}/${id}`, data)
}

export function deleteProvider(id: number) {
  return http.delete<null>(`${PROVIDER_BASE}/${id}`)
}

/** 连通性测试，模型响应可能较慢 */
export function testProvider(id: number, model: string) {
  return http.post<AiChatResult>(`${PROVIDER_BASE}/${id}/test`, { model }, { timeout: AI_TIMEOUT })
}

/* ---------------- 智能体 ---------------- */

export interface AgentQuery {
  page: number
  size: number
  keyword?: string
  providerId?: number | ''
}

export interface AgentBody {
  name: string
  code: string
  providerId: number
  model: string
  systemPrompt?: string | null
  temperature?: number | null
  topP?: number | null
  maxTokens?: number | null
  thinking: number
  reasoningEffort: string
  jsonOutput: number
  status: number
  remark?: string | null
}

export function listAgents(params: AgentQuery) {
  return http.get<PageResult<AiAgent>>(AGENT_BASE, params)
}

export function createAgent(data: AgentBody) {
  return http.post<null>(AGENT_BASE, data)
}

export function updateAgent(id: number, data: AgentBody) {
  return http.put<null>(`${AGENT_BASE}/${id}`, data)
}

export function deleteAgent(id: number) {
  return http.delete<null>(`${AGENT_BASE}/${id}`)
}

/** 试聊：每次携带完整历史，后端不保存会话 */
export function chatAgent(id: number, messages: AiChatMessage[]) {
  return http.post<AiChatResult>(
    `${AGENT_BASE}/${id}/chat`,
    { messages },
    { timeout: AI_TIMEOUT },
  )
}
