<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ isEdit ? '编辑内容' : '新增内容' }}</span>
          <el-button :icon="Back" @click="goBack">返回</el-button>
        </div>
      </template>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="110px"
        style="max-width: 940px"
      >
        <el-divider content-position="left">基本信息</el-divider>

        <el-form-item label="内容类型" prop="typeCode">
          <el-select
            v-model="form.typeCode"
            :disabled="isEdit"
            placeholder="请选择内容类型"
            style="width: 260px"
            @change="onTypeChange"
          >
            <el-option
              v-for="item in typeOptions"
              :key="item.code"
              :label="`${item.name}（${item.code}）`"
              :value="item.code"
            />
          </el-select>
          <div class="hint">
            <template v-if="isEdit">
              内容类型创建后不能修改：它决定自定义字段、模板与详情页 URL。
            </template>
            <template v-else>选择类型后，下面会出现该类型定义的自定义字段。</template>
          </div>
        </el-form-item>
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="请输入标题" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="访问别名">
          <el-input v-model="form.slug" placeholder="URL 里的那一段，可留空" />
          <div class="hint">同一内容类型、同一父级下不能重复，详情页 URL 常按它生成。</div>
        </el-form-item>
        <el-form-item label="摘要">
          <el-input
            v-model="form.summary"
            type="textarea"
            :rows="3"
            maxlength="300"
            show-word-limit
            placeholder="列表页与 SEO 描述会用到的短文本"
          />
        </el-form-item>
        <el-form-item label="封面">
          <el-input v-model="form.cover" placeholder="图片地址，如 /uploads/2026/01/xxx.png">
            <template #append>
              <el-button @click="openMediaPicker">从媒体库选择</el-button>
            </template>
          </el-input>
          <el-image
            v-if="form.cover"
            :src="form.cover"
            fit="cover"
            class="cover-preview"
            :preview-src-list="[form.cover]"
            preview-teleported
          />
        </el-form-item>
        <el-form-item label="主分类">
          <el-cascader
            :model-value="primaryCategoryId === null ? undefined : primaryCategoryId"
            :options="categories"
            :props="cascaderProps"
            placeholder="决定详情页 URL 与面包屑，可留空"
            clearable
            style="width: 100%"
            @update:model-value="onPrimaryCategoryChange"
          />
          <div class="hint">主分类是详情页 URL、面包屑与 canonical 的来源，一个内容至多一个。</div>
        </el-form-item>
        <el-form-item label="附加分类">
          <el-select
            v-model="extraCategoryIds"
            multiple
            collapse-tags
            collapse-tags-tooltip
            placeholder="可留空"
            style="width: 100%"
          >
            <el-option v-for="item in flatCategories" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签">
          <el-select
            v-model="form.tagIds"
            multiple
            collapse-tags
            collapse-tags-tooltip
            placeholder="请选择标签"
            style="width: 100%"
          >
            <el-option v-for="tag in tagOptions" :key="tag.id" :label="tag.name" :value="tag.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 200px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已发布" value="PUBLISHED" />
            <el-option label="已下线" value="OFFLINE" />
          </el-select>
          <div class="hint">这里只是内容本身的状态；生成静态页面还要到发布中心执行发布。</div>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" controls-position="right" />
          <div class="hint">数值越大越靠前（列表默认按它倒序）。</div>
        </el-form-item>
        <el-form-item label="置顶">
          <el-switch
            :model-value="form.top"
            @update:model-value="(val: boolean | string | number) => (form.top = val === true)"
          />
        </el-form-item>
        <el-form-item label="推荐">
          <el-switch
            :model-value="form.recommend"
            @update:model-value="(val: boolean | string | number) => (form.recommend = val === true)"
          />
        </el-form-item>
        <el-form-item label="发布时间">
          <el-date-picker
            :model-value="form.publishTime"
            type="datetime"
            placeholder="可留空，发布时会自动补当前时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 240px"
            clearable
            @update:model-value="onPublishTimeChange"
          />
        </el-form-item>

        <el-divider content-position="left">SEO</el-divider>

        <el-form-item label="SEO 标题">
          <el-input v-model="form.seoTitle" placeholder="留空用内容标题" />
        </el-form-item>
        <el-form-item label="SEO 描述">
          <el-input v-model="form.seoDescription" type="textarea" :rows="2" placeholder="留空用摘要" />
        </el-form-item>
        <el-form-item label="SEO 关键词">
          <el-input v-model="form.seoKeywords" placeholder="英文逗号分隔" />
        </el-form-item>

        <el-divider content-position="left">正文</el-divider>

        <el-form-item label="正文格式">
          <el-radio-group
            :model-value="form.contentFormat"
            @update:model-value="onContentFormatChange"
          >
            <el-radio value="RICHTEXT">富文本</el-radio>
            <el-radio value="MARKDOWN">Markdown</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="正文">
          <div class="editor-wrap">
            <div v-show="form.contentFormat === 'RICHTEXT'">
              <Toolbar
                class="editor-toolbar"
                :editor="editorRef"
                :default-config="toolbarConfig"
                mode="default"
              />
              <Editor
                v-model="htmlValue"
                class="editor-body"
                :default-config="editorConfig"
                mode="default"
                @on-created="handleCreated"
              />
            </div>
            <el-input
              v-show="form.contentFormat === 'MARKDOWN'"
              v-model="form.content"
              type="textarea"
              :rows="16"
              class="markdown-body"
              placeholder="请输入 Markdown 内容"
            />
          </div>
        </el-form-item>

        <template v-if="fields.length > 0">
          <el-divider content-position="left">
            自定义字段（来自「{{ currentTypeName }}」的字段定义）
          </el-divider>

          <el-form-item
            v-for="field in fields"
            :key="field.id"
            :label="field.label"
            :prop="fieldProp(field)"
          >
            <div class="field-control">
              <!-- 文本类 -->
              <el-input
                v-if="field.fieldType === 'TEXT'"
                v-model="textValues[field.code]"
                :placeholder="field.help || `请输入${field.label}`"
              />
              <el-input
                v-else-if="field.fieldType === 'TEXTAREA'"
                v-model="textValues[field.code]"
                type="textarea"
                :rows="4"
                :placeholder="field.help || `请输入${field.label}`"
              />

              <!-- 正文类：与公共正文一样，富文本走 wangEditor -->
              <div v-else-if="field.fieldType === 'RICHTEXT'" class="editor-wrap">
                <Toolbar
                  class="editor-toolbar"
                  :editor="fieldEditorRefs[field.code]"
                  :default-config="toolbarConfig"
                  mode="default"
                />
                <Editor
                  :model-value="textValues[field.code]"
                  class="editor-body"
                  :default-config="editorConfig"
                  mode="default"
                  @on-created="(editor: IDomEditor) => handleFieldEditorCreated(field.code, editor)"
                  @on-change="(editor: IDomEditor) => onFieldRichTextChange(field.code, editor)"
                />
              </div>
              <el-input
                v-else-if="field.fieldType === 'MARKDOWN'"
                v-model="textValues[field.code]"
                type="textarea"
                :rows="10"
                class="markdown-body"
                :placeholder="field.help || `请输入${field.label}（Markdown）`"
              />

              <!-- 数值 / 布尔 -->
              <el-input-number
                v-else-if="field.fieldType === 'INT'"
                v-model="numberValues[field.code]"
                :precision="0"
                controls-position="right"
                :placeholder="field.help || ''"
              />
              <el-input-number
                v-else-if="field.fieldType === 'DECIMAL'"
                v-model="numberValues[field.code]"
                controls-position="right"
                :placeholder="field.help || ''"
              />
              <el-switch
                v-else-if="field.fieldType === 'BOOL'"
                v-model="boolValues[field.code]"
              />

              <!-- 日期 -->
              <el-date-picker
                v-else-if="field.fieldType === 'DATE'"
                :model-value="textValues[field.code]"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择日期"
                style="width: 220px"
                clearable
                @update:model-value="(val: unknown) => onDateChange(field.code, val)"
              />
              <el-date-picker
                v-else-if="field.fieldType === 'DATETIME'"
                :model-value="textValues[field.code]"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="选择日期时间"
                style="width: 240px"
                clearable
                @update:model-value="(val: unknown) => onDateChange(field.code, val)"
              />

              <!-- 枚举 -->
              <el-select
                v-else-if="field.fieldType === 'ENUM'"
                v-model="textValues[field.code]"
                clearable
                placeholder="请选择"
                style="width: 260px"
              >
                <el-option
                  v-for="option in enumOptionsOf(field)"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
              <el-select
                v-else-if="field.fieldType === 'ENUM_MULTI'"
                v-model="multiValues[field.code]"
                multiple
                collapse-tags
                collapse-tags-tooltip
                clearable
                placeholder="可多选"
                style="width: 100%"
              >
                <el-option
                  v-for="option in enumOptionsOf(field)"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>

              <!-- 颜色 -->
              <el-color-picker v-else-if="field.fieldType === 'COLOR'" v-model="textValues[field.code]" />

              <!-- 媒体：文本框 + 上传，值就是媒体地址 -->
              <div
                v-else-if="field.fieldType === 'IMAGE' || field.fieldType === 'FILE'"
                class="upload-row"
              >
                <el-input
                  v-model="textValues[field.code]"
                  :placeholder="`请选择或填写${field.label}的地址`"
                />
                <el-upload
                  :show-file-list="false"
                  :http-request="(options: UploadRequestOptions) => onUpload(field.code, options)"
                >
                  <el-button
                    v-permission="'cms:media:upload'"
                    :icon="UploadFilled"
                    :loading="uploading === field.code"
                  >
                    上传
                  </el-button>
                </el-upload>
                <el-image
                  v-if="field.fieldType === 'IMAGE' && textValues[field.code]"
                  :src="textValues[field.code]"
                  fit="cover"
                  class="media-preview"
                  :preview-src-list="[textValues[field.code]]"
                  preview-teleported
                />
              </div>
              <div
                v-else-if="field.fieldType === 'IMAGES' || field.fieldType === 'FILES'"
                class="upload-list"
              >
                <div v-for="(url, index) in multiValues[field.code]" :key="`${url}-${index}`" class="upload-item">
                  <el-input
                    :model-value="url"
                    @update:model-value="(val: string) => updateUploadItem(field.code, index, val)"
                  />
                  <el-image
                    v-if="field.fieldType === 'IMAGES'"
                    :src="url"
                    fit="cover"
                    class="media-preview"
                    :preview-src-list="[url]"
                    preview-teleported
                  />
                  <el-button link type="danger" @click="removeUploadItem(field.code, index)">
                    移除
                  </el-button>
                </div>
                <el-upload
                  :show-file-list="false"
                  :http-request="(options: UploadRequestOptions) => onUpload(field.code, options)"
                >
                  <el-button
                    v-permission="'cms:media:upload'"
                    :icon="UploadFilled"
                    :loading="uploading === field.code"
                  >
                    添加{{ field.fieldType === 'IMAGES' ? '图片' : '附件' }}
                  </el-button>
                </el-upload>
              </div>

              <!-- 标签 / 关联内容 -->
              <el-select
                v-else-if="field.fieldType === 'TAGS'"
                v-model="multiValues[field.code]"
                multiple
                collapse-tags
                collapse-tags-tooltip
                filterable
                clearable
                placeholder="请选择标签"
                style="width: 100%"
              >
                <el-option
                  v-for="tag in tagOptions"
                  :key="tag.id"
                  :label="tag.name"
                  :value="String(tag.id)"
                />
              </el-select>
              <el-select
                v-else-if="field.fieldType === 'RELATION'"
                v-model="multiValues[field.code]"
                multiple
                collapse-tags
                collapse-tags-tooltip
                filterable
                clearable
                placeholder="请选择关联内容"
                style="width: 100%"
              >
                <el-option
                  v-for="item in relationCandidates"
                  :key="item.id"
                  :label="item.title"
                  :value="String(item.id)"
                />
              </el-select>

              <!-- JSON -->
              <el-input
                v-else-if="field.fieldType === 'JSON'"
                v-model="textValues[field.code]"
                type="textarea"
                :rows="6"
                class="json-body"
                placeholder='[{"label":"尺寸","value":"A4"}]'
              />
              <el-input v-else v-model="textValues[field.code]" />

              <div v-if="field.help" class="hint">{{ field.help }}</div>
              <div v-if="field.fieldType === 'JSON'" class="hint">
                必须是合法 JSON（对象 / 数组 / 裸值都可以），模板按多段字段名取值或迭代键值对。
              </div>
              <div
                v-if="field.fieldType === 'IMAGE' || field.fieldType === 'IMAGES'"
                class="hint"
              >
                上传后存的是媒体地址（URL）。引擎也认纯数字的媒体 id，那种写法模板还能取到尺寸与
                alt；填地址时模板只拿得到这个地址本身。
              </div>
              <div v-if="field.indexed === 0" class="hint">
                这个字段没勾「可筛选」，模板的 <code>where</code> 筛不到它。
              </div>
            </div>
          </el-form-item>
        </template>
        <el-alert
          v-else-if="form.typeCode"
          type="info"
          :closable="false"
          show-icon
          title="这个内容类型还没有定义自定义字段"
          description="自定义字段在「内容类型 → 字段」里维护。"
        />

        <el-form-item>
          <el-button
            v-permission="isEdit ? 'cms:content:edit' : 'cms:content:add'"
            type="primary"
            :loading="saving"
            @click="save"
          >
            保存
          </el-button>
          <el-button @click="goBack">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-dialog v-model="mediaVisible" title="从媒体库选择封面" width="720px">
      <div v-loading="mediaLoading" class="media-grid">
        <div v-for="item in mediaList" :key="item.id" class="media-item" @click="pickMedia(item)">
          <el-image :src="item.url" fit="cover" class="media-thumb" />
          <div class="media-name" :title="item.name">{{ item.name }}</div>
        </div>
        <el-empty v-if="!mediaLoading && mediaList.length === 0" description="媒体库暂无文件" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ElMessage,
  type FormInstance,
  type FormRules,
  type UploadRequestOptions,
} from 'element-plus'
import { Back, UploadFilled } from '@element-plus/icons-vue'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
import type { IDomEditor, IEditorConfig, IToolbarConfig } from '@wangeditor/editor'
import '@wangeditor/editor/dist/css/style.css'

