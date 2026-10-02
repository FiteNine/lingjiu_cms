<template>
  <div class="page-container">
    <el-card shadow="never">
      <el-alert
        v-if="!canChat"
        type="error"
        :closable="false"
        show-icon
        title="你没有使用全站agent的权限（ai:copilot:chat），发送会被后端拒绝。"
        class="block-alert"
      />
      <el-alert
        v-if="agentsLoaded && agents.length === 0"
        type="warning"
        :closable="false"
        show-icon
        title="还没有可用的智能体，请到 AI管理→智能体 新建一个，并绑定 OPENAI 协议的服务商。"
        class="block-alert"
      />

      <div class="copilot-header">
        <div class="header-left">
          <span class="header-label">智能体</span>
          <el-select
            v-model="selectedAgentId"
            placeholder="请选择智能体"
            style="width: 240px"
            :disabled="agents.length === 0"
          >
            <el-option
              v-for="item in agents"
              :key="item.id"
              :label="agentLabel(item)"
              :value="Number(item.id)"
            />
          </el-select>
          <el-tag v-if="siteName" type="info" effect="plain" class="site-badge">
            当前站点：{{ siteName }}
          </el-tag>
          <span v-else class="site-badge muted">当前站点：-</span>
        </div>
        <div class="header-right">
          <el-button :icon="Plus" @click="onNewSession">新会话</el-button>
          <el-button :icon="Clock" @click="openHistory">历史会话</el-button>
        </div>
      </div>

      <div ref="bodyRef" class="copilot-body">
        <template v-for="(item, index) in items" :key="index">
          <div v-if="item.kind === 'user'" class="msg-row user">
            <div class="msg-bubble user-bubble">{{ item.content }}</div>
          </div>

          <div v-else-if="item.kind === 'assistant'" class="msg-row assistant">
            <div class="msg-bubble" :class="{ error: item.error }">
              <el-collapse v-if="item.reasoning" class="chat-reasoning">
                <el-collapse-item title="思考过程" name="reasoning">
                  <div class="reasoning-text">{{ item.reasoning }}</div>
                </el-collapse-item>
              </el-collapse>
              <div class="msg-text">{{ item.content || '（无文本回复）' }}</div>
            </div>
          </div>

          <div v-else class="msg-row assistant">
            <div class="tool-card">
              <div class="tool-head">
                <span class="tool-name">{{ item.name }}</span>
                <span class="tool-title">{{ item.title }}</span>
                <el-tag size="small" :type="riskTagType(item.risk)">
                  {{ riskText(item.risk) }}
                </el-tag>
                <span class="tool-status" :class="toolStatusClass(item)">
                  {{ toolStatusText(item) }}
                </span>
              </div>
              <div class="tool-block">
                <div class="tool-label">参数</div>
                <pre class="tool-pre">{{ formatJson(item.args) }}</pre>
              </div>
              <div v-if="item.result" class="tool-block">
                <div class="tool-label">
                  结果摘要
                  <span class="tool-duration">耗时 {{ item.result.durationMs }} ms</span>
                </div>
                <pre class="tool-pre">{{ item.result.summary || '（无摘要）' }}</pre>
              </div>
              <div v-else class="tool-block">
                <div class="tool-label">结果摘要</div>
                <pre class="tool-pre muted">执行中…</pre>
              </div>
            </div>
          </div>
        </template>

        <el-empty v-if="items.length === 0" description="输入内容开始对话" />
      </div>

      <div class="copilot-input">
        <el-input
          v-model="input"
          class="input-textarea"
          type="textarea"
          :rows="3"
          :disabled="streamActive || !canChat"
          placeholder="输入指令，回车发送（Shift + Enter 换行）"
          @keydown.enter="onSendKey"
        />
        <div class="input-actions">
          <el-button v-if="streamActive" :icon="VideoPause" @click="onStop">停止</el-button>
          <el-button
            type="primary"
            :icon="Promotion"
            :loading="streamActive"
            :disabled="!canSend"
            @click="onSend"
          >
            发送
          </el-button>
        </div>
      </div>
    </el-card>

    <el-dialog
      v-model="confirmVisible"
      title="危险操作确认"
      width="760px"
      :close-on-click-modal="false"
      @closed="stopConfirmTimer"
    >
      <template v-if="confirmData">
        <div class="confirm-head">
          <el-tag size="small" :type="riskTagType(confirmRisk)">{{ riskText(confirmRisk) }}</el-tag>
          <span class="confirm-name">{{ confirmName }}</span>
          <span class="confirm-title">{{ confirmTitle }}</span>
          <span v-if="confirmCountdown > 0" class="confirm-countdown">
            剩余 {{ confirmCountdown }} 秒
          </span>
        </div>

        <el-alert
          v-if="isDestructive"
          type="error"
          :closable="false"
          show-icon
          title="这是破坏性操作，请确认参数无误"
          class="block-alert"
        />
        <el-alert
          v-else
          type="warning"
          :closable="false"
          show-icon
          title="该操作会修改站点数据，请确认参数无误"
          class="block-alert"
        />
        <p class="confirm-tip">5 分钟内未确认将自动取消。</p>

        <div class="confirm-label">参数（可编辑，须为合法 JSON 对象）</div>
        <el-input v-model="confirmArgsText" type="textarea" :rows="6" />
        <div v-if="parsedArgsResult.error" class="confirm-error">{{ parsedArgsResult.error }}</div>

        <template v-if="isFileReplace">
          <div class="confirm-label">改动对照（原内容 / 新内容）</div>
          <div class="diff-wrap">
            <div class="diff-col">
              <div class="diff-col-title">原内容</div>
              <div class="diff-pre">
                <div v-for="(line, i) in replaceOldLines" :key="i" class="diff-line removed">
                  {{ line }}
                </div>
              </div>
            </div>
            <div class="diff-col">
              <div class="diff-col-title">新内容</div>
              <div class="diff-pre">
                <div v-for="(line, i) in replaceNewLines" :key="i" class="diff-line added">
                  {{ line }}
                </div>
              </div>
            </div>
          </div>
        </template>

        <el-checkbox v-if="isFullPublish" v-model="fullConfirmed" class="confirm-checkbox">
          我确认执行全站全量发布（会删除多余产物）
        </el-checkbox>

        <el-alert
          v-if="!hasConfirmPerm"
          type="info"
          :closable="false"
          show-icon
          title="你没有确认危险操作的权限"
          class="block-alert"
        />
      </template>
      <template #footer>
        <el-button :disabled="confirming" @click="onDeny">拒绝</el-button>
        <el-button type="primary" :loading="confirming" :disabled="!canApprove" @click="onApprove">
          批准
        </el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="historyVisible" title="历史会话" size="480px">
      <div v-loading="historyLoading" class="history-list">
        <el-empty v-if="!historyLoading && sessions.length === 0" description="暂无历史会话" />
        <div
          v-for="item in sessions"
          :key="item.id"
          class="history-item"
          @click="onSessionClick(item)"
        >
          <div class="history-title">{{ item.title || '（无标题会话）' }}</div>
          <div class="history-meta">
            <span>{{ item.siteName || '-' }}</span>
            <span>{{ item.agentName || '-' }}</span>
            <span>{{ item.createTime }}</span>
          </div>
        </div>
        <div v-if="historyTotal > historySize" class="history-pager">
          <el-pagination
            v-model:current-page="historyPage"
            :page-size="historySize"
            :total="historyTotal"
            layout="prev, pager, next"
            small
            background
            @current-change="loadSessions"
          />
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Clock, Plus, Promotion, VideoPause } from '@element-plus/icons-vue'
import {
  copilotAgentOptions,
  copilotConfirm,
  copilotSessionDetail,
  copilotSessions,
} from '@/api/ai'
import { sseRequest, type SseHandle } from '@/utils/sse'
import { useSiteStore } from '@/stores/site'
import { useUserStore } from '@/stores/user'
import type {
  CopilotAgentOption,
  CopilotChatMessage,
  CopilotEvent,
  CopilotRisk,
  CopilotSession,
} from '@/types'

