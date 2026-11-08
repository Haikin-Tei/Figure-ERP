# Figure-ERP Server（手办管家后端）

手办管理平台的后端服务，独立于根目录的 Vue 前端。

## 技术选型

| 项 | 选型 | 说明 |
|---|---|---|
| JDK | 21（本机 JDK 24 编译，产物可在 21+ 运行） | Spring Boot 4 最低要求 17，选 21 是当前的长期支持版本 |
| 框架 | **Spring Boot 4.1.2** | 2026-10 的最新 GA。Spring Boot 3.x 已结束 OSS 支持，新项目不建议再用 |
| Web | Spring MVC（`spring-boot-starter-webmvc`） | Boot 4 把原来的 `-web` 改名成 `-webmvc` |
| ORM | MyBatis-Plus 3.5.18（boot4 starter） | 国内生态成熟，CRUD 与分页开箱即用 |
| 数据库 | MySQL 8（Connector/J） | 连接串带 `serverTimezone`、`allowPublicKeyRetrieval` |
| 参数校验 | spring-boot-starter-validation | Jakarta Validation |
| 接口文档 | springdoc-openapi 3.2.0 | Swagger UI |
| 工具 | Lombok | |

> 关于「Java Spring 3」：Spring Boot 3 在 2026 年已经停止开源支持，直接上 4.1.2 可以少一次大版本迁移。
> 如果你所在团队必须用 3.x，把 `pom.xml` 里的 parent 版本换成 3.5.x，并把 `mybatis-plus-spring-boot4-starter` 换成 `mybatis-plus-spring-boot3-starter` 即可，代码本身不用改。

## 接口契约（改接口必看）

**[`docs/api-contract.md`](docs/api-contract.md)** 是前后端之间的唯一约定来源，包含 85 个接口的名称、方法、路径、权限、入参、出参、错误码与示例，以及枚举字典和变更记录。

维护规则：**任何改动对外契约的提交，必须同时更新该文件的「接口总览表 + 接口详情 + 变更记录」**。判断标准、版本号规则见文档开头「§0 维护规则」。

> Swagger（`/swagger-ui.html`）由代码自动生成，能看字段但看不到权限与业务规则；契约文档是手工维护的权威版本，两者不一致时以文档为准并修正代码。

## 目录结构

```
server/
├── mvnw / mvnw.cmd              # Maven Wrapper，本机无需装 Maven
├── pom.xml
├── config/application-dev.yml   # 本地覆盖配置（数据库密码），不进 git
├── db/schema.sql                # 建库脚本（含 figure_product、warehouse、area、shelf、inbound_order、inbound_item、acg_work、sys_message、work_todo 等业务表）
├── docs/api-contract.md         # 接口契约（对外唯一约定，改接口必须同步）
└── src/main/
    ├── java/com/figureerp/
    │   ├── FigureErpServerApplication.java
    │   ├── common/              # 公共能力，不含业务
    │   │   ├── result/          # Result / ResultCode / PageResult 统一响应
    │   │   ├── exception/       # BusinessException + 全局异常处理
    │   │   └── entity/          # BaseEntity（主键/审计字段/逻辑删除）
    │   ├── config/              # MyBatis-Plus、跨域、接口文档、字段自动填充
    │   └── module/              # 业务模块，一个模块一个包
    │       └── system/          # 系统模块（健康检查示例）
    │           └── controller/
    └── resources/
        ├── application.yml      # 公共配置
        ├── application-dev.yml  # 开发环境
        ├── application-prod.yml # 生产环境
        └── mapper/              # MyBatis XML，按模块建子目录
```

### 新增一个业务模块的约定

以「手办档案」为例，在 `module/` 下建包：

```
module/stock/
├── controller/FigureController.java     # 只做参数接收与响应封装
├── service/FigureService.java            # 接口
├── service/impl/FigureServiceImpl.java   # 实现，继承 ServiceImpl<Mapper, Entity>
├── mapper/FigureMapper.java              # 继承 BaseMapper<Figure>
├── entity/Figure.java                    # 继承 BaseEntity
├── dto/FigureQuery.java                  # 入参
└── vo/FigureVO.java                      # 出参
```

## 本地运行

```bash
cd server

# 1. 建库（只需执行一次）
mysql -uroot -p < db/schema.sql

# 2. 填本机数据库密码
#    编辑 config/application-dev.yml（该目录已被 .gitignore 忽略，密码不会进仓库）
#    —— 也可以不改文件，改用环境变量：export DB_PASSWORD=你的密码

# 3. 启动
./mvnw spring-boot:run
```

- 服务地址：http://localhost:8080
- 健康检查：http://localhost:8080/api/system/health
- 数据库连通检查：http://localhost:8080/api/system/db
- 接口文档：http://localhost:8080/swagger-ui.html

> **关于配置分层**：`src/main/resources/application-dev.yml` 里密码写的是 `${DB_PASSWORD:}`（空默认值），
> 实际密码放在 `server/config/application-dev.yml`。Spring Boot 会优先加载 `file:./config/`
> 下的配置，所以这个文件能覆盖 classpath 里的同名配置，同时又不进 git。
> 注意该路径是相对**工作目录**的，启动时要在 `server/` 目录下执行。

> 开发环境把 `hikari.initialization-fail-timeout` 设成了 `-1`，所以**数据库没连上也能先启动**，
> 方便只调前端或看接口文档；真正查库时才会报连接错误。数据库是否连上，直接看 `/api/system/db` 的返回。

## 响应与异常约定

所有接口统一返回：