import {
  categoryTree,
  contentTypeOptions,
  createContent,
  getContent,
  listContents,
  listFields,
  listMedia,
  listTags,
  updateContent,
  uploadMedia,
} from '@/api/cms'
import type {
  CategoryNode,
  ContentBody,
  ContentRow,
  ContentStatus,
  FieldItem,
  MediaItem,
  TableRow,
  TagItem,
} from '@/types'

const route = useRoute()
const router = useRouter()

type ContentFormatValue = 'RICHTEXT' | 'MARKDOWN'
type DynamicValue = string | number | boolean | string[] | null

const formRef = ref<FormInstance>()
const saving = ref(false)
const uploading = ref('')
const typeOptions = ref<Array<{ code: string; name: string }>>([])
const fields = ref<FieldItem[]>([])
const tagOptions = ref<TagItem[]>([])
const relationCandidates = ref<ContentRow[]>([])
const categories = ref<CategoryNode[]>([])

/**
 * 分类分两个控件：后端把 categoryIds 的第一个当主分类（它决定 URL / 面包屑），
 * 顺序有语义，所以不能让用户在一个多选框里"碰运气排序"。
 */
const primaryCategoryId = ref<number | null>(null)
const extraCategoryIds = ref<number[]>([])

const flatCategories = computed(() => {
  const result: Array<{ id: number; name: string }> = []
  const walk = (nodes: CategoryNode[]) => {
    for (const node of nodes) {
      result.push({ id: node.id, name: node.name })
      if (node.children?.length) walk(node.children)
    }
  }
  walk(categories.value)
  return result
})

