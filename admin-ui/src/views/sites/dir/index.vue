<template>
  <div class="page-container">
    <el-card shadow="never" class="dir-card">
      <div class="dir-body">
        <!-- 左：当前站点的网站文件目录 -->
        <div class="dir-aside">
          <div class="dir-aside-head">
            <span class="dir-aside-title">站点目录</span>
            <el-tag v-if="siteStore.currentSiteName" size="small" type="info">
              {{ siteStore.currentSiteName }}
            </el-tag>
          </div>
          <div class="dir-root" :title="rootPath">{{ rootPath || '加载中…' }}</div>
          <div v-permission="'cms:site:file:add'" class="dir-toolbar">
            <el-button size="small" :icon="Plus" @click="onCreate('FILE')">新建文件</el-button>
            <el-button size="small" :icon="FolderAdd" @click="onCreate('DIR')">新建文件夹</el-button>
          </div>
          <el-tree
            ref="treeRef"
            v-loading="treeLoading"
            lazy
            class="dir-tree"
            :load="loadNode"
            :props="treeProps"
            node-key="path"
            highlight-current
            empty-text="站点目录为空"
            @node-click="onNodeClick"
          >
            <template #default="{ data }">
              <span class="tree-node">
                <el-icon>
                  <Folder v-if="data.isDir" />
                  <Picture v-else-if="data.kind === 'IMAGE'" />
                  <Document v-else />
                </el-icon>
                <span class="tree-node-name">{{ data.name }}</span>
                <el-icon
                  v-permission="'cms:site:file:delete'"
                  class="tree-node-delete"
                  title="删除"
                  @click.stop="onDelete(data)"
                >
                  <Delete />
                </el-icon>
              </span>
            </template>
          </el-tree>
        </div>

        <!-- 右：内容编辑 / 预览 -->
        <div class="dir-detail">
          <template v-if="content">
            <div class="detail-head">
              <div class="detail-info">
                <span class="detail-name">{{ content.name }}</span>
                <el-tag size="small" :type="kindTagType(content.kind)">{{ kindText(content.kind) }}</el-tag>
                <span class="detail-meta">{{ formatSize(content.size) }}</span>
                <span class="detail-path" :title="content.path">{{ content.path }}</span>
              </div>
              <div class="detail-actions">
                <el-radio-group v-if="content.kind === 'TEXT'" v-model="mode" size="small">
                  <el-radio-button value="edit">编辑</el-radio-button>
                  <el-radio-button value="preview">预览</el-radio-button>
                </el-radio-group>
                <el-button
                  v-permission="'cms:site:file:edit'"
                  type="primary"
                  size="small"
                  :disabled="!dirty"
                  :loading="saving"
                  @click="onSave"
                >
                  保存
                </el-button>
              </div>
            </div>

            <div v-loading="contentLoading" class="detail-body">
              <template v-if="content.kind === 'TEXT'">
                <el-input
                  v-if="mode === 'edit'"
                  v-model="draft"
                  type="textarea"
                  resize="none"
                  spellcheck="false"
                  class="detail-editor"
                />
                <iframe
                  v-else-if="renderable"
                  :srcdoc="draft"
                  sandbox="allow-scripts"
                  class="detail-frame"
                  title="预览"
                />
                <pre v-else class="detail-text">{{ draft }}</pre>
              </template>
              <div v-else-if="content.kind === 'IMAGE'" class="detail-image">
                <el-image
                  :src="content.dataUrl || undefined"
                  fit="contain"
                  :preview-src-list="content.dataUrl ? [content.dataUrl] : []"
                  preview-teleported
                  class="detail-image-inner"
                />
              </div>
              <div v-else class="detail-placeholder">
                <el-empty description="该文件类型不支持在线编辑或预览" :image-size="80" />
              </div>
            </div>
          </template>
          <div v-else class="detail-placeholder">
            <el-empty description="点击左侧文件查看内容" />
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox, type TreeInstance } from 'element-plus'
import { Delete, Document, Folder, FolderAdd, Picture, Plus } from '@element-plus/icons-vue'
import {
  createSiteFile,
  createSiteFileDir,
  deleteSiteFile,
  getSiteFileContent,
  listSiteFiles,
  saveSiteFileContent,
} from '@/api/cms'
import { useSiteStore } from '@/stores/site'
import type { SiteFileContent, SiteFileKind } from '@/types'

