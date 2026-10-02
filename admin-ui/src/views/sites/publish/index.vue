<template>
  <div class="page-container">
    <el-card shadow="never">
      <div class="site-head">
        <div>
          <div class="site-title">{{ siteStore.currentSiteName || '未选择站点' }}</div>
          <div class="form-tip">
            发布把站点模板（template/&lt;主题&gt;）与数据库内容渲染成静态站，产物写进站点目录下的
            www/；data/（人工上传的资产）与 template/（模板）一个字节都不会被改动。
          </div>
        </div>
        <div class="site-actions">
          <el-button
            v-permission="'cms:publish:run'"
            :icon="View"
            :loading="previewing"
            @click="onPreview"
          >
            预演
          </el-button>
          <el-button
            v-permission="'cms:publish:run'"
            type="primary"
            :icon="Upload"
            :loading="publishing === 'full'"
            :disabled="publishing !== '' || hasProblems"
            @click="onPublish('full')"
          >
            一键全站静态化
          </el-button>
          <el-button
            v-permission="'cms:publish:run'"
            :icon="RefreshRight"
            :loading="publishing === 'incremental'"
            :disabled="publishing !== ''"
            @click="onPublish('incremental')"
          >
            增量发布
          </el-button>
        </div>
      </div>

      <el-descriptions :column="3" border class="site-desc">
        <el-descriptions-item label="当前站点">
          {{ siteStore.currentSiteName || '未选择' }}
        </el-descriptions-item>
        <el-descriptions-item label="静态化主题">{{ themeText }}</el-descriptions-item>
        <el-descriptions-item label="产物目录">{{ outputDirText }}</el-descriptions-item>
      </el-descriptions>

      <div class="form-tip">
        「一键全站静态化」按模板 + 当前内容重写整个 www/，并清理已删除内容留下的旧产物；
        「增量发布」只重写内容变化过的页面，更快，但不做产物清理。发布前建议先「预演」。
      </div>
    </el-card>

    <el-card v-if="preview" shadow="never" class="block-card">
      <template #header>
        <div class="block-head">
          <span>预演结果（只算计划，不写盘）</span>
          <span class="form-tip">
            主题 {{ preview.theme || '未设置' }} · 计划 {{ preview.totalPages }} 个页面 ·
            明细 {{ preview.pages.length }} 条
          </span>
        </div>
      </template>

      <el-alert
        v-if="preview.problems.length"
        class="block-alert"
        type="error"
        :closable="false"
        show-icon
        title="预演不通过，先修问题（修好前不能发布）"
      >
        <div v-for="(item, index) in preview.problems" :key="index" class="alert-line">
          {{ item }}
        </div>
      </el-alert>

      <el-alert
        v-if="preview.warnings.length"
        class="block-alert"
        type="warning"
        :closable="false"
        show-icon
        :title="`预演有 ${preview.warnings.length} 条提醒（不阻塞发布）`"
      >
        <div v-for="(item, index) in preview.warnings" :key="index" class="alert-line">
          {{ item }}
        </div>
      </el-alert>

      <el-table :data="preview.pages" stripe size="small" max-height="360">
        <el-table-column prop="pageType" label="页面类型" width="130" />
        <el-table-column prop="url" label="URL" min-width="220" show-overflow-tooltip />
        <el-table-column prop="path" label="产物路径" min-width="220" show-overflow-tooltip />
        <el-table-column prop="template" label="模板" min-width="160" show-overflow-tooltip />
      </el-table>
    </el-card>

    <el-card v-if="result" shadow="never" class="block-card">
      <template #header>
        <div class="block-head">
          <span>发布结果</span>
          <el-tag :type="failed ? 'danger' : 'success'">
            {{ failed ? '发布未成功' : '发布成功' }}
          </el-tag>
        </div>
      </template>

      <el-alert
        v-if="failed"
        class="block-alert"
        type="error"
        :closable="false"
        show-icon
        title="本批次没有成功，请按下面的错误逐条处理"
      >
        <div v-for="(item, index) in result.errors" :key="index" class="alert-line">{{ item }}</div>
        <div v-if="!result.errors.length" class="alert-line">
          有 {{ result.failedPages }} 个页面渲染失败，但后端没有返回具体的错误信息
        </div>
      </el-alert>

      <el-descriptions :column="2" border>
        <el-descriptions-item label="批次号">{{ result.batchId || '未返回' }}</el-descriptions-item>
        <el-descriptions-item label="计划页数">{{ result.totalPages }}</el-descriptions-item>
        <el-descriptions-item label="写出页数">{{ result.writtenPages }}</el-descriptions-item>
        <el-descriptions-item label="跳过页数（内容没变）">{{ result.skippedPages }}</el-descriptions-item>
        <el-descriptions-item label="失败页数">{{ result.failedPages }}</el-descriptions-item>
        <el-descriptions-item label="删除产物数">{{ result.deletedArtifacts }}</el-descriptions-item>
        <el-descriptions-item label="用时(ms)">{{ result.elapsedMillis }}</el-descriptions-item>
        <el-descriptions-item label="输出目录">{{ result.outputDir }}</el-descriptions-item>
      </el-descriptions>

      <div class="agg">
        <div class="agg-title">
          重建的聚合产物（{{ result.aggregateArtifacts.length }} 个）
        </div>
        <el-empty
          v-if="!result.aggregateArtifacts.length"
          description="没有聚合产物"
          :image-size="50"
        />
        <el-tag
          v-for="item in result.aggregateArtifacts"
          :key="item"
          class="agg-tag"
          effect="plain"
        >
          {{ item }}
        </el-tag>
      </div>

      <el-alert
        v-if="result.warnings.length"
        class="block-alert"
        type="warning"
        :closable="false"
        show-icon
        :title="`本批次有 ${result.warnings.length} 条提醒`"
      >
        <div v-for="(item, index) in result.warnings" :key="index" class="alert-line">
          {{ item }}
        </div>
      </el-alert>
    </el-card>

    <el-card v-permission="'cms:publish:run'" shadow="never" class="block-card">
      <template #header>
        <div class="block-head">
          <span>发布批次记录（最近 {{ tasks.length }} 条）</span>
          <div class="block-actions">
            <el-button link :icon="Refresh" @click="loadTasks">刷新</el-button>
          </div>
        </div>
      </template>

      <el-alert
        v-if="tasksFailed"
        class="block-alert"
        type="warning"
        :closable="false"
        show-icon
        title="批次记录加载失败"
        description="请确认当前账号是否有查看权限，或稍后重试。"
      />

      <el-table v-loading="tasksLoading" :data="tasks" stripe size="small">
        <el-table-column prop="batchId" label="批次号" min-width="200" show-overflow-tooltip />
        <el-table-column label="模式" width="90">
          <template #default="{ row }">{{ modeText(row.mode) }}</template>
        </el-table-column>
        <el-table-column label="触发方式" width="110">
          <template #default="{ row }">{{ triggerText(row.trigger) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <!--
          后端 /publish/site/tasks 现在返回的是 CmsPublishTask 实体（total / done / failed / message /
          startTime），types 里的 PublishTaskRow 用的是另一组名字（totalPages / writtenPages / …）。
          这里两个名字都认：后端补上 VO 之后不用改这一页，现在也不会整列空白。
        -->
        <el-table-column label="计划" width="80">
          <template #default="{ row }">{{ row.totalPages ?? row.total ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="写出" width="80">
          <template #default="{ row }">{{ row.writtenPages ?? row.done ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="失败" width="80">
          <template #default="{ row }">{{ row.failedPages ?? row.failed ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="用时(ms)" width="110">
          <template #default="{ row }">{{ row.elapsedMillis ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="开始时间" width="170">
          <template #default="{ row }">{{ row.startTime ?? row.createTime ?? '—' }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, RefreshRight, Upload, View } from '@element-plus/icons-vue'
