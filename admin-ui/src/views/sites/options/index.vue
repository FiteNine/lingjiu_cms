<template>
  <div class="page-container">
    <el-card v-permission="'cms:publish:option:list'" shadow="never">
      <div class="table-toolbar">
        <div class="toolbar-left">
          <el-input
            v-model="keyword"
            placeholder="按选项名过滤，如 page. / contact."
            clearable
            style="width: 260px"
          />
          <span class="form-tip">
            值留空 = 未设置，发布时按引擎默认值处理；点「保存」只提交改动过的项
          </span>
        </div>
        <div>
          <el-button
            v-permission="'cms:publish:option:edit'"
            :icon="Plus"
            @click="openCreate"
          >
            新增选项
          </el-button>
          <el-button
            v-permission="'cms:publish:option:edit'"
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

      <el-form v-loading="loading" :model="draft" label-width="240px">
        <template v-for="group in groups" :key="group.key">
          <template v-if="group.items.length || (group.key === CUSTOM_KEY && !keyword.trim())">
            <el-divider content-position="left">
              {{ group.title }}（{{ group.items.length }} 项）
            </el-divider>
            <div class="group-tip">{{ group.desc }}</div>

            <el-form-item
              v-for="item in group.items"
              :key="item.optionCode"
              :label="item.optionCode"
              :error="jsonErrors[item.optionCode]"
            >
              <el-switch
                v-if="item.valueType === 'bool'"
                :model-value="boolValue(item.optionCode)"
                active-text="开"
                inactive-text="关"
                @update:model-value="(val: boolean | string | number) => setBool(item.optionCode, val)"
              />
              <el-input-number
                v-else-if="item.valueType === 'number'"
                :model-value="numberValue(item.optionCode)"
                :min="0"
                controls-position="right"
                @update:model-value="(val: number | undefined) => setNumber(item.optionCode, val)"
              />
              <el-input
                v-else-if="item.valueType === 'json'"
                v-model="draft[item.optionCode]"
                type="textarea"
                :rows="3"
                placeholder='JSON 字面量，如 [] 或 {"code":"50x"}'
              />
              <el-input v-else v-model="draft[item.optionCode]" />

              <div class="form-tip current-value">
                当前值：{{ currentText(item.optionCode) }}
                <span v-if="draft[item.optionCode] !== original[item.optionCode]">（已改动，未保存）</span>
              </div>
              <div v-if="hintOf(item.optionCode)" class="form-tip hint current-value">
                {{ hintOf(item.optionCode) }}
              </div>
            </el-form-item>
          </template>
        </template>

        <el-empty
          v-if="!groups.some((group) => group.items.length)"
          description="没有可显示的选项"
        />
      </el-form>
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
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Select } from '@element-plus/icons-vue'
import { listPublishOptions, savePublishOptions } from '@/api/cms'
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
}

/**
 * 前缀 → 分组说明。后端返回的选项名是「前缀.名称」两段式，所以按前缀成组；
 * 引擎以后加了新前缀，在这里补一行即可，补漏的会落到「站点自定义」那一组。
 */
const GROUP_DEFS: OptionGroupDef[] = [
  {
    prefix: 'page.',
    title: '页面开关',
    desc: '控制要不要输出这一类页面：关掉某类页面（归档页、筛选页等）后，发布计划里不再生成它，也就不会多出一堆空页面。',
  },
  {
    prefix: 'pages.',
    title: '自定义静态页（JSON）',
    desc: 'pages.static 声明由模板自己渲染的固定页面（关于我们、50x 错误页、活动页），每项一张页面。',
  },
  {
    prefix: 'url.',
    title: '地址规则',
    desc: '各类页面的 URL 形态，决定产物落在 www/ 的哪个路径、页面之间怎么互链；花括号里是占位符（{tagSlug}、{year}、{n}…），发布时逐个替换。',
  },
  {
    prefix: 'seo.',
    title: 'SEO 收录',
    desc: '哪些页面不写进 sitemap、要不要给分页页与筛选页做收录。',
  },
  {
    prefix: 'publish.',
    title: '发布行为',
    desc: '发布引擎自己的工作参数：默认模式、线程数、单页超时、保留几个历史批次、预演与严格模式等。',
  },
  {
    prefix: 'facets.',
    title: '筛选页（facets）',
    desc: '按字段组合出的筛选页：有哪些组合、单页数量上限与基数阈值；组合越多，产出页面增长越快。',
  },
  {
    prefix: 'feed.',
    title: '订阅源（RSS / Atom）',
    desc: '订阅源的格式、条数、收录哪些内容类型，以及要不要带全文。',
  },
  {
    prefix: 'search.',
    title: '站内搜索',
    desc: '搜索索引的模式与规模上限：static 模式把索引直接打进产物，前端不依赖后端。',
  },
  {
    prefix: 'archive.',
    title: '归档页规则',
    desc: '归档页按年（year）还是按月（month）分组。',
  },
  {
    prefix: 'toc.',
    title: '正文目录',
    desc: '正文目录（toc）取哪几级标题，逗号分隔，如 h2,h3。',
  },
  {
    prefix: 'reading.',
    title: '阅读时长',
    desc: '每分钟按多少字折算阅读时长，影响页面上的「约 x 分钟」。',
  },
  {
    prefix: 'pager.',
    title: '分页文案',
    desc: '分页器四个按钮的文案，按「首页,上一页,下一页,末页」顺序逗号分隔。',
  },
  {
    prefix: 'neighbor.',
    title: '上下篇',
    desc: '详情页「上一篇 / 下一篇」各取几条，0 表示不出。',
  },
  {
    prefix: 'sitemap.',
    title: '站点地图',
    desc: 'sitemap 分片大小：单文件最多放多少条地址，超过就拆成 sitemap 索引。',
  },
  {
    prefix: 'index.',
    title: '全站索引分片',
    desc: '搜索索引单片的条数上限。',
  },
  {
    prefix: 'media.',
    title: '媒体资源',
    desc: '图片派生宽度（逗号分隔）与 CDN 域名；派生宽度决定生成几套缩略图。',
  },
  {
    prefix: 'i18n.',
    title: '多语言备选',
    desc: 'hreflang 备选地址列表（JSON），供搜索引擎识别其它语言版本。',
  },
  {
    prefix: 'comment.',
    title: '评论',
    desc: '评论是否审核、是否随页面产出快照以及快照条数。',
  },
]

const groups = computed(() => {
  const text = keyword.value.trim().toLowerCase()
  const matched = options.value.filter(
    (item) => !text || item.optionCode.toLowerCase().includes(text),
  )
  const result = GROUP_DEFS.map((def) => ({
    key: def.prefix,
    title: def.title,
    desc: def.desc,
    items: [] as PublishOptionItem[],
  }))
  const custom = { key: CUSTOM_KEY, title: '站点自定义', desc: CUSTOM_DESC, items: [] as PublishOptionItem[] }
  for (const item of matched) {
    const group = result.find((candidate) => item.optionCode.startsWith(candidate.key)) || custom
    group.items.push(item)
  }
  return [...result, custom]
})

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

function openCreate() {
  createForm.optionCode = ''
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

/* el-form-item 的内容区是 flex：让「当前值」这类说明独占一行，别挤在开关右边 */
.current-value {
  width: 100%;
}

.hint {
  color: #b88230;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.group-tip {
  font-size: 12px;
  color: #909399;
  line-height: 20px;
  margin: -6px 0 10px;
}

:deep(.el-divider__text) {
  font-weight: 600;
  color: #303133;
}
</style>
