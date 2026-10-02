<template>
  <div class="page-container">
    <el-card shadow="never">
      <el-form class="filter-bar" inline>
        <el-form-item label="关键词">
          <el-input
            v-model="query.keyword"
            placeholder="智能体名称 / 标识"
            clearable
            style="width: 200px"
            @keyup.enter="onSearch"
          />
        </el-form-item>
        <el-form-item label="服务商">
          <el-select v-model="query.providerId" placeholder="全部" clearable style="width: 180px">
            <el-option
              v-for="item in providers"
              :key="item.id"
              :label="providerLabel(item)"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="onSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button v-permission="'ai:agent:add'" type="primary" :icon="Plus" @click="openDialog()">
          新增智能体
        </el-button>
      </div>

      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column prop="name" label="名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="code" label="标识" min-width="130" show-overflow-tooltip />
        <el-table-column label="服务商" min-width="180">
          <template #default="{ row }">
            <span>{{ row.providerName || '-' }}</span>
            <el-tag v-if="row.protocol" size="small" class="row-tag">
              {{ protocolText(row.protocol) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="model" label="模型" width="170" show-overflow-tooltip />
        <el-table-column label="思考" width="140">
          <template #default="{ row }">
            {{ thinkingText(row.thinking, row.reasoningEffort) }}
          </template>
        </el-table-column>
        <el-table-column label="JSON 输出" width="100">
          <template #default="{ row }">{{ row.jsonOutput === 1 ? '开启' : '关闭' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'ai:agent:edit'" link type="primary" @click="openDialog(row)">
              编辑
            </el-button>
            <el-button v-permission="'ai:agent:chat'" link type="success" @click="openChat(row)">
              试聊
            </el-button>
            <el-button v-permission="'ai:agent:delete'" link type="danger" @click="onDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="onSizeChange"
          @current-change="load"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑智能体' : '新增智能体'"
      width="640px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="智能体名称" />
        </el-form-item>
        <el-form-item label="标识" prop="code">
          <el-input v-model="form.code" placeholder="小写字母、数字、下划线或短横线" />
        </el-form-item>
        <el-form-item label="AI 服务商" prop="providerId">
          <el-select v-model="form.providerId" placeholder="请选择服务商" style="width: 100%">
            <el-option
              v-for="item in providers"
              :key="item.id"
              :label="providerLabel(item)"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="模型" prop="model">
          <el-autocomplete
            v-model="form.model"
            :fetch-suggestions="suggestModels"
            placeholder="如 deepseek-flash"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="系统提示词">
          <el-input v-model="form.systemPrompt" type="textarea" :rows="5" placeholder="可留空" />
        </el-form-item>
        <el-form-item label="temperature">
          <el-input-number
            :model-value="form.temperature"
            :min="0"
            :max="2"
            :step="0.1"
            :precision="2"
            controls-position="right"
            @update:model-value="(val: number | undefined) => (form.temperature = val ?? null)"
          />
        </el-form-item>
        <el-form-item label="top_p">
          <el-input-number
            :model-value="form.topP"
            :min="0.01"
            :max="1"
            :step="0.01"
            :precision="2"
            controls-position="right"
            @update:model-value="(val: number | undefined) => (form.topP = val ?? null)"
          />
          <span class="form-tip">仅思考模式生效，有效范围 0.01~1.00</span>
        </el-form-item>
        <el-form-item label="max_tokens">
          <el-input-number
            :model-value="form.maxTokens"
            :min="1"
            :max="393216"
            controls-position="right"
            @update:model-value="(val: number | undefined) => (form.maxTokens = val ?? null)"
          />
          <span class="form-tip">留空用官方默认</span>
        </el-form-item>
        <el-form-item label="思考模式">
          <el-switch
            :model-value="form.thinking"
            :active-value="1"
            :inactive-value="0"
            active-text="开启"
            inactive-text="关闭"
            @update:model-value="(val: boolean | string | number) => (form.thinking = Number(val))"
          />
        </el-form-item>
        <el-form-item label="思考强度">
          <el-select v-model="form.reasoningEffort" :disabled="form.thinking !== 1" style="width: 160px">
            <el-option label="low" value="low" />
            <el-option label="high" value="high" />
            <el-option label="max" value="max" />
          </el-select>
        </el-form-item>
        <el-form-item label="JSON 输出">
          <el-switch
            :model-value="form.jsonOutput"
            :active-value="1"
            :inactive-value="0"
            active-text="开启"
            inactive-text="关闭"
            @update:model-value="(val: boolean | string | number) => (form.jsonOutput = Number(val))"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            :model-value="form.status"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="停用"
            @update:model-value="(val: boolean | string | number) => (form.status = Number(val))"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="chatVisible" title="试聊" width="720px" @closed="resetChat">
      <div class="chat-tip">
        {{ chatTarget.name }}（{{ protocolText(chatTarget.protocol) }} / {{ chatTarget.model }} /
        思考：{{ thinkingText(chatTarget.thinking, chatTarget.reasoningEffort) }}）
      </div>
      <div ref="chatBodyRef" class="chat-body">
        <div v-for="(msg, index) in messages" :key="index" class="chat-row" :class="msg.role">
          <div class="chat-bubble" :class="{ error: msg.error }">
            <el-collapse v-if="msg.reasoningContent" class="chat-reasoning">
              <el-collapse-item title="思考过程" name="reasoning">
                <div class="reasoning-text">{{ msg.reasoningContent }}</div>
              </el-collapse-item>
            </el-collapse>
            <div class="chat-text">{{ msg.content || '（无文本回复）' }}</div>
          </div>
        </div>
        <div v-if="sending" class="chat-row assistant">
          <div class="chat-bubble">正在思考…</div>
        </div>
        <el-empty v-if="messages.length === 0 && !sending" description="输入内容开始试聊" />
      </div>
      <div class="chat-input">
        <el-input
          v-model="chatInput"
          class="chat-textarea"
          type="textarea"
          :rows="2"
          :disabled="sending"
          placeholder="输入内容，回车发送"
          @keydown.enter="onSendKey"
        />
        <el-button type="primary" :loading="sending" :disabled="sending" @click="onSend">
          发送
        </el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import {
  chatAgent,
  createAgent,
  deleteAgent,
  listAgents,
  providerOptions,
  updateAgent,
  type AgentBody,
} from '@/api/ai'
import type { AiAgent, AiChatMessage, AiProvider, TableRow } from '@/types'

const loading = ref(false)
const saving = ref(false)
const rows = ref<AiAgent[]>([])
const total = ref(0)
const providers = ref<AiProvider[]>([])

const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
  providerId: '' as number | '',
})

