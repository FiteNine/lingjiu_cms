<template>
  <div class="page-container">
    <el-card shadow="never">
      <div class="table-toolbar">
        <el-upload
          :show-file-list="false"
          :http-request="doUpload"
          :before-upload="beforeUpload"
          accept=".jpg,.jpeg,.png,.gif,.webp,.svg,.ico,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.md,.zip"
        >
          <el-button
            v-permission="'cms:media:upload'"
            type="primary"
            :icon="Upload"
            :loading="uploading"
            :disabled="uploading"
          >
            上传文件
          </el-button>
        </el-upload>
        <el-form inline @submit.prevent>
          <el-form-item>
            <el-input
              v-model="query.keyword"
              placeholder="文件名"
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
      </div>

      <div v-loading="loading" class="media-grid">
        <el-row :gutter="12">
          <el-col
            v-for="item in rows"
            :key="item.id"
            :xs="12"
            :sm="8"
            :md="6"
            :lg="4"
            class="media-col"
          >
            <el-card shadow="hover" class="media-card">
              <el-image :src="item.url" fit="cover" class="media-thumb" :preview-src-list="[item.url]" preview-teleported>
                <template #error>
                  <div class="media-thumb-error">
                    <el-icon :size="28"><Document /></el-icon>
                  </div>
                </template>
              </el-image>
              <div class="media-name" :title="item.name">{{ item.name }}</div>
              <div class="media-meta">
                {{ formatSize(item.size) }} · {{ item.ext || item.mimeType }}
              </div>
              <div class="media-time">{{ item.createTime }}</div>
              <div class="media-actions">
                <el-button link type="primary" size="small" @click="copyUrl(item)">复制URL</el-button>
                <el-button
                  v-permission="'cms:media:delete'"
                  link
                  type="danger"
                  size="small"
                  @click="onDelete(item)"
                >
                  删除
                </el-button>
              </div>
            </el-card>
          </el-col>
        </el-row>
        <el-empty v-if="!loading && rows.length === 0" description="媒体库暂无文件" />
      </div>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[12, 24, 48, 96]"
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
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type UploadRequestOptions } from 'element-plus'
import { Document, Refresh, Search, Upload } from '@element-plus/icons-vue'
import { deleteMedia, listMedia, uploadMedia } from '@/api/cms'
import type { MediaItem } from '@/types'

const loading = ref(false)
const uploading = ref(false)
const rows = ref<MediaItem[]>([])
const total = ref(0)

const query = reactive({ page: 1, size: 24, keyword: '' })

/** 与后端 MediaService.ALLOWED_EXT 一致；后端 spring multipart 单文件上限 10MB */
const ALLOWED_EXT = [
  'jpg', 'jpeg', 'png', 'gif', 'webp', 'svg', 'ico',
  'pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'txt', 'md', 'zip',
]
const MAX_UPLOAD_SIZE = 10 * 1024 * 1024

async function load() {
  loading.value = true
  try {
    const data = await listMedia({ ...query })
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
  query.keyword = ''
  onSearch()
}

/** 只做前端提示：accept 可被绕过，真正的类型/大小拦截在后端 */
function beforeUpload(file: File) {
  const ext = file.name.includes('.') ? file.name.split('.').pop()!.toLowerCase() : ''
  if (!ALLOWED_EXT.includes(ext)) {
    ElMessage.error(`不支持的文件类型：${ext || '未知'}，请上传 ${ALLOWED_EXT.join(' / ')}`)
    return false
  }
  if (file.size > MAX_UPLOAD_SIZE) {
    ElMessage.error('文件大小不能超过 10MB')
    return false
  }
  return true
}

async function doUpload(options: UploadRequestOptions) {
  uploading.value = true
  try {
    await uploadMedia(options.file as File)
    ElMessage.success('上传成功')
    load()
  } catch {
    // 错误提示由请求拦截器统一处理
  } finally {
    uploading.value = false
  }
}

function formatSize(size: number) {
  if (!size) return '0 B'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(2)} MB`
}

async function copyUrl(item: MediaItem) {
  try {
    await navigator.clipboard.writeText(item.url)
    ElMessage.success('URL 已复制')
  } catch {
    ElMessage.error('复制失败，请手动复制：' + item.url)
  }
}

function onDelete(item: MediaItem) {
  ElMessageBox.confirm(`确认删除文件「${item.name}」吗？`, '提示', { type: 'warning' })
    .then(async () => {
      await deleteMedia(item.id)
      ElMessage.success('删除成功')
      // 删的是当前页最后一条时回退一页，否则会停在一个越界的空白页
      if (rows.value.length === 1 && query.page > 1) {
        query.page -= 1
      }
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
.media-grid {
  min-height: 200px;
}

.media-col {
  margin-bottom: 12px;
}

.media-card :deep(.el-card__body) {
  padding: 10px;
}

.media-thumb {
  width: 100%;
  height: 110px;
  border-radius: 4px;
  background: #f5f7fa;
}

.media-thumb-error {
  width: 100%;
  height: 110px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #909399;
  background: #f5f7fa;
  border-radius: 4px;
}

.media-name {
  font-size: 13px;
  color: #303133;
  margin-top: 6px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.media-meta {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

.media-time {
  font-size: 12px;
  color: #c0c4cc;
  margin-top: 2px;
}

.media-actions {
  margin-top: 6px;
  display: flex;
  justify-content: space-between;
}
</style>
