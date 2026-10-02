<template>
  <div class="page-container">
    <el-card shadow="never">
      <el-form class="filter-bar" inline>
        <el-form-item label="关键词">
          <el-input
            v-model="query.keyword"
            placeholder="服务商名称 / 标识"
            clearable
            style="width: 200px"
            @keyup.enter="onSearch"
          />
        </el-form-item>
        <el-form-item label="协议">
          <el-select v-model="query.protocol" placeholder="全部" clearable style="width: 170px">
            <el-option
              v-for="item in protocolOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="onSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button
          v-permission="'ai:provider:add'"
          type="primary"
          :icon="Plus"
          @click="openDialog()"
        >
          新增服务商
        </el-button>
      </div>

      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column prop="name" label="服务商名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="code" label="标识" min-width="130" show-overflow-tooltip />
        <el-table-column label="协议" width="150">
          <template #default="{ row }">
            <el-tag size="small">{{ protocolText(row.protocol) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="baseUrl" label="base_url" min-width="200" show-overflow-tooltip />
        <el-table-column label="API Key" width="170">
          <template #default="{ row }">
            <span v-if="row.apiKeyMasked">{{ row.apiKeyMasked }}</span>
            <span v-else class="text-danger">未配置</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button
              v-permission="'ai:provider:edit'"
              link
              type="primary"
              @click="openDialog(row)"
            >
              编辑
            </el-button>
            <el-button
              v-permission="'ai:provider:list'"
              link
              type="warning"
              @click="openTest(row)"
            >
              测试连接
            </el-button>
            <el-button
              v-permission="'ai:provider:delete'"
              link
              type="danger"
              @click="onDelete(row)"
            >
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
      :title="form.id ? '编辑服务商' : '新增服务商'"
      width="520px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="服务商名称" />
        </el-form-item>
        <el-form-item label="标识" prop="code">
          <el-input v-model="form.code" placeholder="小写字母、数字、下划线或短横线" />
        </el-form-item>
        <el-form-item label="协议" prop="protocol">
          <el-select v-model="form.protocol" placeholder="请选择协议" style="width: 100%">
            <el-option
              v-for="item in protocolOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="base_url" prop="baseUrl">
          <el-input v-model="form.baseUrl" placeholder="如 https://api.deepseek.com（协议路径由后端拼接）" />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input v-model="form.apiKey" type="password" show-password placeholder="留空表示不修改" />
          <div v-if="form.id" class="form-tip">当前：{{ form.apiKeyMasked || '未配置' }}</div>
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

    <el-dialog v-model="testVisible" title="测试连接" width="520px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="服务商">{{ testForm.name }}</el-form-item>
        <el-form-item label="模型">
          <el-autocomplete
            v-model="testForm.model"
            :fetch-suggestions="suggestModels"
            placeholder="如 deepseek-flash"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <div v-if="testError" class="test-error">{{ testError }}</div>
      <div v-else-if="testResult" class="test-result">
        {{ testResult.content || '连通成功（无文本回复）' }}
      </div>
      <template #footer>
        <el-button @click="testVisible = false">关闭</el-button>
        <el-button type="primary" :loading="testing" @click="onTest">开始测试</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import {
  createProvider,
  deleteProvider,
  listProviders,
  testProvider,
  updateProvider,
  type ProviderBody,
} from '@/api/ai'
import type { AiChatResult, AiProtocol, AiProvider, TableRow } from '@/types'

const loading = ref(false)
const saving = ref(false)
const rows = ref<AiProvider[]>([])
const total = ref(0)

const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
  protocol: '' as AiProtocol | '',
})

const protocolOptions: { label: string; value: AiProtocol }[] = [
  { label: 'OpenAI 兼容', value: 'OPENAI' },
  { label: 'Anthropic 兼容', value: 'ANTHROPIC' },
  { label: 'Responses 兼容', value: 'RESPONSES' },
]

const protocolMap: Record<AiProtocol, string> = {
  OPENAI: 'OpenAI 兼容',
  ANTHROPIC: 'Anthropic 兼容',
  RESPONSES: 'Responses 兼容',
}

const defaultModel = 'deepseek-flash'
const modelPresets = [defaultModel, 'deepseek-v4-pro']

