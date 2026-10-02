/** 统一响应包：{ code, message, data }，code === 0 为成功 */
export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

/** 分页响应 data */
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

/** 分页请求参数 */
export interface PageQuery {
  page: number
  size: number
}

/* ---------------- 认证 ---------------- */

export interface UserProfile {
  id: number
  username: string
  nickname: string
  avatar: string | null
  email?: string | null
  phone?: string | null
  roles: string[]
  perms: string[]
}

export interface LoginResult {
  token: string
  tokenType: string
  expiresIn: number
  user: UserProfile
}

/* ---------------- 系统管理 ---------------- */

export interface RoleItem {
  id: number
  code: string
  name: string
}

export interface RoleRow extends RoleItem {
  sort: number
  status: number
  remark?: string | null
  createTime?: string
}

export interface SysUser {
  id: number
  username: string
  nickname: string
  email?: string | null
  phone?: string | null
  avatar?: string | null
  status: number
  remark?: string | null
  createTime?: string
  roles: RoleItem[]
  roleIds: number[]
  /** 可切换访问的站点 id（admin 角色不受限制） */
  siteIds: number[]
}

export type MenuType = 'DIR' | 'MENU' | 'BUTTON'

export interface MenuNode {
  id: number
  parentId: number
  name: string
  path: string
  component?: string | null
  icon?: string | null
  perms?: string | null
  type: MenuType
  sort: number
  visible: number
  status: number
  children?: MenuNode[]
}

export interface DictType {
  id: number
  code: string
  name: string
  remark?: string | null
  createTime?: string
}

export interface DictItem {
  id: number
  typeId: number
  label: string
  value: string
  sort: number
  status: number
}

export interface OpLog {
  id: number
  module: string
  action: string
  method: string
  uri: string
  ip: string
  username: string
  status: number
  errorMsg?: string | null
  durationMs: number
  createTime: string
}

/* ---------------- AI 管理 ---------------- */

export type AiProtocol = 'OPENAI' | 'ANTHROPIC' | 'RESPONSES'

export interface AiProvider {
  id: number
  name: string
  code: string
  protocol: AiProtocol
  baseUrl: string
  apiKeyMasked: string | null
  status: number
  remark?: string | null
  createTime?: string
  updateTime?: string
}

export interface AiAgent {
  id: number
  name: string
  code: string
  providerId: number
  providerName: string | null
  protocol: AiProtocol | null
  model: string
  systemPrompt?: string | null
  temperature?: number | null
  topP?: number | null
  maxTokens?: number | null
  thinking: number
  reasoningEffort: string | null
  jsonOutput: number
  /** 是否允许 copilot 调用工具（1 开启 / 0 关闭） */
  toolEnabled: number
  /** 逗号分隔的工具组白名单，空 = 默认组 */
  toolScope: string | null
  status: number
  remark?: string | null
  createTime?: string
  updateTime?: string
}

export interface AiChatMessage {
  role: 'user' | 'assistant'
  content: string
}

export interface AiChatResult {
  content: string | null
  reasoningContent: string | null
  inputTokens: number | null
  outputTokens: number | null
}

/* ---------------- AI 管理：全站agent（Copilot） ---------------- */

/** 智能体下拉项：只含 copilot 可用的智能体，不回 systemPrompt */
export interface CopilotAgentOption {
  id: number
  name: string
  code: string
  model: string
  protocol: AiProtocol
  thinking: number
  jsonOutput: number
  toolEnabled: number
  toolScope: string | null
}

export type CopilotRisk = 'READ' | 'WRITE' | 'DESTRUCTIVE'

export interface CopilotSession {
  id: number
  title: string | null
  siteId: number
  siteName: string | null
  agentId: number
  agentName: string | null
  status: number
  rounds: number
  toolCallCount: number
  inputTokens: number
  outputTokens: number
  createTime: string
}

