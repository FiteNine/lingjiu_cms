<template>
  <div class="page-container">
    <el-row :gutter="16">
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>字典类型</span>
              <el-button v-permission="'sys:dict:add'" type="primary" size="small" :icon="Plus" @click="openTypeDialog()">
                新增
              </el-button>
            </div>
          </template>
          <el-input
            v-model="typeKeyword"
            class="type-search"
            placeholder="搜索 code / 名称"
            clearable
            :prefix-icon="Search"
          />
          <div v-loading="typeLoading" class="type-list">
            <div
              v-for="item in filteredTypes"
              :key="item.id"
              class="type-item"
              :class="{ active: currentType?.id === item.id }"
              @click="selectType(item)"
            >
              <div class="type-main">
                <div class="type-code">{{ item.code }}</div>
                <div class="type-name">{{ item.name }}</div>
              </div>
              <div class="type-actions" @click.stop>
                <el-button
                  v-permission="'sys:dict:edit'"
                  link
                  type="primary"
                  size="small"
                  @click="openTypeDialog(item)"
                >
                  编辑
                </el-button>
                <el-button
                  v-permission="'sys:dict:delete'"
                  link
                  type="danger"
                  size="small"
                  @click="onDeleteType(item)"
                >
                  删除
                </el-button>
              </div>
            </div>
            <el-empty v-if="!typeLoading && filteredTypes.length === 0" description="暂无字典类型" />
          </div>
        </el-card>
      </el-col>

      <el-col :span="16">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>字典项{{ currentType ? ` - ${currentType.name}（${currentType.code}）` : '' }}</span>
              <el-button
                v-permission="'sys:dict:add'"
                type="primary"
                size="small"
                :icon="Plus"
                :disabled="!currentType"
                @click="openItemDialog()"
              >
                新增字典项
              </el-button>
            </div>
          </template>
          <el-table v-if="currentType" v-loading="itemLoading" :data="items" stripe>
            <el-table-column prop="label" label="显示名称" min-width="140" show-overflow-tooltip />
            <el-table-column prop="value" label="字典值" min-width="120" show-overflow-tooltip />
            <el-table-column prop="sort" label="排序" width="80" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'">
                  {{ row.status === 1 ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150">
              <template #default="{ row }">
                <el-button v-permission="'sys:dict:edit'" link type="primary" @click="openItemDialog(row)">
                  编辑
                </el-button>
                <el-button v-permission="'sys:dict:delete'" link type="danger" @click="onDeleteItem(row)">
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="请先选择左侧字典类型" />
        </el-card>
      </el-col>
    </el-row>

    <el-dialog
      v-model="typeDialogVisible"
      :title="typeForm.id ? '编辑字典类型' : '新增字典类型'"
      width="460px"
      destroy-on-close
    >
      <el-form ref="typeFormRef" :model="typeForm" :rules="typeRules" label-width="90px">
        <el-form-item label="字典编码" prop="code">
          <el-input v-model="typeForm.code" placeholder="如 content_status" />
        </el-form-item>
        <el-form-item label="字典名称" prop="name">
          <el-input v-model="typeForm.name" placeholder="字典名称" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="typeForm.remark" type="textarea" :rows="2" placeholder="备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSaveType">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="itemDialogVisible"
      :title="itemForm.id ? '编辑字典项' : '新增字典项'"
      width="460px"
      destroy-on-close
    >
      <el-form ref="itemFormRef" :model="itemForm" :rules="itemRules" label-width="90px">
        <el-form-item label="显示名称" prop="label">
          <el-input v-model="itemForm.label" placeholder="显示名称" />
        </el-form-item>
        <el-form-item label="字典值" prop="value">
          <el-input v-model="itemForm.value" placeholder="字典值" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number
            :model-value="itemForm.sort"
            :min="0"
            controls-position="right"
            @update:model-value="(val: number | undefined) => (itemForm.sort = val ?? 0)"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            :model-value="itemForm.status"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="停用"
            @update:model-value="(val: boolean | string | number) => (itemForm.status = Number(val))"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSaveItem">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import {
  createDictItem,
  createDictType,
  deleteDictItem,
  deleteDictType,
  listDictItems,
  listDictTypes,
  updateDictItem,
  updateDictType,
  type DictItemBody,
  type DictTypeBody,
} from '@/api/system'
import type { DictItem, DictType, TableRow } from '@/types'

const typeLoading = ref(false)
const itemLoading = ref(false)
const saving = ref(false)
const types = ref<DictType[]>([])
const items = ref<DictItem[]>([])
const typeKeyword = ref('')
const currentType = ref<DictType | null>(null)

const filteredTypes = computed(() => {
  const keyword = typeKeyword.value.trim().toLowerCase()
  if (!keyword) return types.value
  return types.value.filter(
    (item) =>
      item.code.toLowerCase().includes(keyword) || item.name.toLowerCase().includes(keyword),
  )
})

async function loadTypes() {
  typeLoading.value = true
  try {
    // 左侧是"字典类型"选择列表而非分页表格，一次取 100 条；字典类型是系统级小字典，超过这个量级属于异常
    const data = await listDictTypes({ page: 1, size: 100 })
    types.value = data.records
  } catch {
    // 失败提示由请求拦截器统一给出，这里只保证 loading 被复位
  } finally {
    typeLoading.value = false
  }
}

async function loadItems() {
  if (!currentType.value) return
  // 记下本次请求对应的 code：快速切换类型时，过期响应不能覆盖当前选中类型的字典项
  const code = currentType.value.code
  itemLoading.value = true
  try {
    const data = await listDictItems(code)
    if (currentType.value?.code === code) {
      items.value = data
    }
  } catch {
    // 失败提示由请求拦截器统一给出，这里只保证 loading 被复位
  } finally {
    itemLoading.value = false
  }
}

function selectType(item: DictType) {
  currentType.value = item
  loadItems()
}

/* ---------------- 字典类型 ---------------- */

const typeDialogVisible = ref(false)
const typeFormRef = ref<FormInstance>()
const typeForm = reactive({ id: 0, code: '', name: '', remark: '' })
const typeRules: FormRules = {
  code: [{ required: true, message: '请输入字典编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入字典名称', trigger: 'blur' }],
}

function openTypeDialog(row?: TableRow) {
  typeForm.id = row ? Number(row.id) : 0
  typeForm.code = row ? String(row.code) : ''
  typeForm.name = row ? String(row.name) : ''
  typeForm.remark = row ? String(row.remark ?? '') : ''
  typeDialogVisible.value = true
}

async function onSaveType() {
  const instance = typeFormRef.value
  if (!instance) return
  try {
    await instance.validate()
  } catch {
    // 校验失败是正常分支，不往事件处理器外抛
    return
  }
  saving.value = true
  try {
    const body: DictTypeBody = {
      code: typeForm.code,
      name: typeForm.name,
      remark: typeForm.remark,
    }
    if (typeForm.id) {
      await updateDictType(typeForm.id, body)
    } else {
      await createDictType(body)
    }
    ElMessage.success('保存成功')
    typeDialogVisible.value = false
    await loadTypes()
    if (currentType.value && currentType.value.id === typeForm.id) {
      const codeChanged = currentType.value.code !== typeForm.code
      currentType.value = types.value.find((item) => item.id === typeForm.id) || currentType.value
      // code 变了，右侧列表与表头就对不上了，按新 code 重新拉一次
      if (codeChanged) loadItems()
    }
  } catch {
    // 失败提示由请求拦截器统一给出，这里保持弹窗打开便于重试
  } finally {
    saving.value = false
  }
}

function onDeleteType(row: TableRow) {
  ElMessageBox.confirm(`确认删除字典类型「${row.name}」吗？`, '提示', { type: 'warning' })
    .then(async () => {
      await deleteDictType(Number(row.id))
      ElMessage.success('删除成功')
      if (currentType.value?.id === Number(row.id)) {
        currentType.value = null
        items.value = []
      }
      loadTypes()
    })
    .catch((error) => {
      // 用户取消（'cancel'/'close'）无需处理，接口失败必须区分出来
      if (error !== 'cancel' && error !== 'close') {
        ElMessage.error('删除失败，请稍后重试')
      }
    })
}

/* ---------------- 字典项 ---------------- */

const itemDialogVisible = ref(false)
const itemFormRef = ref<FormInstance>()
const itemForm = reactive({ id: 0, label: '', value: '', sort: 0, status: 1 })
const itemRules: FormRules = {
  label: [{ required: true, message: '请输入显示名称', trigger: 'blur' }],
  value: [{ required: true, message: '请输入字典值', trigger: 'blur' }],
}

function openItemDialog(row?: TableRow) {
  itemForm.id = row ? Number(row.id) : 0
  itemForm.label = row ? String(row.label) : ''
  itemForm.value = row ? String(row.value) : ''
  itemForm.sort = row ? Number(row.sort) : 0
  itemForm.status = row ? Number(row.status) : 1
  itemDialogVisible.value = true
}

async function onSaveItem() {
  const instance = itemFormRef.value
  if (!instance || !currentType.value) return
  try {
    await instance.validate()
  } catch {
    // 校验失败是正常分支，不往事件处理器外抛
    return
  }
  saving.value = true
  try {
    const body: DictItemBody = {
      typeId: currentType.value.id,
      label: itemForm.label,
      value: itemForm.value,
      sort: itemForm.sort,
      status: itemForm.status,
    }
    if (itemForm.id) {
      await updateDictItem(itemForm.id, body)
    } else {
      await createDictItem(body)
    }
    ElMessage.success('保存成功')
    itemDialogVisible.value = false
    loadItems()
  } catch {
    // 失败提示由请求拦截器统一给出，这里保持弹窗打开便于重试
  } finally {
    saving.value = false
  }
}

function onDeleteItem(row: TableRow) {
  ElMessageBox.confirm(`确认删除字典项「${row.label}」吗？`, '提示', { type: 'warning' })
    .then(async () => {
      await deleteDictItem(Number(row.id))
      ElMessage.success('删除成功')
      loadItems()
    })
    .catch((error) => {
      // 用户取消（'cancel'/'close'）无需处理，接口失败必须区分出来
      if (error !== 'cancel' && error !== 'close') {
        ElMessage.error('删除失败，请稍后重试')
      }
    })
}

onMounted(() => {
  loadTypes()
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.type-search {
  margin-bottom: 12px;
}

.type-list {
  max-height: 520px;
  overflow: auto;
}

.type-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  border-radius: 4px;
  cursor: pointer;
}

.type-item:hover {
  background-color: #f5f7fa;
}

.type-item.active {
  background-color: #ecf5ff;
}

.type-code {
  font-size: 13px;
  color: #303133;
  font-weight: 600;
}

.type-name {
  font-size: 12px;
  color: #909399;
}
</style>
