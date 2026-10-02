<template>
  <div class="page-container">
    <el-card v-permission="'cms:publish:option:list'" shadow="never" class="options-card">
      <div class="table-toolbar options-toolbar">
        <div class="toolbar-left">
          <el-input
            v-model="keyword"
            placeholder="按选项名过滤，如 page. / contact."
            clearable
            size="small"
            style="width: 240px"
          />
          <span class="form-tip">
            值留空 = 未设置，发布时按引擎默认值处理；点「保存」只提交改动过的项
          </span>
        </div>
        <div class="toolbar-right">
          <el-button
            v-permission="'cms:publish:option:edit'"
            size="small"
            :icon="Plus"
            @click="openCreate()"
          >
            新增选项
          </el-button>
          <el-button
            v-permission="'cms:publish:option:edit'"
            size="small"
            type="primary"
            :icon="Select"
            :loading="saving"
            :disabled="!dirtyItems.length"
            @click="onSave"
          >
            保存{{ dirtyItems.length ? `（${dirtyItems.length} 项改动）` : '' }}
          </el-button>
        </div>
      </div>

      <div v-loading="loading" class="sections">
        <section
          v-for="group in visibleGroups"
          :key="group.key"
          class="section"
          :class="{ 'is-wide': group.columns === 3 }"
        >
          <header class="section-head">
            <span class="section-title">{{ group.title }}</span>
            <span class="section-count">{{ group.items.length }}</span>
            <el-tooltip :content="group.desc" placement="top" :show-after="150">
              <el-icon class="section-info"><InfoFilled /></el-icon>
            </el-tooltip>
          </header>

          <div class="options" :class="`cols-${group.columns}`">
            <div
              v-for="item in group.items"
              :key="item.optionCode"
              class="opt"
              :class="[`type-${item.valueType}`, { 'is-dirty': isDirty(item.optionCode) }]"
            >
              <div class="opt-head">
                <span class="opt-code" :title="cellTitle(item)">{{ item.optionCode }}</span>
                <el-button
                  v-permission="'cms:publish:option:delete'"
                  class="opt-del"
                  link
                  size="small"
                  :icon="Delete"
                  title="删除这一条选项，发布时按引擎默认值处理"
                  @click="onDelete(item)"
                />
              </div>
              <div class="opt-body">
                <el-switch
                  v-if="item.valueType === 'bool'"
                  size="small"
                  :model-value="boolValue(item.optionCode)"
                  @update:model-value="(val: boolean | string | number) => setBool(item.optionCode, val)"
                />
                <el-input-number
                  v-else-if="item.valueType === 'number'"
                  size="small"
                  :controls="false"
                  :min="0"
                  :model-value="numberValue(item.optionCode)"
                  @update:model-value="(val: number | undefined) => setNumber(item.optionCode, val)"
                />
                <el-input
                  v-else-if="item.valueType === 'json'"
                  v-model="draft[item.optionCode]"
                  type="textarea"
                  :rows="3"
                  placeholder='JSON 字面量，如 [] 或 {"code":"50x"}'
                />
                <el-input v-else v-model="draft[item.optionCode]" size="small" />
              </div>
              <div v-if="jsonErrors[item.optionCode]" class="opt-error">
                {{ jsonErrors[item.optionCode] }}
              </div>
              <div v-if="hintOf(item.optionCode)" class="form-tip hint opt-hint">
                {{ hintOf(item.optionCode) }}
              </div>
            </div>

            <button
              v-permission="'cms:publish:option:edit'"
              type="button"
              class="opt-add"
              :title="`在「${group.title}」下新增选项`"
              @click="openCreate(group.prefix)"
            >
              <el-icon><Plus /></el-icon>
              <span>新增</span>
            </button>
          </div>

          <div v-if="!group.items.length" class="form-tip section-empty">
            还没有自定义选项：点右上「新增选项」补一条，如 contact.wechat 写微信号。
          </div>
        </section>
      </div>

      <el-empty
        v-if="!loading && !visibleGroups.length"
        description="没有可显示的选项"
      />
    </el-card>

    <el-dialog v-model="createVisible" title="新增发布选项" width="560px" destroy-on-close>
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="100px">
        <el-form-item label="选项名" prop="optionCode">
          <el-input v-model="createForm.optionCode" placeholder="如 contact.wechat" />
        </el-form-item>
        <el-form-item label="值类型">
          <el-select v-model="createForm.valueType" style="width: 160px">
            <el-option label="文本 text" value="text" />
            <el-option label="开关 bool" value="bool" />
            <el-option label="整数 number" value="number" />
            <el-option label="JSON json" value="json" />
          </el-select>
          <span class="form-tip inline-tip">
            类型只决定这个弹窗与列表里的输入控件；后端按发布引擎的清单判定类型，
            引擎不认识的选项一律按文本存取
          </span>
        </el-form-item>
        <el-form-item label="值" prop="value">
          <el-switch
            v-if="createForm.valueType === 'bool'"
            :model-value="createForm.value === '1'"
            active-text="开"
            inactive-text="关"
            @update:model-value="(val: boolean | string | number) => (createForm.value = val === true || val === '1' ? '1' : '0')"
          />
          <el-input-number
            v-else-if="createForm.valueType === 'number'"
            :model-value="createNumberValue"
            :min="0"
            controls-position="right"
            @update:model-value="(val: number | undefined) => (createForm.value = val === undefined || val === null ? '' : String(val))"
          />
          <el-input
            v-else-if="createForm.valueType === 'json'"
            v-model="createForm.value"
            type="textarea"
            :rows="3"
            placeholder="JSON 字面量，如 []"
          />
          <el-input v-else v-model="createForm.value" placeholder="选项值" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="onCreate">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Delete, InfoFilled, Plus, Select } from '@element-plus/icons-vue'