const userStore = useUserStore()
const siteStore = useSiteStore()

/* ---------------- 本地消息模型 ---------------- */

interface UserItem {
  kind: 'user'
  content: string
}

interface AssistantItem {
  kind: 'assistant'
  content: string
  reasoning: string
  error?: boolean
}

interface ToolResultState {
  ok: boolean
  summary: string | null
  durationMs: number
  status: number
}

interface ToolItem {
  kind: 'tool'
  toolCallId: string
  name: string
  title: string
  args: unknown
  risk: CopilotRisk
  result?: ToolResultState
}

type ChatItem = UserItem | AssistantItem | ToolItem

type SessionFrame = Extract<CopilotEvent, { event: 'session' }>['data']
type ConfirmFrame = Extract<CopilotEvent, { event: 'confirm' }>['data']

/* ---------------- 页面状态 ---------------- */

const canChat = computed(() => userStore.hasPerm('ai:copilot:chat'))

const agents = ref<CopilotAgentOption[]>([])
const agentsLoaded = ref(false)
const selectedAgentId = ref<number | null>(null)

const items = ref<ChatItem[]>([])
const input = ref('')
const bodyRef = ref<HTMLElement>()

const streamActive = ref(false)
/** 会话 id：null 表示新建，首帧 session 回传后写回 */
const currentSessionId = ref<number | null>(null)
/** 站点名：首帧来自会话，回放来自历史详情，都没有时退回站点 store */
const liveSiteName = ref('')
const replaySiteName = ref('')