const cascaderProps = {
  value: 'id',
  label: 'name',
  children: 'children',
  checkStrictly: true,
  emitPath: false,
}

function onPrimaryCategoryChange(val: unknown) {
  primaryCategoryId.value = typeof val === 'number' ? val : null
}

/** 路由里带 id 就是编辑；新增时类型来自列表页传的 typeCode */
const contentId = computed(() => {
  const id = route.query.id
  return id ? Number(id) : 0
})
const isEdit = computed(() => contentId.value > 0)

const currentTypeName = computed(() => {
  const hit = typeOptions.value.find((item) => item.code === form.typeCode)
  return hit ? hit.name : form.typeCode
})

const form = reactive({
  typeCode: String(route.query.typeCode || ''),
  title: '',
  slug: '',
  summary: '',
  cover: '',
  status: 'DRAFT' as ContentStatus,
  sort: 0,
  top: false,
  recommend: false,
  tagIds: [] as number[],
  publishTime: '',
  seoTitle: '',
  seoDescription: '',
  seoKeywords: '',
  content: '',
  contentFormat: 'RICHTEXT' as ContentFormatValue,
})

/**
 * 自定义字段的取值按控件分桶：Element Plus 对数字框、开关、多选的长度约束不同，
 * 塞在一个对象里类型就没法收敛。提交时再按字段定义合并成 ContentBody.data。
 */