export interface CopilotChatMessage {
  id: number
  seq: number
  role: 'user' | 'assistant' | 'tool'
  content: string | null
  reasoningContent: string | null
  toolCalls: string | null
  toolCallId: string | null
  toolName: string | null
  toolArgs: string | null
  toolStatus: number | null
  toolDurationMs: number | null
  createTime: string
}

/** SSE 判别联合 */
export type CopilotEvent =
  | {
      event: 'session'
      data: {
        sessionId: number
        siteId: number
        siteName: string
        agentId: number
        agentName: string
        tools: { name: string; title: string; risk: CopilotRisk }[]
      }
    }
  | { event: 'reasoning'; data: { delta: string } }
  | { event: 'delta'; data: { delta: string } }
  | {
      event: 'tool_call'
      data: { toolCallId: string; name: string; title: string; args: unknown; risk: CopilotRisk }
    }
  | {
      event: 'tool_result'
      data: {
        toolCallId: string
        ok: boolean
        summary: string | null
        durationMs: number
        siteId: number
        status: number
      }
    }
  | {
      event: 'confirm'
      data: {
        toolCallId: string
        name: string
        title: string
        args: Record<string, unknown>
        risk: CopilotRisk
        expiresIn: number
      }
    }
  | { event: 'done'; data: { messageId: number; inputTokens: number; outputTokens: number; rounds: number } }
  | { event: 'error'; data: { code: number; message: string } }

/* ---------------- 表格 ---------------- */

/** el-table 默认插槽的行数据（Element Plus 未将数据类型透传到列插槽） */
export type TableRow = Record<PropertyKey, any>

/* ---------------- CMS ---------------- */

export interface CategoryNode {
  id: number
  parentId: number
  name: string
  slug: string
  description?: string | null
  cover?: string | null
  sort: number
  status: number
  contentCount: number
  children?: CategoryNode[]
}

export interface TagItem {
  id: number
  name: string
  slug: string
  contentCount: number
}

export interface MediaItem {
  id: number
  name: string
  url: string
  size: number
  mimeType: string
  ext: string
  createTime: string
}

export interface RecentContent {
  id: number
  typeCode: string
  title: string
  status: ContentStatus
  createTime: string
  authorName: string
}

export interface StatsData {
  contentTotal: number
  contentPublished: number
  contentDraft: number
  categoryTotal: number
  tagTotal: number
  mediaTotal: number
  userTotal: number
  recentContents: RecentContent[]
}

/* ---------------- 站点管理 ---------------- */

/** 右上角站点切换器的下拉选项 */
export interface SiteOption {
  id: number
  name: string
  code: string
  /** 1 表示系统默认站点（有且仅有一个） */
  isDefault: number
}

export interface SiteItem {
  id: number
  name: string
  code: string
  domain?: string | null
  logo?: string | null
  description?: string | null
  keywords?: string | null
  seoDescription?: string | null
  /** 站点目录：相对站点根目录的路径 */
  rootDir: string
  /** 站点目录在服务器上的绝对路径（后端拼好给前端展示） */
  rootDirPath: string
  icp?: string | null
  contactPhone?: string | null
  contactEmail?: string | null
  status: number
  createTime?: string
  updateTime?: string
  /* 静态化相关的 6 列：后端 CmsSite 实体已有，站点表单与发布中心都要用 */
  /** 页面语言，如 zh-CN */
  lang?: string | null
  /** http / https：canonical 与 sitemap 的绝对地址由它 + domain 拼出 */
  protocol?: string | null
  /** 静态化主题：站点 template/ 下的目录名；留空则发布报「主题目录不存在」 */
  theme?: string | null
  defaultCover?: string | null
  ogImage?: string | null
  /** 统计脚本：整段原样注入页面（模板里写 [field:site.statisticsCode/]） */
  statisticsCode?: string | null
}

/** 站点根目录下的一个子目录，path 可直接回填表单 */
export interface SiteDirNode {
  name: string
  path: string
}

export interface SiteDirListing {
  path: string
  dirs: SiteDirNode[]
}

/* ---------------- 站点目录（网站文件） ---------------- */