const siteName = computed(
  () => liveSiteName.value || replaySiteName.value || siteStore.currentSiteName,
)

/** 请求序号：新建 / 切换会话 / 卸载时递增，用来丢弃在途的孤儿响应 */
let chatSeq = 0
let streamHandle: SseHandle | null = null

function readStorage(key: string): string | null {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

function writeStorage(key: string, value: string) {
  try {
    localStorage.setItem(key, value)
  } catch {
    // 写不进去（隐私模式 / 配额超限）不影响本次对话
  }
}

function agentLabel(item: CopilotAgentOption) {
  return `${item.name}（${item.model}）`
}

function formatJson(value: unknown): string {
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}

function riskText(risk: CopilotRisk): string {
  if (risk === 'DESTRUCTIVE') return '破坏性'
  if (risk === 'WRITE') return '写操作'
  return '只读'
}

function riskTagType(risk: CopilotRisk): 'info' | 'warning' | 'danger' {
  if (risk === 'DESTRUCTIVE') return 'danger'
  if (risk === 'WRITE') return 'warning'
  return 'info'
}

function toolStatusText(item: ToolItem): string {
  if (!item.result) return '执行中…'
  const status = item.result.status
  if (status === 2) return '✗ 被拒绝'
  if (status === 3) return '✗ 无权限'
  if (status === 1) return '✓ 成功'
  if (status === 0) return '✗ 失败'
  return item.result.ok ? '✓ 成功' : '✗ 失败'
}

function toolStatusClass(item: ToolItem): string {
  if (!item.result) return 'pending'
  return item.result.status === 1 || item.result.ok ? 'ok' : 'fail'
}

async function scrollToBottom() {
  await nextTick()
  const el = bodyRef.value
  if (el) {
    el.scrollTop = el.scrollHeight
  }
}

/* ---------------- 智能体下拉 ---------------- */

async function loadAgents() {
  try {
    const list = await copilotAgentOptions()
    agents.value = list || []
  } catch {
    agents.value = []
  } finally {
    agentsLoaded.value = true
  }
  if (agents.value.length === 0) {
    selectedAgentId.value = null
    return
  }
  const saved = Number(readStorage('copilotAgentId'))
  const matched = agents.value.find((item) => Number(item.id) === saved)
  selectedAgentId.value = Number((matched || agents.value[0]).id)
}

watch(selectedAgentId, (id) => {
  if (id !== null) {
    writeStorage('copilotAgentId', String(id))
  }
})

/* ---------------- 消息追加 ---------------- */

function ensureAssistant(): AssistantItem {
  const last = items.value[items.value.length - 1]
  if (last && last.kind === 'assistant') {
    return last
  }
  const item: AssistantItem = { kind: 'assistant', content: '', reasoning: '' }
  items.value.push(item)
  return item
}

function pushError(message: string) {
  items.value.push({ kind: 'assistant', content: message, reasoning: '', error: true })
}

/* ---------------- 确认弹窗 ---------------- */

const confirmVisible = ref(false)
const confirmData = ref<ConfirmFrame | null>(null)
const confirmArgsText = ref('')
const fullConfirmed = ref(false)
const confirming = ref(false)
const confirmCountdown = ref(0)
let confirmTimer: ReturnType<typeof setInterval> | null = null

const hasConfirmPerm = computed(() => userStore.hasPerm('ai:copilot:confirm'))
const confirmRisk = computed<CopilotRisk>(() => confirmData.value?.risk ?? 'READ')
const confirmName = computed(() => confirmData.value?.name ?? '')
const confirmTitle = computed(() => confirmData.value?.title ?? '')
const isDestructive = computed(() => confirmRisk.value === 'DESTRUCTIVE')

/** 解析用户编辑后的参数；解析失败时 value 为 null，批准按钮随之禁用 */
const parsedArgsResult = computed<{ value: Record<string, unknown> | null; error: string }>(() => {
  const text = confirmArgsText.value.trim()
  if (!text) return { value: null, error: '参数不是合法 JSON' }
  let parsed: unknown
  try {
    parsed = JSON.parse(text)
  } catch {
    return { value: null, error: '参数不是合法 JSON' }
  }
  if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
    return { value: null, error: '参数必须是一个 JSON 对象' }
  }
  return { value: parsed as Record<string, unknown>, error: '' }
})