import { deletePublishOption, listPublishOptions, savePublishOptions } from '@/api/cms'
import type { PublishOptionItem, PublishOptionValueType } from '@/types'

/* ---------------- 列表与草稿 ---------------- */

const loading = ref(false)
const saving = ref(false)
const keyword = ref('')
const options = ref<PublishOptionItem[]>([])
/** 服务端当前值：脏值比对与「当前值」展示都以它为准 */
const original = ref<Record<string, string>>({})
/** 编辑中的值：统一存字符串，bool / number 只在使用时转换 */
const draft = reactive<Record<string, string>>({})

const dirtyItems = computed(() =>
  options.value
    .filter((item) => draft[item.optionCode] !== original.value[item.optionCode])
    .map((item) => ({ optionCode: item.optionCode, value: draft[item.optionCode] ?? '' })),
)

async function load() {
  loading.value = true
  try {
    const list = await listPublishOptions()
    options.value = list
    const next: Record<string, string> = {}
    for (const item of list) {
      const value = item.value ?? ''
      next[item.optionCode] = value
      draft[item.optionCode] = value
    }
    original.value = next
  } finally {
    loading.value = false
  }
}

/* ---------------- 按 valueType 双向转换 ---------------- */

/** 后端也接受 true / false 形态的开关值，别把它显示成「关」 */
function boolValue(code: string) {
  const value = draft[code] ?? ''
  return value === '1' || value === 'true'
}

function setBool(code: string, val: boolean | string | number) {
  draft[code] = val === true || val === '1' ? '1' : '0'
}

function numberValue(code: string): number | undefined {
  const text = (draft[code] ?? '').trim()
  if (!text) return undefined
  const value = Number(text)
  return Number.isNaN(value) ? undefined : value
}

function setNumber(code: string, val: number | undefined) {
  draft[code] = val === undefined || val === null ? '' : String(val)
}

function currentText(code: string) {
  const value = original.value[code]
  return value === undefined || value === '' ? '（未设置）' : value
}

function isDirty(code: string) {
  return draft[code] !== original.value[code]
}