```json
{ "code": 200, "message": "操作成功", "data": {} }
```

- 成功：`code = 200`
- 失败：`code != 200`，`message` 可直接展示给用户
- **异常一律以 HTTP 200 返回**，前端只需判断 `code`，不用再区分 HTTP 状态码
- 分页数据用 `PageResult`：`{ records, total, page, size, pages }`

业务失败直接抛 `BusinessException`，由 `GlobalExceptionHandler` 统一转换，Controller 不需要 try-catch。

## 已配置 / 待补

- [x] 统一响应、全局异常、参数校验
- [x] MyBatis-Plus 分页插件、审计字段自动填充、逻辑删除
- [x] 跨域（允许 5173）
- [x] Swagger 接口文档
- [x] MySQL 连接打通（`/api/system/db` 可自检）
- [x] 前端请求封装对接（axios 拦截器 + 统一错误提示）
- [x] 员工表 + 登录鉴权（JWT + BCrypt）
- [x] 员工管理 CRUD + 权限修改（角色/状态），带管理员保护规则
- [x] 业务表结构与模块 CRUD（已落地：figure_product 手办商品模块、warehouse 仓库 / area 区域 / shelf 货架模块、acg_work ACG 作品与热度分析模块、sys_message 个人消息中心、work_todo 个人待办）
- [ ] 操作日志切面
- [ ] 更多业务模块（货架 / 库位 / 订单等）

## 认证与用户

### 接口

| 方法 | 路径 | 说明 | 是否需要登录 |
|---|---|---|---|
| POST | `/api/auth/login` | 账号密码登录，返回 JWT | 否 |
| GET | `/api/auth/me` | 当前登录用户信息 | 是 |
| POST | `/api/auth/logout` | 退出（无状态，前端删令牌即可） | 是 |
| GET | `/api/system/users` | 分页查询员工（管理员/主管） | 是 |
| GET | `/api/system/users/{id}` | 员工详情 | 是 |
| POST | `/api/system/users` | 新增员工 | 是，管理员 |
| PUT | `/api/system/users/{id}` | 编辑员工（角色/状态/介绍/备注） | 是，管理员 |
| DELETE | `/api/system/users/{id}` | 删除员工（逻辑删除） | 是，管理员 |
| PUT | `/api/system/users/{id}/password` | 重置密码 | 是，管理员 |
| PUT | `/api/system/users/{id}/status` | 启用 / 停用 | 是，管理员 |
| GET | `/api/system/dicts` | 字典：角色 / 状态 | 是 |

### 令牌怎么用

登录成功后拿到 `data.token`，后续请求加请求头：

```
Authorization: Bearer eyJhbGciOiJIUzM4NCJ9...
```

`AuthInterceptor` 会解析令牌并把用户放进 `UserContext`，业务代码里直接
`UserContext.requireId()` 就能拿到当前用户 id，不用一层层传参。

白名单：`/api/auth/login`、`/api/system/health`、`/api/system/db` 不需要登录。
其余 `/api/**` 全部拦截，未登录返回 `{ "code": 401, "message": "请先登录" }`（仍是 HTTP 200 约定）。

### 权限模型（员工权限管理）

「使用权限」由两个维度组成：

| 维度 | 值 | 决定什么 |
|---|---|---|
| `role` 角色 | 12 个角色，分管理级 / 主管级 / 执行级三层（见下表） | 能**操作**什么功能、能查看哪些数据 |
| `status` 状态 | 1 启用 / 0 停用 | 账号能否登录 |

角色定义在 `common/constant/UserRole`，每个角色带 `group`（板块，前端下拉分组）和 `level`（权限层级）：

| 层级 | level | 角色 | 能做什么 |
|---|:--:|---|---|
| 管理级 | 3 | ADMIN 管理员、GM 总经理 | 员工账号增删改 + 全部业务 |
| 主管级 | 2 | 财务 / 销售 / 物流 / 人事 各主管（旧版 `MANAGER` 同层） | 维护业务数据：商品、仓库、区域、货架、物流价格、ACG、发消息 |
| 执行级 | 1 | 对应的各专员（旧版 `STAFF` 同层） | 只读业务数据 |

权限校验只认层级：`CurrentUserService.requireManager()` 要求 `level >= 2`，
`requireAdmin()` / 员工账号维护要求 `level >= 3`。新增角色时在枚举里加一行并指定层级即可，
不用改表、不用改权限代码。前端也不重复写角色清单 —— 登录信息里的 `roleLevel` 直接决定按钮是否可点。

权限校验在 `UserService` 里做（不是 Controller）：每次操作都从库里重新读当前用户的角色，
所以**被降级的账号立刻失去管理权限**，不用等令牌过期。

**角色必填**：`sys_user.role` 为 `NOT NULL` 且**没有默认值**，员工录入时下拉必须选一个；
后端按枚举校验，空值返回 `1003 请选择角色`，非法值返回 `1003 角色不合法`。

内置保护规则：

- 不能删除自己 / 停用自己 / 修改自己的角色
- 系统至少保留一个启用状态的管理级账号（管理员或总经理），最后一个不能被删/停/降级
- 登录账号创建后不可修改（密码走单独的重置接口）
- 密码 BCrypt 加密，重置密码不需要旧密码

### 用户表 `sys_user`

