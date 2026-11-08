import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { createPinia } from 'pinia'

// 1. 引入 Element Plus 样式
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'

// 2. 引入所有图标 (为了方便开发，这里一次性引入所有图标)
// 生产环境如果在意包体积，后面可以优化为按需引入
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

const app = createApp(App)

// 3. 注册所有图标到全局
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

// 4. 使用 Element Plus
app.use(ElementPlus)
app.use(createPinia())
app.use(router)

app.mount('#app')
