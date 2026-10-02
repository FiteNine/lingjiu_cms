<template>
  <div class="page-container">
    <el-card shadow="never">
      <div class="table-toolbar">
        <el-button v-permission="'sys:menu:add'" type="primary" :icon="Plus" @click="openDialog()">
          新增菜单
        </el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="rows"
        row-key="id"
        default-expand-all
        :tree-props="{ children: 'children' }"
        stripe
      >
        <el-table-column prop="name" label="菜单名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.type)">{{ typeText(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="icon" label="图标" width="110" show-overflow-tooltip />
        <el-table-column prop="path" label="路由地址" min-width="140" show-overflow-tooltip />
        <el-table-column prop="component" label="组件" min-width="140" show-overflow-tooltip />
        <el-table-column prop="perms" label="权限标识" min-width="140" show-overflow-tooltip />
        <el-table-column prop="sort" label="排序" width="80" />
        <el-table-column label="可见" width="80">
          <template #default="{ row }">
            <el-tag :type="row.visible === 1 ? 'success' : 'info'" size="small">
              {{ row.visible === 1 ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.type !== 'BUTTON'"
              v-permission="'sys:menu:add'"
              link
              type="primary"
              @click="openDialog(row)"
            >
              新增子级
            </el-button>
            <el-button v-permission="'sys:menu:edit'" link type="primary" @click="openDialog(row, true)">
              编辑
            </el-button>
            <el-button v-permission="'sys:menu:delete'" link type="danger" @click="onDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑菜单' : '新增菜单'"
      width="560px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="上级菜单">
          <el-select v-model="form.parentId" placeholder="根节点" style="width: 100%">
            <el-option label="根节点" :value="0" />
            <el-option v-for="item in parentOptions" :key="item.id" :label="item.label" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="菜单类型" prop="type">
          <el-radio-group
            :model-value="form.type"
            @update:model-value="onTypeChange"
          >
            <el-radio value="DIR">目录</el-radio>
            <el-radio value="MENU">菜单</el-radio>
            <el-radio value="BUTTON">按钮</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="菜单名称" prop="name">
          <el-input v-model="form.name" placeholder="菜单名称" />
        </el-form-item>
        <el-form-item v-if="form.type !== 'BUTTON'" label="路由地址" prop="path">
          <el-input v-model="form.path" placeholder="如 /cms/contents" />
        </el-form-item>
        <el-form-item v-if="form.type === 'MENU'" label="组件路径">
          <el-input v-model="form.component" placeholder="如 views/cms/contents/index.vue" />
        </el-form-item>
        <el-form-item v-if="form.type !== 'BUTTON'" label="图标">
          <el-input v-model="form.icon" placeholder="Element Plus 图标名" />
        </el-form-item>
        <el-form-item v-if="form.type !== 'DIR'" label="权限标识">
          <el-input v-model="form.perms" placeholder="如 cms:content:list" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number
            :model-value="form.sort"
            :min="0"
            controls-position="right"
            @update:model-value="(val: number | undefined) => (form.sort = val ?? 0)"
          />
        </el-form-item>
        <el-form-item v-if="form.type !== 'BUTTON'" label="是否可见">
          <el-switch
            :model-value="form.visible"
            :active-value="1"
            :inactive-value="0"
            active-text="显示"
            inactive-text="隐藏"
            @update:model-value="(val: boolean | string | number) => (form.visible = Number(val))"
          />
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
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { createMenu, deleteMenu, menuTree, updateMenu, type MenuBody } from '@/api/system'
import type { MenuNode, MenuType, TableRow } from '@/types'

const loading = ref(false)
const saving = ref(false)
const rows = ref<MenuNode[]>([])

const typeMap: Record<string, string> = { DIR: '目录', MENU: '菜单', BUTTON: '按钮' }

function typeText(type: string) {
  return typeMap[type] || type
}

function typeTag(type: string) {
  if (type === 'DIR') return 'info'
  if (type === 'BUTTON') return 'warning'
  return 'primary'
}

async function load() {
  loading.value = true
  try {
    rows.value = await menuTree()
  } catch {
    // 失败提示由请求拦截器统一给出，这里只保证 loading 被复位
  } finally {
    loading.value = false
  }
}

/**
 * 把菜单树拍平成带缩进的下拉选项：
 * 按钮不可能有子节点，不作为可选上级；编辑时排除自身及整棵子树，避免形成环；
 * 递归带 visited 保护，后端数据万一有环也不会栈溢出。
 */
function flattenTree(
  nodes: MenuNode[],
  prefix = '',
  visited = new Set<number>(),
): { id: number; label: string }[] {
  const list: { id: number; label: string }[] = []
  for (const node of nodes) {
    if (node.type === 'BUTTON' || visited.has(node.id)) continue
    visited.add(node.id)
    list.push({ id: node.id, label: prefix + node.name })
    if (node.children && node.children.length > 0) {
      list.push(...flattenTree(node.children, prefix + '　', visited))
    }
  }
  return list
}

/** 编辑态排除自身及其整棵子树（环的检测靠 flattenTree 的 visited） */
function descendantIds(nodes: MenuNode[], rootId: number): Set<number> {
  if (!rootId) return new Set<number>()
  const stack = [...nodes]
  while (stack.length > 0) {
    const node = stack.pop()!
    if (node.id === rootId) {
      const ids = new Set<number>()
      const collect = (item: MenuNode) => {
        ids.add(item.id)
        for (const child of item.children ?? []) collect(child)
      }
      collect(node)
      return ids
    }
    if (node.children?.length) stack.push(...node.children)
  }
  return new Set<number>()
}

const parentOptions = computed(() => {
  const excluded = descendantIds(rows.value, form.id)
  return flattenTree(rows.value).filter((item) => !excluded.has(item.id))
})

/* ---------------- 新增 / 编辑 ---------------- */

const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  id: 0,
  parentId: 0,
  name: '',
  path: '',
  component: '',
  icon: '',
  perms: '',
  type: 'MENU' as MenuType,
  sort: 0,
  visible: 1,
  status: 1,
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择菜单类型', trigger: 'change' }],
  path: [{ required: true, message: '请输入路由地址', trigger: 'blur' }],
}

function onTypeChange(val: string | number | boolean | undefined) {
  form.type = val === 'DIR' ? 'DIR' : val === 'BUTTON' ? 'BUTTON' : 'MENU'
  // 按类型清干净不再适用的字段，避免按钮带着路由地址 / 目录带着组件路径提交
  if (form.type === 'BUTTON') {
    form.path = ''
    form.component = ''
    form.icon = ''
  } else if (form.type === 'DIR') {
    form.component = ''
    form.perms = ''
  }
}

function openDialog(row?: TableRow, isEdit = false) {
  form.id = isEdit && row ? Number(row.id) : 0
  form.parentId = row && !isEdit ? Number(row.id) : row ? Number(row.parentId) : 0
  form.name = isEdit && row ? String(row.name) : ''
  form.path = isEdit && row ? String(row.path ?? '') : ''
  form.component = isEdit && row ? String(row.component ?? '') : ''
  form.icon = isEdit && row ? String(row.icon ?? '') : ''
  form.perms = isEdit && row ? String(row.perms ?? '') : ''
  form.type = isEdit && row ? ((String(row.type) as MenuType) || 'MENU') : 'MENU'
  form.sort = isEdit && row ? Number(row.sort) : 0
  form.visible = isEdit && row ? Number(row.visible) : 1
  form.status = isEdit && row ? Number(row.status) : 1
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
    const body: MenuBody = {
      parentId: form.parentId,
      name: form.name,
      path: form.path,
      component: form.component || null,
      icon: form.icon || null,
      perms: form.perms || null,
      type: form.type,
      sort: form.sort,
      visible: form.visible,
      status: form.status,
    }
    if (form.id) {
      await updateMenu(form.id, body)
    } else {
      await createMenu(body)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
  } catch {
    // 失败提示由请求拦截器统一给出，这里保持弹窗打开便于重试
  } finally {
    saving.value = false
  }
}

function onDelete(row: TableRow) {
  ElMessageBox.confirm(`确认删除菜单「${row.name}」吗？`, '提示', { type: 'warning' })
    .then(async () => {
      await deleteMenu(Number(row.id))
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

onMounted(() => {
  load()
})
</script>
