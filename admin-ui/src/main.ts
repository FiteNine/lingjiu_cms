import { createApp } from 'vue'
import { createPinia } from 'pinia'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'

import App from './App.vue'
import router from './router'
import logoUrl from '@/assets/logo.svg'
import { permission } from './directives/permission'
import './styles/index.css'

// 浏览器标签页图标：直接用打包后的资源地址，不在 public/ 里另放一份
const favicon = document.createElement('link')
favicon.rel = 'icon'
favicon.type = 'image/svg+xml'
favicon.href = logoUrl
document.head.appendChild(favicon)

const app = createApp(App)

// 全局错误兜底：组件渲染异常与未处理的 Promise 拒绝至少留下可排查的日志
app.config.errorHandler = (err, _instance, info) => {
  console.error('[全局异常]', info, err)
}
window.addEventListener('unhandledrejection', (event) => {
  console.error('[未处理的 Promise 拒绝]', event.reason)
})

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.directive('permission', permission)

app.mount('#app')