const protocolMap: Record<string, string> = {
  OPENAI: 'OpenAI 兼容',
  ANTHROPIC: 'Anthropic 兼容',
  RESPONSES: 'Responses 兼容',
}

const defaultModel = 'deepseek-flash'
const modelPresets = [defaultModel, 'deepseek-v4-pro']

function protocolText(protocol: string | null) {
  return protocol ? protocolMap[protocol] || protocol : '-'
}

function thinkingText(thinking: number, effort: string | null) {
  if (thinking !== 1) return '关闭'
  return effort ? `开启（${effort}）` : '开启'
}

/** 同名服务商会按协议重复出现，下拉必须带上协议才分得清 */
function providerLabel(item: AiProvider) {
  const suffix = item.status === 1 ? '' : '（已停用）'
  return `${item.name} · ${protocolText(item.protocol)}${suffix}`
}

function suggestModels(query: string, callback: (items: { value: string }[]) => void) {
  const keyword = query.trim().toLowerCase()
  callback(modelPresets.filter((model) => model.includes(keyword)).map((model) => ({ value: model })))
}

/** 取错误文本：业务错误由响应拦截器包成 Error，HTTP 错误即 axios error */
function errorText(error: unknown): string {
  return error instanceof Error && error.message ? error.message : '请求失败'
}