/** 文件树节点：目录可以继续展开，文件是叶子 */
interface TreeNode {
  name: string
  path: string
  isDir: boolean
  size?: number
  kind?: SiteFileKind
  leaf?: boolean
}

/** el-tree 懒加载出来的节点：只用到这两个成员，用来在原地重拉某个目录的子项 */
interface LazyNode {
  loaded?: boolean
  loadData?: (callback: (data?: Record<string, any>[]) => void, defaultProps?: object) => void
}

const siteStore = useSiteStore()

const treeProps = { label: 'name', isLeaf: 'leaf' }
const treeRef = ref<TreeInstance>()
const treeLoading = ref(false)
const rootPath = ref('')

/** 已经加载过的目录节点：path → 节点，站点目录本身（空串）也在内 */
const loadedNodes = new Map<string, LazyNode>()

/** el-tree 懒加载：根节点取站点目录本身，其余节点取它自己的 path */
async function loadNode(
  node: { level: number; data: Record<string, any> } & LazyNode,
  resolve: (data: Record<string, any>[]) => void,
) {
  const path = node.level === 0 ? '' : (node.data as TreeNode).path
  loadedNodes.set(path, node)
  if (node.level === 0) {
    treeLoading.value = true
  }
  try {
    const data = await listSiteFiles(path)
    if (node.level === 0) {
      rootPath.value = data.rootPath
    }
    const nodes: TreeNode[] = [
      ...data.dirs.map((dir) => ({ name: dir.name, path: dir.path, isDir: true })),
      ...data.files.map((file) => ({ ...file, isDir: false, leaf: true })),
    ]
    resolve(nodes)
  } catch {
    // 错误提示由请求拦截器统一处理，这里给个空节点，避免树一直停在加载中
    resolve([])
  } finally {
    if (node.level === 0) {
      treeLoading.value = false
    }
  }
}

/** 在原地重拉某个目录的子项；这个目录还没展开过就什么都不用做，展开时会自己拉一次 */
function reloadDir(path: string) {
  const node = loadedNodes.get(path)
  const loadData = node?.loadData
  if (!node || !loadData) {
    return Promise.resolve()
  }
  return new Promise<void>((done) => {
    node.loaded = false
    loadData(() => done())
  })
}

/* ---------------- 选中文件与内容 ---------------- */

const content = ref<SiteFileContent | null>(null)
const contentLoading = ref(false)
const draft = ref('')
const mode = ref<'edit' | 'preview'>('edit')
const saving = ref(false)

/** 新建文件/文件夹落在这个目录：选中的是目录就是它本身，选中的是文件就是它所在的目录 */
const currentDir = ref('')

const dirty = computed(() => {
  const current = content.value
  return current?.kind === 'TEXT' ? draft.value !== current.text : false
})

/** html / svg / xml 这类能给浏览器直接渲染的用 iframe 预览，其余文本按原文只读展示 */
const renderable = computed(() => /\.(html?|xhtml|svg|xml)$/i.test(content.value?.name || ''))

function kindText(kind: SiteFileKind) {
  return { TEXT: '文本', IMAGE: '图片', OTHER: '其他' }[kind]
}

function kindTagType(kind: SiteFileKind) {
  return { TEXT: 'success', IMAGE: 'warning', OTHER: 'info' }[kind] as 'success' | 'warning' | 'info'
}

