import type { Directive, DirectiveBinding } from 'vue'
import { useUserStore } from '@/stores/user'

/**
 * 按钮级权限指令：无权限时移除元素
 * 用法：v-permission="'cms:content:add'" 或 v-permission="['cms:content:add', 'cms:content:edit']"
 * 数组语义是「全部满足」（userStore.hasPerm 用 every），与后端 allOf 一致；
 * 需要「任一满足」请用 v-if + userStore.hasPerm 自行判断。
 */
export const permission: Directive<HTMLElement, string | string[]> = {
  mounted(el: HTMLElement, binding: DirectiveBinding<string | string[]>) {
    const value = binding.value
    if (!value || (Array.isArray(value) && value.length === 0)) {
      // 不抛异常：指令钩子里抛出会中断组件挂载，一个写错的权限标识不该让整页白屏
      console.warn('v-permission 需要指定权限点，如 v-permission="\'cms:content:add\'"')
      return
    }
    const userStore = useUserStore()
    if (!userStore.hasPerm(value)) {
      el.parentNode?.removeChild(el)
    }
  },
}
