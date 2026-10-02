<template>
  <div class="page-container">
    <el-card shadow="never">
      <div class="table-toolbar">
        <el-button v-permission="'cms:site:add'" type="primary" :icon="Plus" @click="openDialog()">
          新增站点
        </el-button>
      </div>

      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column prop="name" label="站点名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="code" label="标识" width="120" show-overflow-tooltip />
        <el-table-column prop="domain" label="域名" min-width="170" show-overflow-tooltip />
        <el-table-column label="站点目录" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">
            <div>{{ row.rootDir }}</div>
            <div class="form-tip">{{ row.rootDirPath }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'cms:site:edit'" link type="primary" @click="openDialog(row)">
              编辑
            </el-button>
            <el-button v-permission="'cms:site:delete'" link type="danger" @click="onDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑站点' : '新增站点'"
      width="640px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-divider content-position="left">基础信息</el-divider>
        <el-form-item label="站点名称" prop="name">
          <el-input v-model="form.name" placeholder="站点名称" />
        </el-form-item>
        <el-form-item label="站点标识" prop="code">
          <el-input v-model="form.code" placeholder="小写字母、数字、下划线或短横线" />
        </el-form-item>
        <el-form-item label="站点域名" prop="domain">
          <el-input v-model="form.domain" placeholder="如 www.lingjiuw.cn" />
        </el-form-item>
        <el-form-item label="站点 Logo" prop="logo">
          <el-input v-model="form.logo" placeholder="Logo 图片地址" />
        </el-form-item>

        <el-divider content-position="left">站点目录</el-divider>
        <el-form-item label="站点目录" prop="rootDir">
          <el-input v-model="form.rootDir" placeholder="如 lingjiuw.cn">
            <template #append>
              <el-button :icon="FolderOpened" @click="openPicker">选择文件夹</el-button>
            </template>
          </el-input>
          <div class="form-tip">
            网站文件的存放根目录，保存时不存在会自动创建，并在其下补齐 data（静态资源）与
            template（站点模板）两个子目录；只能填站点根目录下的相对路径
          </div>
        </el-form-item>

        <el-divider content-position="left">展示与联系</el-divider>
        <el-form-item label="站点描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="站点描述" />
        </el-form-item>
        <el-form-item label="SEO 关键词" prop="keywords">
          <el-input v-model="form.keywords" placeholder="多个关键词用英文逗号分隔" />
        </el-form-item>
        <el-form-item label="SEO 描述" prop="seoDescription">
          <el-input
            v-model="form.seoDescription"
            type="textarea"
            :rows="2"
            placeholder="搜索引擎结果里的摘要"
          />
        </el-form-item>
        <el-form-item label="ICP 备案号" prop="icp">
          <el-input v-model="form.icp" placeholder="如 粤ICP备xxxxxxxx号" />
        </el-form-item>
        <el-form-item label="联系电话" prop="contactPhone">
          <el-input v-model="form.contactPhone" />
        </el-form-item>
        <el-form-item label="联系邮箱" prop="contactEmail">
          <el-input v-model="form.contactEmail" />
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

        <el-divider content-position="left">静态化（发布）</el-divider>
        <el-form-item label="页面语言" prop="lang">
          <el-select v-model="form.lang" style="width: 220px">
            <el-option label="简体中文（zh-CN）" value="zh-CN" />
            <el-option label="English（en）" value="en" />
          </el-select>
          <div class="form-tip">
            写进页面的 html lang 与 hreflang；必须给值（空串后端会拒绝）
          </div>
        </el-form-item>
        <el-form-item label="站点协议" prop="protocol">
          <el-select v-model="form.protocol" style="width: 220px">
            <el-option label="https" value="https" />
            <el-option label="http" value="http" />
          </el-select>
          <div class="form-tip">
            拼绝对地址时用（sitemap / feed / canonical 的站点根地址）；必须给值
          </div>
        </el-form-item>
        <el-form-item label="静态化主题" prop="theme">
          <el-select
            v-model="form.theme"
            filterable
            allow-create
            default-first-option
            clearable
            placeholder="留空则发布时报「主题目录不存在」"
            style="width: 100%"
          >
            <el-option
              v-for="theme in themes"
              :key="theme.code"
              :label="theme.name"
              :value="theme.code"
            >
              <span :class="{ 'theme-invalid': !theme.valid }">
                {{ theme.name }}
                <span class="form-tip">{{ theme.path }}</span>
                <span v-if="!theme.valid" class="form-tip">theme.json 缺失或不是合法 JSON</span>
              </span>
            </el-option>
          </el-select>
          <div class="form-tip">
            值是站点 template/ 下的目录名，列表来自右上角当前站点的 template/；留空则发布时报「主题目录不存在」，
            标红的主题因为 theme.json 有问题，发布时也用不了
          </div>
        </el-form-item>
        <el-form-item label="默认封面图" prop="defaultCover">
          <el-input v-model="form.defaultCover" placeholder="内容没传封面时输出它，如 /data/cover.jpg" />
        </el-form-item>
        <el-form-item label="分享图" prop="ogImage">
          <el-input v-model="form.ogImage" placeholder="社交分享默认图（og:image）" />
        </el-form-item>
        <el-form-item label="统计脚本" prop="statisticsCode">
          <el-input
            v-model="form.statisticsCode"
            type="textarea"
            :rows="3"
            placeholder="整段统计代码（自己带上 script 标签）"
          />
          <div class="form-tip">
            整段脚本会原样注入页面，模板里用 [field:site.statisticsCode/] 输出
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="pickerVisible" title="选择站点目录" width="560px" destroy-on-close>
      <el-breadcrumb separator="/" class="picker-crumbs">
        <el-breadcrumb-item v-for="crumb in crumbs" :key="crumb.path">
          <a class="picker-link" @click="enterPath(crumb.path)">{{ crumb.label }}</a>
        </el-breadcrumb-item>
      </el-breadcrumb>

      <div class="picker-toolbar">
        <el-button size="small" :icon="Top" :disabled="!pickerPath" @click="enterPath(parentPath)">
          上一级
        </el-button>
        <div class="picker-new">
          <el-input
            v-model="newFolder"
            size="small"
            placeholder="新文件夹名称"
            @keyup.enter="onCreateFolder"
          />
          <el-button size="small" type="primary" :loading="creating" @click="onCreateFolder">
            新建文件夹
          </el-button>
        </div>
      </div>

      <div v-loading="pickerLoading" class="picker-list">
        <el-empty v-if="!pickerDirs.length" description="没有子文件夹" :image-size="60" />
        <div v-for="dir in pickerDirs" :key="dir.path" class="picker-item" @click="enterPath(dir.path)">
          <el-icon><Folder /></el-icon>
          <span>{{ dir.name }}</span>
        </div>
      </div>

      <div class="form-tip picker-tip">
        {{
          pickerPath
            ? `当前目录：${pickerPath}`
            : '站点目录不能是站点根目录本身，请进入或新建一个文件夹'
        }}
      </div>

      <template #footer>
        <el-button @click="pickerVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!pickerPath" @click="confirmPicker">选择此目录</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Folder, FolderOpened, Plus, Top } from '@element-plus/icons-vue'
