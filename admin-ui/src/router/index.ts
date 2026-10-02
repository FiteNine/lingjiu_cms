import { createRouter, createWebHistory } from 'vue-router'
import { useSiteStore } from '@/stores/site'
import { useUserStore } from '@/stores/user'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/login/index.vue'),
      meta: { title: '登录' },
    },
    {
      path: '/',
      component: () => import('@/layout/index.vue'),
      redirect: '/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'Dashboard',
          component: () => import('@/views/dashboard/index.vue'),
          meta: { title: '仪表盘' },
        },
        {
          path: 'cms/types',
          name: 'ContentTypes',
          component: () => import('@/views/cms/types/index.vue'),
          meta: { title: '内容类型', group: '内容管理' },
        },
        {
          path: 'cms/contents',
          name: 'Contents',
          component: () => import('@/views/cms/contents/index.vue'),
          meta: { title: '通用内容', group: '内容管理' },
        },
        {
          path: 'cms/contents/edit',
          name: 'ContentEdit',
          component: () => import('@/views/cms/contents/edit.vue'),
          meta: { title: '内容编辑', group: '内容管理' },
        },
        {
          path: 'cms/menus',
          name: 'SiteMenus',
          component: () => import('@/views/cms/menus/index.vue'),
          meta: { title: '导航菜单', group: '内容管理' },
        },
        {
          path: 'cms/categories',
          name: 'Categories',
          component: () => import('@/views/cms/categories/index.vue'),
          meta: { title: '分类管理', group: '内容管理' },
        },
        {
          path: 'cms/tags',
          name: 'Tags',
          component: () => import('@/views/cms/tags/index.vue'),
          meta: { title: '标签管理', group: '内容管理' },
        },
        {
          path: 'cms/media',
          name: 'Media',
          component: () => import('@/views/cms/media/index.vue'),
          meta: { title: '媒体库', group: '内容管理' },
        },
        {
          path: 'ai/providers',
          name: 'AiProviders',
          component: () => import('@/views/ai/providers/index.vue'),
          meta: { title: 'AI服务商', group: 'AI管理' },
        },
        {
          path: 'ai/agents',
          name: 'AiAgents',
          component: () => import('@/views/ai/agents/index.vue'),
          meta: { title: '智能体', group: 'AI管理' },
        },
        {
          path: 'system/users',
          name: 'Users',
          component: () => import('@/views/system/users/index.vue'),
          meta: { title: '用户管理', group: '系统管理' },
        },
        {
          path: 'system/roles',
          name: 'Roles',
          component: () => import('@/views/system/roles/index.vue'),
          meta: { title: '角色管理', group: '系统管理' },
        },
        {
          path: 'system/menus',
          name: 'Menus',
          component: () => import('@/views/system/menus/index.vue'),
          meta: { title: '菜单管理', group: '系统管理' },
        },
        {
          path: 'system/dict',
          name: 'Dict',
          component: () => import('@/views/system/dict/index.vue'),
          meta: { title: '字典管理', group: '系统管理' },
        },
        {
          path: 'system/logs',
          name: 'Logs',
          component: () => import('@/views/system/logs/index.vue'),
          meta: { title: '操作日志', group: '系统管理' },
        },
        {
          path: 'sites/manage',
          name: 'Sites',
          component: () => import('@/views/sites/index.vue'),
          meta: { title: '站点管理', group: '站点' },
        },
        {
          path: 'sites/dir',
          name: 'SiteDir',
          component: () => import('@/views/sites/dir/index.vue'),
          meta: { title: '站点目录', group: '站点' },
        },
        {
          path: 'sites/publish',
          name: 'SitePublish',
          component: () => import('@/views/sites/publish/index.vue'),
          meta: { title: '发布中心', group: '站点' },
        },
        {
          path: 'sites/options',
          name: 'SitePublishOptions',
          component: () => import('@/views/sites/options/index.vue'),
          meta: { title: '发布选项', group: '站点' },
        },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
  ],
})

router.beforeEach(async (to) => {
  // 登录态的唯一来源是 stores/user（登录成功后写入 localStorage），这里不再重复一套 token 读取逻辑
  const userStore = useUserStore()
  if (to.path === '/login') {
    return userStore.isLogin ? '/dashboard' : true
  }
  if (!userStore.isLogin) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  // 放行前确定站点上下文，页面挂载时不会用陈旧站点 id 发请求
  await useSiteStore().ensureLoaded()
  return true
})

export default router
