<template>
  <div class="page-container">
    <el-card shadow="never">
      <el-form class="filter-bar" inline>
        <el-form-item label="关键词">
          <el-input
            v-model="query.keyword"
            placeholder="类型标识 / 名称"
            clearable
            style="width: 220px"
            @keyup.enter="onSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="onSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button
          v-permission="'cms:type:add'"
          type="primary"
          :icon="Plus"
          @click="openDrawer()"
        >
          新增内容类型
        </el-button>
      </div>

      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column prop="code" label="类型标识" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link type="primary" @click="openDrawer(row)">{{ row.code }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="类型名称" min-width="140" show-overflow-tooltip />
        <el-table-column label="形态" width="110">
          <template #default="{ row }">
            <el-tag :type="kindTagType(row.kind)" effect="plain">{{ kindText(row.kind) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="层级" width="80">
          <template #default="{ row }">{{ row.hierarchical === 1 ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="每页" width="80">
          <template #default="{ row }">{{ row.perPage }}</template>
        </el-table-column>
        <el-table-column label="URL 规则" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="pattern-line">详情：{{ row.detailUrlPattern || '—（不出详情页）' }}</div>
            <div class="pattern-line">列表：{{ row.listUrlPattern || '—（不出列表页）' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="80" />
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'cms:type:edit'" link type="primary" @click="openDrawer(row)">
              编辑
            </el-button>
            <el-button v-permission="'cms:type:list'" link type="primary" @click="openFields(row)">
              字段
            </el-button>
            <el-button v-permission="'cms:type:delete'" link type="danger" @click="onDelete(row)">
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

    <!-- 新增/编辑抽屉。内容类型一共 18 个字段，抽屉比对话框更适合这种长表单 -->
    <el-drawer
      v-model="drawerVisible"
      :title="form.id ? `编辑内容类型 - ${form.code}` : '新增内容类型'"
      size="720px"
      @closed="onDrawerClosed"
    >
      <el-form
        v-if="detailLoaded"
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="150px"
        class="type-form"
      >
        <el-form-item label="类型标识" prop="code">
          <el-input
            v-model="form.code"
            :disabled="isEdit"
            placeholder="如 product，只能用英文小写、数字与下划线"
          />
          <div class="hint">
            模板与接口都用它引用这个类型（如 <code>[list type='product']</code>）。
            <template v-if="isEdit">创建后不可修改：字段定义、内容与模板都按它关联。</template>
            <template v-else>不能使用保留名：site / channel / page / param / query / item。</template>
          </div>
        </el-form-item>
        <el-form-item label="类型名称" prop="name">
          <el-input v-model="form.name" placeholder="中文显示名，如 产品" />
        </el-form-item>
        <el-form-item label="类型形态" prop="kind">
          <el-select v-model="form.kind" style="width: 220px">
            <el-option label="CONTENT - 普通内容" value="CONTENT" />
            <el-option label="SINGLE - 单页" value="SINGLE" />
            <el-option label="TREE - 层级" value="TREE" />
          </el-select>
          <div class="hint">
            CONTENT：多条内容，各自有详情页；TREE：父子层级（栏目树），可挂子内容；
            <strong>SINGLE：单页，每个站点只能有一条内容</strong>（关于我们、联系方式这类）。
          </div>
        </el-form-item>
        <el-form-item label="层级内容">
          <el-switch v-model="form.hierarchical" :active-value="1" :inactive-value="0" />
          <div class="hint">
            1 = 内容之间可以套父子层级（与 TREE 形态等价，后端遇到 TREE 会自动置 1）。
          </div>
        </el-form-item>

        <el-divider content-position="left">页面与 URL</el-divider>

        <el-form-item label="详情页 URL 规则">
          <el-input v-model="form.detailUrlPattern" placeholder="如 /product/{slug}/" />
          <div class="hint">
            <strong>留空 = 这个类型不出详情页</strong>（只做列表或只做数据）。占位符用 <code>{slug}</code>
            、<code>{id}</code> 等。
          </div>
        </el-form-item>
        <el-form-item label="列表页 URL 规则">
          <el-input v-model="form.listUrlPattern" placeholder="如 /product/ 或 /product/page/{page}/" />
          <div class="hint">
            <strong>留空 = 这个类型不出列表页</strong>。SINGLE 类型通常两项都留空。
          </div>
        </el-form-item>
        <el-form-item label="详情页模板">
          <el-input v-model="form.detailTemplate" placeholder="如 product-detail.html" />
          <div class="hint">相对当前站点主题目录的模板文件；留空走引擎默认模板。</div>
        </el-form-item>
        <el-form-item label="列表页模板">
          <el-input v-model="form.listTemplate" placeholder="如 product-list.html" />
          <div class="hint">留空走引擎默认模板，具体见发布中心的预演结果。</div>
        </el-form-item>
        <el-form-item label="正文分页字段">
          <el-input v-model="form.paginateBody" placeholder="如 content" />
          <div class="hint">写正文字段名（通常是 content）时，超长正文按每页条数切成多页。</div>
        </el-form-item>

        <el-divider content-position="left">列表排序与分页</el-divider>

        <el-form-item label="默认排序字段">
          <el-input v-model="form.sortField" placeholder="如 publishTime / 自定义字段 code" />
          <div class="hint">列表页的默认排序依据；留空走引擎默认（发布时间倒序）。</div>
        </el-form-item>
        <el-form-item label="排序方向">
          <el-select v-model="form.sortOrder" placeholder="默认（发布时间倒序）" clearable style="width: 220px">
            <el-option label="asc - 升序" value="asc" />
            <el-option label="desc - 降序" value="desc" />
          </el-select>
          <div class="hint">留空 = 引擎默认 publishTime desc。</div>
        </el-form-item>
        <el-form-item label="每页条数" prop="perPage">
          <el-input-number v-model="form.perPage" :min="1" :max="200" controls-position="right" />
          <div class="hint">列表页与分页的每页条数，后端默认 20。</div>
        </el-form-item>

        <el-divider content-position="left">SEO 与其它</el-divider>

        <el-form-item label="SEO 标题字段">
          <el-input v-model="form.seoTitleField" placeholder="字段 code，如 seoTitle" />
          <div class="hint">详情页 &lt;title&gt; 取哪个自定义字段；留空用内容标题。</div>
        </el-form-item>
        <el-form-item label="SEO 描述字段">
          <el-input v-model="form.seoDescField" placeholder="字段 code，如 seoDesc" />
          <div class="hint">详情页 description 取哪个自定义字段；留空用内容摘要。</div>
        </el-form-item>
        <el-form-item label="类型选项">
          <el-input
            v-model="form.options"
            type="textarea"
            :rows="5"
            placeholder='{"facets":["brand"],"searchable":true}'
          />
          <el-button class="format-btn" size="small" @click="formatOptions">格式化</el-button>
          <div class="hint">
            jsonb 原文，必须是 JSON <strong>对象</strong>（数组、裸值会被后端拒绝）。留空即清空。
            引擎实际读两项：<code>facets</code>（要出筛选面板的字段）
            与 <code>searchable</code>（是否进搜索）。
          </div>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
          <div class="hint">停用后发布引擎不再为这个类型生成任何页面。</div>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" controls-position="right" />
          <div class="hint">类型本身的展示顺序，越小越靠前。</div>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
          <el-button @click="drawerVisible = false">取消</el-button>
        </el-form-item>
      </el-form>
      <div v-else v-loading="true" class="drawer-loading" />
    </el-drawer>

    <!-- 字段定义抽屉：复用 components 形式的字段管理页面 -->
    <FieldsDrawer
      v-model:visible="fieldsVisible"
      :type-code="currentTypeCode"
      @count-change="load"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import {
  createContentType,
  deleteContentType,
  getContentType,
  listContentTypes,
  updateContentType,
  type ContentTypeBody,
} from '@/api/cms'
import type { ContentTypeItem, ContentTypeKind, TableRow } from '@/types'
import FieldsDrawer from '@/views/cms/fields/index.vue'

const loading = ref(false)
const saving = ref(false)
const rows = ref<ContentTypeItem[]>([])
const total = ref(0)

const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
})

const kindMap: Record<ContentTypeKind, string> = {
  CONTENT: '普通内容',
  SINGLE: '单页',
  TREE: '层级',
}

function kindText(kind: ContentTypeKind) {
  return kindMap[kind] || kind
}

function kindTagType(kind: ContentTypeKind) {
  if (kind === 'SINGLE') return 'warning'
  if (kind === 'TREE') return 'success'
  return 'primary'
}

async function load() {
  loading.value = true
  try {
    const data = await listContentTypes({ ...query })
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
  onSearch()
}

/* ---------------- 新增 / 编辑 ---------------- */

/** 表单里数字开关用 0/1、可空文本用空串，提交时再按后端契约收敛成 null */
interface TypeForm {
  id: number
  code: string
  name: string
  kind: ContentTypeKind
  hierarchical: number
  detailUrlPattern: string
  listUrlPattern: string
  detailTemplate: string
  listTemplate: string
  paginateBody: string
  sortField: string
  sortOrder: '' | 'asc' | 'desc'
  perPage: number
  seoTitleField: string
  seoDescField: string
  options: string
  status: number
  sort: number
}

const drawerVisible = ref(false)
const detailLoaded = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<TypeForm>(emptyForm())

const isEdit = computed(() => form.id > 0)

const rules: FormRules = {
  code: [
    { required: true, message: '请输入类型标识', trigger: 'blur' },
    {
      pattern: /^[a-z0-9_]{1,64}$/,
      message: '只能用英文小写字母、数字与下划线',
      trigger: 'blur',
    },
  ],
  name: [{ required: true, message: '请输入类型名称', trigger: 'blur' }],
  kind: [{ required: true, message: '请选择类型形态', trigger: 'change' }],
  perPage: [{ required: true, message: '请输入每页条数', trigger: 'blur' }],
}

function emptyForm(): TypeForm {
  return {
    id: 0,
    code: '',
    name: '',
    kind: 'CONTENT',
    hierarchical: 0,
    detailUrlPattern: '',
    listUrlPattern: '',
    detailTemplate: '',
    listTemplate: '',
    paginateBody: '',
    sortField: '',
    sortOrder: '',
    perPage: 20,
    seoTitleField: '',
    seoDescField: '',
    options: '',
    status: 1,
    sort: 0,
  }
}

function fillForm(item: ContentTypeItem) {
  form.id = item.id
  form.code = item.code
  form.name = item.name
  form.kind = item.kind
  form.hierarchical = item.hierarchical
  form.detailUrlPattern = item.detailUrlPattern || ''
  form.listUrlPattern = item.listUrlPattern || ''
  form.detailTemplate = item.detailTemplate || ''
  form.listTemplate = item.listTemplate || ''
  form.paginateBody = item.paginateBody || ''
  form.sortField = item.sortField || ''
  form.sortOrder = item.sortOrder === 'asc' || item.sortOrder === 'desc' ? item.sortOrder : ''
  form.perPage = item.perPage
  form.seoTitleField = item.seoTitleField || ''
  form.seoDescField = item.seoDescField || ''
  form.options = item.options || ''
  form.status = item.status
  form.sort = item.sort
}

/** 打开抽屉的请求序号：连续点不同行时只接受最后一次响应，避免旧回填覆盖新选中行 */
let detailSeq = 0

async function openDrawer(row?: TableRow) {
  drawerVisible.value = true
  detailLoaded.value = false
  // 抽屉每次打开都重建表单：不用 destroy-on-close 是为了保住内层 ref，
  // 回填放在表单渲染之后（detailLoaded），免得第一次保存时 formRef 还是空的
  Object.assign(form, emptyForm())
  if (!row) {
    detailLoaded.value = true
    return
  }
  const seq = ++detailSeq
  try {
    // 列表列不全，回填走详情接口，避免编辑时把没展示的字段覆盖成空
    const detail = await getContentType(Number(row.id))
    if (seq !== detailSeq) return
    fillForm(detail)
    detailLoaded.value = true
  } catch {
    if (seq !== detailSeq) return
    // 详情没拿到就不能让用户对着空表单保存（form.id 仍为 0 会变成新增）
    ElMessage.error('内容类型详情加载失败，请重试')
    drawerVisible.value = false
  }
}

function onDrawerClosed() {
  formRef.value?.clearValidate()
}

/** 空串 → null：后端这些列是可空列，空格串会被当成真的取值写进去 */
function orNull(value: string) {
  return value.trim() === '' ? null : value.trim()
}

/** 与「格式化」按钮同一套规则：非空时必须是 JSON 对象 */
function validateOptions(): boolean {
  const text = form.options.trim()
  if (!text) return true
  let parsed: unknown
  try {
    parsed = JSON.parse(text)
  } catch (e) {
    ElMessage.error(`类型选项不是合法的 JSON：${(e as Error).message}`)
    return false
  }
  if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
    ElMessage.error('类型选项必须是 JSON 对象，例如 {"facets":["brand"],"searchable":true}')
    return false
  }
  return true
}

function formatOptions() {
  const text = form.options.trim()
  if (!text) {
    ElMessage.warning('类型选项为空，无需格式化')
    return
  }
  let parsed: unknown
  try {
    parsed = JSON.parse(text)
  } catch (e) {
    ElMessage.error(`不是合法的 JSON：${(e as Error).message}`)
    return
  }
  if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
    // 后端只接受 JSON 对象（引擎按对象读 facets / searchable），先在这里拦住
    ElMessage.error('类型选项必须是 JSON 对象，例如 {"facets":["brand"],"searchable":true}')
    return
  }
  form.options = JSON.stringify(parsed, null, 2)
  ElMessage.success('已格式化为 JSON 对象')
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
  // 用户可能不点「格式化」直接提交，这里用同一套规则再拦一次非法 JSON
  if (!validateOptions()) return
  const body: ContentTypeBody = {
    code: form.code.trim(),
    name: form.name.trim(),
    kind: form.kind,
    hierarchical: form.hierarchical,
    detailUrlPattern: orNull(form.detailUrlPattern),
    listUrlPattern: orNull(form.listUrlPattern),
    detailTemplate: orNull(form.detailTemplate),
    listTemplate: orNull(form.listTemplate),
    paginateBody: orNull(form.paginateBody),
    sortField: orNull(form.sortField),
    sortOrder: form.sortOrder || null,
    perPage: form.perPage,
    seoTitleField: orNull(form.seoTitleField),
    seoDescField: orNull(form.seoDescField),
    options: orNull(form.options),
    status: form.status,
    sort: form.sort,
  }
  saving.value = true
  try {
    if (form.id > 0) {
      await updateContentType(form.id, body)
    } else {
      await createContentType(body)
    }
    ElMessage.success('保存成功')
    drawerVisible.value = false
    await load()
  } catch {
    // 失败提示由请求拦截器统一给出，这里保持抽屉打开便于重试
  } finally {
    saving.value = false
  }
}

function onDelete(row: TableRow) {
  ElMessageBox.confirm(
    `确认删除内容类型「${row.name}（${row.code}）」吗？该类型下还有字段或内容时后端会拒绝删除。`,
    '提示',
    { type: 'warning' },
  )
    .then(async () => {
      await deleteContentType(Number(row.id))
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

/* ---------------- 字段定义抽屉 ---------------- */

const fieldsVisible = ref(false)
const currentTypeCode = ref('')

function openFields(row: TableRow) {
  currentTypeCode.value = String(row.code)
  fieldsVisible.value = true
}

onMounted(() => {
  load()
})
</script>

<style scoped>
.hint {
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
  margin-top: 4px;
  width: 100%;
}

.hint code {
  background-color: #f5f7fa;
  padding: 0 3px;
  border-radius: 3px;
}

.pattern-line {
  font-size: 12px;
}

.pattern-line + .pattern-line {
  color: #909399;
}

.format-btn {
  margin-top: 6px;
}

.type-form {
  padding-right: 8px;
}

.drawer-loading {
  height: 200px;
}
</style>