/** 每格只在标签上挂原生 tooltip：当前值 + 改动状态 + 该选项的额外说明，不再单占一行 */
function cellTitle(item: PublishOptionItem) {
  const parts = [`当前值：${currentText(item.optionCode)}`]
  if (isDirty(item.optionCode)) parts.push('已改动，未保存')
  const hint = hintOf(item.optionCode)
  if (hint) parts.push(hint)
  return parts.join('\n')
}

/** JSON 选项的校验结果：空串 = 未设置，不算错 */
const jsonErrors = computed<Record<string, string>>(() => {
  const errors: Record<string, string> = {}
  for (const item of options.value) {
    if (item.valueType !== 'json') continue
    const text = (draft[item.optionCode] ?? '').trim()
    if (!text) continue
    try {
      JSON.parse(text)
    } catch (e) {
      errors[item.optionCode] = `JSON 格式不正确：${(e as Error).message}`
    }
  }
  return errors
})

/** pages.static 的条目长什么样，直接写在表单里，省得每次去翻文档 */
function hintOf(code: string) {
  if (code !== 'pages.static') return ''
  return (
    '示例：[{"code":"50x","url":"/50x.html","template":"50x.html","noindex":1}]。' +
    '每项要有 code（页面标识）、url（访问地址）、template（主题里的模板文件）；' +
    '列表页可另加 "type":"list" 与 "query"，noindex=1 表示这一页不写进 sitemap。'
  )
}

/* ---------------- 分组 ---------------- */

const CUSTOM_KEY = 'custom'

const CUSTOM_DESC =
  '站点自定义事实：模板里用 [field:site.option.<option_code>/] 读取，例如 contact.wechat 写微信号、' +
  'contact.qrImage 写二维码图片地址。官网里所有「出现第二次以上」的事实（电话、地址、微信号、公众号、' +
  '开票信息、营业时间……）都放这里：只写一次，模板里想引用几处就引用几处，改一处全站生效。'

interface OptionGroupDef {
  prefix: string
  title: string
  desc: string
  /**
   * 组内选项的列数。3 = 每个选项占一格排成九宫格（选项多的组用），
   * 1 = 标签在左、控件在右的一行一格（只有一两个参数的零散组用，三组并排一行）。
   */
  columns: 1 | 3
}

/**
 * 前缀 → 分组说明。后端返回的选项名是「前缀.名称」两段式，所以按前缀成组；
 * 引擎以后加了新前缀，在这里补一行即可，补漏的会落到「站点自定义」那一组。
 * 顺序 = 页面上的顺序：先大组（各自占满一行、内部九宫格），再零散组（三组并排），最后自定义。
 */