const textValues = reactive<Record<string, string>>({})
const numberValues = reactive<Record<string, number | undefined>>({})
const boolValues = reactive<Record<string, boolean>>({})
const multiValues = reactive<Record<string, string[]>>({})

/** 动态字段的必填规则：定义里 required=1 的字段，这里现场补一条校验 */
const rules = computed<FormRules>(() => {
  const result: FormRules = {
    typeCode: [{ required: true, message: '请选择内容类型', trigger: 'change' }],
    title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  }
  for (const field of fields.value) {
    if (field.required !== 1) continue
    result[fieldProp(field)] = [
      {
        required: true,
        validator: (_rule, value: unknown, callback: (error?: Error) => void) => {
          if (isEmptyValue(value)) {
            callback(new Error(`请填写${field.label}`))
          } else {
            callback()
          }
        },
        trigger: ['blur', 'change'],
      },
    ]
  }
  return result
})

/**
 * 动态字段的值按控件分桶存在 textValues / numberValues / boolValues / multiValues 里，
 * Element Plus 是按 `:prop` 路径去 `:model`（即 form）上取值做校验的，所以 el-form-item 的
 * prop 与 rules 的键必须指向真正的桶，写成 form 上并不存在的 `data.<code>` 会让必填校验恒不通过。
 */