import { listPublishTasks, listSites, previewPublish, publishSite } from '@/api/cms'
import { useSiteStore } from '@/stores/site'
import { useUserStore } from '@/stores/user'
import type { PublishPreview, PublishResult, PublishTaskRow, SiteItem } from '@/types'

/**
 * types/index.ts 的 SiteItem 还没补上静态化相关的 6 列（后端 CmsSite 实体已经有了），
 * 这里就地声明可选的扩展字段，避免为了读 theme / rootDirPath 使用 any。
 */
type SiteWithPublish = SiteItem & {
  theme?: string | null
  protocol?: string | null
}

const siteStore = useSiteStore()
const userStore = useUserStore()

const previewing = ref(false)
const publishing = ref<'' | 'full' | 'incremental'>('')
const preview = ref<PublishPreview | null>(null)
const result = ref<PublishResult | null>(null)

const tasks = ref<PublishTaskRow[]>([])
const tasksLoading = ref(false)
const tasksFailed = ref(false)

/* ---------------- 顶部：当前站点 / 主题 / 产物目录 ---------------- */

const theme = ref('')
/** 站点目录的绝对路径（后端给展示用），拿不到时产物目录只能等发布结果 */
const siteDirPath = ref('')
/** 发布结果里的真实输出目录，优先于推算值 */
const publishedDir = ref('')

const hasProblems = computed(() => (preview.value?.problems.length ?? 0) > 0)

const themeText = computed(() => {
  if (theme.value) return theme.value
  // 站点没配主题时后端按 _default 找模板目录；template/_default 不存在才报「主题目录不存在」
  return '未设置（按 _default 找，找不到会报「主题目录不存在」）'
})

const outputDirText = computed(() => {
  if (publishedDir.value) return publishedDir.value
  if (!siteDirPath.value) return '发布后由后端返回'
  // 站点目录是服务器上的绝对路径，分隔符跟着服务器走
  return siteDirPath.value.includes('\\') ? `${siteDirPath.value}\\www` : `${siteDirPath.value}/www`
})

