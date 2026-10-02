<template>
  <div class="page-container">
    <el-card shadow="never">
      <el-form class="filter-bar" inline>
        <el-form-item label="角色名称">
          <el-input
            v-model="query.name"
            placeholder="角色名称"
            clearable
            style="width: 180px"
            @keyup.enter="onSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="onSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button v-permission="'sys:role:add'" type="primary" :icon="Plus" @click="openDialog()">
          新增角色
        </el-button>
      </div>

      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="code" label="角色编码" min-width="120" show-overflow-tooltip />
        <el-table-column prop="name" label="角色名称" min-width="120" show-overflow-tooltip />
        <el-table-column prop="sort" label="排序" width="80" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'sys:role:edit'" link type="primary" @click="openDialog(row)">
              编辑
            </el-button>
            <el-button
              v-permission="'sys:role:assign'"
              link
              type="primary"
              @click="openAssignDialog(row)"
            >
              分配权限
            </el-button>
            <el-button v-permission="'sys:role:delete'" link type="danger" @click="onDelete(row)">
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

    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑角色' : '新增角色'"
      width="480px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="角色编码" prop="code">
          <el-input v-model="form.code" placeholder="如 ROLE_EDITOR" />
        </el-form-item>
        <el-form-item label="角色名称" prop="name">
          <el-input v-model="form.name" placeholder="角色名称" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number
            :model-value="form.sort"
            :min="0"
            controls-position="right"
            @update:model-value="(val: number | undefined) => (form.sort = val ?? 0)"
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
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="assignVisible" :title="`分配权限 - ${assignRoleName}`" width="420px">
      <el-tree
        ref="treeRef"
        v-loading="assignLoading"
        :data="menuTreeData"
        node-key="id"
        show-checkbox
        check-strictly
        default-expand-all
        :props="{ label: 'name', children: 'children' }"
      />
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="assignLoading" @click="onSaveAssign">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from 'vue'
import {
  ElMessage,
  ElMessageBox,
  type FormInstance,
  type FormRules,
  type TreeInstance,
} from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import {
  createRole,
  deleteRole,
  getRoleMenus,
  listRoles,
  menuTree,
  updateRole,
  updateRoleMenus,
  type RoleBody,
} from '@/api/system'
import type { MenuNode, RoleRow, TableRow } from '@/types'

const loading = ref(false)
const saving = ref(false)
const rows = ref<RoleRow[]>([])
const total = ref(0)

const query = reactive({ page: 1, size: 20, name: '' })

async function load() {
  loading.value = true
  try {
    const data = await listRoles({ ...query })
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
  query.name = ''
  onSearch()
}

/* ---------------- 新增 / 编辑 ---------------- */

const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({ id: 0, code: '', name: '', sort: 0, status: 1, remark: '' })

const rules: FormRules = {
  code: [{ required: true, message: '请输入角色编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
}

function openDialog(row?: TableRow) {
  form.id = row ? Number(row.id) : 0
  form.code = row ? String(row.code) : ''
  form.name = row ? String(row.name) : ''
  form.sort = row ? Number(row.sort) : 0
  form.status = row ? Number(row.status) : 1
  form.remark = row ? String(row.remark ?? '') : ''
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
    const body: RoleBody = {
      code: form.code,
      name: form.name,
      sort: form.sort,
      status: form.status,
      remark: form.remark,
    }
    if (form.id) {
      await updateRole(form.id, body)
    } else {
      await createRole(body)
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

/* ---------------- 分配权限 ---------------- */

const assignVisible = ref(false)
const assignLoading = ref(false)
const assignRoleId = ref(0)
const assignRoleName = ref('')
const menuTreeData = ref<MenuNode[]>([])
const treeRef = ref<TreeInstance>()

/** 分配弹窗的请求序号：快速切换角色时只接受最后一次响应，避免勾选被旧响应覆盖 */
let assignSeq = 0

async function openAssignDialog(row: TableRow) {
  const roleId = Number(row.id)
  assignRoleId.value = roleId
  assignRoleName.value = String(row.name)
  assignVisible.value = true
  assignLoading.value = true
  treeRef.value?.setCheckedKeys([])
  const seq = ++assignSeq
  try {
    if (menuTreeData.value.length === 0) {
      menuTreeData.value = await menuTree()
    }
    const data = await getRoleMenus(roleId)
    if (seq !== assignSeq) return
    await nextTick()
    if (seq !== assignSeq) return
    treeRef.value?.setCheckedKeys(data.menuIds || [])
  } catch {
    // 失败提示由请求拦截器统一给出，加载失败时保存按钮仍被 assignLoading 挡住
  } finally {
    if (seq === assignSeq) {
      assignLoading.value = false
    }
  }
}

/** 树节点 id → 父节点 id：check-strictly 下勾子节点不会带上父级，提交前要补齐 */
function parentMap(nodes: MenuNode[], parentId: number | null, map: Map<number, number | null>) {
  for (const node of nodes) {
    map.set(node.id, parentId)
    if (node.children?.length) parentMap(node.children, node.id, map)
  }
  return map
}

async function onSaveAssign() {
  const tree = treeRef.value
  if (!tree) return
  const parents = parentMap(menuTreeData.value, null, new Map<number, number | null>())
  const menuIds = new Set<number>(tree.getCheckedKeys().map(Number))
  // 补齐父级链：只勾按钮权限时父级菜单不会进 checked，会导致侧边栏缺入口
  for (const id of [...menuIds]) {
    let parent = parents.get(id)
    while (parent != null && !menuIds.has(parent)) {
      menuIds.add(parent)
      parent = parents.get(parent)
    }
  }
  saving.value = true
  try {
    await updateRoleMenus(assignRoleId.value, [...menuIds])
    ElMessage.success('权限已更新')
    assignVisible.value = false
  } catch {
    // 失败提示由请求拦截器统一给出，这里保持弹窗打开便于重试
  } finally {
    saving.value = false
  }
}

/* ---------------- 删除 ---------------- */

function onDelete(row: TableRow) {
  ElMessageBox.confirm(`确认删除角色「${row.name}」吗？`, '提示', { type: 'warning' })
    .then(async () => {
      await deleteRole(Number(row.id))
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