async function load() {
  loading.value = true
  try {
    const data = await listAgents({ ...query })
    rows.value = data.records
    total.value = data.total
  } catch {
    // 失败提示由请求拦截器统一给出，这里只保证 loading 被复位
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.page = 1
  load()
}

/** 改每页条数时回到第 1 页，否则可能停在一个越界的空白页上 */
function onSizeChange() {
  query.page = 1
  load()
}

function onReset() {
  query.keyword = ''
  query.providerId = ''
  onSearch()
}

/* ---------------- 新增 / 编辑 ---------------- */

const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  id: 0,
  name: '',
  code: '',
  providerId: null as number | null,
  model: defaultModel,
  systemPrompt: '',
  temperature: null as number | null,
  topP: null as number | null,
  maxTokens: null as number | null,
  thinking: 1,
  reasoningEffort: 'high',
  jsonOutput: 0,
  status: 1,
  remark: '',
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入智能体名称', trigger: 'blur' }],
  code: [
    { required: true, message: '请输入智能体标识', trigger: 'blur' },
    {
      pattern: /^[a-z0-9_-]{1,64}$/,
      message: '只能用小写字母、数字、下划线或短横线',
      trigger: 'blur',
    },
  ],
  providerId: [{ required: true, message: '请选择 AI 服务商', trigger: 'change' }],
  model: [{ required: true, message: '请选择或输入模型', trigger: 'change' }],
}

function openDialog(row?: TableRow) {
  form.id = row ? Number(row.id) : 0
  form.name = row ? String(row.name) : ''
  form.code = row ? String(row.code) : ''
  form.providerId = row ? Number(row.providerId) : null
  form.model = row ? String(row.model) : defaultModel
  form.systemPrompt = row ? String(row.systemPrompt ?? '') : ''
  form.temperature = row ? (row.temperature as number | null) ?? null : null
  form.topP = row ? (row.topP as number | null) ?? null : null
  form.maxTokens = row ? (row.maxTokens as number | null) ?? null : null
  form.thinking = row ? Number(row.thinking) : 1
  form.reasoningEffort = row ? String(row.reasoningEffort ?? 'high') : 'high'
  form.jsonOutput = row ? Number(row.jsonOutput) : 0
  form.status = row ? Number(row.status) : 1
  form.remark = row ? String(row.remark ?? '') : ''
  dialogVisible.value = true
}

