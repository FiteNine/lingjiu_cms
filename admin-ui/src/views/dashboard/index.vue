<template>
  <div class="page-container">
    <el-row :gutter="16" class="stat-row">
      <el-col v-for="card in statCards" :key="card.label" :xs="12" :sm="8" :md="6" :lg="4">
        <el-card v-loading="loading" shadow="hover" class="stat-card">
          <div class="stat-value" :style="{ color: card.color }">{{ card.value }}</div>
          <div class="stat-label">{{ card.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <template #header>
        <span>最近内容</span>
      </template>
      <el-table :data="stats.recentContents" v-loading="loading" stripe>
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="authorName" label="作者" width="120" />
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button
              v-permission="'cms:content:edit'"
              link
              type="primary"
              @click="goEdit(row.id, row.typeCode)"
            >
              查看
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getStats } from '@/api/cms'
import type { ContentStatus, StatsData } from '@/types'

const router = useRouter()
const loading = ref(false)
const stats = ref<StatsData>({
  contentTotal: 0,
  contentPublished: 0,
  contentDraft: 0,
  categoryTotal: 0,
  tagTotal: 0,
  mediaTotal: 0,
  userTotal: 0,
  recentContents: [],
})

const statCards = computed(() => [
  { label: '内容总数', value: stats.value.contentTotal, color: '#409eff' },
  { label: '已发布', value: stats.value.contentPublished, color: '#67c23a' },
  { label: '草稿', value: stats.value.contentDraft, color: '#e6a23c' },
  { label: '分类', value: stats.value.categoryTotal, color: '#909399' },
  { label: '标签', value: stats.value.tagTotal, color: '#909399' },
  { label: '媒体', value: stats.value.mediaTotal, color: '#909399' },
  { label: '用户', value: stats.value.userTotal, color: '#909399' },
])

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

/** 内容类型决定编辑页的自定义字段与模板，一并带过去 */
function goEdit(id: number, typeCode: string) {
  router.push({ path: '/cms/contents/edit', query: { id: String(id), typeCode } })
}

onMounted(async () => {
  loading.value = true
  try {
    // 后端字段缺失/返回 null 时用初始值兜底，模板里的 statCards 与表格不能读到 undefined
    const data = await getStats()
    if (data && typeof data === 'object') {
      stats.value = { ...stats.value, ...data }
    }
  } catch (error) {
    // 错误提示由请求拦截器统一给出，这里避免 reject 逃逸成未处理的 Promise 拒绝
    console.error('加载统计数据失败', error)
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.stat-row {
  margin-bottom: 16px;
}

.stat-card {
  text-align: center;
  margin-bottom: 16px;
}

.stat-value {
  font-size: 28px;
  font-weight: 600;
  line-height: 1.4;
}

.stat-label {
  font-size: 13px;
  color: #909399;
}
</style>
