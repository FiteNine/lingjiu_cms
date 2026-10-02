<template>
  <div class="page-container">
    <el-card shadow="never">
      <el-form class="filter-bar" inline>
        <el-form-item label="关键词">
          <el-input
            v-model="keyword"
            placeholder="标签名称 / slug"
            clearable
            style="width: 200px"
            @keyup.enter="onSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="onSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button v-permission="'cms:tag:add'" type="primary" :icon="Plus" @click="openDialog()">
          新增标签
        </el-button>
      </div>

      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="标签名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="slug" label="slug" min-width="160" show-overflow-tooltip />
        <el-table-column prop="contentCount" label="内容数" width="100" />
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button v-permission="'cms:tag:edit'" link type="primary" @click="openDialog(row)">
              编辑
            </el-button>
            <el-button v-permission="'cms:tag:delete'" link type="danger" @click="onDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑标签' : '新增标签'"
      width="440px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="标签名称" prop="name">
          <el-input v-model="form.name" placeholder="标签名称" />
        </el-form-item>
        <el-form-item label="slug" prop="slug">
          <el-input v-model="form.slug" placeholder="URL slug" />
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
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { createTag, deleteTag, listTags, updateTag } from '@/api/cms'
import type { TagItem, TableRow } from '@/types'

const loading = ref(false)
const saving = ref(false)
const rows = ref<TagItem[]>([])
const keyword = ref('')

async function load() {
  loading.value = true
  try {
    rows.value = await listTags(keyword.value.trim() || undefined)
  } catch {
    // 失败提示由请求拦截器统一给出，这里只保证 loading 被复位
  } finally {
    loading.value = false
  }
}

function onSearch() {
  load()
}

function onReset() {
  keyword.value = ''
  load()
}

const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({ id: 0, name: '', slug: '' })

const rules: FormRules = {
  name: [{ required: true, message: '请输入标签名称', trigger: 'blur' }],
  slug: [{ required: true, message: '请输入 slug', trigger: 'blur' }],
}

function openDialog(row?: TableRow) {
  form.id = row ? Number(row.id) : 0
  form.name = row ? String(row.name) : ''
  form.slug = row ? String(row.slug) : ''
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
    const body = { name: form.name, slug: form.slug }
    if (form.id) {
      await updateTag(form.id, body)
    } else {
      await createTag(body)
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
  ElMessageBox.confirm(`确认删除标签「${row.name}」吗？`, '提示', { type: 'warning' })
    .then(async () => {
      await deleteTag(Number(row.id))
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
