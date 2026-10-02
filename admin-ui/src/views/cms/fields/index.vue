<template>
  <el-drawer
    v-model="drawerVisible"
    :title="`「${typeCode}」类型的字段`"
    size="820px"
    @open="load"
  >
    <div class="field-tips">
      <p>
        <strong>indexed（可筛选 / 可排序）</strong>：只有勾了它的字段才能被模板的
        <code>where='字段:eq:值'</code> 筛选、被 <code>orderby</code> 排序。漏勾会让模板编译报
        E2007，或页面能出但一条内容也筛不到。
      </p>
      <p>
        <strong>raw（原样输出 HTML）</strong>：富文本字段必须开，否则页面上会出现一堆
        <code>&lt;p&gt;</code> 源码（标签被转义）。
      </p>
      <p>
        <strong>字段名 code</strong> 保存后不可修改：它是模板、字段索引表与内容
        <code>data</code> 共用的键，改名等于要连着迁移数据。
      </p>
    </div>

    <div class="table-toolbar">
      <el-button v-permission="'cms:field:add'" type="primary" :icon="Plus" @click="openDialog()">
        新增字段
      </el-button>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" stripe>
      <el-table-column prop="sort" label="排序" width="70" />
      <el-table-column prop="code" label="字段名" min-width="130" show-overflow-tooltip>
        <template #default="{ row }">
          <el-link type="primary" @click="openDialog(row)">{{ row.code }}</el-link>
        </template>
      </el-table-column>
      <el-table-column prop="label" label="显示名" min-width="120" show-overflow-tooltip />
      <el-table-column prop="fieldType" label="字段类型" width="120" />
      <el-table-column label="必填" width="70">
        <template #default="{ row }">{{ row.required === 1 ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column label="原样输出" width="90">
        <template #default="{ row }">{{ row.raw === 1 ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column label="可筛选" width="90">
        <template #default="{ row }">
          <el-tag :type="row.indexed === 1 ? 'success' : 'danger'" size="small" effect="plain">
            {{ row.indexed === 1 ? '已开' : '未开' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="搜索" width="80">
        <template #default="{ row }">{{ row.searchable === 1 ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="230" fixed="right">
        <template #default="{ row, $index }">
          <el-button v-permission="'cms:field:edit'" link type="primary" @click="openDialog(row)">
            编辑
          </el-button>
          <el-button
            v-permission="'cms:field:edit'"
            link
            type="primary"
            :disabled="$index === 0 || loading"
            @click="move($index, -1)"
          >
            上移
          </el-button>
          <el-button
            v-permission="'cms:field:edit'"
            link
            type="primary"
            :disabled="$index === rows.length - 1 || loading"
            @click="move($index, 1)"
          >
            下移
          </el-button>
          <el-button v-permission="'cms:field:delete'" link type="danger" @click="onDelete(row)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && rows.length === 0" description="这个类型还没有定义字段" />

    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? `编辑字段 - ${form.code}` : '新增字段'"
      width="680px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px" class="field-form">
        <el-form-item label="字段名" prop="code">
          <el-input
            v-model="form.code"
            :disabled="form.id > 0"
            placeholder="如 brand，只能用英文、数字与下划线"
          />
          <div class="hint">
            模板里用 <code>[field:brand/]</code> 取值。
            <template v-if="form.id > 0">已经保存过，不可修改（改它要连着迁移索引表与内容 data）。</template>
            <template v-else>不能使用保留名：site / channel / page / param / query / item。</template>
          </div>
        </el-form-item>
        <el-form-item label="显示名" prop="label">
          <el-input v-model="form.label" placeholder="后台表单里看到的名称，如 品牌" />
        </el-form-item>
        <el-form-item label="字段类型" prop="fieldType">
          <el-select v-model="form.fieldType" style="width: 240px">
            <el-option v-for="item in fieldTypes" :key="item" :label="item" :value="item" />
          </el-select>
          <div class="hint">{{ fieldTypeHint }}</div>
        </el-form-item>
        <el-form-item label="选项">
          <el-input
            v-if="isEnum"
            v-model="form.options"
            placeholder="dev:应用开发,auto:效率与自动化"
          />
          <el-input v-else model-value="" disabled placeholder="仅 ENUM / ENUM_MULTI 需要填写" />
          <div v-if="isEnum" class="hint">
            写法 <code>值:标签</code>，多个用英文逗号分隔，例如
            <code>dev:应用开发,auto:效率与自动化</code>。内容里存的是"值"，模板
            <code>format='label'</code> 输出"标签"。
          </div>
        </el-form-item>
        <el-form-item label="格式化器">
          <el-input v-model="form.formatter" placeholder="可选，如 date:'yyyy年MM月dd日'" />
          <div class="hint">模板里没写 format 时用的默认格式化，留空即无。</div>
        </el-form-item>
        <el-form-item label="原样输出">
          <el-switch v-model="form.raw" :active-value="1" :inactive-value="0" />
          <div class="hint">
            <strong>raw = 按 HTML 原样输出</strong>：富文本（RICHTEXT）字段必须开，否则页面上会出现一堆
            <code>&lt;p&gt;</code> 源码。
          </div>
        </el-form-item>
        <el-form-item label="必填">
          <el-switch v-model="form.required" :active-value="1" :inactive-value="0" />
          <div class="hint">打开后，后台保存内容时这个字段不允许留空。</div>
        </el-form-item>
        <el-form-item label="默认值">
          <el-input v-model="form.defaultValue" placeholder="可选，新建内容时的初始值" />
        </el-form-item>
        <el-form-item label="可筛选 / 可排序">
          <el-switch v-model="form.indexed" :active-value="1" :inactive-value="0" />
          <div class="hint">
            <strong>indexed：模板里 <code>where='字段:eq:值'</code> 只能筛勾了它的字段</strong>，
            漏勾会让模板编译报 E2007，或筛不到内容（索引表里没有这个字段的行）。
          </div>
        </el-form-item>
        <el-form-item label="进搜索索引">
          <el-switch v-model="form.searchable" :active-value="1" :inactive-value="0" />
          <div class="hint">打开后这个字段参与站内搜索的关键字匹配。</div>
        </el-form-item>
        <el-form-item label="跨站点">
          <el-switch v-model="form.crossSite" :active-value="1" :inactive-value="0" />
          <div class="hint">同一字段名在多个站点之间共用取值口径。</div>
        </el-form-item>
        <el-form-item label="填写提示">
          <el-input v-model="form.help" placeholder="可选，显示在内容编辑表单该字段下方" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" controls-position="right" />
          <div class="hint">数字越小越靠前，决定内容编辑表单里字段的顺序。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { createField, deleteField, listFields, updateField, type FieldBody } from '@/api/cms'
import type { FieldItem, FieldTypeName, TableRow } from '@/types'

const props = withDefaults(
  defineProps<{
    /** 目标内容类型标识，必填：字段定义挂在类型下 */
    typeCode: string
    /** v-model 控制抽屉显示 */
    visible: boolean
  }>(),
  { visible: false },
)

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  /** 字段增删后通知父级刷新（类型列表不展示字段数，这里只是给父级一个刷新时机） */
  (e: 'count-change'): void
}>()

const drawerVisible = computed({
  get: () => props.visible,
  set: (value: boolean) => emit('update:visible', value),
})

/** 19 个字段类型，取值与引擎 FieldType 一一对应 */
const fieldTypes: FieldTypeName[] = [
  'TEXT',
  'TEXTAREA',
  'RICHTEXT',
  'MARKDOWN',
  'INT',
  'DECIMAL',
  'BOOL',
  'DATE',
  'DATETIME',
  'ENUM',
  'ENUM_MULTI',
  'COLOR',
  'IMAGE',
  'IMAGES',
  'FILE',
  'FILES',
  'RELATION',
  'TAGS',
  'JSON',
]

const fieldTypeHints: Record<FieldTypeName, string> = {
  TEXT: '单行文本。',
  TEXTAREA: '多行文本，输出时换行会转成 <br>。',
  RICHTEXT: '富文本，入库前按白名单清洗；记得同时打开"原样输出"。',
  MARKDOWN: 'Markdown，入库时预渲染，模板用 [field:contentHtml/] 取渲染结果。',
  INT: '整数（bigint）。',
  DECIMAL: '小数（numeric）。',
  BOOL: '布尔，内容里存 0 / 1。',
  DATE: '日期，存 YYYY-MM-DD。',
  DATETIME: '日期时间，存 YYYY-MM-DD HH:mm:ss。',
  ENUM: '单选枚举，存"值"，可用 format=\'label\' 输出标签。',
  ENUM_MULTI: '多选枚举，每个取值一行进索引表，可被 in / has 过滤。',
  COLOR: '颜色（#rrggbb）。',
  IMAGE: '单张图片（媒体 URL）。',
  IMAGES: '图集（多个媒体 URL），可用 foreach 迭代。',
  FILE: '单个附件（媒体 URL）。',
  FILES: '多个附件（媒体 URL 列表）。',
  RELATION: '关联内容：值填目标内容的 id，可配 orderby=\'relationOrder\'。',
  TAGS: '标签（系统标签库的 id 列表）。',
  JSON: '任意 jsonb：多段字段名访问，或迭代键值对做规格参数表。',
}

const loading = ref(false)
const saving = ref(false)
const rows = ref<FieldItem[]>([])

/* ---------------- 列表 ---------------- */

async function load() {
  if (!props.typeCode) {
    rows.value = []
    return
  }
  loading.value = true
  try {
    rows.value = await listFields(props.typeCode)
  } catch {
    // 失败提示由请求拦截器统一给出，这里只保证 loading 被复位
  } finally {
    loading.value = false
  }
}

/* ---------------- 新增 / 编辑 ---------------- */

interface FieldForm {
  id: number
  code: string
  label: string
  fieldType: FieldTypeName
  formatter: string
  raw: number
  required: number
  defaultValue: string
  options: string
  searchable: number
  indexed: number
  crossSite: number
  help: string
  sort: number
}

const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<FieldForm>(emptyForm())

const isEnum = computed(
  () => form.fieldType === 'ENUM' || form.fieldType === 'ENUM_MULTI',
)

const fieldTypeHint = computed(() => fieldTypeHints[form.fieldType])

const rules: FormRules = {
  code: [
    { required: true, message: '请输入字段名', trigger: 'blur' },
    {
      pattern: /^[A-Za-z_][A-Za-z0-9_]{0,63}$/,
      message: '只能由字母、数字与下划线组成，并以字母或下划线开头',
      trigger: 'blur',
    },
  ],
  label: [{ required: true, message: '请输入显示名', trigger: 'blur' }],
  fieldType: [{ required: true, message: '请选择字段类型', trigger: 'change' }],
}

function emptyForm(): FieldForm {
  return {
    id: 0,
    code: '',
    label: '',
    fieldType: 'TEXT',
    formatter: '',
    raw: 0,
    required: 0,
    defaultValue: '',
    options: '',
    searchable: 0,
    indexed: 0,
    crossSite: 0,
    help: '',
    sort: 0,
  }
}

function openDialog(row?: TableRow) {
  Object.assign(form, emptyForm())
  if (row) {
    const item = row as unknown as FieldItem
    form.id = item.id
    form.code = item.code
    form.label = item.label
    form.fieldType = item.fieldType
    form.formatter = item.formatter || ''
    form.raw = item.raw
    form.required = item.required
    form.defaultValue = item.defaultValue || ''
    form.options = item.options || ''
    form.searchable = item.searchable
    form.indexed = item.indexed
    form.crossSite = item.crossSite
    form.help = item.help || ''
    form.sort = item.sort
  } else {
    // 追加到末尾：列表按 sort 升序，取当前最大值 +1 是最不容易让人再手改一次的选择
    const maxSort = rows.value.reduce((max, item) => Math.max(max, item.sort), 0)
    form.sort = maxSort + 1
  }
  dialogVisible.value = true
}

/** 空串 → null：后端可空列，空格串会被当成真的取值存进去 */
function orNull(value: string) {
  return value.trim() === '' ? null : value.trim()
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
  const body: FieldBody = {
    typeCode: props.typeCode,
    code: form.code.trim(),
    label: form.label.trim(),
    fieldType: form.fieldType,
    formatter: orNull(form.formatter),
    raw: form.raw,
    required: form.required,
    defaultValue: orNull(form.defaultValue),
    // 非 ENUM 类型留着上次填的选项没有意义，清掉免得引擎误读
    options: isEnum.value ? orNull(form.options) : null,
    searchable: form.searchable,
    indexed: form.indexed,
    crossSite: form.crossSite,
    help: orNull(form.help),
    sort: form.sort,
  }
  saving.value = true
  try {
    if (form.id > 0) {
      await updateField(form.id, body)
    } else {
      await createField(body)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await load()
    emit('count-change')
  } catch {
    // 失败提示由请求拦截器统一给出，这里保持弹窗打开便于重试
  } finally {
    saving.value = false
  }
}

function onDelete(row: TableRow) {
  ElMessageBox.confirm(
    `确认删除字段「${row.label}（${row.code}）」吗？它的字段索引行会一起清掉，已经填过值的内容该字段会丢失筛选能力。`,
    '提示',
    { type: 'warning' },
  )
    .then(async () => {
      await deleteField(Number(row.id))
      ElMessage.success('删除成功')
      await load()
      emit('count-change')
    })
    .catch((error) => {
      // 用户取消（'cancel'/'close'）无需处理，接口失败必须区分出来
      if (error !== 'cancel' && error !== 'close') {
        ElMessage.error('删除失败，请稍后重试')
      }
    })
}

/**
 * 上移 / 下移：不能只交换两个 sort 值——新建的字段 sort 常常都是 0，交换等于没动。
 * 这里按当前顺序重排成 1..n 并只提交顺序变了的那些行，语义确定且不受旧取值影响。
 */
async function move(index: number, delta: number) {
  const target = index + delta
  if (target < 0 || target >= rows.value.length) return
  const ordered = rows.value.slice()
  const [moved] = ordered.splice(index, 1)
  ordered.splice(target, 0, moved)
  loading.value = true
  try {
    for (let i = 0; i < ordered.length; i++) {
      const item = ordered[i]
      const sort = i + 1
      if (item.sort === sort) continue
      await updateField(item.id, {
        typeCode: item.typeCode,
        code: item.code,
        label: item.label,
        fieldType: item.fieldType,
        formatter: item.formatter ?? null,
        raw: item.raw,
        required: item.required,
        defaultValue: item.defaultValue ?? null,
        options: item.options ?? null,
        searchable: item.searchable,
        indexed: item.indexed,
        crossSite: item.crossSite,
        help: item.help ?? null,
        sort,
      })
    }
    ElMessage.success('顺序已更新')
    await load()
  } catch {
    // 第 k 条失败时前面的 sort 已经写进库，界面顺序与库存不一致；
    // 重新拉一次真实顺序，避免用户对着界面以为已保存
    ElMessage.warning('排序未全部保存，已刷新为数据库里的真实顺序')
    await load()
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.field-tips {
  background-color: #f5f7fa;
  border-radius: 4px;
  padding: 10px 14px;
  margin-bottom: 14px;
  font-size: 12px;
  color: #606266;
  line-height: 1.7;
}

.field-tips p {
  margin: 0 0 4px;
}

.field-tips p:last-child {
  margin-bottom: 0;
}

.field-tips code,
.hint code {
  background-color: #ebeef5;
  padding: 0 3px;
  border-radius: 3px;
}

.hint {
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
  margin-top: 4px;
  width: 100%;
}

.field-form {
  padding-right: 8px;
}
</style>