| 字段 | 说明 |
|---|---|
| `username` | 登录账号，唯一，创建后不可改 |
| `real_name` | 员工姓名（右上角显示的就是它） |
| `password` | BCrypt 哈希，**不存明文**，任何接口都不返回 |
| `role` | 角色编码（必填，无默认值）：ADMIN / GM / FINANCE_MANAGER / … / HR_STAFF，旧版 MANAGER / STAFF 兼容保留，见上方角色表 |
| `intro` | 员工介绍 |
| `remark` | 备注 |
| `status` | 1 启用 / 0 停用（停用后不能登录） |
| `last_login_time` | 最后登录时间，每次登录更新 |

### 初始账号

首次启动时，如果 `sys_user` 表为空，会自动创建：

```
账号：admin
密码：admin123
姓名：系统管理员
```

由 `config/DataInitializer` 完成，受 `app.init-admin.enabled` 控制；
上线前请把 `enabled` 改成 `false`，或用 `ADMIN_INIT_PASSWORD` 环境变量指定强密码。

### 配置项

```yaml
app:
  jwt:
    secret: ${JWT_SECRET:...}   # HS256 密钥，至少 32 字节，生产必须用环境变量覆盖
    expire-seconds: 28800       # 8 小时
  init-admin:
    enabled: true
```

> **踩坑记录**：Spring Boot 4 默认用 **Jackson 3**（`tools.jackson.*`），
> 不再自动提供 `com.fasterxml.jackson.databind.ObjectMapper` 这个 Bean。
> 拦截器里写 JSON 响应要用 `tools.jackson.databind.ObjectMapper`。
> 另外 `jjwt-jackson` 自带的是 Jackson 2，两者可以共存，不冲突。

## 物流管理模块（module/logistics）

### 数据模型 `logistics_price`

一条记录 = 某供应商从某出发地到某目的地的一档价格，计价用国内快递通用的「首重 + 续重」：

```
重量 ≤ 首重        → 总价 = 首重价
重量 > 首重        → 总价 = 首重价 + ceil((重量-首重)/续重单位) × 续重单价
```

- 目的地按「省」配置，`dest_city` 留空表示全省统一价；给某城市单独设价后，查询该城市时**城市级优先**，
  查其他城市自动回落到省级价（适合新疆、西藏等需要按城市细调的场景）
- 表为空时 `LogisticsDataInitializer` 会生成 **8 家供应商 × 34 个省级行政区** 的参考价
  （顺丰/京东/中通/圆通/韵达/申通/极兔/EMS），由 `app.init-logistics.*` 控制，只填空表、重启无副作用

### 接口

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/api/logistics/prices` | 分页查询（供应商/出发地/目的地/状态/关键字） | 登录 |
| GET | `/api/logistics/prices/{id}` | 详情 | 登录 |
| POST | `/api/logistics/prices` | 新增（同路线重复会拒绝） | 管理员/主管 |
| PUT | `/api/logistics/prices/{id}` | 编辑 | 管理员/主管 |
| DELETE | `/api/logistics/prices/{id}` | 删除（逻辑删除） | 管理员/主管 |
| PUT | `/api/logistics/prices/{id}/status` | 启用/停用 | 管理员/主管 |
| POST | `/api/logistics/prices/batch` | 批量 upsert 全国价（按供应商+出发地+目的地匹配） | 管理员/主管 |
| GET | `/api/logistics/quote` | 运费报价：给定出发地+目的地+重量，返回各家对比（按总价升序） | 登录 |
| GET | `/api/logistics/dicts` | 字典：供应商/省份（含城市与运费区域）/已配置出发地 | 登录 |

### 运费区域

`FreightZone` 把全国划成 10 个区域（华东/华北/华中/华南/东北/西南/西北/新疆/西藏/港澳台），
每个区域有「首重加价 + 续重加价 + 时效增加」三个偏移量，叠加在供应商的华东基准价上生成初始价。
供应商基准价在 `LogisticsSupplier` 枚举里，**只影响首次初始化**，之后以数据库为准。

### 扩展新出发地

未来开新仓（比如从广州发货）不需要改代码：

1. 在「物流价格维护 → 批量设置全国价」里把出发地切到新城市，填一遍价格保存；
2. 查询页的出发地下拉会自动出现新出发地（数据来自 `/api/logistics/dicts` 的 `origins`）。

## 商品（手办）管理模块（module/product）

手办商品的档案维护与列表查询：录入图片、长宽高、带盒重量，支持多条件查询。对应前台「商品维护 → 商品列表」。

### 数据模型 `figure_product`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | bigint | 主键 |
| `name` | varchar | 商品名称（手办全称），非空 |
| `code` | varchar | 货号 / SKU，可空，唯一 |
| `brand` / `series` / `character_name` / `material` | varchar | 品牌 / 系列 / 角色名 / 材质，可空 |
| `length_cm` / `width_cm` / `height_cm` | decimal(6,1) | 长 / 宽 / 高（cm），可空 |
| `weight_with_box` | int | **带盒重量（g，克）**，含外包装盒，非空、> 0 |
| `images` | text | 商品图片 URL，逗号分隔，3~4 张 |
| `remark` | varchar | 备注 |
| `status` | tinyint | 1 启用 / 0 停用 |
| 审计字段 + `deleted` | — | 来自 `BaseEntity`（逻辑删除） |

表为空时 `ProductDataInitializer` 会插入 6 条演示数据（初音未来/明日香/御坂美琴/蕾姆/帕瓦/樱花版初音），受 `app.init-product.enabled` 控制（默认开启，只填空表）。

### 图片上传与存储

- 上传接口 `POST /api/product/upload`（multipart，登录即可，任意角色），由 `FileStorageService` 校验 content-type 与扩展名白名单、单张大小（默认 5MB）后落盘到 `{user.dir}/uploads/products/`。
- 返回相对 URL `/uploads/products/<uuid>.<ext>`，通过 `WebMvcConfig` 注册的静态资源处理器对外提供（无需鉴权，可直接访问）。
- 前端 `el-upload` 改用 `:http-request` 自定义上传（axios 带 `Authorization`），规避浏览器端 `action` 不携带令牌的问题；保存时只提交服务端返回的真实 URL（不要传 `blob:` 临时地址）。

### 接口

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/api/product/page` | 分页查询（名称/货号/品牌/系列/角色名模糊 + 状态） | 登录 |
| GET | `/api/product/{id}` | 详情 | 登录 |
| POST | `/api/product` | 新增（带盒重量>0、图片 3~4 张、货号唯一） | 管理员/主管 |
| PUT | `/api/product/{id}` | 编辑 | 管理员/主管 |
| DELETE | `/api/product/{id}` | 删除（逻辑删除） | 管理员/主管 |
| PUT | `/api/product/{id}/status` | 启用/停用 | 管理员/主管 |
| POST | `/api/product/upload` | 上传单张图片，返回 URL | 登录 |