/** 文件类型：TEXT 可编辑、IMAGE 可预览、OTHER 只能看文件信息 */
export type SiteFileKind = 'TEXT' | 'IMAGE' | 'OTHER'

/** 站点目录下的一个文件；path 相对站点目录，可直接回传给接口 */
export interface SiteFileNode {
  name: string
  path: string
  size: number
  kind: SiteFileKind
}

export interface SiteFileListing {
  /** 站点目录在服务器上的绝对路径 */
  rootPath: string
  /** 当前路径，相对站点目录；站点目录本身为空串 */
  path: string
  dirs: SiteDirNode[]
  files: SiteFileNode[]
}

export interface SiteFileContent {
  path: string
  name: string
  size: number
  kind: SiteFileKind
  /** 文本文件的内容，其余类型为 null */
  text: string | null
  /** 图片的 data URL，其余类型为 null */
  dataUrl: string | null
}

/* ---------------- 内容模型：内容类型 / 字段定义 ---------------- */

/** 内容形态：CONTENT 普通内容、SINGLE 单页（每个类型只允许一条）、TREE 层级内容 */
export type ContentTypeKind = 'CONTENT' | 'SINGLE' | 'TREE'

export interface ContentTypeItem {
  id: number
  code: string
  name: string
  kind: ContentTypeKind
  hierarchical: number
  detailUrlPattern?: string | null
  listUrlPattern?: string | null
  detailTemplate?: string | null
  listTemplate?: string | null
  paginateBody?: string | null
  sortField?: string | null
  sortOrder?: string | null
  perPage: number
  seoTitleField?: string | null
  seoDescField?: string | null
  /** jsonb 原文；引擎按对象读 facets / searchable 两项 */
  options?: string | null
  status: number
  sort: number
  createTime?: string
  updateTime?: string
}

/** 字段类型（与引擎 FieldType 的 19 个取值一一对应） */
export type FieldTypeName =
  | 'TEXT'
  | 'TEXTAREA'
  | 'RICHTEXT'
  | 'MARKDOWN'
  | 'INT'
  | 'DECIMAL'
  | 'BOOL'
  | 'DATE'
  | 'DATETIME'
  | 'ENUM'
  | 'ENUM_MULTI'
  | 'COLOR'
  | 'IMAGE'
  | 'IMAGES'
  | 'FILE'
  | 'FILES'
  | 'RELATION'
  | 'TAGS'
  | 'JSON'

export interface FieldItem {
  id: number
  typeCode: string
  code: string
  label: string
  fieldType: FieldTypeName
  formatter?: string | null
  raw: number
  required: number
  defaultValue?: string | null
  /** ENUM / ENUM_MULTI 用，"值:标签,值:标签" */
  options?: string | null
  searchable: number
  indexed: number
  crossSite: number
  help?: string | null
  sort: number
  createTime?: string
  updateTime?: string
}

/* ---------------- 通用内容 ---------------- */

export type ContentStatus = 'DRAFT' | 'PUBLISHED' | 'OFFLINE'

export interface ContentRow {
  id: number
  typeCode: string
  typeName?: string | null
  parentId: number
  slug?: string | null
  title: string
  summary?: string | null
  cover?: string | null
  status: ContentStatus
  sort: number
  top?: boolean | null
  recommend?: boolean | null
  publishTime?: string | null
  authorName?: string | null
  createTime?: string
  updateTime?: string
}

export interface ContentDetail extends ContentRow {
  /** 正文源（RICHTEXT / MARKDOWN） */
  content?: string | null
  contentFormat?: string | null
  wordCount?: number | null
  /** 自定义字段的取值：{ 字段 code: 值 } */
  data?: Record<string, unknown> | null
  seoTitle?: string | null
  seoDescription?: string | null
  seoKeywords?: string | null
  categoryIds?: number[] | null
  tagIds?: number[] | null
}