const isFileReplace = computed(
  () =>
    confirmName.value === 'cms_site_file_replace' &&
    typeof confirmData.value?.args?.oldText === 'string' &&
    typeof confirmData.value?.args?.newText === 'string',
)

const replaceOldLines = computed(() => {
  const text = confirmData.value?.args?.oldText
  return typeof text === 'string' ? text.split('\n') : []
})

const replaceNewLines = computed(() => {
  const text = confirmData.value?.args?.newText
  return typeof text === 'string' ? text.split('\n') : []
})

const isFullPublish = computed(
  () => confirmName.value === 'cms_publish_run' && confirmData.value?.args?.mode === 'full',
)

const canApprove = computed(() => {
  if (!hasConfirmPerm.value) return false
  if (parsedArgsResult.value.value === null) return false
  if (isFullPublish.value && !fullConfirmed.value) return false
  return true
})

function stopConfirmTimer() {
  if (confirmTimer !== null) {
    clearInterval(confirmTimer)
    confirmTimer = null
  }
}

function startConfirmTimer(expiresIn: number) {
  stopConfirmTimer()
  // 后端以秒下发（5 分钟 = 300）；防御性地把明显是毫秒的取值归一到秒
  const total = expiresIn > 3600 ? Math.floor(expiresIn / 1000) : Math.floor(expiresIn)
  confirmCountdown.value = Math.max(0, total)
  confirmTimer = setInterval(() => {
    if (confirmCountdown.value > 0) {
      confirmCountdown.value -= 1
    }
    if (confirmCountdown.value <= 0) {
      stopConfirmTimer()
      confirmVisible.value = false
      ElMessage.warning('确认超时，操作已自动取消')
    }
  }, 1000)
}

function openConfirm(frame: ConfirmFrame) {
  confirmData.value = frame
  confirmArgsText.value = formatJson(frame.args)
  fullConfirmed.value = false
  confirmVisible.value = true
  startConfirmTimer(frame.expiresIn)
}

async function submitConfirm(decision: 'ALLOW' | 'DENY', override?: Record<string, unknown>) {
  const frame = confirmData.value
  if (!frame) return
  const sessionId = currentSessionId.value
  if (sessionId === null || !streamActive.value) {
    confirmVisible.value = false
    ElMessage.warning('会话已结束')
    return
  }
  confirming.value = true
  try {
    const payload: {
      sessionId: number
      toolCallId: string
      decision: 'ALLOW' | 'DENY'
      argsOverride?: Record<string, unknown>
    } = { sessionId, toolCallId: frame.toolCallId, decision }
    if (decision === 'ALLOW' && override && JSON.stringify(override) !== JSON.stringify(frame.args)) {
      payload.argsOverride = override
    }
    await copilotConfirm(payload)
    confirmVisible.value = false
  } catch {
    // 失败提示由请求拦截器统一给出，这里保持弹窗打开便于重试
  } finally {
    confirming.value = false
  }
}