详细入参 / 出参 / 错误码见契约文档 §8 与 §3.6。

## 库存基础档案模块（module/stock：仓库 / 区域 / 货架）

库存管理 → 仓库设置 下的三个基础档案页面。原先的数据都写死在页面里（`LocationManage/index.vue`、`ShelvesManage.vue`、`AreaConfig.vue`），现已全部落库，查询 / 新增 / 编辑 / 删除 / 详情都走接口，操作后立即重新拉取列表。

层级关系：`仓库 → 区域 → 货架 → 库位`。仓库是顶层；区域挂在仓库下且必选仓库；货架挂在仓库下，可选挂区域（`area_id` 可空，老数据不受影响）。

### 仓库档案 `warehouse`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | bigint | 主键 |
| `warehouse_code` | varchar(50) | 仓库代码，非空；未删除记录中唯一 |
| `warehouse_name` | varchar(100) | 仓库名称，非空 |
| `warehouse_address` | varchar(255) | 仓库地址，可空 |
| `is_return` | char(1) | `N` 正常仓 / `Y` 退货仓 |
| `status` | char(1) | `A` 使用中 / `C` 盘库中 / `D` 停用中 |
| `remark` | varchar(500) | 备注 |
| `sort_no` | int | 排序号，越小越靠前 |
| 审计字段 + `deleted` | — | 来自 `BaseEntity`（逻辑删除） |

表为空时 `WarehouseDataInitializer` 会插入 12 条初始仓库（上海 / 北京 / 广州 / 成都 / 杭州 / 深圳，含退货仓），受 `app.init-warehouse.enabled` 控制（默认开启，只填空表）。

> `warehouse_code` 没有建 UNIQUE 索引：本表用逻辑删除，若建唯一索引，删掉的记录会一直占用代码，导致改错后无法重建同代码仓库。唯一性由 Service 层在「未删除记录」范围内校验。

### 接口

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/api/warehouse/page` | 分页查询（代码/名称模糊，类型/状态过滤） | 登录 |
| GET | `/api/warehouse/{id}` | 详情 | 登录 |
| POST | `/api/warehouse` | 新增（代码在未删除记录中唯一） | 管理员/主管 |
| PUT | `/api/warehouse/{id}` | 编辑 | 管理员/主管 |
| DELETE | `/api/warehouse/{id}` | 删除（逻辑删除，代码可复用） | 管理员/主管 |
| PUT | `/api/warehouse/{id}/status` | 修改状态（A/C/D） | 管理员/主管 |

详细入参 / 出参 / 错误码见契约文档 §9。

### 区域档案 `area`

库存管理 → 仓库设置 → 区域配置 页面维护的数据，一条记录 = 某个仓库下的一片区域（如 `A区-核心展示柜`）。

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | bigint | 主键 |
| `area_code` | varchar(50) | 区域编号，如 `A`；**同一仓库 + 未删除**范围内唯一 |
| `area_name` | varchar(100) | 区域名称，如 `A区-核心展示柜` |
| `warehouse_id` | bigint | 所属仓库 `warehouse.id`，必填 |
| `location` | varchar(100) | 物理位置，如 `一层-主墙面`，可空 |
| `capacity` | int | 容量：可容纳货架数（原页面「容量 / 格子数」） |
| `status` | char(1) | `A` 使用中 / `D` 停用 |
| `remark` | varchar(255) | 备注 |
| `sort_no` | int | 排序号，越小越靠前 |
| 审计字段 + `deleted` | — | 来自 `BaseEntity`（逻辑删除） |

表为空时 `AreaDataInitializer` 会按仓库代码（3101 / 3102）关联插入 3 条初始区域，受 `app.init-area.enabled` 控制（默认开启，只填空表；找不到对应仓库的种子会跳过）。它用 `@Order(10)` 排在 `ShelfDataInitializer(20)` 之前，保证货架初始化时能按「仓库 + 区域编号」找到刚建好的区域。

设计说明：

- **为什么 `warehouse_id` 必填**：原页面 3 条区域没有归属仓库，落库后如果不挂仓库就是孤立数据，查不出「这是哪个仓库的 A 区」。
- **为什么编号只在同一仓库内唯一**：每个仓库都会有自己的 `A/B/C` 区，全局唯一不现实；和货架同套做法，不建 UNIQUE（逻辑删除会占用索引），由 `AreaService` 校验。
- **列表里的「已放货架」**：`AreaVO.shelfCount` 由后端按当前页区域批量算一次（一次 `in` 查询后在内存分组），前端直接显示 `2 / 20 个货架`，不用逐行请求货架接口。
- **删除 / 改仓库保护**：区域下还有未删除货架时，删除区域返回 `1000`；把有货架的区域改到其他仓库同样返回 `1000`，避免货架与区域仓库不一致。

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/api/area/page` | 分页查询（编号/名称模糊，仓库/状态过滤） | 登录 |
| GET | `/api/area/{id}` | 详情 | 登录 |
| POST | `/api/area` | 新增（同仓库编号唯一） | 管理员/主管 |
| PUT | `/api/area/{id}` | 编辑（区域下有货架时不能换仓库） | 管理员/主管 |
| DELETE | `/api/area/{id}` | 删除（逻辑删除；区域下有货架时拒绝） | 管理员/主管 |
| PUT | `/api/area/{id}/status` | 修改状态（A/D） | 管理员/主管 |