const GROUP_DEFS: OptionGroupDef[] = [
  {
    prefix: 'page.',
    title: '页面开关',
    columns: 3,
    desc: '控制要不要输出这一类页面：关掉某类页面（归档页、筛选页等）后，发布计划里不再生成它，也就不会多出一堆空页面。',
  },
  {
    prefix: 'url.',
    title: '地址规则',
    columns: 3,
    desc: '各类页面的 URL 形态，决定产物落在 www/ 的哪个路径、页面之间怎么互链；花括号里是占位符（{tagSlug}、{year}、{n}…），发布时逐个替换。',
  },
  {
    prefix: 'publish.',
    title: '发布行为',
    columns: 3,
    desc: '发布引擎自己的工作参数：默认模式、线程数、单页超时、保留几个历史批次、预演与严格模式等。',
  },
  {
    prefix: 'facets.',
    title: '筛选页（facets）',
    columns: 3,
    desc: '按字段组合出的筛选页：有哪些组合、单页数量上限与基数阈值；组合越多，产出页面增长越快。',
  },
  {
    prefix: 'pages.',
    title: '自定义静态页（JSON）',
    columns: 3,
    desc: 'pages.static 声明由模板自己渲染的固定页面（关于我们、50x 错误页、活动页），每项一张页面。',
  },
  {
    prefix: 'feed.',
    title: '订阅源（RSS / Atom）',
    columns: 3,
    desc: '订阅源的格式、条数、收录哪些内容类型，以及要不要带全文。',
  },
  {
    prefix: 'seo.',
    title: 'SEO 收录',
    columns: 3,
    desc: '哪些页面不写进 sitemap、要不要给分页页与筛选页做收录。',
  },
  {
    prefix: 'search.',
    title: '站内搜索',
    columns: 1,
    desc: '搜索索引的模式与规模上限：static 模式把索引直接打进产物，前端不依赖后端。',
  },
  {
    prefix: 'comment.',
    title: '评论',
    columns: 1,
    desc: '评论是否审核、是否随页面产出快照以及快照条数。',
  },
  {
    prefix: 'media.',
    title: '媒体资源',
    columns: 1,
    desc: '图片派生宽度（逗号分隔）与 CDN 域名；派生宽度决定生成几套缩略图。',
  },
  {
    prefix: 'archive.',
    title: '归档页规则',
    columns: 1,
    desc: '归档页按年（year）还是按月（month）分组。',
  },
  {
    prefix: 'toc.',
    title: '正文目录',
    columns: 1,
    desc: '正文目录（toc）取哪几级标题，逗号分隔，如 h2,h3。',
  },
  {
    prefix: 'reading.',
    title: '阅读时长',
    columns: 1,
    desc: '每分钟按多少字折算阅读时长，影响页面上的「约 x 分钟」。',
  },
  {
    prefix: 'pager.',
    title: '分页文案',
    columns: 1,
    desc: '分页器四个按钮的文案，按「首页,上一页,下一页,末页」顺序逗号分隔。',
  },
  {
    prefix: 'neighbor.',
    title: '上下篇',
    columns: 1,
    desc: '详情页「上一篇 / 下一篇」各取几条，0 表示不出。',
  },
  {
    prefix: 'sitemap.',
    title: '站点地图',
    columns: 1,
    desc: 'sitemap 分片大小：单文件最多放多少条地址，超过就拆成 sitemap 索引。',
  },
  {
    prefix: 'index.',
    title: '全站索引分片',
    columns: 1,
    desc: '搜索索引单片的条数上限。',
  },
  {
    prefix: 'i18n.',
    title: '多语言备选',
    columns: 1,
    desc: 'hreflang 备选地址列表（JSON），供搜索引擎识别其它语言版本。',
  },
]

const groups = computed(() => {
  const text = keyword.value.trim().toLowerCase()
  const matched = options.value.filter(
    (item) => !text || item.optionCode.toLowerCase().includes(text),
  )
  const result = GROUP_DEFS.map((def) => ({
    key: def.prefix,
    prefix: def.prefix,
    title: def.title,
    desc: def.desc,
    columns: def.columns,
    items: [] as PublishOptionItem[],
  }))
  const custom = {
    key: CUSTOM_KEY,
    prefix: '',
    title: '站点自定义',
    desc: CUSTOM_DESC,
    columns: 3 as const,
    items: [] as PublishOptionItem[],
  }
  for (const item of matched) {
    const group = result.find((candidate) => item.optionCode.startsWith(candidate.key)) || custom
    group.items.push(item)
  }
  return [...result, custom]
})

/**
 * 能显示的小节。空小节里只有「站点自定义」要留着——它是新增选项的落点，
 * 且没有过滤词时得让人看见「这里可以加自己的事实」。
 */
const visibleGroups = computed(() =>
  groups.value.filter(
    (group) => group.items.length || (group.key === CUSTOM_KEY && !keyword.value.trim()),
  ),
)

/* ---------------- 保存 ---------------- */

async function onSave() {
  if (Object.keys(jsonErrors.value).length) {
    ElMessage.warning('有 JSON 选项格式不正确，先改好再保存')
    return
  }
  if (!dirtyItems.value.length) {
    ElMessage.info('没有改动')
    return
  }
  const changed = dirtyItems.value.length
  saving.value = true
  try {
    await savePublishOptions(dirtyItems.value)
    ElMessage.success(`已保存 ${changed} 项`)
    await load()
  } finally {
    saving.value = false
  }
}

/* ---------------- 删除选项 ---------------- */

