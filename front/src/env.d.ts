// src/env.d.ts

// 这行代码的意思是：告诉 TypeScript，所有以 .vue 结尾的文件，都是 Vue 组件模块
declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}
