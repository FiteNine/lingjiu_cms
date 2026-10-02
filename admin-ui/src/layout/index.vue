<template>
  <el-container class="layout-container">
    <el-aside :width="isCollapse ? '64px' : '210px'" class="layout-aside">
      <div class="logo">
        <img :src="logoUrl" class="logo-img" alt="凌久网cms" />
        <span v-if="!isCollapse">凌久网cms</span>
      </div>
      <el-scrollbar>
        <el-menu
          :default-active="route.path"
          :collapse="isCollapse"
          :collapse-transition="false"
          router
          background-color="#304156"
          text-color="#bfcbd9"
          active-text-color="#409eff"
          class="layout-menu"
        >
          <el-menu-item index="/dashboard">
            <el-icon><Odometer /></el-icon>
            <span>仪表盘</span>
          </el-menu-item>
          <el-sub-menu index="site">
            <template #title>
              <el-icon><Monitor /></el-icon>
              <span>站点</span>
            </template>
            <el-menu-item index="/sites/manage">站点管理</el-menu-item>
            <el-menu-item index="/sites/publish">发布中心</el-menu-item>
            <el-menu-item index="/sites/options">发布选项</el-menu-item>
            <el-menu-item index="/sites/dir">站点目录</el-menu-item>
          </el-sub-menu>
          <el-sub-menu index="content">
            <template #title>
              <el-icon><Document /></el-icon>
              <span>内容管理</span>
            </template>
            <el-menu-item index="/cms/types">内容类型</el-menu-item>
            <el-menu-item index="/cms/contents">通用内容</el-menu-item>
            <el-menu-item index="/cms/menus">导航菜单</el-menu-item>
            <el-menu-item index="/cms/categories">分类管理</el-menu-item>
            <el-menu-item index="/cms/tags">标签管理</el-menu-item>
            <el-menu-item index="/cms/media">媒体库</el-menu-item>
          </el-sub-menu>
          <el-sub-menu index="system">
            <template #title>
              <el-icon><Setting /></el-icon>
              <span>系统管理</span>
            </template>
            <el-menu-item index="/system/users">用户管理</el-menu-item>
            <el-menu-item index="/system/roles">角色管理</el-menu-item>
            <el-menu-item index="/system/menus">菜单管理</el-menu-item>
            <el-menu-item index="/system/dict">字典管理</el-menu-item>
            <el-menu-item index="/system/logs">操作日志</el-menu-item>
          </el-sub-menu>
          <el-sub-menu index="ai">
            <template #title>
              <el-icon><MagicStick /></el-icon>
              <span>AI管理</span>
            </template>
            <el-menu-item index="/ai/providers">AI服务商</el-menu-item>
            <el-menu-item index="/ai/agents">智能体</el-menu-item>
            <el-menu-item index="/ai/copilot">全站agent</el-menu-item>
          </el-sub-menu>
        </el-menu>
      </el-scrollbar>
    </el-aside>

    <el-container direction="vertical">
      <el-header class="layout-header" height="56px">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="isCollapse = !isCollapse">
            <Expand v-if="isCollapse" />
            <Fold v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item v-if="route.meta.group">{{ route.meta.group }}</el-breadcrumb-item>
            <el-breadcrumb-item>{{ route.meta.title || '首页' }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <!--
            预览站点：顶栏是全局的，所以每个页面都露出，看的是当前站点的已发布产物。
            它开的是后端为该站点起的只读端口（站点在那里独占 "/"），所以是另开标签页。
          -->
          <el-button
            v-permission="'cms:publish:run'"
            :icon="View"
            :loading="previewingSite"
            @click="onPreviewSite"
          >
            预览站点
          </el-button>
          <el-dropdown @command="onCommand">
            <span class="user-info">
              <el-avatar :size="30" class="user-avatar">
                {{ userStore.nickname.slice(0, 1) || 'U' }}
              </el-avatar>
              <span class="user-name">{{ userStore.nickname }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="password">修改密码</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-dropdown
            v-if="siteStore.sites.length"
            popper-class="site-dropdown"
            @command="onSiteCommand"
          >
            <span class="site-info">
              <span class="site-name">{{ siteStore.currentSiteName }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item
                  v-for="site in siteStore.sites"
                  :key="site.id"
                  :command="site.id"
                  :class="{ 'is-active': site.id === siteStore.currentSiteId }"
                >
                  {{ site.name }}
                  <el-tag v-if="site.isDefault === 1" size="small">默认</el-tag>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="layout-main">
        <router-view :key="siteStore.currentSiteId" />
      </el-main>
    </el-container>
  </el-container>

  <el-dialog v-model="pwdVisible" title="修改密码" width="420px" destroy-on-close>
    <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="90px">
      <el-form-item label="原密码" prop="oldPassword">
        <el-input v-model="pwdForm.oldPassword" type="password" show-password />
      </el-form-item>
      <el-form-item label="新密码" prop="newPassword">
        <el-input v-model="pwdForm.newPassword" type="password" show-password />
      </el-form-item>
      <el-form-item label="确认新密码" prop="confirmPassword">
        <el-input v-model="pwdForm.confirmPassword" type="password" show-password />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="pwdVisible = false">取消</el-button>
      <el-button type="primary" :loading="pwdLoading" @click="submitPassword">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { ArrowDown, Document, Expand, Fold, MagicStick, Monitor, Odometer, Setting, View } from '@element-plus/icons-vue'
import { previewSiteUrl } from '@/api/cms'
import { useUserStore } from '@/stores/user'
import { useSiteStore } from '@/stores/site'
import logoUrl from '@/assets/logo.svg'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const siteStore = useSiteStore()

const isCollapse = ref(false)

/* ---------------- 预览站点（发布中心顶栏按钮） ---------------- */

const previewingSite = ref(false)

/**
 * 窗口先开、地址后填：请求回来再 window.open 会被浏览器的弹窗拦截器当成非用户触发的窗口。
 */
async function onPreviewSite() {
  previewingSite.value = true
  const tab = window.open('', '_blank')
  try {
    const data = await previewSiteUrl()
    if (tab) {
      tab.location.href = data.url
    } else {
      ElMessage.warning('浏览器拦截了新标签页，预览地址：' + data.url)
    }
  } catch {
    // 「还没有发布过」这类错误由拦截器统一提示，这里只把空标签页收掉
    tab?.close()
  } finally {
    previewingSite.value = false
  }
}

const pwdVisible = ref(false)
const pwdLoading = ref(false)
const pwdFormRef = ref<FormInstance>()
const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})
const pwdRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度至少 6 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback: (error?: Error) => void) => {
        if (value !== pwdForm.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

async function onCommand(command: string) {
  if (command === 'password') {
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
    pwdForm.confirmPassword = ''
    pwdVisible.value = true
  } else if (command === 'logout') {
    // 先等 logout 走完（store 内部 finally 会清本地状态），再跳转，避免请求在飞行中就被卸载
    await userStore.logout()
    router.push('/login')
  }
}

function onSiteCommand(id: number) {
  siteStore.switchSite(id)
}

async function submitPassword() {
  const form = pwdFormRef.value
  if (!form) return
  try {
    await form.validate()
  } catch {
    // 校验失败是正常分支，不往事件处理器外抛
    return
  }
  pwdLoading.value = true
  try {
    await userStore.changePassword(pwdForm.oldPassword, pwdForm.newPassword)
    ElMessage.success('密码修改成功')
    pwdVisible.value = false
  } catch {
    // 失败原因由请求拦截器统一提示，这里保持弹窗打开便于重试
  } finally {
    pwdLoading.value = false
  }
}
</script>

<style scoped>
.layout-container {
  height: 100%;
}

.layout-aside {
  background-color: #304156;
  transition: width 0.2s;
  overflow: hidden;
}

.logo {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #fff;
  font-size: 18px;
  font-weight: 600;
  background-color: #263445;
  white-space: nowrap;
}

.logo-img {
  width: 28px;
  height: 28px;
}

.layout-menu {
  border-right: none;
}

.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background-color: #fff;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  z-index: 5;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 14px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 18px;
}

.collapse-btn {
  font-size: 20px;
  cursor: pointer;
  color: #5a5e66;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: #303133;
  outline: none;
}

.user-avatar {
  background-color: #409eff;
  color: #fff;
}

.user-name {
  font-size: 14px;
}

.site-info {
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  color: #303133;
  outline: none;
}

.site-name {
  font-size: 14px;
}

.layout-main {
  padding: 0;
  background-color: var(--app-bg-color);
  overflow: auto;
}
</style>

<style>
/* 下拉菜单被 teleport 到 body，scoped 样式够不到，用 popper-class 定位 */
.site-dropdown .el-dropdown-menu__item.is-active {
  color: var(--el-color-primary);
  font-weight: 600;
}

.site-dropdown .el-tag {
  margin-left: 6px;
}
</style>