async function onSave() {
  if (saving.value) return
  const instance = formRef.value
  if (!instance) return
  try {
    await instance.validate()
  } catch {
    // 校验失败是正常分支，不往事件处理器外抛
    return
  }
  const providerId = form.providerId
  if (providerId === null) return
  saving.value = true
  try {
    const body: AgentBody = {
      name: form.name,
      code: form.code,
      providerId,
      model: form.model,
      systemPrompt: form.systemPrompt,
      temperature: form.temperature,
      topP: form.topP,
      maxTokens: form.maxTokens,
      thinking: form.thinking,
      reasoningEffort: form.reasoningEffort,
      jsonOutput: form.jsonOutput,
      status: form.status,
      remark: form.remark,
    }
    if (form.id) {
      await updateAgent(form.id, body)
    } else {
      await createAgent(body)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
  } catch {
    // 失败提示由请求拦截器统一给出，这里保持弹窗打开便于重试
  } finally {
    saving.value = false
  }
}

function onDelete(row: TableRow) {
  ElMessageBox.confirm(`确认删除智能体「${row.name}」吗？`, '提示', { type: 'warning' })
    .then(async () => {
      await deleteAgent(Number(row.id))
      ElMessage.success('删除成功')
      load()
    })
    .catch((error) => {
      // 用户取消（'cancel'/'close'）无需处理，接口失败必须区分出来
      if (error !== 'cancel' && error !== 'close') {
        ElMessage.error('删除失败，请稍后重试')
      }
    })
}

/* ---------------- 试聊 ---------------- */

/** 本地消息：error 为失败提示，不参与后续请求 */
interface ChatItem {
  role: 'user' | 'assistant'
  content: string
  reasoningContent?: string | null
  error?: boolean
}

const chatVisible = ref(false)
const sending = ref(false)
/** 试聊请求序号：关闭或切换智能体时递增，用来丢弃在途响应 */
let chatSeq = 0
const chatInput = ref('')
const chatBodyRef = ref<HTMLElement>()
const messages = ref<ChatItem[]>([])
const chatTarget = reactive({
  id: 0,
  name: '',
  protocol: null as string | null,
  model: '',
  thinking: 0,
  reasoningEffort: null as string | null,
})

function openChat(row: TableRow) {
  chatTarget.id = Number(row.id)
  chatTarget.name = String(row.name)
  chatTarget.protocol = row.protocol ? String(row.protocol) : null
  chatTarget.model = String(row.model)
  chatTarget.thinking = Number(row.thinking)
  chatTarget.reasoningEffort = row.reasoningEffort ? String(row.reasoningEffort) : null
  resetChat()
  chatVisible.value = true
}

/** 关闭或切换智能体时作废在途请求，避免孤儿回复被当成下一个智能体的历史 */
function resetChat() {
  chatSeq += 1
  messages.value = []
  chatInput.value = ''
  sending.value = false
}

/** 中文输入法回车是上屏候选词，不能当作发送 */
function onSendKey(event: Event | KeyboardEvent) {
  if (event instanceof KeyboardEvent && event.isComposing) return
  event.preventDefault()
  onSend()
}

/** 后端不保存会话，每次带上完整历史（错误提示除外） */
function history(): AiChatMessage[] {
  return messages.value
    .filter((item) => !item.error)
    .map((item) => ({ role: item.role, content: item.content }))
}

async function scrollToBottom() {
  await nextTick()
  const el = chatBodyRef.value
  if (el) {
    el.scrollTop = el.scrollHeight
  }
}

async function onSend() {
  const content = chatInput.value.trim()
  if (!content || sending.value || !chatTarget.id) return
  messages.value.push({ role: 'user', content })
  chatInput.value = ''
  sending.value = true
  const seq = chatSeq
  await scrollToBottom()
  try {
    const result = await chatAgent(chatTarget.id, history())
    if (seq !== chatSeq) return
    messages.value.push({
      role: 'assistant',
      content: result.content ?? '',
      reasoningContent: result.reasoningContent,
    })
  } catch (error) {
    if (seq !== chatSeq) return
    // 响应拦截器已弹出错误提示，这里只追加一条消息
    messages.value.push({ role: 'assistant', error: true, content: errorText(error) })
  } finally {
    if (seq === chatSeq) {
      sending.value = false
    }
    await scrollToBottom()
  }
}

onMounted(() => {
  load()
  providerOptions()
    .then((data) => {
      providers.value = data
    })
    .catch(() => {
      // 失败提示由请求拦截器统一给出；下拉为空时只影响筛选，列表照常可用
    })
})
</script>

<style scoped>
.form-tip {
  margin-left: 8px;
  font-size: 12px;
  color: #909399;
}

.row-tag {
  margin-left: 6px;
}

.chat-tip {
  margin-bottom: 8px;
  font-size: 12px;
  color: #909399;
}

.chat-body {
  height: 380px;
  overflow-y: auto;
  padding: 12px;
  border-radius: 4px;
  background-color: #f5f7fa;
}

.chat-row {
  display: flex;
  margin-bottom: 10px;
}

.chat-row.user {
  justify-content: flex-end;
}

.chat-row.assistant {
  justify-content: flex-start;
}

.chat-bubble {
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

.chat-row.user .chat-bubble {
  background-color: #409eff;
  color: #fff;
}

.chat-bubble.error {
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

.chat-input {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-top: 12px;
}

.chat-textarea {
  flex: 1;
}
</style>