function protocolText(protocol: AiProtocol) {
  return protocolMap[protocol] || protocol
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
    const data = await listProviders({ ...query })
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
  query.protocol = ''
  onSearch()
}

/* ---------------- 新增 / 编辑 ---------------- */

const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  id: 0,
  name: '',
  code: '',
  protocol: 'OPENAI' as AiProtocol,
  baseUrl: '',
  apiKey: '',
  apiKeyMasked: '',
  status: 1,
  remark: '',
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入服务商名称', trigger: 'blur' }],
  code: [
    { required: true, message: '请输入服务商标识', trigger: 'blur' },
    {
      pattern: /^[a-z0-9_-]{1,64}$/,
      message: '只能用小写字母、数字、下划线或短横线',
      trigger: 'blur',
    },
  ],
  protocol: [{ required: true, message: '请选择协议', trigger: 'change' }],
  baseUrl: [{ required: true, message: '请输入 base_url', trigger: 'blur' }],
}

function openDialog(row?: TableRow) {
  form.id = row ? Number(row.id) : 0
  form.name = row ? String(row.name) : ''
  form.code = row ? String(row.code) : ''
  form.protocol = row ? (String(row.protocol) as AiProtocol) : 'OPENAI'
  form.baseUrl = row ? String(row.baseUrl) : ''
  form.apiKey = ''
  form.apiKeyMasked = row ? String(row.apiKeyMasked ?? '') : ''
  form.status = row ? Number(row.status) : 1
  form.remark = row ? String(row.remark ?? '') : ''
  dialogVisible.value = true
}

async function onSave() {
  const instance = formRef.value
  if (!instance) return
  try {
    await instance.validate()
  } catch {
    // 校验失败是正常分支，不往事件处理器外抛
    return
  }
  saving.value = true
  try {
    const body: ProviderBody = {
      name: form.name,
      code: form.code,
      protocol: form.protocol,
      baseUrl: form.baseUrl,
      apiKey: form.apiKey,
      status: form.status,
      remark: form.remark,
    }
    if (form.id) {
      await updateProvider(form.id, body)
    } else {
      await createProvider(body)
    }
    ElMessage.success('保存成功')
    form.apiKey = ''
    dialogVisible.value = false
    load()
  } catch {
    // 失败提示由请求拦截器统一给出，这里保持弹窗打开便于重试
  } finally {
    saving.value = false
  }
}

function onDelete(row: TableRow) {
  ElMessageBox.confirm(`确认删除服务商「${row.name}」吗？`, '提示', { type: 'warning' })
    .then(async () => {
      await deleteProvider(Number(row.id))
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

/* ---------------- 测试连接 ---------------- */

const testVisible = ref(false)
const testing = ref(false)
const testResult = ref<AiChatResult | null>(null)
const testError = ref('')
const testForm = reactive({ id: 0, name: '', model: defaultModel })

function openTest(row: TableRow) {
  testForm.id = Number(row.id)
  testForm.name = String(row.name)
  testForm.model = defaultModel
  testResult.value = null
  testError.value = ''
  testVisible.value = true
}

async function onTest() {
  if (!testForm.model) {
    ElMessage.warning('请选择或输入模型')
    return
  }
  testing.value = true
  testResult.value = null
  testError.value = ''
  try {
    testResult.value = await testProvider(testForm.id, testForm.model)
  } catch (error) {
    // 响应拦截器已弹出错误提示，这里只在对话框内展示
    testError.value = errorText(error)
  } finally {
    testing.value = false
  }
}

onMounted(() => {
  load()
})
</script>

<style scoped>
.form-tip {
  font-size: 12px;
  color: #909399;
  line-height: 20px;
}

.text-danger {
  color: #f56c6c;
}

.test-result {
  max-height: 240px;
  overflow: auto;
  padding: 10px 12px;
  border-radius: 4px;
  background-color: #f5f7fa;
  color: #303133;
  font-size: 13px;
  line-height: 20px;
  white-space: pre-wrap;
  word-break: break-word;
}

.test-error {
  max-height: 240px;
  overflow: auto;
  padding: 10px 12px;
  border-radius: 4px;
  background-color: #fef0f0;
  color: #f56c6c;
  font-size: 13px;
  line-height: 20px;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