详细入参 / 出参 / 错误码见契约文档 §9（AREA-01~06）。

### 入库单 `inbound_order` / 明细 `inbound_item`

库存管理 → 入库记录 页面维护的数据。一条 `inbound_order` = 一次入库业务（一张入库单），
手办明细放在 `inbound_item`，一张单可以有多款手办。

`inbound_order`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | bigint | 主键 |
| `inbound_no` | varchar(50) | 入库单号，如 `RK202611070001`；**未删除**范围内唯一 |
| `inbound_type` | char(1) | `P` 采购 / `R` 退货 / `T` 调拨 / `O` 其他 |
| `warehouse_id` | bigint | 入库仓库 `warehouse.id`，必填 |
| `area_id` | bigint | 入库区域 `area.id`，可空；须与仓库一致 |
| `shelf_id` | bigint | 入库货架 `shelf.id`，可空；须与仓库一致 |
| `supplier` | varchar(100) | 供应商 / 来源单位，可空 |
| `status` | char(1) | `D` 待入库 / `C` 已入库 / `X` 已作废 |
| `total_quantity` | int | 明细数量合计，**服务层按明细重算** |
| `total_amount` | decimal(12,2) | 明细金额合计，**服务层按明细重算** |
| `inbound_time` | datetime | 入库时间，留空取当前时间 |
| `operator` | varchar(50) | 经办人，留空取当前登录用户姓名 |
| `remark` | varchar(500) | 备注 |
| 审计字段 + `deleted` | — | 来自 `BaseEntity`（逻辑删除） |

`inbound_item`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | bigint | 主键 |
| `inbound_id` | bigint | 所属入库单 `inbound_order.id` |
| `product_id` | bigint | 手办 `figure_product.id` |
| `product_name` / `product_code` | varchar | 下单时的**快照**，手办改名 / 下架后历史单仍显示当时信息 |
| `quantity` | int | 入库数量，> 0 |
| `unit_price` | decimal(10,2) | 单价，可空 |
| `amount` | decimal(12,2) | 小计 = 数量 × 单价，服务层计算 |
| `remark` / `sort_no` | — | 明细备注、排序号 |

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/api/inbound/page` | 分页查询（单号/供应商/经办人/手办名称模糊，仓库/类型/状态/日期区间过滤） | 登录 |
| GET | `/api/inbound/{id}` | 详情（含明细） | 登录 |
| GET | `/api/inbound/summary` | 统计卡（待入库数 + 本月单数/件数/金额） | 登录 |
| GET | `/api/inbound/dicts` | 入库类型 / 状态字典 | 登录 |
| POST | `/api/inbound` | 新增（单号可留空自动生成） | 管理员/主管 |
| PUT | `/api/inbound/{id}` | 编辑（仅待入库；明细整体替换） | 管理员/主管 |
| DELETE | `/api/inbound/{id}` | 删除（仅待入库；主表 + 明细一起逻辑删除） | 管理员/主管 |
| PUT | `/api/inbound/{id}/confirm` | 确认入库（D → C） | 管理员/主管 |
| PUT | `/api/inbound/{id}/cancel` | 作废（D/C → X） | 管理员/主管 |

详细入参 / 出参 / 错误码见契约文档 §9（INB-01~09）。

设计说明：

- **为什么拆主表 + 明细**：一次到货通常包含多款手办，塞进主表只能记成多条互不关联的记录，或者把商品名拼成一坨文本，无法统计单品入库量。
- **合计为什么后端算**：`totalQuantity` / `totalAmount` / `amount` 一律按明细重算，忽略前端传值，避免列表数字与明细对不上。
- **确认 / 作废为什么用 `LambdaUpdateWrapper`**：`supplier` / `area_id` / `shelf_id` / `remark` 标了 `FieldStrategy.ALWAYS`（编辑时要能清空），如果用空实体 `updateById`，这些列会被一并写成 `NULL`——所以只 `set` 状态列。
- **库存联动**：目前没有 `stock` 库存表，确认入库只改单据状态、不累加库存；库存表落地后在 `InboundService.confirm` 内补扣加逻辑。
- **初始数据**：表为空时 `InboundDataInitializer` 写入 4 条演示单（3 已入库 / 1 待入库）+ 6 条明细，受 `app.init-inbound.enabled` 控制；执行顺序 仓库(5) → 区域(10) → 货架(20) → 手办(30) → 入库单(40)。

### 货架档案 `shelf`



库存管理 → 仓库设置 → 货架管理 页面维护的数据，一条记录 = 某个仓库下的一个货架。

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | bigint | 主键 |
| `shelf_code` | varchar(50) | 货架编号，如 `01-1`；**同一仓库 + 未删除**范围内唯一 |
| `warehouse_id` | bigint | 所属仓库 `warehouse.id` |
| `area_id` | bigint | 所属区域 `area.id`，**可空**（不填表示未分区）；填了必须与 `warehouse_id` 同仓库 |
| `location` | varchar(100) | 库位信息，如 `01区-1层`，可空 |
| `status` | char(1) | `A` 正常 / `B` 维护中 / `D` 停用 |
| `remark` | varchar(255) | 状态描述 / 备注 |
| `sort_no` | int | 排序号，越小越靠前 |
| 审计字段 + `deleted` | — | 来自 `BaseEntity`（逻辑删除） |

表为空时 `ShelfDataInitializer` 会按仓库代码（3101 / 3102）关联插入 3 条初始货架，受 `app.init-shelf.enabled` 控制（默认开启，只填空表；找不到对应仓库的种子会跳过）。

设计说明：

- **为什么 `area_id` 可空**：货架先于区域落库，已有数据没有区域；给可空列后老数据不用洗，新货架可以在页面上选「所属区域」。填写时 `ShelfService` 会校验区域存在且与货架同仓库，避免「3101 的货架挂在 3301 的区域下」。
- **为什么编号只在同一仓库内唯一**：不同城市的仓库都会用 `01-1` 这种短编号，全局唯一不现实；因此不建 UNIQUE 索引（逻辑删除还会占用索引），由 `ShelfService` 在「同仓库 + 未删除」范围内校验。
- **删除保护**：仓库下还有未删除货架或区域时，删除仓库会被拦下并提示先删下层数据（`1000`），避免出现指向不存在仓库的货架/区域。

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/api/shelf/page` | 分页查询（编号/库位信息模糊，仓库/区域/状态过滤） | 登录 |
| GET | `/api/shelf/{id}` | 详情 | 登录 |
| POST | `/api/shelf` | 新增（同仓库编号唯一） | 管理员/主管 |
| PUT | `/api/shelf/{id}` | 编辑（可换仓库，按新仓库校验编号） | 管理员/主管 |
| DELETE | `/api/shelf/{id}` | 删除（逻辑删除，编号可复用） | 管理员/主管 |
| PUT | `/api/shelf/{id}/status` | 修改状态（A/B/D） | 管理员/主管 |