import {
  createSite,
  createSiteDir,
  deleteSite,
  listSiteDirs,
  listSites,
  listThemes,
  updateSite,
  type SiteBody,
} from '@/api/cms'
import type { SiteDirNode, SiteItem, TableRow, ThemeItem } from '@/types'

const loading = ref(false)
const saving = ref(false)
const rows = ref<SiteItem[]>([])
/** 站点 template/ 下的主题目录，来自当前站点的 template/（右上角切换器决定） */
const themes = ref<ThemeItem[]>([])

async function load() {
  loading.value = true
  try {
    rows.value = await listSites()
  } finally {
    loading.value = false
  }
}

async function loadThemes() {
  try {
    themes.value = await listThemes()
  } catch {
    // 没有 cms:theme:list 权限、或当前站点还没有 template/ 目录时读不到
  }
}

/* ---------------- 新增 / 编辑 ---------------- */

const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  id: 0,
  name: '',
  code: '',
  domain: '',
  logo: '',
  description: '',
  keywords: '',
  seoDescription: '',
  rootDir: '',
  icp: '',
  contactPhone: '',
  contactEmail: '',
  status: 1,
  // 静态化相关：protocol / lang 是 not null 列，后端对空串会 400，所以给默认值
  protocol: 'https',
  lang: 'zh-CN',
  theme: '',
  defaultCover: '',
  ogImage: '',
  statisticsCode: '',
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入站点名称', trigger: 'blur' }],
  code: [
    { required: true, message: '请输入站点标识', trigger: 'blur' },
    {
      pattern: /^[a-z0-9_-]{1,64}$/,
      message: '只能用小写字母、数字、下划线或短横线',
      trigger: 'blur',
    },
  ],
  rootDir: [{ required: true, message: '请选择站点目录', trigger: 'blur' }],
  contactEmail: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
}

