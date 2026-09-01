import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'
import './styles/index.css'
import App from './App.vue'
import router from './router'
import { setupPermission } from './router/permission'
import permDirective from './directives/perm'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })
Object.entries(ElementPlusIconsVue).forEach(([name, component]) => {
  app.component(name, component)
})
app.directive('perm', permDirective)
setupPermission(router)

app.mount('#app')
