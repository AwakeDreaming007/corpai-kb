import { useUserStore } from '../stores/user'

/** v-perm 指令：无权限码时直接移除元素 */
const permDirective = {
  mounted(el, binding) {
    const userStore = useUserStore()
    if (!userStore.hasPerm(binding.value)) {
      el.parentNode?.removeChild(el)
    }
  },
}

export default permDirective
