import { defineStore } from 'pinia'
import { siteOptions } from '@/api/cms'
import type { SiteOption } from '@/types'

interface SiteState {
  sites: SiteOption[]
  currentSiteId: number
  loaded: boolean
}

/** 后端 cms_site.is_default 的取值：1 表示系统默认站点（有且仅有一个） */
const SITE_DEFAULT_FLAG = 1

/** localStorage 在隐私模式 / 配额耗尽时会抛异常，读写都要兜住 */
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
    // 写不进去时仍以内存里的站点头为准，后端拿不到 X-Site-Id 会落到默认站点
  }
}

function removeStorage(key: string) {
  try {
    localStorage.removeItem(key)
  } catch {
    // 存储不可用时忽略
  }
}

function loadSiteId(): number {
  const id = Number(readStorage('siteId'))
  // 非法取值（NaN / 0 / 负数）一律回落成 0，避免把 "NaN" 写进存储再当成站点头发出去
  return Number.isInteger(id) && id > 0 ? id : 0
}

/** 并发调用复用的在途请求：路由守卫与页面同时 ensureLoaded 时只发一次 */
let inFlight: Promise<void> | null = null

export const useSiteStore = defineStore('site', {
  state: (): SiteState => ({
    sites: [],
    currentSiteId: loadSiteId(),
    loaded: false,
  }),
  getters: {
    currentSite: (state) => state.sites.find((site) => site.id === state.currentSiteId),
    currentSiteName(): string {
      return this.currentSite?.name || ''
    },
  },
  actions: {
    /**
     * 拉取站点选项并校准当前站点；失败不抛错，保证路由守卫和页面照常渲染。
     * 并发调用（守则 + 页面）复用同一次在途请求。
     */
    async ensureLoaded() {
      if (this.loaded) return
      inFlight ??= this.loadSites().finally(() => {
        inFlight = null
      })
      await inFlight
    },
    async loadSites() {
      try {
        this.sites = (await siteOptions()) || []
        // 本地 id 可能已失效（站点被删或首次登录没选过），退到默认站点，再退到第一个
        const fallback =
          this.sites.find((site) => site.isDefault === SITE_DEFAULT_FLAG) || this.sites[0]
        const id = this.sites.some((site) => site.id === this.currentSiteId)
          ? this.currentSiteId
          : fallback?.id || 0
        this.currentSiteId = id
        if (id) {
          writeStorage('siteId', String(id))
        } else {
          removeStorage('siteId')
        }
        // 只有真正拿到列表才算加载完成；失败时 loaded 保持 false，下一次路由导航会再试一次
        this.loaded = true
      } catch (error) {
        console.error('站点列表加载失败，本次切换站点不可用', error)
      }
    },
    switchSite(id: number) {
      if (!Number.isInteger(id) || id <= 0) return
      const target = this.sites.find((site) => site.id === id)
      if (!target) return
      this.currentSiteId = target.id
      writeStorage('siteId', String(target.id))
    },
  },
})