详细入参 / 出参 / 错误码见契约文档 §9（SHELF-01~06）。

## ACG 作品目录模块（module/acg）

左侧菜单「目录维护 → 作品目录及维护」。定位是**主数据 + 选品分析**：

1. 维护作品档案，供商品、订单等业务表作为下拉选项引用（`/api/acg/options`）；
2. 每次录入热度写一条快照，形成趋势线，用于判断「在涨还是在跌」；
3. 用热度、销量、趋势三个信号算出潜力分，配合四象限给出「哪些作品更值得投入」的结论。

### 数据模型

`acg_work`（作品档案）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `work_code` | varchar(50) | 作品编号，如 `ACG-001`；未删除记录中唯一 |
| `name` / `original_name` | varchar(120 / 160) | 中文名 / 原名 |
| `work_type` | varchar(20) | `ANIME` / `MANGA` / `GAME` / `NOVEL` / `MOVIE` / `OTHER` |
| `ip_owner` / `studio` / `author` | varchar(100) | 版权方 / 制作公司 / 作者 |
| `release_year` | int | 首播或发行年份 |
| `release_status` | varchar(20) | `ONGOING` 连载中 / `FINISHED` 已完结 / `UPCOMING` 待播出 |
| `heat_score` | int | 当前热度 0~100 |
| `monthly_sales` / `total_sales` | int | 近 30 天销量 / 累计销量（件） |
| `tags` | varchar(255) | 标签，逗号分隔 |
| `cover_image` | varchar(255) | 封面 URL，`/uploads/acg/...` |
| `heat_updated_at` | datetime | 热度最近更新时间 |
| `status` | tinyint | 1 可选（进入下拉）/ 0 停用 |

`acg_heat_log`（热度快照）：`work_id` + `record_date` + `heat_score` + `monthly_sales` + `remark`，同一作品同一天只保留一条（重复录入覆盖）。

### 潜力分与四象限

```
潜力分 = 热度 × 45% + 销量分 × 35% + 趋势分 × 20%
  销量分 = 本作品近 30 天销量 ÷ 全库最高月销量 × 100
  趋势分 = 50 + 热度环比涨跌幅(%) × 2（夹在 0~100）
等级：S ≥ 75 主推 / A ≥ 62 备货 / B ≥ 48 跟进 / C < 48 谨慎
```

四象限以「全库平均热度 × 平均月销」为十字线，分为 `STAR` 双高主推、`HEAT_ONLY` 高热度待转化、`SALES_ONLY` 低热度闷声卖货、`LOW` 双低谨慎。系数与阈值集中在 `AcgPotentialCalculator`，改口径只改这一个类。