async function onApprove() {
  if (confirming.value) return
  if (!hasConfirmPerm.value) {
    ElMessage.warning('你没有确认危险操作的权限')
    return
  }
  const parsed = parsedArgsResult.value
  if (parsed.value === null) {
    ElMessage.error(parsed.error || '参数不是合法 JSON')
    return
  }
  if (isFullPublish.value && !fullConfirmed.value) {
    ElMessage.warning('请先勾选确认执行全站全量发布')
    return
  }
  await submitConfirm('ALLOW', parsed.value)
}

async function onDeny() {
  if (confirming.value) return
  await submitConfirm('DENY')
}

/* ---------------- SSE 事件处理 ---------------- */

function handleEvent(event: string, data: unknown) {
  switch (event) {
    case 'session': {
      const frame = data as SessionFrame
      currentSessionId.value = frame.sessionId
      liveSiteName.value = frame.siteName
      break
    }
    case 'reasoning': {
      const frame = data as Extract<CopilotEvent, { event: 'reasoning' }>['data']
      ensureAssistant().reasoning += frame.delta
      break
    }
    case 'delta': {
      const frame = data as Extract<CopilotEvent, { event: 'delta' }>['data']
      ensureAssistant().content += frame.delta
      break
    }
    case 'tool_call': {
      const frame = data as Extract<CopilotEvent, { event: 'tool_call' }>['data']
      items.value.push({
        kind: 'tool',
        toolCallId: frame.toolCallId,
        name: frame.name,
        title: frame.title,
        args: frame.args,
        risk: frame.risk,
      })
      break
    }
    case 'tool_result': {
      const frame = data as Extract<CopilotEvent, { event: 'tool_result' }>['data']
      const target = items.value.find(
        (item): item is ToolItem => item.kind === 'tool' && item.toolCallId === frame.toolCallId,
      )
      if (target) {
        target.result = {
          ok: frame.ok,
          summary: frame.summary,
          durationMs: frame.durationMs,
          status: frame.status,
        }
      }
      break
    }
    case 'confirm': {
      openConfirm(data as ConfirmFrame)
      break
    }
    case 'done':
      break
    case 'error': {
      const frame = data as Extract<CopilotEvent, { event: 'error' }>['data']
      pushError(frame.message || '发生未知错误')
      break
    }
    default:
      break
  }
  void scrollToBottom()
}

function startStream(agentId: number, message: string) {
  chatSeq += 1
  const seq = chatSeq
  if (streamHandle) {
    streamHandle.abort()
    streamHandle = null
  }
  streamActive.value = true
  streamHandle = sseRequest(
    '/api/ai/copilot/chat',
    { sessionId: currentSessionId.value, agentId, message },
    {
      onEvent: (event, data) => {
        if (seq !== chatSeq) return
        handleEvent(event, data)
      },
      onError: (error) => {
        if (seq !== chatSeq) return
        pushError(error.message || '请求失败')
        streamActive.value = false
        void scrollToBottom()
      },
      onClose: () => {
        if (seq !== chatSeq) return
        streamActive.value = false
        streamHandle = null
        if (confirmVisible.value) {
          confirmVisible.value = false
          ElMessage.warning('会话已结束')
        }
      },
    },
  )
}

/* ---------------- 发送 / 停止 / 新会话 ---------------- */

const canSend = computed(
  () => !streamActive.value && canChat.value && selectedAgentId.value !== null && !!input.value.trim(),
)

async function onSend() {
  const content = input.value.trim()
  const agentId = selectedAgentId.value
  if (!content || streamActive.value || !canChat.value || agentId === null) return
  items.value.push({ kind: 'user', content })
  input.value = ''
  startStream(agentId, content)
  await scrollToBottom()
}

/** 中文输入法回车是上屏候选词；Shift+Enter 保留换行 */
function onSendKey(event: Event | KeyboardEvent) {
  if (event instanceof KeyboardEvent && (event.isComposing || event.shiftKey)) return
  event.preventDefault()
  void onSend()
}