/** 有已知前缀 = 站点播种出来的标准选项（分组时也是这么判的，见 groups） */
function isStandardOption(code: string) {
  return GROUP_DEFS.some((def) => code.startsWith(def.prefix))
}

async function onDelete(item: PublishOptionItem) {
  let tip = isStandardOption(item.optionCode)
    ? `确认删除标准选项「${item.optionCode}」吗？删掉后发布时按引擎默认值处理，` +
      '需要时用「新增选项」补回同名选项即可。'
    : `确认删除自定义选项「${item.optionCode}」吗？`
  if (dirtyItems.value.length) {
    tip += ` 另外列表里有 ${dirtyItems.value.length} 项未保存的改动，删除后会按服务端值重新加载，这些改动会丢。`
  }
  try {
    await ElMessageBox.confirm(tip, '提示', { type: 'warning' })
  } catch {
    return // 用户取消（'cancel' / 'close'）
  }
  try {
    await deletePublishOption(item.optionCode)
    ElMessage.success(`已删除选项 ${item.optionCode}`)
    await load()
  } catch {
    // 失败提示由请求拦截器统一给出
  }
}

/* ---------------- 新增选项 ---------------- */

const createVisible = ref(false)
const creating = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = reactive({
  optionCode: '',
  valueType: 'text' as PublishOptionValueType,
  value: '',
})

const createRules: FormRules = {
  optionCode: [
    { required: true, message: '请输入选项名', trigger: 'blur' },
    {
      pattern: /^[A-Za-z0-9_.-]{1,64}$/,
      message: '只能用字母、数字、下划线、点或短横线，最长 64 个字符',
      trigger: 'blur',
    },
  ],
}

const createNumberValue = computed(() => {
  const text = createForm.value.trim()
  if (!text) return undefined
  const value = Number(text)
  return Number.isNaN(value) ? undefined : value
})

/** 从分类末尾的新增图标进来时带上该分类前缀，选出来的选项名会自动落回这一组 */
function openCreate(prefix = '') {
  createForm.optionCode = prefix
  createForm.valueType = 'text'
  createForm.value = ''
  createVisible.value = true
}

async function onCreate() {
  const instance = createFormRef.value
  if (!instance) return
  await instance.validate()

  const code = createForm.optionCode.trim()
  if (options.value.some((item) => item.optionCode === code)) {
    ElMessage.warning(`选项 ${code} 已存在，直接在上面对应分组里改它的值即可`)
    return
  }
  if (createForm.valueType === 'json' && createForm.value.trim()) {
    try {
      JSON.parse(createForm.value)
    } catch (e) {
      ElMessage.error(`JSON 格式不正确：${(e as Error).message}`)
      return
    }
  }

  creating.value = true
  try {
    // 后端按 optionCode upsert：库里没有就是新增，所以新增走的就是保存接口
    await savePublishOptions([{ optionCode: code, value: createForm.value }])
    ElMessage.success(`已新增选项 ${code}`)
    createVisible.value = false
    await load()
  } finally {
    creating.value = false
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

/* 弹窗里跟在控件后面的说明，与控件拉开一点距离 */
.inline-tip {
  margin-left: 8px;
}

.hint {
  color: #b88230;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.toolbar-right {
  display: flex;
  align-items: center;
}

/*
 * el-card 是 overflow: hidden、.el-card__body 是 overflow: auto——两个都会变成
 * 「滚动容器」，把吸顶的工具栏按死在卡片里，所以都要放开（这里没有需要裁切的内容）。
 */
.options-card,
.options-card :deep(.el-card__body) {
  overflow: visible;
}

/* 55 个选项拉得很长，工具栏吸顶，滚到哪都能直接保存 */
.options-toolbar {
  position: sticky;
  top: 0;
  z-index: 3;
  margin: -20px -20px 12px;
  padding: 14px 20px 12px;
  background-color: #fff;
  border-bottom: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-card-border-radius) var(--el-card-border-radius) 0 0;
}

/* 三列铺开：大组占满一行（内部九宫格），零散组各占一格、三组并排 */
.sections {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  align-items: start;
}

.section {
  min-width: 0;
  padding: 8px 10px 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}

.section.is-wide {
  grid-column: 1 / -1;
}

.section-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}

