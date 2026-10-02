import { http } from './request'
import type {
  CategoryNode,
  ContentBody,
  ContentDetail,
  ContentRow,
  ContentTypeItem,
  FieldItem,
  MediaItem,
  MenuItem,
  MenuItemNode,
  PageResult,
  PublishOptionItem,
  PublishPreview,
  PublishPreviewSite,
  PublishResult,
  PublishTaskRow,
  SiteDirListing,
  SiteFileContent,
  SiteFileListing,
  SiteItem,
  SiteOption,
  StatsData,
  TagItem,
  ThemeItem,
} from '@/types'

/* ---------------- 分类 ---------------- */

export interface CategoryBody {
  parentId: number
  name: string
  slug: string
  description?: string | null
  cover?: string | null
  sort: number
  status: number
}

export function categoryTree() {
  return http.get<CategoryNode[]>('/api/cms/categories/tree')
}

export function categoryList() {
  return http.get<CategoryNode[]>('/api/cms/categories')
}

export function createCategory(data: CategoryBody) {
  return http.post<null>('/api/cms/categories', data)
}

export function updateCategory(id: number, data: CategoryBody) {
  return http.put<null>(`/api/cms/categories/${id}`, data)
}

export function deleteCategory(id: number) {
  return http.delete<null>(`/api/cms/categories/${id}`)
}

/* ---------------- 标签 ---------------- */

export function listTags(keyword?: string) {
  return http.get<TagItem[]>('/api/cms/tags', { keyword })
}

export function createTag(data: { name: string; slug: string }) {
  return http.post<null>('/api/cms/tags', data)
}

export function updateTag(id: number, data: { name: string; slug: string }) {
  return http.put<null>(`/api/cms/tags/${id}`, data)
}

export function deleteTag(id: number) {
  return http.delete<null>(`/api/cms/tags/${id}`)
}

/* ---------------- 媒体 ---------------- */

export interface MediaQuery {
  page: number
  size: number
  keyword?: string
}

export function listMedia(params: MediaQuery) {
  return http.get<PageResult<MediaItem>>('/api/cms/media', params)
}

export function uploadMedia(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  // 交给运行时生成 multipart 的 boundary：手写 Content-Type 会覆盖掉 boundary 导致后端解析不出文件
  return http.post<MediaItem>('/api/cms/media', formData)
}

export function deleteMedia(id: number) {
  return http.delete<null>(`/api/cms/media/${id}`)
}

/* ---------------- 统计 ---------------- */

export function getStats() {
  return http.get<StatsData>('/api/cms/stats')
}

/* ---------------- 站点 ---------------- */

export interface SiteBody {
  name: string
  code: string
  domain?: string | null
  logo?: string | null
  description?: string | null
  keywords?: string | null
  seoDescription?: string | null
  /** 站点目录：相对站点根目录的路径，保存时不存在会自动创建 */
  rootDir: string
  icp?: string | null
  contactPhone?: string | null
  contactEmail?: string | null
  status: number
  /** 页面语言，如 zh-CN（留空落默认 zh-CN） */
  lang?: string | null
  /** http / https（留空落默认 https） */
  protocol?: string | null
  /** 静态化主题：站点 template/ 下的目录名；留空则发布时报"主题目录不存在" */
  theme?: string | null
  defaultCover?: string | null
  ogImage?: string | null
  statisticsCode?: string | null
}

export function listSites() {
  return http.get<SiteItem[]>('/api/cms/sites')
}

/**
 * 右上角站点切换器的下拉选项：登录即可读，无需 cms:site:list 权限。
 * 后端把 Long 序列化成字符串（避免精度丢失），这里统一转成 number，
 * 否则和 localStorage 里存的站点 id 严格比较会不相等。
 */
export async function siteOptions() {
  const list = await http.get<SiteOption[]>('/api/cms/sites/options')
  return list.map((item) => ({ ...item, id: Number(item.id) }))
}

export function createSite(data: SiteBody) {
  return http.post<null>('/api/cms/sites', data)
}

export function updateSite(id: number, data: SiteBody) {
  return http.put<null>(`/api/cms/sites/${id}`, data)
}

export function deleteSite(id: number) {
  return http.delete<null>(`/api/cms/sites/${id}`)
}

/** path 留空表示站点根目录 */
export function listSiteDirs(path: string) {
  return http.get<SiteDirListing>('/api/cms/sites/dirs', { path })
}

export function createSiteDir(parent: string, name: string) {
  return http.post<null>('/api/cms/sites/dirs', { parent, name })
}

/* ---------------- 站点目录（当前站点的网站文件） ---------------- */
/* 站点由请求头 X-Site-Id 决定，即右上角切换器选中的那个站点 */

/** path 留空表示站点目录本身 */
export function listSiteFiles(path: string) {
  return http.get<SiteFileListing>('/api/cms/sites/files', { path })
}

export function getSiteFileContent(path: string) {
  return http.get<SiteFileContent>('/api/cms/sites/files/content', { path })
}

export function saveSiteFileContent(path: string, content: string) {
  return http.put<null>('/api/cms/sites/files/content', { path, content })
}

/** 在 parent 目录下新建空文件，返回新建文件的相对路径；parent 留空表示站点目录本身 */
export function createSiteFile(parent: string, name: string) {
  return http.post<string>('/api/cms/sites/files', { parent, name })
}

export function createSiteFileDir(parent: string, name: string) {
  return http.post<string>('/api/cms/sites/files/dirs', { parent, name })
}