function onStop() {
  if (streamHandle) {
    streamHandle.abort()
  }
}

function onNewSession() {
  chatSeq += 1
  if (streamHandle) {
    streamHandle.abort()
    streamHandle = null
  }
  streamActive.value = false
  items.value = []
  input.value = ''
  currentSessionId.value = null
  liveSiteName.value = ''
  replaySiteName.value = ''
  confirmVisible.value = false
  stopConfirmTimer()
}

/* ---------------- 历史会话 ---------------- */

const historyVisible = ref(false)
const historyLoading = ref(false)
const sessions = ref<CopilotSession[]>([])
const historyTotal = ref(0)
const historyPage = ref(1)
const historySize = 20

function openHistory() {
  historyVisible.value = true
  historyPage.value = 1
  void loadSessions()
}

async function loadSessions() {
  historyLoading.value = true
  try {
    const data = await copilotSessions({ page: historyPage.value, size: historySize })
    sessions.value = data.records
    historyTotal.value = data.total
  } catch {
    sessions.value = []
    historyTotal.value = 0
  } finally {
    historyLoading.value = false
  }
}

function parseJson(text: string | null): unknown {
  if (!text) return {}
  try {
    return JSON.parse(text)
  } catch {
    return text
  }
}

async function onSessionClick(row: CopilotSession) {
  historyLoading.value = true
  try {
    const detail = await copilotSessionDetail(Number(row.id))
    replaySession(detail.session, detail.messages)
    historyVisible.value = false
  } catch {
    // 失败提示由请求拦截器统一给出
  } finally {
    historyLoading.value = false
  }
}

function replaySession(session: CopilotSession, messages: CopilotChatMessage[]) {
  chatSeq += 1
  if (streamHandle) {
    streamHandle.abort()
    streamHandle = null
  }
  streamActive.value = false
  confirmVisible.value = false
  stopConfirmTimer()

  currentSessionId.value = Number(session.id)
  liveSiteName.value = ''
  replaySiteName.value = session.siteName || ''
  const matched = agents.value.find((item) => Number(item.id) === Number(session.agentId))
  if (matched) {
    selectedAgentId.value = Number(matched.id)
  }

  const list: ChatItem[] = []
  for (const msg of messages) {
    if (msg.role === 'user') {
      list.push({ kind: 'user', content: msg.content || '' })
    } else if (msg.role === 'assistant') {
      list.push({
        kind: 'assistant',
        content: msg.content || '',
        reasoning: msg.reasoningContent || '',
      })
    } else {
      list.push({
        kind: 'tool',
        toolCallId: msg.toolCallId || `replay-${msg.id}`,
        name: msg.toolName || '工具',
        title: msg.toolName || '',
        args: parseJson(msg.toolArgs),
        risk: 'READ',
        result:
          msg.toolStatus === null
            ? undefined
            : {
                ok: msg.toolStatus === 1,
                summary: msg.content,
                durationMs: msg.toolDurationMs ?? 0,
                status: msg.toolStatus,
              },
      })
    }
  }
  items.value = list
  void scrollToBottom()
}

/* ---------------- 生命周期 ---------------- */

onMounted(() => {
  void loadAgents()
})

onBeforeUnmount(() => {
  // 切站点会让 router-view remount，不 abort 会留下孤儿流
  chatSeq += 1
  if (streamHandle) {
    streamHandle.abort()
    streamHandle = null
  }
  stopConfirmTimer()
})
</script>

<style scoped>
.block-alert {
  margin-bottom: 12px;
}

.copilot-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-label {
  font-size: 14px;
  color: #606266;
}

.site-badge {
  font-size: 13px;
}

.site-badge.muted {
  color: #909399;
}

.copilot-body {
  height: 460px;
  overflow-y: auto;
  padding: 12px;
  border-radius: 4px;
  background-color: #f5f7fa;
}

.msg-row {
  display: flex;
  margin-bottom: 10px;
}

.msg-row.user {
  justify-content: flex-end;
}

.msg-row.assistant {
  justify-content: flex-start;
}