.section-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}

.section-title::before {
  content: '';
  display: inline-block;
  width: 3px;
  height: 11px;
  margin-right: 6px;
  border-radius: 2px;
  background-color: var(--el-color-primary);
  vertical-align: -1px;
}

.section-count {
  font-size: 12px;
  color: #a8abb2;
}

.section-info {
  font-size: 13px;
  color: #c0c4cc;
  cursor: help;
}

.section-info:hover {
  color: var(--el-color-primary);
}

.section-empty {
  padding: 2px 6px;
}

.options {
  display: grid;
  gap: 2px 12px;
  align-items: start;
}

.options.cols-3 {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.options.cols-1 {
  grid-template-columns: minmax(0, 1fr);
}

.opt {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
  padding: 3px 6px;
  border-radius: 4px;
}

.opt:hover {
  background-color: var(--el-fill-color-light);
}

/* 已改动未保存：底色 + 标签变色，保存按钮上的条数是汇总 */
.opt.is-dirty {
  background-color: #fdf6ec;
}

/* 分类末尾的「新增」占位格：虚线格，平时淡成占位色，指向才高亮 */
.opt-add {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  min-height: 28px;
  padding: 3px 8px;
  border: 1px dashed var(--el-border-color);
  border-radius: 4px;
  background: none;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  cursor: pointer;
}

.opt-add:hover {
  color: var(--el-color-primary);
  border-color: var(--el-color-primary);
  background-color: var(--el-color-primary-light-9);
}

/* 选项名与删除按钮同一行：名字过长时省略，删除按钮始终贴这一行的右端 */
.opt-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 4px;
  min-width: 0;
}

.opt-code {
  min-width: 0;
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-size: 12px;
  color: #606266;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: help;
}

/*
 * 删除是低频动作：平时淡成占位色，不跟输入控件抢注意力，指向它才变红。
 * 选择器带上 .opt-head 是为了压过 el-button 自己那条 .el-button.is-link（同为两段）。
 */
.opt-head .opt-del {
  flex: none;
  height: auto;
  padding: 0;
  color: var(--el-text-color-placeholder);
}

.opt-head .opt-del:hover {
  color: var(--el-color-danger);
}

.opt.is-dirty .opt-code {
  color: #b88230;
  font-weight: 600;
}

.opt-body {
  min-width: 0;
}

.opt-body :deep(.el-input),
.opt-body :deep(.el-input-number) {
  width: 100%;
}

/* 计数只用得着几位数，别拉满一格宽 */
.opt-body :deep(.el-input-number) {
  max-width: 160px;
}

.opt-error {
  font-size: 12px;
  line-height: 18px;
  color: var(--el-color-danger);
}

.opt-hint {
  margin-top: 2px;
}

/* JSON 文本域要宽度：占满整行 */
.opt.type-json {
  grid-column: 1 / -1;
}

/* 开关只有一格：标签在左、开关在右，省掉一整行控件高度 */
.options.cols-3 .opt.type-bool {
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-height: 28px;
}

/* 开关行里标签要占满剩余宽度，删除按钮才会贴到开关左边而不是紧跟选项名 */
.options.cols-3 .opt.type-bool .opt-head {
  flex: 1 1 auto;
}

/* 零散组：一行一个，标签定宽 */
.options.cols-1 .opt {
  flex-direction: row;
  align-items: center;
  gap: 8px;
  min-height: 30px;
}

.options.cols-1 .opt-head {
  flex: 0 0 132px;
}

.options.cols-1 .opt-body {
  flex: 1 1 auto;
}

.options.cols-1 .opt.type-json {
  flex-direction: column;
  align-items: stretch;
  gap: 3px;
}

.options.cols-1 .opt.type-json .opt-head {
  flex: none;
}

@media (max-width: 1200px) {
  .options.cols-3 {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .sections {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .sections,
  .options.cols-3 {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
