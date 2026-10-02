<template>
  <div class="login-page">
    <el-card class="login-card">
      <img :src="logoUrl" class="login-logo" alt="凌久网cms" />
      <h2 class="login-title">凌久网cms</h2>
      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="onSubmit">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            show-password
            :prefix-icon="Lock"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" class="login-btn" :loading="loading" @click="onSubmit">
            登 录
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { type FormInstance, type FormRules } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import logoUrl from '@/assets/logo.svg'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({
  username: '',
  password: '',
})
const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 32, message: '用户名长度需在 3-32 位之间', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 64, message: '密码长度需在 6-64 位之间', trigger: 'blur' },
  ],
}

/** 只接受站内绝对路径：以单个 / 开头，拒绝 //evil.com 这类协议相对地址（开放重定向） */
function safeRedirect(value: unknown): string {
  if (typeof value !== 'string') return '/dashboard'
  if (!value.startsWith('/')) return '/dashboard'
  if (value.startsWith('//') || value.startsWith('/\\')) return '/dashboard'
  return value
}

async function onSubmit() {
  if (loading.value) return
  const instance = formRef.value
  if (!instance) return
  try {
    await instance.validate()
  } catch {
    // 校验失败是正常分支，不往事件处理器外抛
    return
  }
  loading.value = true
  try {
    await userStore.login(form.username, form.password)
  } catch {
    // 失败提示由请求拦截器统一给出，这里收敛异常，不跳转
    return
  } finally {
    loading.value = false
  }
  await router.push(safeRedirect(route.query.redirect))
}
</script>

<style scoped>
.login-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1f2d3d 0%, #3a506b 100%);
}

.login-card {
  width: 380px;
  padding: 8px 12px 4px;
}

.login-logo {
  display: block;
  width: 56px;
  height: 56px;
  margin: 8px auto 0;
}

.login-title {
  text-align: center;
  margin: 8px 0 24px;
  color: #303133;
}

.login-btn {
  width: 100%;
}
</style>
