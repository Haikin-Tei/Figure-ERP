# Figure-ERP Front（手办管家前端）

Vue 3 + Vite + TypeScript + Element Plus + Pinia + Vue Router + ECharts 的后台管理前端。
后端在同级目录 [`../server`](../server)。

## 本地运行

```bash
cd front
npm install          # 首次或依赖变更后
npm run dev          # 开发服务器 http://localhost:5173
```

其他命令：

```bash
npm run build        # 类型检查 + 生产构建（产物在 front/dist）
npm run type-check   # 只做 vue-tsc 类型检查
npm run lint         # ESLint
```

> 开发期 `/api/**` 由 `vite.config.ts` 的代理转发到 `http://localhost:8080`，
> 所以代码里直接写 `request({ url: '/system/health' })` 即可，不存在跨域问题。
> 后端没启动也不会白屏，只是页面上的服务状态灯会变红。

## 目录结构

```
front/
├── index.html
├── vite.config.ts          # 别名 @ → src，/api 代理到后端 8080
├── tsconfig*.json
├── eslint.config.ts / .oxlintrc.json / .oxfmtrc.json
└── src/
    ├── main.ts             # 入口：Element Plus / Pinia / Router 注册
    ├── App.vue
    ├── api/                # 请求层
    │   ├── request.ts      # axios 实例 + 统一响应/异常拦截
    │   └── system.ts       # 系统模块接口
    ├── components/
    │   ├── Layout.vue      # 整体布局（侧边栏 + 顶栏 + 标签页 + 内容区）
    │   ├── Sidebar.vue     # 侧边菜单（高亮跟随路由）
    │   ├── Navbar.vue      # 顶栏（服务状态灯 + 用户）
    │   ├── TagsView.vue    # 多标签页，支持右键菜单
    │   ├── menuConfig.ts   # 菜单数据
    │   ├── PlaceholderPage.vue  # 通用占位页（功能建设中）
    │   └── common/         # 通用组件（BaseTable 等）
    ├── router/index.ts     # 路由表
    ├── stores/             # Pinia
    ├── types/              # 类型定义
    └── views/
        ├── auth/           # 登录、404
        ├── dashboard/      # 数据概览
        ├── stock/          # 库存：手办列表（库存查询）/ 入库记录 / 库位 / 货架
        ├── acg/            # 目录维护：作品目录 + 三个占位页
        ├── order/          # 订单
        └── system/         # 用户管理、操作日志
```

## 库存页面说明（库存管理 → 手办列表）

`views/stock/FigureList.vue` 展示**当前在库库存**，数据来自后端 `stock` 表（接口 `api/stock.ts`）：

- 一行 = 一款手办的库存汇总（在库件数、库存金额、均价、占用库位数、最近入库时间），点「库位明细」下钻到仓库 / 货架。
- 顶部统计卡：在库款式 / 在库总件数 / 库存金额 / 占用库位。
- 筛选：手办名称 / 货号 / 品牌关键字、按仓库过滤、按件数 / 金额 / 最近入库 / 名称排序。
- **这一页只读**：库存由「入库记录」确认入库后自动累加，已入库单作废时自动回退；页面不提供手工增删改，避免库存与入库记录对不上账。
- 商品已被下架、但还有库存时，行上会标「已下架」，提示需要处理滞留货。

## 占位页面（待接入）

「目录维护」下还有两个**仅完成路由与菜单接入、暂未做后台数据与页面设计**的占位页，
统一使用 `components/PlaceholderPage.vue` 渲染「功能建设中」空状态：

| 菜单 | 路由 | 页面文件 |
| --- | --- | --- |
| 供应商目录维护 | `/acg/supplier` | `views/acg/SupplierCatalog.vue` |
| 大客户目录维护 | `/acg/key-customer` | `views/acg/KeyCustomerCatalog.vue` |

后续接入时直接替换对应 views 里的包装页面即可，路由与菜单配置无需再改动。

> 「厂商目录维护」（`/acg/manufacturer` → `views/acg/ManufacturerCatalog.vue`）已接入后端
> `module/manufacturer`，是正式页面，不再是占位页。接口见 `server/docs/api-contract.md` §10.6~10.8。

## 请求约定

后端统一返回 `{ code, message, data }`，`request.ts` 的响应拦截器已经把外层剥掉：

```ts
import request from '@/api/request'

// 拿到的直接就是 data，业务异常会被拦截器统一弹提示并 reject
const info = await request<HealthInfo>({ url: '/system/health', method: 'get' })
```

## 登录与权限

- **令牌**：登录成功后存 `localStorage`（`figure_erp_token`），请求拦截器自动加
  `Authorization: Bearer {token}`。
- **路由守卫** `router/index.ts`：未登录访问任何页面都会跳到 `/login?redirect=原地址`，
  登录成功后自动回到原页面；刷新页面时会用 `/auth/me` 恢复登录态。
- **401 处理**：拦截器收到 `code === 401` 时清令牌、提示并跳登录页；
  登录接口本身带 `skipAuthRedirect`，避免「密码错」被当成「登录过期」。
- **状态**：`stores/user.ts` 维护 `token / userInfo`，`displayName` 就是右上角显示的名字。
- **右上角**：登录后显示头像 + 员工姓名，点开有「个人消息 / 个人信息 / 退出登录」；
  未登录时显示「登录」按钮。

初始账号 `admin / admin123`（后端首次启动自动创建）。

## IDE 建议

[VS Code](https://code.visualstudio.com/) + [Vue (Official)](https://marketplace.visualstudio.com/items?itemName=Vue.volar)（并关闭 Vetur）。
`.vue` 文件的类型检查由 `vue-tsc` 负责，编辑器里依赖 Volar 提供 TypeScript 支持。
