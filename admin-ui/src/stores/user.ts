import { defineStore } from 'pinia'
import { login as loginApi, logout as logoutApi, updatePassword } from '@/api/auth'
import type { UserProfile } from '@/types'

interface UserState {
  token: string
  user: UserProfile | null
}

/** localStorage 在隐私模式 / 配额耗尽时会抛异常，读写都要兜住，不能让 store 初始化失败 */
function readStorage(key: string): string | null {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

function writeStorage(key: string, value: string) {
  try {
    localStorage.setItem(key, value)
  } catch {
    // 写不进去（隐私模式 / 配额超限）不阻断登录流程，内存里的登录态依旧可用
  }
}

/**
 * localStorage 可被用户或脚本篡改：roles / perms 不是数组时 getter 里的
 * includes 会直接抛 TypeError，所以反序列化后必须校验结构，不合法就当没登录。
 */
function loadUser(): UserProfile | null {
  const raw = readStorage('user')
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw) as UserProfile
    if (!parsed || typeof parsed !== 'object') return null
    if (!Array.isArray(parsed.roles) || !Array.isArray(parsed.perms)) return null
    return parsed
  } catch {
    return null
  }
}

export const useUserStore = defineStore('user', {
  state: (): UserState => ({
    token: readStorage('token') || '',
    user: loadUser(),
  }),
  getters: {
    isLogin: (state) => !!state.token,
    nickname: (state) => state.user?.nickname || state.user?.username || '',
    perms: (state) => state.user?.perms || [],
    roles: (state) => state.user?.roles || [],
  },
  actions: {
    async login(username: string, password: string) {
      const data = await loginApi({ username, password })
      this.token = data.token
      this.user = data.user
      writeStorage('token', data.token)
      writeStorage('user', JSON.stringify(data.user))
    },
    async changePassword(oldPassword: string, newPassword: string) {
      await updatePassword({ oldPassword, newPassword })
    },
    async logout() {
      try {
        await logoutApi()
      } finally {
        this.clear()
      }
    },
    clear() {
      this.token = ''
      this.user = null
      try {
        localStorage.removeItem('token')
        localStorage.removeItem('user')
      } catch {
        // 内存状态已经清干净，存储不可用时不影响登出
      }
    },
    /** 数组表示「必须同时具备全部权限」，与后端 @PreAuthorize 的多权限语义一致 */
    hasPerm(perm: string | string[]): boolean {
      const list = Array.isArray(perm) ? perm : [perm]
      if (this.roles.includes('admin')) {
        return true
      }
      return list.every((item) => this.perms.includes(item))
    },
  },
})