function fieldBucket(field: FieldItem): string {
  switch (field.fieldType) {
    case 'INT':
    case 'DECIMAL':
      return 'numberValues'
    case 'BOOL':
      return 'boolValues'
    case 'ENUM_MULTI':
    case 'IMAGES':
    case 'FILES':
    case 'RELATION':
    case 'TAGS':
      return 'multiValues'
    default:
      return 'textValues'
  }
}

/** 取值路径：桶名 + 双下划线 + 字段 code（code 是下划线标识，不会与之混淆） */
function fieldProp(field: FieldItem): string {
  return `${fieldBucket(field)}__${field.code}`
}

function isEmptyValue(value: unknown): boolean {
  if (value === null || value === undefined) return true
  if (typeof value === 'string') return value.trim() === ''
  if (Array.isArray(value)) return value.length === 0
  return false
}

function ensureBuckets(field: FieldItem) {
  if (textValues[field.code] === undefined) textValues[field.code] = ''
  if (multiValues[field.code] === undefined) multiValues[field.code] = []
  if (boolValues[field.code] === undefined) boolValues[field.code] = false
}

/* ---------------- wangEditor（公共正文） ---------------- */

const editorRef = shallowRef<IDomEditor>()
const fieldEditorRefs = shallowRef<Record<string, IDomEditor>>({})
const htmlValue = ref('')
const toolbarConfig: Partial<IToolbarConfig> = {}
/** wangEditor 默认的 server 上传会绕过统一请求层，这里改成用 uploadMedia 自己传 */
const editorConfig: Partial<IEditorConfig> = {
  placeholder: '请输入正文内容...',
  MENU_CONF: {
    uploadImage: {
      customUpload(file: File, insertFn: (url: string, alt: string, href: string) => void) {
        uploadMedia(file)
          .then((item) => {
            if (item?.url) {
              insertFn(item.url, item.name, item.url)
            }
          })
          .catch(() => {
            // 失败提示由请求拦截器统一给出，这里只需保证不产生未处理的拒绝
          })
      },
    },
  },
}

function handleCreated(editor: IDomEditor) {
  editorRef.value = editor
}

function handleFieldEditorCreated(code: string, editor: IDomEditor) {
  fieldEditorRefs.value = { ...fieldEditorRefs.value, [code]: editor }
}

/** 富文本自定义字段：值收进 textValues，和别的文本字段同一处保存 */
function onFieldRichTextChange(code: string, editor: IDomEditor) {
  textValues[code] = editor.getHtml()
}

function destroyEditors() {
  editorRef.value?.destroy()
  for (const editor of Object.values(fieldEditorRefs.value)) {
    editor.destroy()
  }
  fieldEditorRefs.value = {}
}

onBeforeUnmount(destroyEditors)

function onContentFormatChange(val: string | number | boolean | undefined) {
  form.contentFormat = val === 'MARKDOWN' ? 'MARKDOWN' : 'RICHTEXT'
}

function onPublishTimeChange(val: unknown) {
  form.publishTime = typeof val === 'string' ? val : ''
}

function onDateChange(code: string, val: unknown) {
  textValues[code] = typeof val === 'string' ? val : ''
}

watch(
  () => form.contentFormat,
  (fmt) => {
    // 与文章编辑一致：两种格式共用 form.content，切换时把编辑器里的结果带过去
    if (fmt === 'RICHTEXT') {
      htmlValue.value = form.content
      void nextTick(() => editorRef.value?.setHtml(form.content))
    } else if (htmlValue.value) {
      // 编辑器还没回填过内容时不要把正文反向覆盖成空串（详情加载顺序见 onMounted）
      form.content = htmlValue.value
    }
  },
)