function openDialog(row?: TableRow) {
  form.id = row ? Number(row.id) : 0
  form.name = row ? String(row.name) : ''
  form.code = row ? String(row.code) : ''
  form.domain = row ? String(row.domain ?? '') : ''
  form.logo = row ? String(row.logo ?? '') : ''
  form.description = row ? String(row.description ?? '') : ''
  form.keywords = row ? String(row.keywords ?? '') : ''
  form.seoDescription = row ? String(row.seoDescription ?? '') : ''
  form.rootDir = row ? String(row.rootDir) : ''
  form.icp = row ? String(row.icp ?? '') : ''
  form.contactPhone = row ? String(row.contactPhone ?? '') : ''
  form.contactEmail = row ? String(row.contactEmail ?? '') : ''
  form.status = row ? Number(row.status) : 1
  // 这 6 列后端已经返回（types 里的 SiteItem 还没补），走 TableRow 读取
  form.protocol = row && row.protocol ? String(row.protocol) : 'https'
  form.lang = row && row.lang ? String(row.lang) : 'zh-CN'
  form.theme = row ? String(row.theme ?? '') : ''
  form.defaultCover = row ? String(row.defaultCover ?? '') : ''
  form.ogImage = row ? String(row.ogImage ?? '') : ''
  form.statisticsCode = row ? String(row.statisticsCode ?? '') : ''
  dialogVisible.value = true
}

async function onSave() {
  const instance = formRef.value
  if (!instance) return
  await instance.validate()
  saving.value = true
  try {
    const body: SiteBody = {
      name: form.name,
      code: form.code,
      domain: form.domain,
      logo: form.logo,
      description: form.description,
      keywords: form.keywords,
      seoDescription: form.seoDescription,
      rootDir: form.rootDir,
      icp: form.icp,
      contactPhone: form.contactPhone,
      contactEmail: form.contactEmail,
      status: form.status,
      // 空串会让后端 400（protocol / lang 是 not null 列），兜一个默认值
      protocol: form.protocol || 'https',
      lang: form.lang || 'zh-CN',
      theme: form.theme,
      defaultCover: form.defaultCover,
      ogImage: form.ogImage,
      statisticsCode: form.statisticsCode,
    }
    if (form.id) {
      await updateSite(form.id, body)
    } else {
      await createSite(body)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

function onDelete(row: TableRow) {
  ElMessageBox.confirm(
    `确认删除站点「${row.name}」吗？磁盘上的站点目录会保留，不会被一起删除。`,
    '提示',
    { type: 'warning' },
  )
    .then(async () => {
      await deleteSite(Number(row.id))
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

/* ---------------- 站点目录选择器 ---------------- */
/* 只允许在站点根目录（cms.site.root-dir）内浏览与新建，越界由后端统一拦截 */

const pickerVisible = ref(false)
const pickerLoading = ref(false)
const pickerPath = ref('')
const pickerDirs = ref<SiteDirNode[]>([])
const newFolder = ref('')
const creating = ref(false)

const crumbs = computed(() => {
  const items = [{ label: '站点根目录', path: '' }]
  let acc = ''
  for (const part of pickerPath.value ? pickerPath.value.split('/') : []) {
    acc = acc ? `${acc}/${part}` : part
    items.push({ label: part, path: acc })
  }
  return items
})

const parentPath = computed(() => pickerPath.value.split('/').slice(0, -1).join('/'))

async function enterPath(path: string) {
  pickerLoading.value = true
  try {
    const data = await listSiteDirs(path)
    pickerPath.value = data.path
    pickerDirs.value = data.dirs
  } finally {
    pickerLoading.value = false
  }
}

async function openPicker() {
  pickerVisible.value = true
  newFolder.value = ''
  pickerPath.value = ''
  pickerDirs.value = []
  if (form.rootDir.trim()) {
    try {
      // 编辑已有站点时直接落在它的目录上
      await enterPath(form.rootDir.trim())
      return
    } catch {
      // 目录还不存在（比如刚手输、尚未保存），退回根目录重新选
    }
  }
  await enterPath('')
}

async function onCreateFolder() {
  const name = newFolder.value.trim()
  if (!name) {
    ElMessage.warning('请输入文件夹名称')
    return
  }
  creating.value = true
  try {
    await createSiteDir(pickerPath.value, name)
    newFolder.value = ''
    ElMessage.success('新建成功')
    // 建完直接进到新文件夹，方便接着点「选择此目录」
    await enterPath(pickerPath.value ? `${pickerPath.value}/${name}` : name)
  } finally {
    creating.value = false
  }
}

function confirmPicker() {
  form.rootDir = pickerPath.value
  pickerVisible.value = false
}

onMounted(() => {
  load()
  loadThemes()
})
</script>

<style scoped>
.form-tip {
  font-size: 12px;
  color: #909399;
  line-height: 20px;
}

/* theme.json 有问题的主题标红，选之前就能看出来 */
.theme-invalid {
  color: #f56c6c;
}

.picker-crumbs {
  margin-bottom: 12px;
}

.picker-link {
  color: #409eff;
  cursor: pointer;
}

.picker-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.picker-new {
  display: flex;
  gap: 8px;
}

.picker-list {
  height: 240px;
  overflow: auto;
  padding: 4px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
}

.picker-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border-radius: 4px;
  font-size: 14px;
  cursor: pointer;
}

.picker-item:hover {
  background-color: #f5f7fa;
}

.picker-tip {
  margin-top: 8px;
}
</style>