/**
 * 后端 PublishResult 里的 success 是 record 的派生方法（不是字段），
 * JSON 里可能根本没有这个键；缺失时按后端 success() 的口径自己判一次，
 * 否则成功的发布会一律被标成失败。
 */
const failed = computed(() => {
  const data = result.value
  if (!data) return false
  return data.success === false || data.failedPages > 0 || data.errors.length > 0
})

async function loadSiteInfo() {
  // 站点配置要 cms:site:list 才读得到；没有这个权限就别发请求，免得一进页面就弹一个 403
  if (!userStore.hasPerm('cms:site:list')) return
  try {
    const sites: SiteWithPublish[] = await listSites()
    // 后端把 Long 序列化成字符串，站点 id 必须转 number 才能和 store 里的当前站点比
    const current = sites.find((site) => Number(site.id) === siteStore.currentSiteId)
    if (!current) return
    siteDirPath.value = current.rootDirPath || ''
    theme.value = current.theme || ''
  } catch {
    // 读不到站点配置不影响发布：主题会在预演后填上，产物目录会在发布结果里给出
  }
}

/* ---------------- 预演 / 发布 ---------------- */

async function onPreview() {
  previewing.value = true
  try {
    const data = await previewPublish()
    preview.value = data
    if (data.theme) theme.value = data.theme
    if (data.problems.length) {
      ElMessage.warning(`预演不通过：${data.problems.length} 个问题需要先修`)
    }
  } catch {
    // 后端把「主题目录不存在」这类问题作为错误返回，拦截器已经弹过提示，这里只结束 loading
  } finally {
    previewing.value = false
  }
}

async function onPublish(mode: 'full' | 'incremental') {
  if (mode === 'full') {
    try {
      await ElMessageBox.confirm(
        '一键全站静态化会用主题模板 + 当前数据库内容重新渲染整站，并重写站点目录下的整个 www/ ' +
          '产物目录；data/ 里的人工资产与 template/ 模板不受影响。确认现在发布吗？',
        '确认全站静态化',
        { type: 'warning', confirmButtonText: '开始发布', cancelButtonText: '再想想' },
      )
    } catch {
      return // 用户取消
    }
  }
  publishing.value = mode
  try {
    const data = await publishSite(mode)
    result.value = data
    if (data.outputDir) publishedDir.value = data.outputDir
    if (failed.value) {
      ElMessage.error('发布未成功，请按结果面板里的错误逐条处理')
    } else {
      ElMessage.success(`发布完成：写出 ${data.writtenPages} 页，跳过 ${data.skippedPages} 页`)
    }
    loadTasks()
  } catch {
    // 同上：错误信息由拦截器统一提示
  } finally {
    publishing.value = ''
  }
}

/* ---------------- 批次记录 ---------------- */
async function loadTasks() {
  tasksLoading.value = true
  tasksFailed.value = false
  try {
    tasks.value = await listPublishTasks()
  } catch {
    tasksFailed.value = true
  } finally {
    tasksLoading.value = false
  }
}

const modeMap: Record<string, string> = {
  full: '全量',
  incremental: '增量',
  narrow: '局部',
  aggregate: '聚合',
}

const triggerMap: Record<string, string> = {
  manual: '手动',
  content: '内容变更',
  schedule: '定时任务',
  theme: '主题变更',
  expire: '到期下线',
}

const statusMap: Record<string, string> = {
  PENDING: '待执行',
  RUNNING: '执行中',
  SUCCESS: '成功',
  PARTIAL: '部分失败',
  FAILED: '失败',
  CANCELLED: '已取消',
}

function modeText(mode: string) {
  return modeMap[mode] || mode || '—'
}

function triggerText(trigger: string | null | undefined) {
  if (!trigger) return '—'
  return triggerMap[trigger] || trigger
}

function statusText(status: string) {
  return statusMap[status] || status || '—'
}

function statusTag(status: string): 'success' | 'info' | 'warning' | 'danger' | 'primary' {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'PARTIAL') return 'warning'
  if (status === 'RUNNING') return 'primary'
  return 'info'
}

onMounted(() => {
  loadSiteInfo()
  loadTasks()
})
</script>

<style scoped>
.form-tip {
  font-size: 12px;
  color: #909399;
  line-height: 20px;
}

.site-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}

.site-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 4px;
}

.site-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.site-desc {
  margin-bottom: 8px;
}

.block-card {
  margin-top: 16px;
}

.block-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.block-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.block-alert {
  margin-bottom: 12px;
}

.alert-line {
  line-height: 20px;
  word-break: break-all;
}

.agg {
  margin-top: 12px;
}

.agg-title {
  font-size: 13px;
  color: #606266;
  margin-bottom: 8px;
}

.agg-tag {
  margin: 0 6px 6px 0;
}
</style>