/* ---------------- 枚举与上传 ---------------- */

interface EnumOption {
  value: string
  label: string
}

/** options 的写法是「值:标签」逗号分隔，只写值表示标签与值相同 */
function enumOptionsOf(field: FieldItem): EnumOption[] {
  const text = (field.options || '').trim()
  if (!text) return []
  return text
    .split(',')
    .map((piece) => piece.trim())
    .filter((piece) => piece !== '')
    .map((piece) => {
      const colon = piece.indexOf(':')
      return colon < 0
        ? { value: piece, label: piece }
        : { value: piece.slice(0, colon).trim(), label: piece.slice(colon + 1).trim() }
    })
}

async function onUpload(code: string, options: UploadRequestOptions) {
  uploading.value = code
  try {
    const item = await uploadMedia(options.file)
    if (!item?.url) {
      ElMessage.error('上传失败：响应里没有地址')
      return
    }
    if (code in multiValues) {
      multiValues[code].push(item.url)
    } else {
      textValues[code] = item.url
    }
    ElMessage.success('上传成功')
    options.onSuccess?.(item)
  } catch (e) {
    // 错误提示已经由请求拦截器给出；这里补一个带 HTTP 上下文的错误交给上传组件收尾，
    // 否则它会一直停在 loading 态
    const error = new Error((e as Error).message) as Error & {
      status: number
      method: string
      url: string
    }
    error.status = 0
    error.method = 'POST'
    error.url = '/api/cms/media'
    options.onError?.(error)
  } finally {
    uploading.value = ''
  }
}

function updateUploadItem(code: string, index: number, value: string) {
  multiValues[code][index] = value
}

function removeUploadItem(code: string, index: number) {
  multiValues[code].splice(index, 1)
}

/* ---------------- 媒体库选封面 ---------------- */

const mediaVisible = ref(false)
const mediaLoading = ref(false)
const mediaList = ref<MediaItem[]>([])

async function openMediaPicker() {
  mediaVisible.value = true
  mediaLoading.value = true
  try {
    const data = await listMedia({ page: 1, size: 24 })
    mediaList.value = data.records
  } catch {
    // 失败提示由请求拦截器统一给出，弹窗里没有列表时用户可以直接关闭
  } finally {
    mediaLoading.value = false
  }
}

function pickMedia(item: MediaItem) {
  form.cover = item.url
  mediaVisible.value = false
}

/* ---------------- 字段定义与取值 ---------------- */

/**
 * 后端只在 data 里接受该类型声明过的字段，值按类型归一：多值字段收成数组、
 * 标量收成字符串 / 数值 / 0-1（BOOL 必须转成 1 / 0），JSON 原样保留。
 */
function toPayloadValue(field: FieldItem): DynamicValue {
  switch (field.fieldType) {
    case 'TEXT':
    case 'TEXTAREA':
    case 'RICHTEXT':
    case 'MARKDOWN':
    case 'DATE':
    case 'DATETIME':
    case 'COLOR':
    case 'ENUM':
    case 'IMAGE':
    case 'FILE':
      return normalizeText(textValues[field.code])
    case 'INT':
    case 'DECIMAL':
      return numberValues[field.code] ?? null
    case 'BOOL':
      return boolValues[field.code] ? 1 : 0
    case 'ENUM_MULTI':
    case 'IMAGES':
    case 'FILES':
    case 'RELATION':
    case 'TAGS':
      return multiValues[field.code].map((item) => item.trim()).filter((item) => item !== '')
    case 'JSON':
      return parseJsonValue(textValues[field.code], field.label)
    default:
      return normalizeText(textValues[field.code])
  }
}

/** 空串统一回 null：空值本来就会被后端丢掉，留着空串只会让"填没填"变得看不出来 */
function normalizeText(value: string | undefined): string | null {
  if (value === undefined) return null
  const text = value.trim()
  return text === '' ? null : text
}

/** JSON 字段存的是结构化取值：模板要按对象 / 数组取值，存成字符串数组会读不出来 */
function parseJsonValue(text: string | undefined, label: string): DynamicValue {
  const raw = (text || '').trim()
  if (raw === '') return null
  try {
    return JSON.parse(raw) as DynamicValue
  } catch {
    ElMessage.warning(
      `自定义字段「${label}」不是合法 JSON，已按纯文本保存（模板按多段取值会读不到）`,
    )
    return raw
  }
}