.msg-bubble {
  max-width: 78%;
  padding: 8px 12px;
  border-radius: 6px;
  background-color: #fff;
  color: #303133;
  font-size: 14px;
  line-height: 20px;
  white-space: pre-wrap;
  word-break: break-word;
}

.user-bubble {
  background-color: #409eff;
  color: #fff;
}

.msg-text {
  white-space: pre-wrap;
  word-break: break-word;
}

.msg-bubble.error {
  background-color: #fef0f0;
  color: #f56c6c;
}

.chat-reasoning {
  margin-bottom: 6px;
}

.chat-reasoning :deep(.el-collapse-item__header) {
  height: 26px;
  line-height: 26px;
  font-size: 12px;
  color: #909399;
  border-bottom: none;
  background-color: transparent;
}

.chat-reasoning :deep(.el-collapse-item__wrap) {
  border-bottom: none;
  background-color: transparent;
}

.chat-reasoning :deep(.el-collapse-item__content) {
  padding-bottom: 6px;
  font-size: 12px;
  line-height: 18px;
  color: #909399;
}

.reasoning-text {
  white-space: pre-wrap;
  word-break: break-word;
}

.tool-card {
  width: 78%;
  padding: 10px 12px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  background-color: #fff;
  font-size: 13px;
}

.tool-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}

.tool-name {
  font-weight: 600;
  color: #303133;
}

.tool-title {
  color: #606266;
}

.tool-status {
  margin-left: auto;
  font-size: 12px;
}

.tool-status.ok {
  color: #67c23a;
}

.tool-status.fail {
  color: #f56c6c;
}

.tool-status.pending {
  color: #909399;
}

.tool-block {
  margin-top: 6px;
}

.tool-label {
  margin-bottom: 4px;
  font-size: 12px;
  color: #909399;
}

.tool-duration {
  margin-left: 8px;
}

.tool-pre {
  margin: 0;
  padding: 8px;
  border-radius: 4px;
  background-color: #f5f7fa;
  color: #303133;
  font-size: 12px;
  line-height: 18px;
  white-space: pre-wrap;
  word-break: break-word;
}

.tool-pre.muted {
  color: #909399;
}

.copilot-input {
  margin-top: 12px;
}

.input-textarea {
  width: 100%;
}

.input-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}

.confirm-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.confirm-name {
  font-weight: 600;
}

.confirm-title {
  color: #606266;
}

.confirm-countdown {
  margin-left: auto;
  font-size: 12px;
  color: #e6a23c;
}

.confirm-tip {
  margin: 8px 0;
  font-size: 12px;
  color: #909399;
}

.confirm-label {
  margin: 12px 0 6px;
  font-size: 13px;
  color: #606266;
}

.confirm-error {
  margin-top: 4px;
  font-size: 12px;
  color: #f56c6c;
}

.confirm-checkbox {
  margin-top: 12px;
}

.diff-wrap {
  display: flex;
  gap: 10px;
}

.diff-col {
  flex: 1;
  min-width: 0;
}

.diff-col-title {
  margin-bottom: 4px;
  font-size: 12px;
  color: #909399;
}

.diff-pre {
  max-height: 240px;
  overflow: auto;
  padding: 6px 8px;
  border-radius: 4px;
  background-color: #f5f7fa;
  font-family: monospace;
  font-size: 12px;
  line-height: 18px;
}

.diff-line {
  white-space: pre-wrap;
  word-break: break-all;
}

.diff-line.removed {
  background-color: #fef0f0;
  color: #f56c6c;
}

.diff-line.added {
  background-color: #f0f9eb;
  color: #67c23a;
}

.history-list {
  min-height: 200px;
}

.history-item {
  padding: 10px 12px;
  border-radius: 4px;
  border: 1px solid #ebeef5;
  cursor: pointer;
}

.history-item + .history-item {
  margin-top: 8px;
}

.history-item:hover {
  background-color: #f5f7fa;
}

.history-title {
  font-size: 14px;
  color: #303133;
  margin-bottom: 4px;
}

.history-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 12px;
  color: #909399;
}

.history-pager {
  display: flex;
  justify-content: center;
  margin-top: 12px;
}
</style>
