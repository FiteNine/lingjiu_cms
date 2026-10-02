<template>
  <div class="page-container">
    <el-card shadow="never">
      <el-form class="filter-bar" inline>
        <el-form-item label="内容类型">
          <el-select
            v-model="query.typeCode"
            placeholder="请选择内容类型"
            style="width: 200px"
            @change="onSearch"
          >
            <el-option
              v-for="item in typeOptions"
              :key="item.code"
              :label="item.name"
              :value="item.code"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 140px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已发布" value="PUBLISHED" />
            <el-option label="已下线" value="OFFLINE" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input
            v-model="query.keyword"
            placeholder="标题"
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
        <el-button v-permission="'cms:content:add'" type="primary" :icon="Plus" @click="goCreate">
          新增内容
        </el-button>
      </div>

      <el-table v-loading="loading" :data="rows" stripe>
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
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <!-- 没有编辑权限时只是一个纯文本标题：v-permission 移除的是元素本身，
                 不能和 v-else 搭在同一个标签上 -->
            <el-link v-permission="'cms:content:edit'" type="primary" @click="goEdit(row)">
              {{ row.title }}
            </el-link>
            <span v-if="!canEdit">{{ row.title }}</span>
          </template>
        </el-table-column>
        <el-table-column label="内容类型" width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.typeName || row.typeCode }}</template>
        </el-table-column>
        <el-table-column label="分类" width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ categoryText(row) }}</template>
        </el-table-column>
        <el-table-column label="标签" width="180">
          <template #default="{ row }">
            <el-tag
              v-for="id in (row.tagIds as number[] | null) || []"
              :key="id"
              size="small"
              class="row-tag"
              effect="plain"
            >
              {{ tagNames[id] || id }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="slug" label="访问别名" width="160" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="置顶" width="80">
          <template #default="{ row }">
            <el-switch
              :model-value="row.top === true"
              :active-value="true"
              :inactive-value="false"
              @change="(val: boolean | string | number) => onFlagChange(row, 'top', val)"
            />
          </template>
        </el-table-column>
        <el-table-column label="推荐" width="80">
          <template #default="{ row }">
            <el-switch
              :model-value="row.recommend === true"
              :active-value="true"
              :inactive-value="false"
              @change="(val: boolean | string | number) => onFlagChange(row, 'recommend', val)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="publishTime" label="发布时间" width="170">
          <template #default="{ row }">{{ row.publishTime || '—' }}</template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" width="170">
          <template #default="{ row }">{{ row.updateTime || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'cms:content:edit'" link type="primary" @click="goEdit(row)">
              编辑
            </el-button>
            <el-button
              v-if="row.status !== 'PUBLISHED'"
              v-permission="'cms:content:publish'"
              link
              type="success"
              @click="onPublish(row)"
            >
              发布
            </el-button>
            <el-button
              v-else
              v-permission="'cms:content:publish'"
              link
              type="warning"
              @click="onOffline(row)"
            >
              下线
            </el-button>
            <el-button
              v-permission="'cms:content:delete'"
              link
              type="danger"
              @click="onDelete(row)"
            >
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
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import {
  categoryTree,
  contentTypeOptions,
  deleteContent,
  getContent,
  listContents,
  listTags,
  updateContent,
  updateContentStatus,
} from '@/api/cms'
import type { CategoryNode, ContentRow, ContentStatus, TableRow } from '@/types'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
/** 没有编辑权限时标题退化成纯文本，否则点进去只会看到一个 403 */
const canEdit = computed(() => userStore.hasPerm('cms:content:edit'))
const loading = ref(false)
const rows = ref<ContentRow[]>([])
const total = ref(0)
const typeOptions = ref<Array<{ code: string; name: string }>>([])
/** 列表行只带分类 / 标签的 id，名字在这里一次性查好（两棵树都不大） */
const categoryNames = ref<Record<number, string>>({})
const tagNames = ref<Record<number, string>>({})

const query = reactive({
  page: 1,
  size: 20,
  typeCode: '',
  status: '' as ContentStatus | '',
  keyword: '',
})

const statusMap: Record<ContentStatus, string> = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  OFFLINE: '已下线',
}

function statusText(status: ContentStatus) {
  return statusMap[status] || status
}