function setBuckets(field: FieldItem, value: DynamicValue) {
  switch (field.fieldType) {
    case 'ENUM_MULTI':
    case 'IMAGES':
    case 'FILES':
    case 'RELATION':
    case 'TAGS':
      multiValues[field.code] = toStringArray(value)
      break
    case 'INT':
    case 'DECIMAL':
      // 后端返回的整数可能是字符串（Long 序列化约定），这里统一收敛成 number
      numberValues[field.code] = toNumber(value)
      break
    case 'BOOL':
      boolValues[field.code] = toBool(value)
      break
    case 'JSON':
      // 用格式化后的 JSON 回填文本域，编辑时能直接看到结构
      textValues[field.code] = value === null || value === undefined ? '' : JSON.stringify(value, null, 2)
      break
    case 'RICHTEXT':
      textValues[field.code] = typeof value === 'string' ? value : ''
      // 编辑器实例此时还没创建，下一个 tick 再灌 HTML（和公共正文同一套写法）
      void nextTick(() => fieldEditorRefs.value[field.code]?.setHtml(textValues[field.code]))
      break
    default:
      textValues[field.code] = toScalarText(value)
  }
}

function toScalarText(value: DynamicValue): string {
  if (value === null || value === undefined) return ''
  if (typeof value === 'string') return value
  if (typeof value === 'number' || typeof value === 'boolean') return String(value)
  return ''
}

function toNumber(value: DynamicValue): number | undefined {
  if (typeof value === 'number') return value
  if (typeof value === 'string' && value.trim() !== '') {
    const parsed = Number(value)
    return Number.isNaN(parsed) ? undefined : parsed
  }
  return undefined
}

function toBool(value: DynamicValue): boolean {
  if (typeof value === 'boolean') return value
  if (typeof value === 'number') return value !== 0
  if (typeof value === 'string') return value === '1' || value.toLowerCase() === 'true'
  return false
}

function toStringArray(value: DynamicValue): string[] {
  if (Array.isArray(value)) return value.map((item) => String(item))
  if (value === null || value === undefined || value === '') return []
  return [String(value)]
}

async function loadFields(typeCode: string) {
  fields.value = []
  if (!typeCode) return
  // 换类型时旧字段的取值一并清掉：残留的键既不会提交（后端只认声明过的字段），
  // 又会让表单上出现上一个类型的遗留值
  for (const key of [...Object.keys(textValues), ...Object.keys(numberValues), ...Object.keys(multiValues), ...Object.keys(boolValues)]) {
    delete textValues[key]
    delete numberValues[key]
    delete multiValues[key]
    delete boolValues[key]
  }
  fields.value = await listFields(typeCode)
  for (const field of fields.value) {
    ensureBuckets(field)
  }
}

/** 关联字段的候选项：同类型的其他内容（值存内容 id） */
async function loadRelationCandidates(typeCode: string) {
  relationCandidates.value = []
  if (!fields.value.some((field) => field.fieldType === 'RELATION') || !typeCode) return
  const data = await listContents({ page: 1, size: 200, typeCode })
  relationCandidates.value = data.records
}

/* ---------------- 保存 ---------------- */

function goBack() {
  router.push('/cms/contents')
}

async function save() {
  const instance = formRef.value
  if (!instance) return
  try {
    await instance.validate()
  } catch {
    // 校验失败是正常分支，不往事件处理器外抛
    return
  }
  if (!form.typeCode) {
    ElMessage.warning('请先选择内容类型')
    return
  }
  const data: Record<string, unknown> = {}
  for (const field of fields.value) {
    data[field.code] = toPayloadValue(field)
  }
  const body: ContentBody = {
    typeCode: form.typeCode,
    title: form.title,
    slug: form.slug.trim() || null,
    summary: form.summary.trim() || null,
    cover: form.cover.trim() || null,
    status: form.status,
    sort: form.sort,
    top: form.top,
    recommend: form.recommend,
    publishTime: form.publishTime || null,
    content: form.contentFormat === 'RICHTEXT' ? htmlValue.value : form.content,
    contentFormat: form.contentFormat,
    seoTitle: form.seoTitle.trim() || null,
    seoDescription: form.seoDescription.trim() || null,
    seoKeywords: form.seoKeywords.trim() || null,
    // 第一个是主分类（顺序有语义），附加分类里重复选中的由后端去重
    categoryIds: [
      ...(primaryCategoryId.value === null ? [] : [primaryCategoryId.value]),
      ...extraCategoryIds.value,
    ],
    tagIds: form.tagIds,
    data,
  }
  saving.value = true
  try {
    if (isEdit.value) {
      await updateContent(contentId.value, body)
    } else {
      await createContent(body)
    }
    ElMessage.success('保存成功')
    router.push('/cms/contents')
  } catch {
    // 失败提示由请求拦截器统一给出，这里留在页面上便于修正后重试
  } finally {
    saving.value = false
  }
}