### 接口

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/api/acg/page` | 分页查询（名称/原名/编号/标签模糊，类型/状态/年份/热度区间过滤） | 登录 |
| GET | `/api/acg/{id}` | 详情 | 登录 |
| GET | `/api/acg/{id}/trend` | 热度趋势（默认 90 天，可传 days） | 登录 |
| GET | `/api/acg/analysis` | 选品分析：KPI + 排行 + 四象限 + 类型分布 + 结论 | 登录 |
| GET | `/api/acg/options` | 下拉选项（仅启用作品，按热度倒序） | 登录 |
| GET | `/api/acg/types` | 类型与连载状态字典 | 登录 |
| POST | `/api/acg` | 新增（同类型下不可重名，编号唯一） | 管理员/主管 |
| PUT | `/api/acg/{id}` | 编辑 | 管理员/主管 |
| DELETE | `/api/acg/{id}` | 删除（逻辑删除，快照一并失效） | 管理员/主管 |
| PUT | `/api/acg/{id}/status` | 启用 / 停用 | 管理员/主管 |
| POST | `/api/acg/{id}/heat` | 录入热度（同日覆盖，同步当前值） | 管理员/主管 |
| POST | `/api/acg/upload` | 上传封面，落盘 `uploads/acg` | 登录 |

表为空时 `AcgDataInitializer` 写入 14 部作品 + 每部 3 条热度快照（60 天前 / 30 天前 / 今天），受 `app.init-acg.enabled` 控制（默认开启，只填空表）。**演示数据里的热度与销量是估算值**，实际使用请在页面上按真实情况录入。

详细入参 / 出参 / 错误码见契约文档 §10。

## 厂商目录模块（module/manufacturer）

左侧菜单「目录维护 → 厂商目录维护」。定位是**主数据**：维护手办 / 景品厂商（万代、BANPRESTO 眼镜厂、MegaHouse…）的厂牌 Logo、公司名称、创立时间、国家地区，供商品、入库等业务作为「厂商」下拉选项引用；停用后档案保留但不再出现在下拉中。

### 数据模型

`manufacturer`：

| 字段 | 类型 | 说明 |
|---|---|---|
| `name` | varchar(120) | 公司 / 厂牌名称；未删除记录中唯一 |
| `logo` | varchar(255) | 公司厂牌 Logo URL，`/uploads/manufacturer/...` |
| `founded_date` | date | 创立时间 |
| `country` | varchar(50) | 国家 / 地区，如 日本 / 中国香港 / 美国 |
| `remark` | varchar(500) | 备注 |
| `status` | tinyint | 1 启用（进入下拉）/ 0 停用 |

> 名称不建 UNIQUE：表为逻辑删除，唯一性由 Service 在未删除记录内校验，避免删掉后同名厂商建不回来。编辑用 `LambdaUpdateWrapper` 显式 set 每一列，保证「清空 Logo / 国家 / 备注」能真正落库。

### 接口

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/api/manufacturer/page` | 分页查询（名称/国家地区模糊，国家地区/状态过滤，多种排序） | 登录 |
| GET | `/api/manufacturer/{id}` | 详情 | 登录 |
| GET | `/api/manufacturer/summary` | 统计：总数 / 启用 / 停用 / 覆盖国家地区数 | 登录 |
| GET | `/api/manufacturer/countries` | 国家地区筛选项（去重升序） | 登录 |
| GET | `/api/manufacturer/options` | 下拉选项（仅启用，按名称升序） | 登录 |
| POST | `/api/manufacturer` | 新增（名称唯一） | 管理员/主管 |
| PUT | `/api/manufacturer/{id}` | 编辑（可清空 Logo / 国家 / 备注） | 管理员/主管 |
| DELETE | `/api/manufacturer/{id}` | 删除（逻辑删除） | 管理员/主管 |
| PUT | `/api/manufacturer/{id}/status` | 启用 / 停用 | 管理员/主管 |
| POST | `/api/manufacturer/upload` | 上传厂牌 Logo，落盘 `uploads/manufacturer` | 登录 |

表为空时 `ManufacturerDataInitializer` 写入 13 家知名厂商，受 `app.init-manufacturer.enabled` 控制（默认开启，只填空表）。演示数据的创立时间按年份占位，可在页面上改成官方信息。

详细入参 / 出参 / 错误码见契约文档 §10.6~10.8（MFG-01~MFG-10）。

---

## 个人消息模块（module/message）

入口在**右上角用户菜单 → 个人消息**，不占左侧菜单。点开后是右侧抽屉，定位是站内消息中心：

1. 集中承接审批、订单、库存、系统四类提醒（`msgType` 是可扩展的 varchar，加类型只改枚举不改表）；
2. 未读数由 `GET /api/message/summary` 一次性给出，头像红点、菜单徽标与页面统计共用，前端 60 秒轮询；
3. 每条消息带 `bizUrl`，点「去处理」会标记为已处理并直接跳到对应业务页面，形成「看到 → 去办」的闭环。

### 数据模型 `sys_message`

| 字段 | 类型 | 说明 |
|---|---|---|
| `title` / `content` | varchar(120 / 1000) | 标题 / 正文 |
| `msg_type` | varchar(20) | `APPROVAL` 审批 / `ORDER` 订单 / `INVENTORY` 库存 / `SYSTEM` 系统 |
| `biz_type` / `biz_id` | varchar(30) / bigint | 来源单据类型与 ID |
| `biz_url` | varchar(255) | 「去处理」跳转的前端路由，为空表示纯通知 |
| `receiver_id` | bigint | 接收人（`sys_user.id`），所有接口只查当前登录人 |
| `sender_id` / `sender_name` | bigint / varchar(50) | 发送人及其姓名快照 |
| `priority` | char(1) | `N` 普通 / `U` 紧急 |
| `status` | char(1) | `U` 未读 / `R` 已读 / `H` 已处理 |
| `read_time` / `handle_time` | datetime | 首次读取时间 / 处理完成时间 |
| `handle_remark` | varchar(255) | 处理备注 |

### 状态流转

```
U 未读 ──查看详情 / 标记已读──> R 已读 ──去处理──> H 已处理（终态）
```