export interface ContentBody {
  typeCode: string
  parentId?: number | null
  slug?: string | null
  title: string
  summary?: string | null
  cover?: string | null
  status?: ContentStatus
  sort?: number | null
  top?: boolean | null
  recommend?: boolean | null
  publishTime?: string | null
  data?: Record<string, unknown> | null
  content?: string | null
  contentFormat?: string | null
  seoTitle?: string | null
  seoDescription?: string | null
  seoKeywords?: string | null
  categoryIds?: number[] | null
  tagIds?: number[] | null
}

/* ---------------- 导航菜单（站点导航，区别于系统菜单 sys_menu） ---------------- */

export type MenuItemKind =
  | 'category'
  | 'content'
  | 'url'
  | 'type'
  | 'tag'
  | 'archive'
  | 'custom'
  | 'author'

export interface MenuItemNode {
  id: number
  parentId: number
  label?: string | null
  kind: MenuItemKind
  refId?: number | null
  refCode?: string | null
  url?: string | null
  target?: string | null
  rel?: string | null
  visible: number
  sort: number
  children?: MenuItemNode[]
}

export interface MenuItem {
  id: number
  code: string
  name: string
  status: number
  sort: number
  items: MenuItemNode[]
}

/* ---------------- 站点发布（选项 / 预演 / 批次） ---------------- */

/** bool / number / json 三种选项在后台用开关 / 数字框 / JSON 文本域呈现，其余是普通文本框 */
export type PublishOptionValueType = 'bool' | 'number' | 'json' | 'text'

export interface PublishOptionItem {
  optionCode: string
  value: string
  valueType: PublishOptionValueType
}

export interface PublishTaskRow {
  id: number
  siteId: number
  batchId?: string | null
  mode: string
  trigger?: string | null
  status: string
  /**
   * 批次记录的列名以 `CmsPublishTask` 实体为准：total=计划页数、done=写出页数、
   * failed=失败页数、message=错误摘要（前若干条）、startTime / endTime=起止时间。
   */
  total?: number | null
  done?: number | null
  failed?: number | null
  message?: string | null
  startTime?: string | null
  endTime?: string | null
  createTime?: string
  /* 以下是「发布结果」口径的名字，与上面的列同义：版本不同可能返回任一组，页面对两者都兼容 */
  totalPages?: number | null
  writtenPages?: number | null
  skippedPages?: number | null
  failedPages?: number | null
  deletedArtifacts?: number | null
  /** 用时（毫秒）。批次表里没有这一列，只有发布结果里有；页面按"有则显示、无则 —"处理 */
  elapsedMillis?: number | null
  errorMsg?: string | null
}

export interface PublishPreviewPage {
  pageType: string
  url: string
  path: string
  template: string
}

export interface PublishPreview {
  theme: string
  totalPages: number
  pages: PublishPreviewPage[]
  warnings: string[]
  problems: string[]
}

export interface PublishResult {
  batchId?: string
  totalPages: number
  writtenPages: number
  skippedPages: number
  failedPages: number
  deletedArtifacts: number
  aggregateArtifacts: string[]
  elapsedMillis: number
  outputDir: string
  warnings: string[]
  errors: string[]
  /**
   * 成功与否。后端是 record 的派生方法 `success()`（不是组件），Jackson 默认不会输出这个键，
   * 所以**不要**按它判成功：用 `failedPages > 0 || errors.length > 0` 更可靠（两者口径一致）。
   */
  success?: boolean
}

/** 预览站点：已发布产物的只读地址（另起一个端口，只在后台点按钮时拿它） */
export interface PublishPreviewSite {
  siteId: number
  siteName: string
  /** 被预览的产物目录，与发布结果里的 outputDir 是同一个 */
  outputDir: string
  /** 预览地址，形如 http://127.0.0.1:8096/ */
  url: string
}

/** 站点 template/ 下的一个主题目录 */
export interface ThemeItem {
  code: string
  name: string
  version?: string | null
  description?: string | null
  /** 相对站点目录的路径，如 template/lingjiuw */
  path: string
  valid: boolean
}