function formatSize(size: number) {
  if (!size) return '0 B'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(2)} MB`
}

function parentPath(path: string) {
  const slash = path.lastIndexOf('/')
  return slash < 0 ? '' : path.slice(0, slash)
}

async function onNodeClick(data: TreeNode) {
  currentDir.value = data.isDir ? data.path : parentPath(data.path)
  if (data.isDir || data.path === content.value?.path) {
    return
  }
  if (!(await confirmDiscard())) {
    return
  }
  await openFile(data.path)
}

async function openFile(path: string) {
  contentLoading.value = true
  try {
    const detail = await getSiteFileContent(path)
    content.value = detail
    draft.value = detail.text ?? ''
    mode.value = 'edit'
  } catch {
    // 错误提示由请求拦截器统一处理；读取失败时不留在上一个文件的内容上
    content.value = null
    draft.value = ''
  } finally {
    contentLoading.value = false
  }
}

/** 新建文件或文件夹：建完刷新所在目录，新建的文件直接打开接着编辑 */
async function onCreate(kind: 'FILE' | 'DIR') {
  const label = kind === 'FILE' ? '文件' : '文件夹'
  const dir = currentDir.value
  if (kind === 'FILE' && !(await confirmDiscard())) {
    return
  }
  let name: string
  try {
    const { value } = await ElMessageBox.prompt(`在 ${dir || '站点目录'} 下新建${label}`, `新建${label}`, {
      inputPlaceholder: kind === 'FILE' ? '如 index.html' : '如 images',
      inputValidator: (input: string) => (input.trim() ? true : `请输入${label}名称`),
    })
    name = value.trim()
  } catch {
    return
  }
  try {
    const path = kind === 'FILE' ? await createSiteFile(dir, name) : await createSiteFileDir(dir, name)
    await reloadDir(dir)
    ElMessage.success(`新建成功：${path}`)
    if (kind === 'FILE') {
      await openFile(path)
    }
  } catch {
    // 错误提示由请求拦截器统一处理
  }
}

/** 删除文件或空文件夹；非空文件夹由后端拦下 */
async function onDelete(data: TreeNode) {
  const label = data.isDir ? '文件夹' : '文件'
  if (data.path === content.value?.path && !(await confirmDiscard())) {
    return
  }
  try {
    await ElMessageBox.confirm(
      data.isDir
        ? `确认删除文件夹「${data.name}」吗？文件夹必须为空`
        : `确认删除文件「${data.name}」吗？`,
      '提示',
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await deleteSiteFile(data.path)
    if (data.path === content.value?.path) {
      content.value = null
      draft.value = ''
    }
    loadedNodes.delete(data.path)
    await reloadDir(parentPath(data.path))
    ElMessage.success(`已删除${label}：${data.name}`)
  } catch {
    // 错误提示由请求拦截器统一处理
  }
}

/** 有未保存的改动时先问一句，避免点一下别的文件就把编辑内容丢掉 */
async function confirmDiscard(): Promise<boolean> {
  if (!dirty.value) {
    return true
  }
  try {
    await ElMessageBox.confirm('当前文件有未保存的修改，确定放弃吗？', '提示', { type: 'warning' })
    return true
  } catch {
    return false
  }
}

async function onSave() {
  const current = content.value
  if (!current || current.kind !== 'TEXT') {
    return
  }
  saving.value = true
  try {
    await saveSiteFileContent(current.path, draft.value)
    current.text = draft.value
    ElMessage.success('保存成功')
  } catch {
    // 错误提示由请求拦截器统一处理
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.dir-card :deep(.el-card__body) {
  padding: 0;
}

.dir-body {
  display: flex;
  height: calc(100vh - 120px);
  min-height: 420px;
}

.dir-aside {
  display: flex;
  flex-direction: column;
  width: 300px;
  flex-shrink: 0;
  border-right: 1px solid #ebeef5;
}

.dir-aside-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 14px 6px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.dir-root {
  padding: 0 14px 8px;
  font-size: 12px;
  color: #909399;
  word-break: break-all;
}

.dir-toolbar {
  padding: 0 14px 8px;
}

.dir-tree {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 0 8px 12px;
}

.tree-node {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
  gap: 6px;
  overflow: hidden;
}

.tree-node-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tree-node-delete {
  flex-shrink: 0;
  visibility: hidden;
  color: #c0c4cc;
}

.tree-node:hover .tree-node-delete {
  visibility: visible;
}

.tree-node-delete:hover {
  color: #f56c6c;
}

.dir-detail {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
}

.detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid #ebeef5;
}

.detail-info {
  display: flex;
  align-items: center;
  gap: 8px;
  overflow: hidden;
  font-size: 14px;
  color: #303133;
}

.detail-name {
  font-weight: 600;
}

.detail-meta,
.detail-path {
  font-size: 12px;
  color: #909399;
}

.detail-path {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.detail-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 12px 16px;
  overflow: hidden;
}

.detail-placeholder {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.detail-editor {
  flex: 1;
  min-height: 0;
}

.detail-editor :deep(.el-textarea__inner) {
  height: 100%;
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-size: 13px;
}

.detail-frame {
  flex: 1;
  min-height: 0;
  width: 100%;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  background: #fff;
}

.detail-text {
  flex: 1;
  min-height: 0;
  margin: 0;
  overflow: auto;
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}

.detail-image {
  flex: 1;
  min-height: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.detail-image-inner {
  max-width: 100%;
  max-height: 100%;
}
</style>
