# Figure-ERP 手办管家

手办（Figure）库存与订单管理平台。前后端分离，两个应用平级放在同一个仓库里。

## 项目组成

| 目录 | 说明 | 技术栈 | 默认端口 |
|---|---|---|---|
| [`front/`](front) | 前端 | Vue 3 + Vite + TypeScript + Element Plus + Pinia + ECharts | 5173 |
| [`server/`](server) | 后端 | Spring Boot 4.1.2 + MyBatis-Plus + MySQL | 8080 |

各自的详细说明见 [front/README.md](front/README.md) 与 [server/README.md](server/README.md)。

## 快速启动

两个终端分别执行：

```bash
# 终端 1：后端（首次需先建库、配数据库密码，见 server/README.md）
cd server
./mvnw spring-boot:run

# 终端 2：前端
cd front
npm install     # 首次
npm run dev
```

打开 http://localhost:5173 ，顶栏左上角的状态灯会显示「服务正常」，
hover 可看到 MySQL 版本、当前库名、表数量 —— 这就是前后端 + 数据库全链路打通的自检。

- 后端健康检查：http://localhost:8080/api/system/health
- 数据库连通检查：http://localhost:8080/api/system/db
- 接口文档：http://localhost:8080/swagger-ui.html

## 目录结构

```
Figure-ERP/
├── front/                  # 前端应用
├── server/                 # 后端应用
│   ├── config/             # 本地数据库密码等覆盖配置（不进 git）
│   ├── db/schema.sql       # 建库脚本
│   └── src/main/java/com/figureerp/
│       ├── common/         # 统一响应、全局异常、基础实体
│       ├── config/         # MyBatis-Plus、跨域、Swagger
│       └── module/         # 业务模块，一个模块一个包
├── .editorconfig
├── .gitattributes
└── README.md
```

## 前后端联调约定

- 前端请求统一走 `/api/**`，由 `front/vite.config.ts` 代理到 `http://localhost:8080`，
  开发期不存在跨域；部署时把 `/api` 反向代理到后端即可。
- 后端统一响应 `{ code, message, data }`，异常也以 HTTP 200 返回，前端只判断 `code === 200`。
- 分页数据结构：`{ records, total, page, size, pages }`。

## 当前进度

- [x] 前端框架（布局、菜单、标签页、基础表格）
- [x] 后端框架（统一响应、全局异常、分页、审计字段、Swagger）
- [x] MySQL 连通、前后端代理打通、前端请求层封装
- [x] 员工表 + 登录（JWT + BCrypt），右上角显示员工姓名
- [x] 员工权限管理（员工列表 / 员工维护）：增删改查、角色调整、启停用、重置密码
- [x] 物流管理（物流价格维护 / 物流价格查询）：8 家供应商 × 34 省份价格、首重+续重计价、多供应商比价
- [x] 手办商品档案（手办列表 / 商品维护）：增删改查、图片上传、启停用
- [x] 库存基础档案（仓库 / 货架）：接口驱动，仓库 → 货架层级与占用保护（原「区域配置」已下线）
- [x] 入库记录（入库单 + 手办明细）：单号自动生成、明细多行、金额后端重算、确认入库 / 作废
- [x] 个人消息中心 + 工作台（今日计划 / 待办事项），首页按角色分流
- [ ] 出库单、库存台账（手办 + 库位 + 数量）
- [ ] 操作日志切面
- [ ] 订单模块 CRUD 与页面接入真实接口

## 默认账号

后端首次启动会自动创建管理员（`server/config/DataInitializer`）：

```
账号：admin
密码：admin123
```

登录页直接输入即可，登录后右上角会显示「系统管理员」。