/** 删除文件或空文件夹，path 相对站点目录 */
export function deleteSiteFile(path: string) {
  return http.delete<null>('/api/cms/sites/files', { path })
}

/* ---------------- 内容类型（动态建模） ---------------- */

export type ContentTypeBody = Omit<ContentTypeItem, 'id' | 'createTime' | 'updateTime'>

export function listContentTypes(params: { page: number; size: number; keyword?: string }) {
  return http.get<PageResult<ContentTypeItem>>('/api/cms/types', params)
}

/** 下拉用：只取 code 与 name，登录即可读 */
export function contentTypeOptions() {
  return http.get<Array<{ code: string; name: string }>>('/api/cms/types/options')
}

export function getContentType(id: number) {
  return http.get<ContentTypeItem>(`/api/cms/types/${id}`)
}

export function createContentType(data: ContentTypeBody) {
  return http.post<null>('/api/cms/types', data)
}

export function updateContentType(id: number, data: ContentTypeBody) {
  return http.put<null>(`/api/cms/types/${id}`, data)
}

export function deleteContentType(id: number) {
  return http.delete<null>(`/api/cms/types/${id}`)
}

/* ---------------- 字段定义 ---------------- */

export type FieldBody = Omit<FieldItem, 'id' | 'createTime' | 'updateTime'>

export function listFields(typeCode: string) {
  return http.get<FieldItem[]>('/api/cms/fields', { typeCode })
}

export function createField(data: FieldBody) {
  return http.post<null>('/api/cms/fields', data)
}

export function updateField(id: number, data: FieldBody) {
  return http.put<null>(`/api/cms/fields/${id}`, data)
}

export function deleteField(id: number) {
  return http.delete<null>(`/api/cms/fields/${id}`)
}

/* ---------------- 通用内容（引擎真正渲染的那张表） ---------------- */

export interface ContentQuery {
  page: number
  size: number
  typeCode?: string
  status?: string
  keyword?: string
}

export function listContents(params: ContentQuery) {
  return http.get<PageResult<ContentRow>>('/api/cms/contents', params)
}

export function getContent(id: number) {
  return http.get<ContentDetail>(`/api/cms/contents/${id}`)
}

export function createContent(data: ContentBody) {
  return http.post<null>('/api/cms/contents', data)
}

export function updateContent(id: number, data: ContentBody) {
  return http.put<null>(`/api/cms/contents/${id}`, data)
}

export function deleteContent(id: number) {
  return http.delete<null>(`/api/cms/contents/${id}`)
}

export function updateContentStatus(id: number, status: string) {
  return http.put<null>(`/api/cms/contents/${id}/status`, { status })
}

/* ---------------- 导航菜单（站点导航，不是系统菜单） ---------------- */

export function listMenus() {
  return http.get<MenuItem[]>('/api/cms/menus')
}

export function createMenu(data: { code: string; name: string; status?: number; sort?: number }) {
  return http.post<null>('/api/cms/menus', data)
}

export function updateMenu(id: number, data: { code: string; name: string; status?: number; sort?: number }) {
  return http.put<null>(`/api/cms/menus/${id}`, data)
}

export function deleteMenu(id: number) {
  return http.delete<null>(`/api/cms/menus/${id}`)
}

export type MenuItemBody = Omit<MenuItemNode, 'id' | 'children'>

export function createMenuItem(menuId: number, data: MenuItemBody) {
  return http.post<null>(`/api/cms/menus/${menuId}/items`, data)
}

export function updateMenuItem(itemId: number, data: MenuItemBody) {
  return http.put<null>(`/api/cms/menus/items/${itemId}`, data)
}

export function deleteMenuItem(itemId: number) {
  return http.delete<null>(`/api/cms/menus/items/${itemId}`)
}

/* ---------------- 站点发布：选项 / 预演 / 发布 / 批次 ---------------- */

export function listPublishOptions() {
  return http.get<PublishOptionItem[]>('/api/cms/publish/options')
}

export function savePublishOptions(options: Array<{ optionCode: string; value: string }>) {
  return http.put<null>('/api/cms/publish/options', { options })
}

/** 删除一条选项：删掉后该项回到引擎默认值，同一个选项名可以再新增回来 */
export function deletePublishOption(optionCode: string) {
  return http.delete<null>('/api/cms/publish/options', { optionCode })
}

/** 预演：只算计划不写盘（当前站点）。problems 非空时不能发布 */
export function previewPublish() {
  return http.get<PublishPreview>('/api/cms/publish/site/preview')
}

/** 一键全站静态化（当前站点）。mode 缺省 full */
export function publishSite(mode: 'full' | 'incremental' = 'full') {
  return http.post<PublishResult>('/api/cms/publish/site', { mode })
}

/** 当前站点的发布批次记录（权限位与发布同一个：cms:publish:run） */
export function listPublishTasks(limit = 20) {
  return http.get<PublishTaskRow[]>('/api/cms/publish/site/tasks', { limit })
}

/**
 * 预览站点：拿当前站点**已发布产物**的只读地址（不发布）。
 * 第一次调用会在服务端为该站点起一个预览端口，之后重复调用返回同一个地址。
 */
export function previewSiteUrl() {
  return http.get<PublishPreviewSite>('/api/cms/publish/site/preview-url')
}

/* ---------------- 主题 ---------------- */

export function listThemes() {
  return http.get<ThemeItem[]>('/api/cms/themes')
}
