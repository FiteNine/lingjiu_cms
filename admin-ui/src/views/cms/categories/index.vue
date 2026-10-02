<template>
  <div class="page-container">
    <el-card shadow="never">
      <div class="table-toolbar">
        <el-button
          v-permission="'cms:category:add'"
          type="primary"
          :icon="Plus"
          @click="openDialog()"
        >
          新增分类
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
        <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="slug" label="slug" min-width="120" show-overflow-tooltip />
        <el-table-column prop="description" label="描述" min-width="160" show-overflow-tooltip />
        <el-table-column label="封面" width="90">
          <template #default="{ row }">
            <el-image
              v-if="row.cover"
              :src="row.cover"
              fit="cover"
              style="width: 60px; height: 40px; border-radius: 4px"
              :preview-src-list="[row.cover]"
              preview-teleported
            />
            <span v-else class="no-cover">无</span>
          </template>
        </el-table-column>
        <el-table-column prop="contentCount" label="内容数" width="90" />
        <el-table-column prop="sort" label="排序" width="80" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button
              v-permission="'cms:category:add'"
              link
              type="primary"
              @click="openDialog(row)"
            >
              新增子级
            </el-button>
            <el-button
              v-permission="'cms:category:edit'"
              link
              type="primary"
              @click="openDialog(row, true)"
            >
              编辑
            </el-button>
            <el-button
              v-permission="'cms:category:delete'"
              link
              type="danger"
              @click="onDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑分类' : '新增分类'"
      width="520px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="上级分类">
          <el-select v-model="form.parentId" placeholder="顶级分类" style="width: 100%">
            <el-option label="顶级分类" :value="0" />
            <el-option
              v-for="item in parentOptions"
              :key="item.id"
              :label="item.label"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="分类名称" />
        </el-form-item>
        <el-form-item label="slug" prop="slug">
          <el-input v-model="form.slug" placeholder="URL slug" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="描述" />
        </el-form-item>
        <el-form-item label="封面">
          <el-input v-model="form.cover" placeholder="图片 URL" />
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
import {
  categoryTree,
  createCategory,
  deleteCategory,
  updateCategory,
  type CategoryBody,
} from '@/api/cms'
import type { CategoryNode, TableRow } from '@/types'

const loading = ref(false)
const saving = ref(false)
const rows = ref<CategoryNode[]>([])

async function load() {
  loading.value = true
  try {
    rows.value = await categoryTree()
  } catch {
    // 失败提示由请求拦截器统一给出，这里只保证 loading 被复位
  } finally {
    loading.value = false
  }
}

/** 把分类树拍平成带缩进的下拉选项 */
function flattenTree(nodes: CategoryNode[], prefix = ''): { id: number; label: string }[] {
  const list: { id: number; label: string }[] = []
  for (const node of nodes) {
    list.push({ id: node.id, label: prefix + node.name })
    if (node.children && node.children.length > 0) {
      list.push(...flattenTree(node.children, prefix + '　'))
    }
  }
  return list
}

/** 编辑态要排除自身及其整棵子树，否则能把父级选成自己或后代，形成环 */
function subtreeIds(node: CategoryNode): number[] {
  const ids = [node.id]
  for (const child of node.children ?? []) {
    ids.push(...subtreeIds(child))
  }
  return ids
}

function descendantIds(nodes: CategoryNode[], rootId: number): Set<number> {
  if (!rootId) return new Set<number>()
  const stack = [...nodes]
  while (stack.length > 0) {
    const node = stack.pop()!
    if (node.id === rootId) return new Set(subtreeIds(node))
    if (node.children?.length) stack.push(...node.children)
  }
  return new Set<number>()
}

const parentOptions = computed(() => {
  const excluded = descendantIds(rows.value, form.id)
  return flattenTree(rows.value).filter((item) => !excluded.has(item.id))
})

const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  id: 0,
  parentId: 0,
  name: '',
  slug: '',
  description: '',
  cover: '',
  sort: 0,
  status: 1,
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入分类名称', trigger: 'blur' }],
  slug: [{ required: true, message: '请输入 slug', trigger: 'blur' }],
}

function openDialog(row?: TableRow, isEdit = false) {
  form.id = isEdit && row ? Number(row.id) : 0
  if (!row) {
    form.parentId = 0 // 新增顶级分类
  } else if (isEdit) {
    form.parentId = Number(row.parentId) // 编辑：保持原父级
  } else {
    form.parentId = Number(row.id) // 新增子级：把当前行当父级
  }
  form.name = isEdit && row ? String(row.name) : ''
  form.slug = isEdit && row ? String(row.slug) : ''
  form.description = isEdit && row ? String(row.description ?? '') : ''
  form.cover = isEdit && row ? String(row.cover ?? '') : ''
  form.sort = isEdit && row ? Number(row.sort) : 0
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
    const body: CategoryBody = {
      parentId: form.parentId,
      name: form.name,
      slug: form.slug,
      description: form.description,
      cover: form.cover,
      sort: form.sort,
      status: form.status,
    }
    if (form.id) {
      await updateCategory(form.id, body)
    } else {
      await createCategory(body)
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
  ElMessageBox.confirm(`确认删除分类「${row.name}」吗？`, '提示', { type: 'warning' })
    .then(async () => {
      await deleteCategory(Number(row.id))
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

<style scoped>
.no-cover {
  color: #c0c4cc;
  font-size: 12px;
}
</style>