async function onTypeChange() {
  if (isEdit.value) return
  try {
    await loadFields(form.typeCode)
    await loadRelationCandidates(form.typeCode)
  } catch {
    // 失败提示由请求拦截器统一给出；字段没加载出来时表单保持空字段状态
  }
}

/* ---------------- 初始化 ---------------- */

/** 入库的正文格式只有 RICHTEXT / MARKDOWN，历史数据里的 HTML 按富文本处理 */
function normalizeFormat(value: string | null | undefined): ContentFormatValue {
  return value === 'MARKDOWN' ? 'MARKDOWN' : 'RICHTEXT'
}

onMounted(async () => {
  try {
    // 类型 / 标签 / 分类是筛选与回填的依赖，任一失败都给提示，避免对着半初始化的表单操作
    const [types, tags, tree] = await Promise.all([
      contentTypeOptions(),
      listTags(),
      categoryTree(),
    ])
    typeOptions.value = types
    tagOptions.value = tags
    categories.value = tree

    if (isEdit.value) {
      const detail = await getContent(contentId.value)
      form.typeCode = detail.typeCode
      form.title = detail.title
      form.slug = detail.slug || ''
      form.summary = detail.summary || ''
      form.cover = detail.cover || ''
      form.status = detail.status
      form.sort = detail.sort
      form.top = detail.top === true
      form.recommend = detail.recommend === true
      form.tagIds = detail.tagIds ? [...detail.tagIds] : []
      // 后端给的就是"主分类在前"的顺序，第一个回填到主分类控件
      const categoryIds = detail.categoryIds ? [...detail.categoryIds] : []
      primaryCategoryId.value = categoryIds.length > 0 ? categoryIds[0] : null
      extraCategoryIds.value = categoryIds.slice(1)
      form.publishTime = detail.publishTime || ''
      form.seoTitle = detail.seoTitle || ''
      form.seoDescription = detail.seoDescription || ''
      form.seoKeywords = detail.seoKeywords || ''
      // 先定格式再赋正文：反过来会先触发 contentFormat 的 watch，用还没回填的 htmlValue 把正文覆盖成空
      form.contentFormat = normalizeFormat(detail.contentFormat)
      form.content = detail.content || ''
      htmlValue.value = form.contentFormat === 'RICHTEXT' ? form.content : ''
      await loadFields(form.typeCode)
      const data = detail.data || {}
      for (const field of fields.value) {
        setBuckets(field, (data[field.code] ?? null) as DynamicValue)
      }
    } else if (form.typeCode) {
      await loadFields(form.typeCode)
    }
    await loadRelationCandidates(form.typeCode)
  } catch {
    ElMessage.error('页面初始化失败，请刷新重试')
  }
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

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

.cover-preview {
  width: 160px;
  height: 90px;
  border-radius: 4px;
  margin-top: 8px;
  border: 1px solid #e4e7ed;
}

.editor-wrap {
  width: 100%;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
}

.editor-toolbar {
  border-bottom: 1px solid #e4e7ed;
}

.editor-body {
  height: 320px;
  overflow-y: hidden;
}

.markdown-body :deep(textarea),
.json-body :deep(textarea) {
  font-family: Consolas, Menlo, 'Courier New', monospace;
}

.field-control {
  width: 100%;
}

.upload-row {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.upload-list {
  width: 100%;
}

.upload-item {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.media-preview {
  width: 72px;
  height: 52px;
  border-radius: 4px;
  border: 1px solid #e4e7ed;
  flex-shrink: 0;
}

.media-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  min-height: 120px;
}

.media-item {
  width: 110px;
  cursor: pointer;
  border: 1px solid transparent;
  border-radius: 4px;
  padding: 4px;
}

.media-item:hover {
  border-color: #409eff;
}

.media-thumb {
  width: 100px;
  height: 72px;
  border-radius: 4px;
  background: #f5f7fa;
}

.media-name {
  font-size: 12px;
  color: #606266;
  margin-top: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