function statusType(status: ContentStatus) {
  if (status === 'PUBLISHED') return 'success'
  if (status === 'OFFLINE') return 'info'
  return 'warning'
}

async function load() {
  loading.value = true
  try {
    const data = await listContents({ ...query })
    rows.value = data.records
    total.value = data.total
  } catch {
    // 失败提示由请求拦截器统一给出，这里保证加载流程不向外抛异常
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
  // 类型下拉是筛选条件之一，重置要连它一起复位
  query.typeCode = typeOptions.value[0]?.code ?? ''
  query.status = ''
  query.keyword = ''
  onSearch()
}

function goCreate() {
  if (!query.typeCode) {
    ElMessage.warning('请先选择内容类型')
    return
  }
  router.push({ path: '/cms/contents/edit', query: { typeCode: query.typeCode } })
}

function goEdit(row: TableRow) {
  router.push({
    path: '/cms/contents/edit',
    query: { id: String(row.id), typeCode: String(row.typeCode) },
  })
}

function categoryText(row: TableRow) {
  const ids = (row.categoryIds as number[] | null) || []
  return ids.map((id) => categoryNames.value[id] || id).join(' / ') || '—'
}

/**
 * 置顶 / 推荐没有单独的开关接口（契约里只有整篇更新），按整篇提交——
 * 这与文章列表当初处理"推荐"开关的做法一致。
 */
async function onFlagChange(row: TableRow, field: 'top' | 'recommend', val: boolean | string | number) {
  const next = val === true
  const id = Number(row.id)
  try {
    const detail = await getContent(id)
    await updateContent(id, {
      typeCode: detail.typeCode,
      parentId: detail.parentId,
      slug: detail.slug,
      title: detail.title,
      summary: detail.summary,
      cover: detail.cover,
      status: detail.status,
      sort: detail.sort,
      top: field === 'top' ? next : detail.top === true,
      recommend: field === 'recommend' ? next : detail.recommend === true,
      publishTime: detail.publishTime,
      data: detail.data,
      content: detail.content,
      contentFormat: detail.contentFormat,
      seoTitle: detail.seoTitle,
      seoDescription: detail.seoDescription,
      seoKeywords: detail.seoKeywords,
      categoryIds: detail.categoryIds,
      tagIds: detail.tagIds,
    })
    row[field] = next
    ElMessage.success(next ? (field === 'top' ? '已置顶' : '已设为推荐') : '已取消')
  } catch {
    // 开关是受控的（model-value 绑在 row 上），提交失败时它自己会弹回原状；
    // 错误提示由请求拦截器统一给出
  }
}

async function onPublish(row: TableRow) {
  try {
    await updateContentStatus(Number(row.id), 'PUBLISHED')
    ElMessage.success('发布成功')
    load()
  } catch {
    // 失败提示由请求拦截器统一给出
  }
}

async function onOffline(row: TableRow) {
  try {
    await updateContentStatus(Number(row.id), 'OFFLINE')
    ElMessage.success('已下线')
    load()
  } catch {
    // 失败提示由请求拦截器统一给出
  }
}

function onDelete(row: TableRow) {
  ElMessageBox.confirm(`确认删除内容「${row.title}」吗？`, '提示', { type: 'warning' })
    .then(async () => {
      await deleteContent(Number(row.id))
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

onMounted(async () => {
  try {
    const [types, tree, tags] = await Promise.all([contentTypeOptions(), categoryTree(), listTags()])
    typeOptions.value = types
    const names: Record<number, string> = {}
    const walk = (nodes: CategoryNode[]) => {
      for (const node of nodes) {
        names[node.id] = node.name
        if (node.children?.length) walk(node.children)
      }
    }
    walk(tree)
    categoryNames.value = names
    tagNames.value = Object.fromEntries(tags.map((tag) => [tag.id, tag.name]))
    // 默认选中第一个类型：这个下拉基本不变，让列表一进来就有数据，不必先手选一次
    if (typeOptions.value.length > 0) {
      query.typeCode = typeOptions.value[0].code
    }
  } catch {
    // 分类 / 标签只影响列表里的名字展示，失败了也要继续把列表拉出来
  }
  await load()
})
</script>