已处理是终态，前端对 `handled=true` 的消息不再显示「去处理」，避免重复办同一件事。

### 接口

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/api/message/page` | 分页查询我的消息（类型 / 状态 / 优先级 / 关键字） | 登录 |
| GET | `/api/message/summary` | 未读统计（总数 / 未读 / 紧急未读 / 已处理 / 按类型） | 登录 |
| GET | `/api/message/types` | 消息类型字典 | 登录 |
| GET | `/api/message/{id}` | 详情（未读自动置为已读） | 登录 |
| PUT | `/api/message/{id}/read` | 标记已读（幂等） | 登录 |
| PUT | `/api/message/read-all` | 全部标为已读，可只处理某一类型 | 登录 |
| PUT | `/api/message/{id}/handle` | 处理完成，可带处理备注 | 登录 |
| DELETE | `/api/message/{id}` | 删除（逻辑删除） | 登录 |
| POST | `/api/message` | 手工发消息 | 管理员 / 主管 |

### 给其他模块推消息

审批流、订单、库存预警不要直接写表，统一调用：

```java
messageService.push(MessagePush.ofBiz(
        receiverId, "入库单待审批", "单号 RK20261106001，共 86 件",
        MessageDict.TYPE_APPROVAL, "INBOUND", inboundId, "/stock/inbound"));
```

`push` 供内部流程调用，不做角色校验，只兜底非法类型与空标题（标题为空返回 `1003`）。
表为空时 `MessageDataInitializer` 会给管理员生成 8 条演示消息（4 未读 / 2 已读 / 2 已处理），受 `app.init-message.enabled` 控制，只填空表。

详细入参 / 出参 / 错误码见契约文档 §11。


## 工作台与个人待办模块（module/work）

页面：**工作台**（`/home`）。首页按角色分流：

| 角色 | 登录后首页 | 左侧菜单 |
|---|---|---|
| `GM` 总经理、`SALES_MANAGER` 销售主管 | `/dashboard` 数据概览 | 工作台 + 数据概览 |
| `ADMIN` 管理员 | `/home` 工作台 | 工作台 + 数据概览（可查看看板，方便核对数据） |
| 其他主管 / 专员 | `/home` 工作台 | 只有工作台 |

分流在前端路由守卫里做（`front/src/utils/role.ts` 的 `homePathFor` / `canViewDashboard`）：无权角色直接访问 `/dashboard` 会被 `replace` 回 `/home`，浏览器后退也不会反复弹回。后端不拦 `/dashboard`（它只是前端页面），页面用到的数据接口本身已有各自权限。

工作台一屏由三块组成，数据来自两个模块：

1. **今日工作计划**（`work_todo`，本模块）：快速输入框回车即建、勾选完成、悬停删除，顶部圆环是今日完成率；
2. **待办事项**（`sys_message`）：最近 20 条里筛出「还没处理」的（未读 + 已读未处理），点「去处理」跳业务页；
3. **常用入口**：手办、入库、货架、订单、物流查询、个人消息。

### 数据模型 `work_todo`

| 字段 | 类型 | 说明 |
|---|---|---|
| `user_id` | bigint | 归属员工（`sys_user.id`），所有接口只操作当前登录人 |
| `title` / `content` | varchar(120 / 500) | 标题 / 补充说明 |
| `plan_date` | date | 计划日期，工作台只展示当天，昨天的未完成计入「逾期」 |
| `priority` | char(1) | `H` 高 / `N` 普通 / `L` 低 |
| `status` | char(1) | `P` 待办 / `D` 已完成 |
| `finish_time` | datetime | 完成时间；状态改回 `P` 时清空 |
| `sort_no` | int | 排序号，越小越靠前 |

表为空时 `TodoDataInitializer` 给管理员写 6 条演示待办（含 1 条昨天未完成，用来演示逾期），受 `app.init-todo.enabled` 控制，只填空表。

### 与「个人消息」的分工

| | `sys_message` | `work_todo` |
|---|---|---|
| 谁产生 | 别人推送（审批 / 订单 / 库存 / 系统） | 自己记下来 |
| 结束状态 | `H` 已处理 | `D` 已完成 |
| 展示位置 | 工作台右栏 + 右上角消息抽屉 | 工作台左栏 |

两者并排展示但互不生成：消息点「去处理」不会自动建待办，避免系统臆测「谁该干什么」。

### 接口

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/api/todo/today` | 今日待办 + 统计（总数 / 完成 / 未完成 / 逾期 / 完成率） | 登录 |
| GET | `/api/todo/page` | 分页查询我的待办（日期区间 / 状态 / 优先级 / 关键字） | 登录 |
| GET | `/api/todo/dicts` | 优先级与状态字典 | 登录 |
| GET | `/api/todo/{id}` | 详情 | 登录 |
| POST | `/api/todo` | 新增（不传日期默认今天） | 登录 |
| PUT | `/api/todo/{id}` | 编辑（不含状态） | 登录 |
| PUT | `/api/todo/{id}/status` | 勾选 / 取消完成 | 登录 |
| DELETE | `/api/todo/{id}` | 删除（逻辑删除） | 登录 |

两个实现注意点：

- **`content` / `finish_time` 必须加 `@TableField(updateStrategy = ALWAYS)`**：MyBatis-Plus 默认跳过 null 字段，「清空说明」和「取消完成后清掉完成时间」会被静默忽略。
- **排序在 Service 里统一**：未完成在前 → 优先级 → `sortNo` → 新建的在前（`id` 倒序），保证刚记下的事不会沉到列表底部。

详细入参 / 出参 / 错误码见契约文档 §12。
