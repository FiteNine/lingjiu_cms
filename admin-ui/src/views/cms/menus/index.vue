<template>
  <div class="page-container">
    <el-alert
      class="menus-tip"
      type="info"
      :closable="false"
      show-icon
      title="这里维护的是站点导航（数据表 cms_menu / cms_menu_item，模板里用 {cms:channel} 标签读取）；它和「系统管理 → 菜单管理」里的后台菜单（sys_menu）不是一回事，改这里不会影响后台侧边栏。"
    />

    <el-row :gutter="16">
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>
            <div class="block-head">
              <span>菜单容器</span>
              <el-button
                v-permission="'cms:menu:add'"
                type="primary"
                size="small"
                :icon="Plus"
                @click="openMenuDialog()"
              >
                新增菜单
              </el-button>
            </div>
          </template>

          <div class="form-tip">
            一个菜单容器是一组导航，模板用它的 code 取（新建站点默认会有 main）。点一行看它下面的菜单项。
          </div>

          <el-table
            v-loading="loading"
            :data="menus"
            :row-class-name="menuRowClass"
            stripe
            size="small"
            @row-click="selectMenu"
          >
            <el-table-column label="菜单" min-width="130">
              <template #default="{ row }">
                <div>{{ row.name }}</div>
                <div class="form-tip">{{ row.code }}</div>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                  {{ row.status === 1 ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="sort" label="排序" width="70" />
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-button
                  v-permission="'cms:menu:edit'"
                  link
                  type="primary"
                  @click="openMenuDialog(row)"
                >
                  编辑
                </el-button>
                <el-button
                  v-permission="'cms:menu:delete'"
                  link
                  type="danger"
                  @click="onDeleteMenu(row)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <el-col :span="16">
        <el-card shadow="never">
          <template #header>
            <div class="block-head">
              <span>
                菜单项{{ currentMenu ? `：${currentMenu.name}（${currentMenu.code}）` : '' }}
              </span>
              <el-button
                v-if="currentMenu"
                v-permission="'cms:menu:add'"
                type="primary"
                size="small"
                :icon="Plus"
                @click="openItemDialog()"
              >
                新增顶级项
              </el-button>
            </div>
          </template>

          <el-empty
            v-if="!currentMenu"
            description="先在左边选择或新增一个菜单容器"
            :image-size="80"
          />
          <template v-else>
            <div class="form-tip">
              菜单项的 kind 决定它指向什么：链接填 url、内容类型 / 标签 / 归档填 refCode、分类 / 内容填
              refId、占位只用来分层。
            </div>
            <el-table
              :data="currentMenu.items"
              row-key="id"
              :tree-props="{ children: 'children' }"
              default-expand-all
              stripe
              size="small"
            >
              <el-table-column label="名称" min-width="150">
                <template #default="{ row }">
                  <span v-if="row.label">{{ row.label }}</span>
                  <span v-else class="placeholder">（留空则取所指向对象的名称）</span>
                </template>
              </el-table-column>
              <el-table-column label="类型" width="100">
                <template #default="{ row }">
                  <el-tag size="small" effect="plain">{{ kindText(row.kind) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="指向" min-width="180" show-overflow-tooltip>
                <template #default="{ row }">{{ targetText(row) }}</template>
              </el-table-column>
              <el-table-column label="可见" width="80">
                <template #default="{ row }">
                  <el-tag :type="row.visible === 1 ? 'success' : 'info'" size="small">
                    {{ row.visible === 1 ? '显示' : '隐藏' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="sort" label="排序" width="70" />
              <el-table-column label="操作" width="210" fixed="right">
                <template #default="{ row }">
                  <el-button
                    v-permission="'cms:menu:add'"
                    link
                    type="primary"
                    @click="openItemDialog(undefined, Number(row.id))"
                  >
                    新增子项
                  </el-button>
                  <el-button
                    v-permission="'cms:menu:edit'"
                    link
                    type="primary"
                    @click="openItemDialog(row)"
                  >
                    编辑
                  </el-button>
                  <el-button
                    v-permission="'cms:menu:delete'"
                    link
                    type="danger"
                    @click="onDeleteItem(row)"
                  >
                    删除
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </template>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog
      v-model="menuDialogVisible"
      :title="menuForm.id ? '编辑菜单' : '新增菜单'"
      width="520px"
      destroy-on-close
    >
      <el-form ref="menuFormRef" :model="menuForm" :rules="menuRules" label-width="90px">
        <el-form-item label="菜单标识" prop="code">
          <el-input v-model="menuForm.code" placeholder="模板里用它取这份导航，如 main" />
        </el-form-item>
        <el-form-item label="菜单名称" prop="name">
          <el-input v-model="menuForm.name" placeholder="如 主导航" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number
            :model-value="menuForm.sort"
            :min="0"
            controls-position="right"
            @update:model-value="(val: number | undefined) => (menuForm.sort = val ?? 0)"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            :model-value="menuForm.status"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="停用"
            @update:model-value="(val: boolean | string | number) => (menuForm.status = Number(val))"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="menuDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingMenu" @click="onSaveMenu">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="itemDialogVisible"
      :title="itemForm.id ? '编辑菜单项' : '新增菜单项'"
      width="640px"
      destroy-on-close
    >
      <el-alert
        class="item-tip"
        type="info"
        :closable="false"
        show-icon
        title="按 kind 联动：链接 → url 必填；内容类型 / 标签 / 归档 → refCode 必填；分类 / 内容 → refId 必填；占位只做导航层级、不跳转。"
      />
      <el-form ref="itemFormRef" :model="itemForm" :rules="itemRules" label-width="100px">
        <el-form-item label="上级项">
          <el-select v-model="itemForm.parentId" placeholder="顶级" style="width: 100%">
            <el-option label="顶级（不缩进）" :value="0" />
            <el-option
              v-for="option in parentOptions"
              :key="option.id"
              :label="option.label"
              :value="option.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="名称" prop="label">
          <el-input v-model="itemForm.label" placeholder="导航上显示的文字；留空取所指向对象的名称" />
        </el-form-item>
        <el-form-item label="类型" prop="kind">
          <el-select v-model="itemForm.kind" style="width: 100%">
            <el-option
              v-for="option in KIND_OPTIONS"
              :key="option.value"
              :label="`${option.label}（${option.value}）`"
              :value="option.value"
            />
          </el-select>
          <div class="form-tip">{{ KIND_HINTS[itemForm.kind] }}</div>
        </el-form-item>

        <el-form-item v-if="KIND_FIELDS[itemForm.kind].url" label="链接地址" prop="url">
          <el-input v-model="itemForm.url" placeholder="如 /about/ 或 https://example.com" />
        </el-form-item>
        <el-form-item v-if="KIND_FIELDS[itemForm.kind].refCode" label="refCode" prop="refCode">
          <el-input
            v-model="itemForm.refCode"
            placeholder="如内容类型 article、标签 slug、归档 2026-03"
          />
        </el-form-item>
        <el-form-item v-if="KIND_FIELDS[itemForm.kind].refId" label="refId" prop="refId">
          <el-input-number
            :model-value="itemForm.refId"
            :min="1"
            controls-position="right"
            @update:model-value="(val: number | undefined) => (itemForm.refId = val ?? undefined)"
          />
          <div class="form-tip">{{ KIND_FIELDS[itemForm.kind].refIdHint }}</div>
        </el-form-item>

        <el-form-item label="target">
          <el-input v-model="itemForm.target" placeholder="留空 = 当前窗口；新窗口填 _blank" />
        </el-form-item>
        <el-form-item label="rel">
          <el-input v-model="itemForm.rel" placeholder="如 _blank 时填 noopener noreferrer" />
        </el-form-item>
        <el-form-item label="可见">
          <el-switch
            :model-value="itemForm.visible"
            :active-value="1"
            :inactive-value="0"
            active-text="显示"
            inactive-text="隐藏"
            @update:model-value="(val: boolean | string | number) => (itemForm.visible = Number(val))"
          />
          <span class="form-tip inline-tip">隐藏的菜单项不会渲染到页面上</span>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number
            :model-value="itemForm.sort"
            :min="0"
            controls-position="right"
            @update:model-value="(val: number | undefined) => (itemForm.sort = val ?? 0)"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingItem" @click="onSaveItem">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  createMenu,
  createMenuItem,
  deleteMenu,
  deleteMenuItem,
  listMenus,
  updateMenu,
  updateMenuItem,
  type MenuItemBody,
} from '@/api/cms'
import type { MenuItem, MenuItemKind, MenuItemNode, TableRow } from '@/types'

const loading = ref(false)
const menus = ref<MenuItem[]>([])
const currentMenuId = ref(0)

/** 后端把 Long 序列化成字符串，凡是要比较或下发的 id 一律过一遍 Number */
const currentMenu = computed(
  () => menus.value.find((menu) => Number(menu.id) === currentMenuId.value) || null,
)

async function load() {
  loading.value = true
  try {
    const list = await listMenus()
    menus.value = list
    // 选中的菜单可能刚被删掉，这时退回第一个，右侧不至于空着
    if (!list.some((menu) => Number(menu.id) === currentMenuId.value)) {
      currentMenuId.value = list.length ? Number(list[0].id) : 0
    }
  } catch {
    // 失败提示由请求拦截器统一给出，这里只保证 loading 被复位
  } finally {
    loading.value = false
  }
}

function selectMenu(row: TableRow) {
  currentMenuId.value = Number(row.id)
}

function menuRowClass({ row }: { row: TableRow }) {
  return Number(row.id) === currentMenuId.value ? 'menu-row-current' : ''
}

/* ---------------- kind 的中文名 / 联动规则 ---------------- */

const KIND_OPTIONS: Array<{ value: MenuItemKind; label: string }> = [
  { value: 'category', label: '分类' },
  { value: 'content', label: '内容' },
  { value: 'url', label: '链接' },
  { value: 'type', label: '内容类型' },
  { value: 'tag', label: '标签' },
  { value: 'archive', label: '归档' },
  { value: 'custom', label: '占位' },
  { value: 'author', label: '作者' },
]

const KIND_HINTS: Record<MenuItemKind, string> = {
  category: '分类：必填 refId（分类 id），链接由发布引擎按分类生成。',
  content: '内容：必填 refId（内容 id），链接由发布引擎按内容生成。',
  url: '链接：必填 url，写死的外部地址或站内路径。',
  type: '内容类型：必填 refCode，值就是内容类型的 code（如 article）。',
  tag: '标签：refCode 必填（后台校验用，写标签 slug）；发布时导航链接按 refId（标签 id）解析，两个都填才不会出现空链接。',
  archive: '归档：必填 refCode，写 2026-03（按月）或 2026（按年）。',
  custom: '占位：只做导航层级（如「关于我们」下面挂几项），本身不跳转；填了 url 就是固定路径。',
  author: '作者：静态站没有「当前作者」判定，填 refId 指向作者内容即可生成链接。',
}

/** 每种类型在表单里渲染哪些输入位 */
const KIND_FIELDS: Record<
  MenuItemKind,
  { url: boolean; refCode: boolean; refId: boolean; refIdHint: string }
> = {
  category: { url: false, refCode: false, refId: true, refIdHint: '分类管理里那条分类的 id' },
  content: { url: false, refCode: false, refId: true, refIdHint: '通用内容 / 文章里那条内容的 id' },
  author: {
    url: false,
    refCode: false,
    refId: true,
    refIdHint: '作者内容项（type_code = author）的 id',
  },
  type: { url: false, refCode: true, refId: false, refIdHint: '' },
  // 标签同时留两个：refCode 是后台校验要求的，refId 才是引擎解析链接用的
  tag: { url: false, refCode: true, refId: true, refIdHint: '标签管理里那条标签的 id' },
  archive: { url: false, refCode: true, refId: false, refIdHint: '' },
  url: { url: true, refCode: false, refId: false, refIdHint: '' },
  custom: { url: true, refCode: false, refId: false, refIdHint: '' },
}

/** 每种类型必填哪个字段（与后端 CmsMenuService 的校验一致） */
const KIND_REQUIRED: Partial<Record<MenuItemKind, 'url' | 'refCode' | 'refId'>> = {
  url: 'url',
  type: 'refCode',
  tag: 'refCode',
  archive: 'refCode',
  category: 'refId',
  content: 'refId',
}

function kindText(kind: string) {
  const option = KIND_OPTIONS.find((item) => item.value === kind)
  return option ? option.label : kind || '占位'
}

/** 「指向」列：按 kind 显示它真正指向的东西 */
function targetText(row: TableRow) {
  const kind = String(row.kind || 'custom')
  if (kind === 'url') return String(row.url || '（未填链接）')
  if (kind === 'custom') return row.url ? String(row.url) : '占位，不跳转'
  if (kind === 'type') return row.refCode ? String(row.refCode) : '（未填类型 code）'
  if (kind === 'archive') return row.refCode ? String(row.refCode) : '（未填归档，如 2026-03）'
  if (kind === 'tag') {
    const parts: string[] = []
    if (row.refCode) parts.push(`slug ${row.refCode}`)
    if (row.refId) parts.push(`id ${row.refId}`)
    return parts.length ? parts.join(' · ') : '（未填标签）'
  }
  return row.refId ? `id ${row.refId}` : '（未填 id）'
}

/* ---------------- 菜单容器 ---------------- */

const menuDialogVisible = ref(false)
const savingMenu = ref(false)
const menuFormRef = ref<FormInstance>()
const menuForm = reactive({ id: 0, code: '', name: '', status: 1, sort: 0 })

const menuRules: FormRules = {
  code: [
    { required: true, message: '请输入菜单标识', trigger: 'blur' },
    {
      pattern: /^[a-z0-9_-]{1,64}$/,
      message: '只能用小写字母、数字、下划线或短横线',
      trigger: 'blur',
    },
  ],
  name: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
}

function openMenuDialog(row?: TableRow) {
  menuForm.id = row ? Number(row.id) : 0
  menuForm.code = row ? String(row.code ?? '') : ''
  menuForm.name = row ? String(row.name ?? '') : ''
  menuForm.status = row ? Number(row.status ?? 1) : 1
  menuForm.sort = row ? Number(row.sort ?? 0) : 0
  menuDialogVisible.value = true
}

async function onSaveMenu() {
  const instance = menuFormRef.value
  if (!instance) return
  try {
    await instance.validate()
  } catch {
    // 校验失败是正常分支，不往事件处理器外抛
    return
  }
  savingMenu.value = true
  try {
    const body = {
      code: menuForm.code,
      name: menuForm.name,
      status: menuForm.status,
      sort: menuForm.sort,
    }
    if (menuForm.id) {
      await updateMenu(menuForm.id, body)
    } else {
      await createMenu(body)
    }
    ElMessage.success('保存成功')
    menuDialogVisible.value = false
    await load()
  } catch {
    // 失败提示由请求拦截器统一给出，这里保持弹窗打开便于重试
  } finally {
    savingMenu.value = false
  }
}

function onDeleteMenu(row: TableRow) {
  ElMessageBox.confirm(`确认删除菜单「${row.name}」吗？它下面的菜单项会一起删除。`, '提示', {
    type: 'warning',
  })
    .then(async () => {
      await deleteMenu(Number(row.id))
      ElMessage.success('删除成功')
      await load()
    })
    .catch((error) => {
      // 用户取消（'cancel'/'close'）无需处理，接口失败必须区分出来
      if (error !== 'cancel' && error !== 'close') {
        ElMessage.error('删除失败，请稍后重试')
      }
    })
}

/* ---------------- 菜单项 ---------------- */

const itemDialogVisible = ref(false)
const savingItem = ref(false)
const itemFormRef = ref<FormInstance>()
const itemForm = reactive({
  id: 0,
  parentId: 0,
  label: '',
  kind: 'category' as MenuItemKind,
  refId: undefined as number | undefined,
  refCode: '',
  url: '',
  target: '',
  rel: '',
  visible: 1,
  sort: 0,
})

const itemRules = computed<FormRules>(() => {
  const required = KIND_REQUIRED[itemForm.kind]
  return {
    kind: [{ required: true, message: '请选择菜单项类型', trigger: 'change' }],
    url:
      required === 'url'
        ? [{ required: true, message: '链接类型必须填 url', trigger: 'blur' }]
        : [],
    refCode:
      required === 'refCode'
        ? [{ required: true, message: '这类菜单项必须填 refCode', trigger: 'blur' }]
        : [],
    refId:
      required === 'refId'
        ? [
            {
              validator: (_rule, value: number | undefined, callback: (error?: Error) => void) => {
                if (typeof value === 'number' && value > 0) {
                  callback()
                } else {
                  callback(new Error('这类菜单项必须填对应的记录 id'))
                }
              },
              trigger: 'change',
            },
          ]
        : [],
  }
})

/** 上级项下拉：当前菜单的整棵树，排除自己（连带自己的子孙），免得把自己挂到自己下面 */
const parentOptions = computed(() => {
  if (!currentMenu.value) return []
  const list: Array<{ id: number; label: string }> = []
  const walk = (nodes: MenuItemNode[], depth: number) => {
    for (const node of nodes) {
      const id = Number(node.id)
      if (id === itemForm.id) continue
      list.push({ id, label: `${'　'.repeat(depth)}${node.label || kindText(node.kind)}` })
      if (node.children && node.children.length) {
        walk(node.children, depth + 1)
      }
    }
  }
  walk(currentMenu.value.items, 0)
  return list
})

function openItemDialog(row?: TableRow, parentId?: number) {
  if (row) {
    itemForm.id = Number(row.id)
    itemForm.parentId = Number(row.parentId ?? 0)
    itemForm.label = String(row.label ?? '')
    itemForm.kind = String(row.kind || 'custom') as MenuItemKind
    // Long 序列化成字符串，id 类字段统一转回 number，否则下拉和数字框的选中态对不上
    itemForm.refId =
      row.refId === null || row.refId === undefined || row.refId === ''
        ? undefined
        : Number(row.refId)
    itemForm.refCode = String(row.refCode ?? '')
    itemForm.url = String(row.url ?? '')
    itemForm.target = String(row.target ?? '')
    itemForm.rel = String(row.rel ?? '')
    itemForm.visible = Number(row.visible ?? 1)
    itemForm.sort = Number(row.sort ?? 0)
  } else {
    itemForm.id = 0
    itemForm.parentId = parentId ?? 0
    itemForm.label = ''
    itemForm.kind = 'category'
    itemForm.refId = undefined
    itemForm.refCode = ''
    itemForm.url = ''
    itemForm.target = ''
    itemForm.rel = ''
    itemForm.visible = 1
    itemForm.sort = 0
  }
  itemDialogVisible.value = true
}

/** 只提交当前 kind 用得到的字段：切类型后旧值不会留在库里当「看着有、其实没用」的数据 */
function buildItemBody(): MenuItemBody {
  const fields = KIND_FIELDS[itemForm.kind]
  return {
    parentId: itemForm.parentId,
    label: itemForm.label.trim() || null,
    kind: itemForm.kind,
    refId: fields.refId ? itemForm.refId ?? null : null,
    refCode: fields.refCode ? itemForm.refCode.trim() || null : null,
    url: fields.url ? itemForm.url.trim() || null : null,
    target: itemForm.target.trim() || null,
    rel: itemForm.rel.trim() || null,
    visible: itemForm.visible,
    sort: itemForm.sort,
  }
}

async function onSaveItem() {
  const instance = itemFormRef.value
  if (!instance || !currentMenu.value) return
  try {
    await instance.validate()
  } catch {
    // 校验失败是正常分支，不往事件处理器外抛
    return
  }
  savingItem.value = true
  try {
    const body = buildItemBody()
    if (itemForm.id) {
      await updateMenuItem(itemForm.id, body)
    } else {
      await createMenuItem(Number(currentMenu.value.id), body)
    }
    ElMessage.success('保存成功')
    itemDialogVisible.value = false
    await load()
  } catch {
    // 失败提示由请求拦截器统一给出，这里保持弹窗打开便于重试
  } finally {
    savingItem.value = false
  }
}

function onDeleteItem(row: TableRow) {
  ElMessageBox.confirm(
    `确认删除菜单项「${row.label || kindText(String(row.kind))}」吗？它的子项会一起删除。`,
    '提示',
    { type: 'warning' },
  )
    .then(async () => {
      await deleteMenuItem(Number(row.id))
      ElMessage.success('删除成功')
      await load()
    })
    .catch((error) => {
      // 用户取消（'cancel'/'close'）无需处理，接口失败必须区分出来
      if (error !== 'cancel' && error !== 'close') {
        ElMessage.error('删除失败，请稍后重试')
      }
    })
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

.inline-tip {
  margin-left: 8px;
}

.placeholder {
  color: #c0c4cc;
  font-size: 12px;
}

.menus-tip {
  margin-bottom: 16px;
}

.block-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.item-tip {
  margin-bottom: 12px;
}

:deep(.menu-row-current) {
  background-color: #f0f7ff;
}

:deep(.el-table__row) {
  cursor: pointer;
}
</style>
