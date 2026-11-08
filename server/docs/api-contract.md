# 接口契约（API Contract）

> 适用代码：`server/`（Spring Boot 4.1.2 + MyBatis-Plus）
> 文档版本：**v2.1**
> 最后更新：2026-11-08
> 在线调试：`http://localhost:8080/swagger-ui.html`（字段中文说明与本文一致，本文额外记录了权限、错误码与业务规则）

本文档是前后端之间的**唯一约定来源**。前端按本文对接，后端按本文实现；两者不一致时，以本文为准并立即修正代码或文档。

---

## 0. 维护规则（改后端必读）

**任何触及对外契约的代码改动，必须在同一次提交里更新本文件**，具体三处：

1. §2 接口总览表（增 / 删 / 改行）
2. §3~§11 对应接口详情（共享结构、入参、出参、权限、错误码、示例）
3. §14 变更记录（新增一行，并把顶部「文档版本 / 最后更新」一起改掉）

触发条件（命中任意一条就要改文档）：

- 新增 / 删除 / 修改 `@GetMapping`、`@PostMapping`、`@PutMapping`、`@DeleteMapping`
- 修改 DTO / VO 的字段名、类型、必填性、默认值
- 修改校验注解（`@NotBlank`、`@Min`、`@Size` …）导致报错文案或边界变化
- 修改 `WebMvcConfig` 白名单、`AuthInterceptor` 鉴权方式
- 修改 Service 层的权限判定（`requireManager` / `requireAdmin`）
- 修改 `ResultCode` 枚举或业务异常的错误码

版本号规则：

| 变更类型 | 版本动作 | 例子 |
|---|---|---|
| 新增接口 / 新增出参字段（可向后兼容） | `v1.x` +1 | 给 `UserVO` 加 `phone` |
| 删除接口 / 字段改名 / 类型变更 / 必填变严格 | **`v2.0`（破坏性）** | `destProvince` 改名 `destinationProvince` |

> 新增可选入参、放宽校验、补充错误码属于兼容变更，走 `v1.x`。

---

## 1. 通用约定

### 1.1 地址与鉴权

| 项 | 约定 |
|---|---|
| Base URL | `http://localhost:8080`（前端通过 Vite 代理 `/api` → 该地址） |
| 路径前缀 | 所有业务接口以 `/api` 开头 |
| 鉴权方式 | 请求头 `Authorization: Bearer {token}`，token 由 `AUTH-01` 登录返回 |
| 令牌有效期 | 28800 秒（8 小时），过期需重新登录 |
| 免登录白名单 | `POST /api/auth/login`、`GET /api/system/health`、`GET /api/system/db`，其余接口一律需要令牌 |
| 跨域 | 允许来源 `http://localhost:5173`、`http://127.0.0.1:5173` |

未带令牌 / 令牌过期 / 令牌非法时，**HTTP 状态码仍为 200**，响应体 `code = 401`，`message` 分别为「请先登录」「登录已过期，请重新登录」「登录凭证无效，请重新登录」。

### 1.2 统一响应结构

所有接口（含异常）返回 HTTP 200，前端只判断响应体里的 `code`。

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {}
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| code | int | 业务状态码，见 §1.3 |
| message | string | 提示文案，可直接展示给用户 |
| data | any | 业务数据；失败时为 `null` |

**`data` 为 `null` 时该字段会被省略**（全局配置 `default-property-inclusion: non_null`），例如无返回值的写操作实际响应是：

```json
{ "code": 200, "message": "保存成功" }
```

同理，出参中值为 `null` 的业务字段（如未设置 `destCity`、未登录过的 `lastLoginTime`）**不会出现在 JSON 里**，前端取值必须容忍字段缺失。`boolean` 基本类型（`cheapest` / `fastest`）不受影响，始终出现。

### 1.3 业务状态码

| code | 含义 | 出现场景 |
|---|---|---|
| 200 | 成功 | 正常返回 |
| 400 | 请求参数错误 | 缺少必填 query 参数、路径变量类型不对、JSON 体格式错误 |
| 401 | 未登录 / 登录已过期 | 无令牌、令牌失效、账号已被删除 |
| 403 | 没有权限 | 角色不足（如员工访问员工列表） |
| 404 | 资源不存在 | 访问了不存在的接口路径 |
| 405 | 请求方法不支持 | 用错 GET / POST |
| 500 | 系统内部错误 | 未预期异常，`message` 固定为「系统内部错误」 |
| 1000 | 业务处理失败 | 具体业务规则拦截，`message` 可直接展示 |
| 1001 | 数据不存在 | 按 id 查不到记录，或已被逻辑删除 |
| 1002 | 数据已存在 | 账号重复、同一路线重复定价 |
| 1003 | 参数校验失败 | `@Valid` 校验不通过，多条错误用「；」拼接 |

### 1.4 分页约定

分页接口入参统一为 `page`（从 **1** 开始）+ `size`，出参统一为 `PageResult<T>`：

| 字段 | 类型 | 说明 |
|---|---|---|
| records | T[] | 当前页数据 |
| total | long | 总条数 |
| page | long | 当前页码 |
| size | long | 每页条数 |
| pages | long | 总页数 |

各接口的 `size` 上限不同：员工列表 1~200，物流价格列表 1~500。

### 1.5 字段类型约定

| 类型 | 约定 |
|---|---|
| 金额 | `BigDecimal`，单位「元」，报价值保留 2 位小数（四舍五入） |
| 重量 | `BigDecimal`，单位 kg，最多 2 位小数；**例外**：商品模块（§8）的「带盒重量」`weightWithBox` 为 `Integer`，单位 **g（克）** |
| 时间 | `LocalDateTime` 序列化为 ISO-8601 字符串，如 `2026-11-01T20:03:11`（可能带毫秒）。**注意不是** `yyyy-MM-dd HH:mm:ss`，前端展示需自行把 `T` 换成空格 |
| 状态 | `Integer`，`1` 启用 / `0` 停用；**例外**：仓库模块（§9）用单字符 `A` 使用中 / `C` 盘库中 / `D` 停用中，退货类型 `isReturn` 用 `N` / `Y` |
| 热度 | `Integer`，`0`~`100` 的整数；ACG 作品模块（§10）的 `heatScore` 是当前值，历史值存 `acg_heat_log` 并按日一点 |
| 逻辑删除 | 删除接口均为逻辑删除，数据仍在库中；被删除记录的 id 再次查询返回 `1001` |

### 1.6 角色与权限矩阵

角色分三层，**后端所有权限判断只认层级**（`UserRole.level`），不再逐个角色写 if；新增角色只要在枚举里加一行并指定层级，权限自动生效。

| 层级 | level | 包含角色 | 能做什么 |
|---|:--:|---|---|
| 管理级 | 3 | `ADMIN` 管理员、`GM` 总经理 | 员工账号增删改 + 全部业务 |
| 主管级 | 2 | `FINANCE_MANAGER` 财务主管、`SALES_MANAGER` 销售主管、`LOGISTICS_MANAGER` 物流主管、`HR_MANAGER` 人事主管（旧版 `MANAGER` 同层） | 维护业务数据：商品、仓库、区域、货架、入库单、物流价格、ACG 作品、发消息 |
| 执行级 | 1 | 对应的 `*_STAFF` 专员（旧版 `STAFF` 同层） | 只读业务数据 |

| 能力 | 管理级 | 主管级 | 执行级 |
|---|:--:|:--:|:--:|
| 登录、取当前用户、退出 | ✓ | ✓ | ✓ |
| 系统字典 | ✓ | ✓ | ✓ |
| 员工列表 / 详情 | ✓ | ✓ | ✗ |
| 员工增删改 / 重置密码 / 启停 | ✓ | ✗ | ✗ |
| 物流价格查询、运费报价、物流字典 | ✓ | ✓ | ✓ |
| 物流价格增删改 / 批量设置 / 启停 | ✓ | ✓ | ✗ |
| 手办商品查询、详情、图片访问 | ✓ | ✓ | ✓ |
| 手办商品增删改 / 启停 | ✓ | ✓ | ✗ |
| 上传商品图片 | ✓ | ✓ | ✓ |
| 仓库查询、详情 | ✓ | ✓ | ✓ |
| 仓库增删改 / 改状态 | ✓ | ✓ | ✗ |
| 货架查询、详情 | ✓ | ✓ | ✓ |
| 货架增删改 / 改状态 | ✓ | ✓ | ✗ |
| 区域查询、详情 | ✓ | ✓ | ✓ |
| 区域增删改 / 改状态 | ✓ | ✓ | ✗ |
| 入库记录查询 / 详情 / 统计 / 字典 | ✓ | ✓ | ✓ |
| 入库单增删改 / 确认入库 / 作废 | ✓ | ✓ | ✗ |
| ACG 作品查询、详情、趋势、下拉选项、选品分析 | ✓ | ✓ | ✓ |
| ACG 作品增删改 / 启停 | ✓ | ✓ | ✗ |
| 录入 ACG 作品热度 | ✓ | ✓ | ✗ |
| 上传 ACG 作品封面 | ✓ | ✓ | ✓ |
| 个人消息查询 / 详情 / 已读 / 处理 / 删除 | ✓ | ✓ | ✓ |
| 个人待办查询 / 新增 / 编辑 / 勾选完成 / 删除（只限本人） | ✓ | ✓ | ✓ |
| 给他人发送消息 | ✓ | ✓ | ✗ |

> 前端不重复写角色清单：`GET /api/system/dicts` 的每个角色都带 `level`，登录用户信息里也带 `roleLevel`，页面统一按 `roleLevel >= 2` 判断按钮是否可点。
> **首页归属（前端路由）**：`/dashboard` 数据概览只作为 `GM` 总经理、`SALES_MANAGER` 销售主管的首页；其他角色（含 `ADMIN` 管理员、各部门主管与专员）登录后进入 `/home` 工作台。管理员仍可从菜单打开数据概览，其余角色直接访问 `/dashboard` 会被路由守卫送回 `/home`。
> **角色在员工录入时必填**（`USER-03`）：数据库 `sys_user.role` 为 `NOT NULL` 且无默认值，后端按 `UserRole` 枚举校验，空值返回 `1003 请选择角色`，非法值返回 `1003 角色不合法`。

保护规则（违反返回 `1000`）：不能删除自己、不能停用自己、不能改自己的角色、系统必须保留至少一个启用状态的管理级账号（管理员或总经理）。

---

## 2. 接口总览

共 **95** 个接口。「权限」列中「登录用户」= 任意已登录账号。

| ID | 模块 | 方法 | 路径 | 名称 | 权限 | 入参 | 出参 |
|---|---|---|---|---|---|---|---|
| SYS-01 | 系统 | GET | `/api/system/health` | 健康检查 | 免登录 | — | `Map` |
| SYS-02 | 系统 | GET | `/api/system/db` | 数据库连通检查 | 免登录 | — | `Map` |
| SYS-03 | 系统 | GET | `/api/system/dicts` | 系统字典 | 登录用户 | — | `Map` |
| AUTH-01 | 认证 | POST | `/api/auth/login` | 登录 | 免登录 | `LoginRequest` | `LoginVO` |
| AUTH-02 | 认证 | GET | `/api/auth/me` | 当前登录用户 | 登录用户 | — | `UserVO` |
| AUTH-03 | 认证 | POST | `/api/auth/logout` | 退出登录 | 登录用户 | — | — |
| USER-01 | 员工 | GET | `/api/system/users` | 分页查询员工 | 主管级及以上 | `UserQuery` | `PageResult<UserVO>` |
| USER-02 | 员工 | GET | `/api/system/users/{id}` | 员工详情 | 主管级及以上 | `id` | `UserVO` |
| USER-03 | 员工 | POST | `/api/system/users` | 新增员工 | 管理级 | `UserSaveRequest` | `Long` |
| USER-04 | 员工 | PUT | `/api/system/users/{id}` | 编辑员工 | 管理级 | `id` + `UserSaveRequest` | — |
| USER-05 | 员工 | DELETE | `/api/system/users/{id}` | 删除员工 | 管理级 | `id` | — |
| USER-06 | 员工 | PUT | `/api/system/users/{id}/password` | 重置密码 | 管理级 | `id` + `PasswordResetRequest` | — |
| USER-07 | 员工 | PUT | `/api/system/users/{id}/status` | 启用 / 停用员工 | 管理级 | `id` + `status` | — |
| LOG-01 | 物流 | GET | `/api/logistics/prices` | 分页查询物流价格 | 登录用户 | `LogisticsPriceQuery` | `PageResult<LogisticsPriceVO>` |
| LOG-02 | 物流 | GET | `/api/logistics/prices/{id}` | 价格详情 | 登录用户 | `id` | `LogisticsPriceVO` |
| LOG-03 | 物流 | POST | `/api/logistics/prices` | 新增价格 | 主管级及以上 | `LogisticsPriceSaveRequest` | `Long` |
| LOG-04 | 物流 | PUT | `/api/logistics/prices/{id}` | 编辑价格 | 主管级及以上 | `id` + `LogisticsPriceSaveRequest` | — |
| LOG-05 | 物流 | DELETE | `/api/logistics/prices/{id}` | 删除价格 | 主管级及以上 | `id` | — |
| LOG-06 | 物流 | PUT | `/api/logistics/prices/{id}/status` | 启用 / 停用价格 | 主管级及以上 | `id` + `status` | — |
| LOG-07 | 物流 | POST | `/api/logistics/prices/batch` | 批量保存全国价 | 主管级及以上 | `BatchPriceRequest` | `Integer` |
| LOG-08 | 物流 | GET | `/api/logistics/quote` | 运费报价查询 | 登录用户 | `QuoteQuery` | `QuoteResultVO` |
| LOG-09 | 物流 | GET | `/api/logistics/dicts` | 物流字典 | 登录用户 | — | `Map` |
| PRO-01 | 商品 | GET | `/api/product/page` | 分页查询手办商品 | 登录用户 | `FigureProductQuery` | `PageResult<FigureProductVO>` |
| PRO-02 | 商品 | GET | `/api/product/{id}` | 商品详情 | 登录用户 | `id` | `FigureProductVO` |
| PRO-03 | 商品 | POST | `/api/product` | 新增商品 | 主管级及以上 | `FigureProductSaveRequest` | `Long` |
| PRO-04 | 商品 | PUT | `/api/product/{id}` | 编辑商品 | 主管级及以上 | `id` + `FigureProductSaveRequest` | — |
| PRO-05 | 商品 | DELETE | `/api/product/{id}` | 删除商品 | 主管级及以上 | `id` | — |
| PRO-06 | 商品 | PUT | `/api/product/{id}/status` | 启用 / 停用商品 | 主管级及以上 | `id` + `status` | — |
| PRO-07 | 商品 | POST | `/api/product/upload` | 上传商品图片 | 登录用户 | `file`(Multipart) | `String`(URL) |
| WH-01 | 仓库 | GET | `/api/warehouse/page` | 分页查询仓库 | 登录用户 | `WarehouseQuery` | `PageResult<WarehouseVO>` |
| WH-02 | 仓库 | GET | `/api/warehouse/{id}` | 仓库详情 | 登录用户 | `id` | `WarehouseVO` |
| WH-03 | 仓库 | POST | `/api/warehouse` | 新增仓库 | 主管级及以上 | `WarehouseSaveRequest` | `Long` |
| WH-04 | 仓库 | PUT | `/api/warehouse/{id}` | 编辑仓库 | 主管级及以上 | `id` + `WarehouseSaveRequest` | — |
| WH-05 | 仓库 | DELETE | `/api/warehouse/{id}` | 删除仓库 | 主管级及以上 | `id` | — |
| WH-06 | 仓库 | PUT | `/api/warehouse/{id}/status` | 修改仓库状态 | 主管级及以上 | `id` + `status` | — |
| SHELF-01 | 货架 | GET | `/api/shelf/page` | 分页查询货架 | 登录用户 | `ShelfQuery` | `PageResult<ShelfVO>` |
| SHELF-02 | 货架 | GET | `/api/shelf/{id}` | 货架详情 | 登录用户 | `id` | `ShelfVO` |
| SHELF-03 | 货架 | POST | `/api/shelf` | 新增货架 | 主管级及以上 | `ShelfSaveRequest` | `Long` |
| SHELF-04 | 货架 | PUT | `/api/shelf/{id}` | 编辑货架 | 主管级及以上 | `id` + `ShelfSaveRequest` | — |
| SHELF-05 | 货架 | DELETE | `/api/shelf/{id}` | 删除货架 | 主管级及以上 | `id` | — |
| SHELF-06 | 货架 | PUT | `/api/shelf/{id}/status` | 修改货架状态 | 主管级及以上 | `id` + `status` | — |
| AREA-01 | 区域 | GET | `/api/area/page` | 分页查询区域 | 登录用户 | `AreaQuery` | `PageResult<AreaVO>` |
| AREA-02 | 区域 | GET | `/api/area/{id}` | 区域详情 | 登录用户 | `id` | `AreaVO` |
| AREA-03 | 区域 | POST | `/api/area` | 新增区域 | 主管级及以上 | `AreaSaveRequest` | `Long` |
| AREA-04 | 区域 | PUT | `/api/area/{id}` | 编辑区域 | 主管级及以上 | `id` + `AreaSaveRequest` | — |
| AREA-05 | 区域 | DELETE | `/api/area/{id}` | 删除区域 | 主管级及以上 | `id` | — |
| AREA-06 | 区域 | PUT | `/api/area/{id}/status` | 修改区域状态 | 主管级及以上 | `id` + `status` | — |
| INB-01 | 入库 | GET | `/api/inbound/page` | 分页查询入库单 | 登录用户 | `InboundQuery` | `PageResult<InboundOrderVO>` |
| INB-02 | 入库 | GET | `/api/inbound/{id}` | 入库单详情（含明细） | 登录用户 | `id` | `InboundOrderVO` |
| INB-03 | 入库 | GET | `/api/inbound/summary` | 入库记录统计 | 登录用户 | — | `InboundSummaryVO` |
| INB-04 | 入库 | GET | `/api/inbound/dicts` | 入库类型 / 状态字典 | 登录用户 | — | `Map` |
| INB-05 | 入库 | POST | `/api/inbound` | 新增入库单 | 主管级及以上 | `InboundSaveRequest` | `Long` |
| INB-06 | 入库 | PUT | `/api/inbound/{id}` | 编辑入库单 | 主管级及以上 | `id` + `InboundSaveRequest` | — |
| INB-07 | 入库 | DELETE | `/api/inbound/{id}` | 删除入库单 | 主管级及以上 | `id` | — |
| INB-08 | 入库 | PUT | `/api/inbound/{id}/confirm` | 确认入库 | 主管级及以上 | `id` | — |
| INB-09 | 入库 | PUT | `/api/inbound/{id}/cancel` | 作废入库单 | 主管级及以上 | `id` | — |
| ACG-01 | ACG | GET | `/api/acg/page` | 分页查询作品 | 登录用户 | `AcgWorkQuery` | `PageResult<AcgWorkVO>` |
| ACG-02 | ACG | GET | `/api/acg/{id}` | 作品详情 | 登录用户 | `id` | `AcgWorkVO` |
| ACG-03 | ACG | POST | `/api/acg` | 新增作品 | 主管级及以上 | `AcgWorkSaveRequest` | `Long` |
| ACG-04 | ACG | PUT | `/api/acg/{id}` | 编辑作品 | 主管级及以上 | `id` + `AcgWorkSaveRequest` | — |
| ACG-05 | ACG | DELETE | `/api/acg/{id}` | 删除作品 | 主管级及以上 | `id` | — |
| ACG-06 | ACG | PUT | `/api/acg/{id}/status` | 启用 / 停用作品 | 主管级及以上 | `id` + `status` | — |
| ACG-07 | ACG | POST | `/api/acg/{id}/heat` | 录入当期热度 | 主管级及以上 | `id` + `HeatRecordRequest` | — |
| ACG-08 | ACG | GET | `/api/acg/{id}/trend` | 作品热度趋势 | 登录用户 | `id` + `days` | `AcgHeatPointVO[]` |
| ACG-09 | ACG | GET | `/api/acg/analysis` | 选品分析总览 | 登录用户 | — | `AcgAnalysisVO` |
| ACG-10 | ACG | GET | `/api/acg/options` | 作品下拉选项 | 登录用户 | `keyword`（可选） | `AcgOptionVO[]` |
| ACG-11 | ACG | GET | `/api/acg/types` | 作品类型字典 | 登录用户 | — | `Map` |
| ACG-12 | ACG | POST | `/api/acg/upload` | 上传作品封面 | 登录用户 | `file`(Multipart) | `String`(URL) |
| MFG-01 | 厂商 | GET | `/api/manufacturer/page` | 分页查询厂商 | 登录用户 | `ManufacturerQuery` | `PageResult<ManufacturerVO>` |
| MFG-02 | 厂商 | GET | `/api/manufacturer/{id}` | 厂商详情 | 登录用户 | `id` | `ManufacturerVO` |
| MFG-03 | 厂商 | POST | `/api/manufacturer` | 新增厂商 | 主管级及以上 | `ManufacturerSaveRequest` | `Long` |
| MFG-04 | 厂商 | PUT | `/api/manufacturer/{id}` | 编辑厂商 | 主管级及以上 | `id` + `ManufacturerSaveRequest` | — |
| MFG-05 | 厂商 | DELETE | `/api/manufacturer/{id}` | 删除厂商 | 主管级及以上 | `id` | — |
| MFG-06 | 厂商 | PUT | `/api/manufacturer/{id}/status` | 启用 / 停用厂商 | 主管级及以上 | `id` + `status` | — |
| MFG-07 | 厂商 | GET | `/api/manufacturer/summary` | 厂商目录统计 | 登录用户 | — | `ManufacturerSummaryVO` |
| MFG-08 | 厂商 | GET | `/api/manufacturer/countries` | 国家地区筛选项 | 登录用户 | — | `String[]` |
| MFG-09 | 厂商 | GET | `/api/manufacturer/options` | 厂商下拉选项 | 登录用户 | `keyword`（可选） | `ManufacturerOptionVO[]` |
| MFG-10 | 厂商 | POST | `/api/manufacturer/upload` | 上传厂商 Logo | 登录用户 | `file`(Multipart) | `String`(URL) |
| MSG-01 | 消息 | GET | `/api/message/page` | 分页查询我的消息 | 登录用户 | `MessageQuery` | `PageResult<MessageVO>` |
| MSG-02 | 消息 | GET | `/api/message/summary` | 未读统计 | 登录用户 | — | `MessageSummaryVO` |
| MSG-03 | 消息 | GET | `/api/message/types` | 消息类型字典 | 登录用户 | — | `Array<{code,text}>` |
| MSG-04 | 消息 | GET | `/api/message/{id}` | 消息详情（自动已读） | 登录用户 | `id` | `MessageVO` |
| MSG-05 | 消息 | PUT | `/api/message/{id}/read` | 标记已读 | 登录用户 | `id` | — |
| MSG-06 | 消息 | PUT | `/api/message/read-all` | 全部标为已读 | 登录用户 | `msgType`（可选） | `Integer` |
| MSG-07 | 消息 | PUT | `/api/message/{id}/handle` | 处理消息 | 登录用户 | `id` + `MessageHandleRequest` | — |
| MSG-08 | 消息 | DELETE | `/api/message/{id}` | 删除消息 | 登录用户 | `id` | — |
| MSG-09 | 消息 | POST | `/api/message` | 发送消息 | 主管级及以上 | `MessageSendRequest` | `Long` |
| TODO-01 | 待办 | GET | `/api/todo/today` | 今日待办汇总 | 登录用户 | — | `TodayTodoVO` |
| TODO-02 | 待办 | GET | `/api/todo/page` | 分页查询我的待办 | 登录用户 | `TodoQuery` | `PageResult<TodoVO>` |
| TODO-03 | 待办 | GET | `/api/todo/dicts` | 待办优先级 / 状态字典 | 登录用户 | — | `Map` |
| TODO-04 | 待办 | GET | `/api/todo/{id}` | 待办详情 | 登录用户 | `id` | `TodoVO` |
| TODO-05 | 待办 | POST | `/api/todo` | 新增待办 | 登录用户 | `TodoSaveRequest` | `Long` |
| TODO-06 | 待办 | PUT | `/api/todo/{id}` | 编辑待办 | 登录用户 | `id` + `TodoSaveRequest` | — |
| TODO-07 | 待办 | PUT | `/api/todo/{id}/status` | 勾选 / 取消完成 | 登录用户 | `id` + `status` | — |
| TODO-08 | 待办 | DELETE | `/api/todo/{id}` | 删除待办 | 登录用户 | `id` | — |

---

## 3. 共享数据结构

多个接口复用的出参结构集中定义在这里，接口详情里只写结构名。

### 3.1 `UserVO`（员工信息）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long | 员工 ID |
| username | String | 登录账号 |
| realName | String | 员工姓名 |
| avatar | String | 头像地址，未设置为空 |
| role | String | 角色编码：`ADMIN` / `GM` / `FINANCE_MANAGER` / `FINANCE_STAFF` / `SALES_MANAGER` / `SALES_STAFF` / `LOGISTICS_MANAGER` / `LOGISTICS_STAFF` / `HR_MANAGER` / `HR_STAFF`（旧版 `MANAGER` / `STAFF`），见 §13.1 |
| roleLabel | String | 角色中文，如「财务主管」 |
| roleGroup | String | 角色板块：系统 / 管理层 / 财务 / 销售 / 物流 / 人事 / 通用，前端下拉分组用 |
| roleLevel | Integer | 权限层级：3 管理级 / 2 主管级 / 1 执行级，前端据此判断按钮是否可点 |
| status | Integer | 1 启用 / 0 停用 |
| intro | String | 员工介绍 |
| remark | String | 备注 |
| lastLoginTime | LocalDateTime | 最后登录时间，从未登录则为空 |
| createTime | LocalDateTime | 创建时间 |

> 永不返回 `password`。字段到 VO 的转换只有一个出口 `UserConverter.toVO`。

### 3.2 `LoginVO`（登录结果）

| 字段 | 类型 | 说明 |
|---|---|---|
| token | String | JWT 令牌，放入 `Authorization: Bearer {token}` |
| tokenType | String | 固定 `Bearer` |
| expiresIn | long | 有效期（秒），当前 28800 |
| user | `UserVO` | 登录用户信息，登录后无需再调 `AUTH-02` |

### 3.3 `LogisticsPriceVO`（物流价格）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long | 价格记录 ID |
| supplierCode | String | 供应商编码，见 §13.3 |
| supplierName | String | 供应商名称，如「顺丰速运」 |
| originProvince | String | 出发地-省 |
| originCity | String | 出发地-市 |
| originDistrict | String | 出发地-区/县，未填为空串 |
| originLabel | String | 出发地展示文案，如「上海市 浦东新区」 |
| destProvince | String | 目的地-省 |
| destCity | String | 目的地-市，为空表示全省统一价 |
| destLabel | String | 目的地展示文案，如「广东省 深圳市」/「广东省（全省）」 |
| zoneLabel | String | 所属运费区域，如「华南」 |
| firstWeight | BigDecimal | 首重重量（kg） |
| firstPrice | BigDecimal | 首重价格（元） |
| additionalWeight | BigDecimal | 续重单位（kg） |
| additionalPrice | BigDecimal | 续重单价（元） |
| priceLabel | String | 计价公式说明，如「首重 1kg 12 元，续重每 1kg 2 元」 |
| estimatedDays | Integer | 预计时效（天） |
| status | Integer | 1 启用 / 0 停用 |
| remark | String | 备注 |
| createTime | LocalDateTime | 创建时间 |
| updateTime | LocalDateTime | 更新时间 |

### 3.4 `QuoteResultVO`（运费报价对比）

| 字段 | 类型 | 说明 |
|---|---|---|
| originLabel | String | 出发地展示文案 |
| destLabel | String | 目的地展示文案 |
| zoneLabel | String | 目的地所属运费区域 |
| weight | BigDecimal | 本次查询重量（kg） |
| items | `QuoteItemVO[]` | 各家报价，按总价升序、同价按时效升序；无可用报价时为 `[]` |
| count | int | 报价条数 |
| lowestPrice | BigDecimal | 最低总价，无报价时为空 |
| lowestSupplier | String | 最低价供应商名称，无报价时为空 |
| fastestDays | Integer | 最短时效（天），全部无时效时为空 |
| advice | String | 一句话建议（含价格最低 / 时效最快的对比文案） |

### 3.5 `QuoteItemVO`（单个供应商报价）

| 字段 | 类型 | 说明 |
|---|---|---|
| supplierCode | String | 供应商编码 |
| supplierName | String | 供应商名称 |
| totalPrice | BigDecimal | 总价（元），2 位小数 |
| firstWeight | BigDecimal | 首重重量（kg） |
| firstPrice | BigDecimal | 首重价格（元） |
| additionalWeight | BigDecimal | 续重单位（kg） |
| additionalPrice | BigDecimal | 续重单价（元） |
| additionalUnits | BigDecimal | 超出首重后计费的档数（向上取整），未超首重为 0 |
| formula | String | 计价明细，如「首重 1kg 12.00 元 + 续重 2×1kg × 2.00 元」 |
| estimatedDays | Integer | 预计时效（天），可能为空 |
| cheapest | boolean | 是否最低价（并列最低价全部为 `true`） |
| fastest | boolean | 是否最快（并列最快全部为 `true`） |
| remark | String | 备注 |

### 3.6 `FigureProductVO`（手办商品）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long | 商品 ID |
| name | String | 商品名称（手办全称） |
| code | String | 货号 / SKU，未填为空（不出现在 JSON 中） |
| brand | String | 品牌 |
| series | String | 系列 |
| characterName | String | 角色名 |
| material | String | 材质，如 PVC/ABS |
| lengthCm | BigDecimal | 长（cm，保留 1 位小数） |
| widthCm | BigDecimal | 宽（cm） |
| heightCm | BigDecimal | 高（cm） |
| weightWithBox | Integer | **带盒重量（g，克）**，含外包装盒；这是录入时必须标注的重量口径 |
| images | String[] | 商品图片 URL 列表，3~4 张，形如 `/uploads/products/<uuid>.<ext>` |
| specLabel | String | 规格展示文案，如「28.5×18.0×30.0 cm / 带盒 1200 g」；缺字段则省略对应段 |
| remark | String | 备注 |
| status | Integer | 1 启用 / 0 停用 |
| createTime | LocalDateTime | 创建时间 |
| updateTime | LocalDateTime | 更新时间 |

> 字段到 VO 的转换只有一个出口 `FigureProductConverter.toVO`；图片在库中以逗号拼接存储，出参时拆回列表并过滤空串。

### 3.7 `AcgWorkVO`（ACG 作品）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long | 作品 ID |
| workCode | String | 作品编号，如 `ACG-001`，未填为空（不出现在 JSON 中） |
| name | String | 作品名称（中文） |
| originalName | String | 原名（日文 / 英文） |
| workType | String | 类型码，见 §13.5 |
| workTypeText | String | 类型中文，如「动画」 |
| ipOwner | String | 版权方 / 发行方 |
| studio | String | 制作公司 / 开发商 |
| author | String | 作者 / 原作 |
| releaseYear | Integer | 首播 / 发行年份 |
| releaseStatus | String | 连载状态码，见 §13.6 |
| releaseStatusText | String | 连载状态中文 |
| heatScore | Integer | 当前热度 0~100 |
| monthlySales | Integer | 近 30 天周边销量（件） |
| totalSales | Integer | 累计销量（件） |
| tags | String[] | 标签列表，库中逗号拼接 |
| coverImage | String | 封面图 URL，形如 `/uploads/acg/<uuid>.<ext>` |
| heatUpdatedAt | LocalDateTime | 热度最近更新时间 |
| remark | String | 备注 |
| status | Integer | 1 可选（进入下拉选项）/ 0 停用 |
| trendRate | BigDecimal | **计算值**：热度环比涨跌幅（%），`10.3` 表示上涨 10.3%；无历史快照时为 `0.0` |
| trendText | String | **计算值**：上升 / 下降 / 持平（±0.5% 以内算持平） |
| potentialScore | BigDecimal | **计算值**：选品潜力分 0~100，口径见 §10.2 |
| potentialLevel | String | **计算值**：S / A / B / C |
| potentialLevelText | String | 等级建议：重点主推 / 积极备货 / 常规跟进 / 谨慎 / 淘汰 |
| createTime | LocalDateTime | 创建时间 |
| updateTime | LocalDateTime | 更新时间 |

> 转换唯一出口 `AcgWorkConverter.toVO(work, maxMonthlySales, prevHeat)`。后两个参数是外部上下文：全库最高月销量（销量归一化）与上一次热度（算环比），由 `AcgMetricsService` 批量取，避免 N+1 查询。

### 3.8 `AcgAnalysisVO`（选品分析总览）

| 字段 | 类型 | 说明 |
|---|---|---|
| kpi | Object | 关键指标，见下 |
| ranking | Array | 排行条目，字段同 `AcgWorkVO` 的常用子集（id/name/coverImage/workType/heatScore/monthlySales/potentialScore/potentialLevel/trendRate），已按潜力分倒序，前端可本地重排 |
| quadrant | Array | 四象限散点：`{ id, name, heatScore, monthlySales, potentialLevel, quadrant, quadrantText }`，`quadrant` 取 `STAR` / `HEAT_ONLY` / `SALES_ONLY` / `LOW` |
| typeShares | Array | 类型分布：`{ workType, workTypeText, count, monthlySales, avgHeat }` |
| insights | String[] | 后端算好的中文结论（带货王、涨得最快、需警惕、S 级名单、类型机会） |
| formula | String | 潜力分口径说明，页面直接展示，保证算法对使用者透明 |

`kpi` 字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| workCount | Integer | 作品总数（未删除） |
| selectableCount | Integer | 其中启用（可选）数量 |
| avgHeat | BigDecimal | 平均热度，1 位小数 |
| monthlySalesTotal | Integer | 近 30 天销量合计 |
| avgPotential | BigDecimal | 平均潜力分 |
| levelSCount / levelACount / levelBCount / levelCCount | Integer | 各潜力等级数量 |
| topTypeText | String | 作品数最多的类型文案 |
| topTypeCount | Integer | 该类型作品数 |

### 3.9 `ManufacturerVO`（厂商）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long | 厂商 ID（其他表存这个） |
| name | String | 公司 / 厂牌名称，未删除记录内唯一 |
| logo | String | 公司厂牌 Logo URL，可空 |
| foundedDate | String(yyyy-MM-dd) | 创立时间，可空 |
| country | String | 国家 / 地区，如 日本、中国香港、美国 |
| remark | String | 备注 |
| status | Integer | 1 启用 / 0 停用 |
| statusText | String | 启用 / 停用 |
| createTime / updateTime | String | 创建 / 更新时间 |

---

## 4. 系统接口

### SYS-01 健康检查

`GET /api/system/health` · 免登录 · `SystemController#health`

确认服务已启动，不访问数据库。用于部署探活。

**入参**：无

**出参 `Result<Map>`**

| 字段 | 类型 | 说明 |
|---|---|---|
| status | String | 固定 `UP` |
| app | String | 应用名 `figure-erp-server` |
| version | String | 应用版本（取自 `pom.xml`） |
| javaVersion | String | 运行时 JVM 版本 |
| serverTime | String | 服务器时间，格式 `yyyy-MM-dd HH:mm:ss`（此字段是手工格式化的字符串，与其他时间字段不同） |

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "status": "UP",
    "app": "figure-erp-server",
    "version": "0.0.1-SNAPSHOT",
    "javaVersion": "24.0.1",
    "serverTime": "2026-11-01 20:03:11"
  }
}
```

**错误码**：无（异常时 `500`）

### SYS-02 数据库连通检查

`GET /api/system/db` · 免登录 · `SystemController#db`

真实取一次连接，返回数据库版本、当前库名、表数量与连接池情况。

**入参**：无

**出参 `Result<Map>`**

| 字段 | 类型 | 说明 |
|---|---|---|
| connected | boolean | 是否连通 |
| latencyMs | long | 取连接耗时（毫秒） |
| product | String | 数据库产品名，如 `MySQL` |
| productVersion | String | 数据库版本 |
| driverVersion | String | JDBC 驱动版本 |
| database | String | 当前库名，应为 `figure_erp` |
| tableCount | int | 当前库中的表数量 |
| pool | Object | 连接池状态，见下表 |
| error | String | **仅在连通失败时出现**，形如 `SQLException: ...` |

`pool` 字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| active | int | 活跃连接数（-1 表示取不到） |
| idle | int | 空闲连接数 |
| total | int | 当前总连接数 |
| maxPoolSize | int | 池上限 |

> 连接失败**不抛异常**，返回 `code = 200` + `connected = false` + `error`，方便前端直接展示排障信息。

**错误码**：无

### SYS-03 系统字典

`GET /api/system/dicts` · 登录用户 · `SystemController#dicts`

供前端下拉框使用的固定枚举，无需入参。

**入参**：无

**出参 `Result<Map>`**

| 字段 | 类型 | 说明 |
|---|---|---|
| roles | `{code,label,group,level}[]` | 角色（12 个，顺序固定）；`group` 为板块、`level` 为权限层级（3 管理级 / 2 主管级 / 1 执行级），见 §13.1 |
| statuses | `{code,label}[]` | 账号状态：1 启用 / 0 停用 |

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "roles": [
      { "code": "ADMIN", "label": "管理员", "group": "系统", "level": 3 },
      { "code": "GM", "label": "总经理", "group": "管理层", "level": 3 },
      { "code": "FINANCE_MANAGER", "label": "财务主管", "group": "财务", "level": 2 },
      { "code": "FINANCE_STAFF", "label": "财务专员", "group": "财务", "level": 1 }
    ],
    "statuses": [
      { "code": 1, "label": "启用" },
      { "code": 0, "label": "停用" }
    ]
  }
}
```

**错误码**：`401`

---

## 5. 认证接口

### AUTH-01 登录

`POST /api/auth/login` · 免登录 · `AuthController#login`

**入参（JSON Body，`LoginRequest`）**

| 字段 | 类型 | 必填 | 约束 | 说明 |
|---|---|---|---|---|
| username | String | 是 | 非空，≤50 字符 | 登录账号 |
| password | String | 是 | 6~64 字符 | 登录密码 |

**出参 `Result<LoginVO>`**：结构见 §3.2，其中 `user` 为 §3.1。

```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 28800,
    "user": {
      "id": 1,
      "username": "admin",
      "realName": "系统管理员",
      "role": "ADMIN",
      "roleLabel": "管理员",
      "status": 1,
      "intro": "",
      "remark": "",
      "lastLoginTime": "2026-11-01T19:58:02",
      "createTime": "2026-10-28T09:12:44"
    }
  }
}
```

**错误码**

| code | message | 场景 |
|---|---|---|
| 1003 | 请输入登录账号 / 请输入密码 / 密码长度需在 6-64 个字符之间 | 入参校验不通过 |
| 401 | 账号或密码不正确 | 账号不存在或密码错误（两种情况文案相同，防止被试探账号） |
| 403 | 该账号已被停用，请联系管理员 | 账号 `status = 0` |

### AUTH-02 当前登录用户

`GET /api/auth/me` · 登录用户 · `AuthController#me`

用于刷新页面后恢复登录态。每次实时读库，角色与状态变更立即反映。

**入参**：无（用户身份取自令牌）

**出参 `Result<UserVO>`**：结构见 §3.1

**错误码**

| code | message | 场景 |
|---|---|---|
| 401 | 请先登录 / 登录已过期，请重新登录 / 登录凭证无效，请重新登录 | 令牌缺失或无效 |
| 401 | 账号不存在或已被删除 | 令牌有效但账号已被删除 |

### AUTH-03 退出登录

`POST /api/auth/logout` · 登录用户 · `AuthController#logout`

JWT 无状态，服务端不维护会话，**前端删除本地令牌即可**；此接口仅用于记录操作日志，不做服务端失效处理（令牌在过期前仍可使用）。

**入参**：无

**出参**：`code = 200`，`data` 省略

```json
{ "code": 200, "message": "已退出登录" }
```

**错误码**：`401`

---

## 6. 员工管理接口

### USER-01 分页查询员工

`GET /api/system/users` · 主管级及以上 · `UserController#page`

**入参（Query，`UserQuery`）**

| 字段 | 类型 | 必填 | 默认 | 约束 | 说明 |
|---|---|---|---|---|---|
| page | long | 否 | 1 | ≥1 | 页码，从 1 开始 |
| size | long | 否 | 10 | 1~200 | 每页条数 |
| keyword | String | 否 | — | — | 关键字，模糊匹配**登录账号或姓名** |
| role | String | 否 | — | 见 §13.1 | 角色精确匹配 |
| status | Integer | 否 | — | 0/1 | 状态精确匹配 |

排序：`id` 升序。空字符串参数按「不筛选」处理。

**出参 `Result<PageResult<UserVO>>`**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "records": [
      {
        "id": 1,
        "username": "admin",
        "realName": "系统管理员",
        "role": "ADMIN",
        "roleLabel": "管理员",
        "status": 1,
        "createTime": "2026-10-28T09:12:44"
      }
    ],
    "total": 3,
    "page": 1,
    "size": 10,
    "pages": 1
  }
}
```

**错误码**

| code | message | 场景 |
|---|---|---|
| 403 | 没有查看员工列表的权限 | 当前角色为 STAFF |
| 1003 | 页码不能小于 1 / 每页条数不能超过 200 等 | 入参校验不通过 |
| 401 | 见 §1.1 | 未登录 |

### USER-02 员工详情

`GET /api/system/users/{id}` · 主管级及以上 · `UserController#detail`

**入参（Path）**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | Long | 是 | 员工 ID |

**出参 `Result<UserVO>`**：结构见 §3.1

**错误码**

| code | message | 场景 |
|---|---|---|
| 403 | 没有查看员工列表的权限 | STAFF 访问 |
| 1001 | 员工不存在或已被删除 | id 不存在或已逻辑删除 |
| 400 | 参数格式不正确：id | id 非数字 |

### USER-03 新增员工

`POST /api/system/users` · 管理级（ADMIN / GM） · `UserController#create`

**入参（JSON Body，`UserSaveRequest`）**

| 字段 | 类型 | 必填 | 约束 | 说明 |
|---|---|---|---|---|
| username | String | **是**（仅新增） | `^[a-zA-Z0-9_]{3,20}$` | 登录账号，全局唯一；已删除账号仍占用唯一索引 |
| realName | String | 是 | ≤50 字符 | 员工姓名 |
| password | String | **是**（仅新增） | 6~64 字符 | 初始密码，入库前 BCrypt 加密 |
| role | String | 是 | 见 §13.1，**必填** | 角色编码，后端按枚举校验，空值/非法值返回 `1003` |
| status | Integer | 是 | 0/1 | 账号状态 |
| intro | String | 否 | ≤500 字符 | 员工介绍，传 null 存为空串 |
| remark | String | 否 | ≤500 字符 | 备注，传 null 存为空串 |

> `username` 上的 `@Pattern` 在字段为 `null` 时**不生效**，所以「新增必填」由 Service 层兜底判断，缺失返回 `1000 请输入登录账号`。

**出参 `Result<Long>`**：新增员工的 id

```json
{ "code": 200, "message": "新增成功", "data": 12 }
```

**错误码**

| code | message | 场景 |
|---|---|---|
| 403 | 只有管理员或总经理可以管理员工账号 | 非管理级 |
| 1003 | 账号只能由字母、数字、下划线组成，长度 3-20 位 等 | 格式校验不通过 |
| 1000 | 请输入登录账号 | username 为空 |
| 1000 | 初始密码至少 6 位 | password 为空或过短 |
| 1000 | 角色不合法：{role} | role 不在枚举内 |
| 1002 | 账号已存在：{username} | 库中已有同账号 |
| 1002 | 账号已被占用（可能属于已删除的员工）：{username} | 违反唯一索引（逻辑删除记录占位） |

### USER-04 编辑员工

`PUT /api/system/users/{id}` · 管理级（ADMIN / GM） · `UserController#update`

**入参**

- Path：`id`（Long，必填）
- Body：同 `UserSaveRequest`（§USER-03），但 **`username` 与 `password` 会被忽略**，密码请用 `USER-06`

**出参**：`code = 200`，`data` 省略

```json
{ "code": 200, "message": "保存成功" }
```

**错误码**

| code | message | 场景 |
|---|---|---|
| 403 | 只有管理员或总经理可以管理员工账号 | 非管理级 |
| 1001 | 员工不存在或已被删除 | id 无效 |
| 1000 | 角色不合法：{role} | role 非法 |
| 1000 | 不能修改自己的角色，请让其他管理员操作 | 改自己的 role 且目标不是 ADMIN |
| 1000 | 不能停用自己的账号 | 把自己 status 改为 0 |
| 1000 | 系统至少需要保留一个启用状态的管理员 | 把最后一个启用管理员降级或停用 |

### USER-05 删除员工

`DELETE /api/system/users/{id}` · 管理级（ADMIN / GM） · `UserController#delete`

逻辑删除，数据仍在库中（`deleted = 1`）。

**入参（Path）**：`id`（Long，必填）

**出参**：`code = 200`，`data` 省略

**错误码**

| code | message | 场景 |
|---|---|---|
| 403 | 只有管理员或总经理可以管理员工账号 | 非管理级 |
| 1001 | 员工不存在或已被删除 | id 无效 |
| 1000 | 不能删除自己的账号 | 删除自己 |
| 1000 | 系统至少需要保留一个管理员 | 删除最后一个管理员 |

### USER-06 重置密码

`PUT /api/system/users/{id}/password` · 管理级（ADMIN / GM） · `UserController#resetPassword`

管理员操作，不需要原密码。

**入参**

- Path：`id`（Long，必填）
- Body（`PasswordResetRequest`）：

| 字段 | 类型 | 必填 | 约束 | 说明 |
|---|---|---|---|---|
| newPassword | String | 是 | 6~64 字符 | 新密码（明文传输，服务端 BCrypt 存储） |

**出参**：`code = 200`，`data` 省略

```json
{ "code": 200, "message": "密码已重置" }
```

**错误码**

| code | message | 场景 |
|---|---|---|
| 403 | 只有管理员或总经理可以管理员工账号 | 非管理级 |
| 1003 | 请输入新密码 / 密码长度需在 6-64 个字符之间 | 入参校验 |
| 1001 | 员工不存在或已被删除 | id 无效 |
| 1000 | 新密码至少 6 位 | Service 层兜底校验 |

### USER-07 启用 / 停用员工

`PUT /api/system/users/{id}/status` · 管理级（ADMIN / GM） · `UserController#updateStatus`

列表页快捷开关。停用后该账号无法登录（`AUTH-01` 返回 `403`），已签发的令牌在 `AUTH-02` 等接口仍可用，但业务权限按新状态实时判定。

**入参**

| 位置 | 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| Path | id | Long | 是 | 员工 ID |
| Query | status | Integer | 是 | `1` 启用 / `0` 停用 |

示例：`PUT /api/system/users/3/status?status=0`

**出参**：`code = 200`，`data` 省略

```json
{ "code": 200, "message": "状态已更新" }
```

**错误码**

| code | message | 场景 |
|---|---|---|
| 403 | 只有管理员或总经理可以管理员工账号 | 非管理级 |
| 400 | 缺少必要参数：status | 未传 status |
| 1001 | 员工不存在或已被删除 | id 无效 |
| 1000 | 状态值只能是 0 或 1 | status 非 0/1 |
| 1000 | 不能停用自己的账号 | 停用自己 |
| 1000 | 系统至少需要保留一个启用状态的管理员 | 停用最后一个启用管理员 |

---

## 7. 物流管理接口

计价模型（全国快递通用）：

```
重量 ≤ 首重                       → 总价 = 首重价
重量 > 首重                       → 总价 = 首重价 + ceil((重量 - 首重) / 续重单位) × 续重单价
```

目的地按「省」配置，个别城市可单独设价；查询时**城市级价格优先于省级统一价**（给深圳单独设价，查广州则回落到广东省统一价）。

### LOG-01 分页查询物流价格

`GET /api/logistics/prices` · 登录用户 · `LogisticsPriceController#page`

**入参（Query，`LogisticsPriceQuery`）**

| 字段 | 类型 | 必填 | 默认 | 约束 | 说明 |
|---|---|---|---|---|---|
| page | long | 否 | 1 | ≥1 | 页码 |
| size | long | 否 | 10 | 1~500 | 每页条数 |
| supplierCode | String | 否 | — | §13.3 枚举 | 供应商精确匹配 |
| originProvince | String | 否 | — | — | 出发地-省精确匹配 |
| originCity | String | 否 | — | — | 出发地-市精确匹配 |
| destProvince | String | 否 | — | — | 目的地-省精确匹配 |
| destCity | String | 否 | — | — | 目的地-市精确匹配（查省级统一价需显式传空以外的值，见下） |
| keyword | String | 否 | — | — | 模糊匹配目的地**省或市** |
| status | Integer | 否 | — | 0/1 | 状态精确匹配 |

排序：`supplierCode` 升序 → `destProvince` 升序 → `destCity` 升序。

> 想只看「省级统一价」时用 `destCity` 传空会变成「不筛选」，前端如需此筛选建议用 `keyword` 或在前端二次过滤；当前契约不提供「destCity IS NULL」的专用参数。

**出参 `Result<PageResult<LogisticsPriceVO>>`**：元素结构见 §3.3

**错误码**

| code | message | 场景 |
|---|---|---|
| 401 | 见 §1.1 | 未登录 |
| 1003 | 页码不能小于 1 / 每页条数不能超过 500 | 入参校验 |

### LOG-02 价格详情

`GET /api/logistics/prices/{id}` · 登录用户 · `LogisticsPriceController#detail`

**入参（Path）**：`id`（Long，必填）

**出参 `Result<LogisticsPriceVO>`**：结构见 §3.3

**错误码**

| code | message | 场景 |
|---|---|---|
| 1001 | 价格记录不存在或已被删除 | id 无效 |
| 400 | 参数格式不正确：id | id 非数字 |

### LOG-03 新增价格

`POST /api/logistics/prices` · 主管级及以上 · `LogisticsPriceController#create`

**入参（JSON Body，`LogisticsPriceSaveRequest`）**

| 字段 | 类型 | 必填 | 默认 | 约束 | 说明 |
|---|---|---|---|---|---|
| supplierCode | String | 是 | — | §13.3 枚举 | 供应商编码 |
| originProvince | String | 是 | — | 非空 | 出发地-省 |
| originCity | String | 是 | — | 非空 | 出发地-市 |
| originDistrict | String | 否 | — | — | 出发地-区/县，null 存为空串 |
| destProvince | String | 是 | — | 非空 | 目的地-省 |
| destCity | String | 否 | — | — | 目的地-市，**留空 = 全省统一价** |
| firstWeight | BigDecimal | 否 | 1.00 | 0.1~100 | 首重重量（kg） |
| firstPrice | BigDecimal | **是** | — | 0~99999 | 首重价格（元） |
| additionalWeight | BigDecimal | 否 | 1.00 | 0.1~100 | 续重单位（kg） |
| additionalPrice | BigDecimal | **是** | — | 0~99999 | 续重单价（元） |
| estimatedDays | Integer | 否 | — | 1~60 | 预计时效（天） |
| status | Integer | 否 | 1 | 0/1 | 状态 |
| remark | String | 否 | — | — | 备注，null 存为空串 |

业务唯一键：`supplierCode + originProvince + originCity + destProvince + destCity`（`destCity` 为空时按 `NULL` 或空串匹配，`originDistrict` 不参与唯一性）。

**出参 `Result<Long>`**：新增记录 id

```json
{ "code": 200, "message": "新增成功", "data": 273 }
```

**错误码**

| code | message | 场景 |
|---|---|---|
| 403 | 没有权限维护物流价格，请联系管理员或主管 | 执行级操作 |
| 1003 | 请选择物流供应商 / 请填写首重价格 等 | 入参校验 |
| 1000 | 不支持的物流供应商：{code} | supplierCode 不在枚举内 |
| 1000 | 请填写完整的首重价与续重价 | 价格为空（Service 兜底） |
| 1002 | 该路线已存在价格：{供应商} {目的地} | 命中同一业务唯一键 |

### LOG-04 编辑价格

`PUT /api/logistics/prices/{id}` · 主管级及以上 · `LogisticsPriceController#update`

**入参**

- Path：`id`（Long，必填）
- Body：同 `LogisticsPriceSaveRequest`（§LOG-03），字段全量覆盖

**出参**：`code = 200`，`data` 省略

```json
{ "code": 200, "message": "保存成功" }
```

**错误码**

| code | message | 场景 |
|---|---|---|
| 403 | 没有权限维护物流价格，请联系管理员或主管 | 执行级操作 |
| 1001 | 价格记录不存在或已被删除 | id 无效 |
| 1003 | 入参校验失败 | 同 LOG-03 |
| 1000 | 不支持的物流供应商：{code} | supplierCode 非法 |
| 1002 | 该路线已存在另一条价格，请直接编辑那一条 | 改后与另一条记录撞唯一键 |

### LOG-05 删除价格

`DELETE /api/logistics/prices/{id}` · 主管级及以上 · `LogisticsPriceController#delete`

逻辑删除，数据仍在库中。

**入参（Path）**：`id`（Long，必填）

**出参**：`code = 200`，`data` 省略

```json
{ "code": 200, "message": "删除成功" }
```

**错误码**

| code | message | 场景 |
|---|---|---|
| 403 | 没有权限删除物流价格，请联系管理员或主管 | 执行级操作 |
| 1001 | 价格记录不存在或已被删除 | id 无效 |

### LOG-06 启用 / 停用价格

`PUT /api/logistics/prices/{id}/status` · 主管级及以上 · `LogisticsPriceController#updateStatus`

停用后该条价格**不参与** `LOG-08` 报价计算。

**入参**

| 位置 | 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| Path | id | Long | 是 | 价格记录 ID |
| Query | status | Integer | 是 | `1` 启用 / `0` 停用 |

示例：`PUT /api/logistics/prices/88/status?status=0`

**出参**：`code = 200`，`data` 省略

```json
{ "code": 200, "message": "状态已更新" }
```

**错误码**

| code | message | 场景 |
|---|---|---|
| 403 | 没有权限修改物流价格状态，请联系管理员或主管 | 执行级操作 |
| 400 | 缺少必要参数：status | 未传 status |
| 1001 | 价格记录不存在或已被删除 | id 无效 |
| 1000 | 状态值只能是 0 或 1 | status 非 0/1 |

### LOG-07 批量保存全国价

`POST /api/logistics/prices/batch` · 主管级及以上 · `LogisticsPriceController#batchSave`

一次提交某供应商从固定出发地发往全国（34 个省级行政区）的价格。按业务唯一键 upsert：**已存在则更新，不存在则新增**；未出现在 `items` 里的省份**不会被删除**。

**入参（JSON Body，`BatchPriceRequest`）**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| supplierCode | String | 是 | 供应商编码，§13.3 |
| originProvince | String | 是 | 出发地-省 |
| originCity | String | 是 | 出发地-市 |
| originDistrict | String | 否 | 出发地-区/县 |
| items | `Item[]` | 是 | 至少 1 条；`items[].destProvince` 为空的条目会被**跳过**（不报错，不计入返回条数） |

`Item` 结构：

| 字段 | 类型 | 必填 | 默认 | 约束 | 说明 |
|---|---|---|---|---|---|
| destProvince | String | 是 | — | 非空 | 目的地-省 |
| destCity | String | 否 | — | — | 留空 = 全省统一价 |
| firstWeight | BigDecimal | 否 | 1.00 | 0.1~100 | 首重重量（kg） |
| firstPrice | BigDecimal | 是 | — | 0~99999 | 首重价格（元） |
| additionalWeight | BigDecimal | 否 | 1.00 | 0.1~100 | 续重单位（kg） |
| additionalPrice | BigDecimal | 是 | — | 0~99999 | 续重单价（元） |
| estimatedDays | Integer | 否 | — | 1~60 | 预计时效（天） |
| status | Integer | 否 | 1 | 0/1 | 状态 |
| remark | String | 否 | — | — | 备注 |

**出参 `Result<Integer>`**：实际新增 + 更新的条数

```json
{ "code": 200, "message": "已保存 34 条价格", "data": 34 }
```

**错误码**

| code | message | 场景 |
|---|---|---|
| 403 | 没有权限批量维护物流价格，请联系管理员或主管 | 执行级操作 |
| 1003 | 请至少填写一个省份的价格 / 目的地省份不能为空 | 入参校验 |
| 1000 | 不支持的物流供应商：{code} | supplierCode 非法 |

请求示例：

```json
{
  "supplierCode": "SF",
  "originProvince": "上海市",
  "originCity": "上海市",
  "originDistrict": "浦东新区",
  "items": [
    { "destProvince": "广东省", "firstPrice": 14.00, "additionalPrice": 3.00, "estimatedDays": 2 },
    { "destProvince": "北京市", "firstPrice": 14.00, "additionalPrice": 3.00, "estimatedDays": 2 }
  ]
}
```

### LOG-08 运费报价查询

`GET /api/logistics/quote` · 登录用户 · `LogisticsPriceController#quote`

给定「出发地 + 目的地 + 重量」，返回各启用供应商的价格对比。

**入参（Query，`QuoteQuery`）**

| 字段 | 类型 | 必填 | 默认 | 约束 | 说明 |
|---|---|---|---|---|---|
| originProvince | String | 否 | `上海市` | — | 出发地-省 |
| originCity | String | 否 | `上海市` | — | 出发地-市；传空则取 `originProvince` |
| originDistrict | String | 否 | `浦东新区` | — | 出发地-区/县，**仅影响展示文案，不参与价格匹配** |
| destProvince | String | **是** | — | 非空 | 目的地-省 |
| destCity | String | 否 | — | — | 目的地-市；留空 = 查该省统一价 |
| weight | BigDecimal | 否 | 1 | 0.1~500 | 重量（kg） |

匹配规则：

1. 只取 `status = 1` 且出发地省/市、目的地省完全相等的记录；
2. 同一供应商只保留一条：`destCity` 精确匹配的记录优先，否则用 `destCity` 为空的省级统一价；
3. 结果按总价升序、同价按时效升序；并列最低价全部标 `cheapest = true`，并列最快全部标 `fastest = true`；
4. 该路线无任何配置时返回 `items = []`、`count = 0`，`advice` 提示去维护价格。

示例：`GET /api/logistics/quote?destProvince=广东省&destCity=深圳市&weight=2.5`

**出参 `Result<QuoteResultVO>`**：结构见 §3.4

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "originLabel": "上海市 浦东新区",
    "destLabel": "广东省 深圳市",
    "zoneLabel": "华南",
    "weight": 2.5,
    "items": [
      {
        "supplierCode": "YUNDA",
        "supplierName": "韵达快递",
        "totalPrice": 8.50,
        "firstWeight": 1.00,
        "firstPrice": 5.50,
        "additionalWeight": 1.00,
        "additionalPrice": 1.50,
        "additionalUnits": 2,
        "formula": "首重 1kg 5.50 元 + 续重 2×1kg × 1.50 元",
        "estimatedDays": 3,
        "cheapest": true,
        "fastest": false,
        "remark": ""
      }
    ],
    "count": 1,
    "lowestPrice": 8.50,
    "lowestSupplier": "韵达快递",
    "fastestDays": 3,
    "advice": "韵达快递 价格最低且时效最快，优先选它"
  }
}
```

**错误码**

| code | message | 场景 |
|---|---|---|
| 1003 | 请选择目的地省份 | destProvince 为空 |
| 1003 | 重量不能小于 0.1kg / 单票重量不能超过 500kg | weight 越界 |

### LOG-09 物流字典

`GET /api/logistics/dicts` · 登录用户 · `LogisticsPriceController#dicts`

维护页与查询页的下拉数据来源，避免前端硬编码省份/城市。

**入参**：无

**出参 `Result<Map>`**

| 字段 | 类型 | 说明 |
|---|---|---|
| suppliers | `Supplier[]` | 8 家供应商，见下 |
| provinces | `Province[]` | 34 个省级行政区，见下 |
| zones | `Zone[]` | 10 个运费区域，见下 |
| origins | `Origin[]` | 已配置过价格的出发地，用于查询页下拉 |

`Supplier`：

| 字段 | 类型 | 说明 |
|---|---|---|
| code | String | 供应商编码 |
| label | String | 供应商名称 |
| baseFirstPrice | BigDecimal | 参考首重价（仅初始化用，不代表当前库中价格） |
| baseAdditionalPrice | BigDecimal | 参考续重价 |

`Province`：

| 字段 | 类型 | 说明 |
|---|---|---|
| name | String | 省名，如「广东省」 |
| zone | String | 运费区域编码，如 `SOUTH` |
| zoneLabel | String | 运费区域中文，如「华南」 |
| cities | String[] | 该省城市列表 |
| municipality | boolean | 是否直辖市 |

`Zone`：

| 字段 | 类型 | 说明 |
|---|---|---|
| code | String | 区域编码，如 `EAST` |
| label | String | 区域中文，如「华东」 |
| firstOffset | int | 首重加价（元），仅初始化用 |
| additionalOffset | int | 续重加价（元/kg），仅初始化用 |
| daysOffset | int | 时效增加（天），仅初始化用 |

`Origin`：

| 字段 | 类型 | 说明 |
|---|---|---|
| province | String | 出发地-省 |
| city | String | 出发地-市 |
| district | String | 出发地-区/县 |
| label | String | 展示文案，如「上海市 浦东新区」 |

**错误码**：`401`

---

## 8. 商品（手办）管理接口

手办商品档案：录入图片、长宽高、带盒重量等信息，并提供支持多条件查询的商品列表。图片先通过 `PRO-07` 单独上传，拿到可访问 URL 后随表单一并提交。

**关键业务规则（后端强制）：**

- **带盒重量**：`weightWithBox` 为正整数，单位 **克（g）**，指含外包装盒的总重量；必须 > 0。
- **图片容量**：商品图片必须 **3~4 张**（不足或多出均被拦截，`1003`）。
- **货号唯一**：`code` 可空；一旦填写，不可与其他商品重复（`1002`）。
- **权限**：查询 / 详情 / 上传图片——任意登录用户可用；新增 / 编辑 / 删除 / 启停——仅 ADMIN 或 MANAGER。
- **图片存储**：上传文件落盘到 `{user.dir}/uploads/products/`，由 `WebMvcConfig` 注册的静态资源处理器对外提供，URL 形如 `/uploads/products/<uuid>.<ext>`；该路径为静态文件服务，**不要求 `Authorization` 头，可直接访问**（需先登录拿到图片 URL）。

### PRO-01 分页查询手办商品

`GET /api/product/page` · 登录用户 · `FigureProductController#page`

**入参（Query，`FigureProductQuery`）**

| 字段 | 类型 | 必填 | 默认 | 约束 | 说明 |
|---|---|---|---|---|---|
| page | long | 否 | 1 | ≥1 | 页码 |
| size | long | 否 | 10 | 1~500 | 每页条数 |
| name | String | 否 | — | 模糊 | 商品名称模糊匹配 |
| code | String | 否 | — | 模糊 | 货号模糊匹配 |
| brand | String | 否 | — | 模糊 | 品牌模糊匹配 |
| series | String | 否 | — | 模糊 | 系列模糊匹配 |
| characterName | String | 否 | — | 模糊 | 角色名模糊匹配 |
| status | Integer | 否 | — | 0/1 | 状态精确匹配 |

排序：按 `createTime` 倒序（新录入的在前）。空字符串参数按「不筛选」处理。

**出参 `Result<PageResult<FigureProductVO>>`**：元素结构见 §3.6

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "records": [
      {
        "id": 1,
        "name": "初音未来 雪未来 1/7 手办",
        "code": "GSC-0001",
        "brand": "Good Smile Company",
        "series": "Scale",
        "characterName": "初音未来",
        "material": "PVC/ABS",
        "lengthCm": 28.5,
        "widthCm": 18.0,
        "heightCm": 30.0,
        "weightWithBox": 1200,
        "images": ["/uploads/products/4cc0d380-....png", "/uploads/products/90306187-....png", "/uploads/products/faa549b2-....png"],
        "specLabel": "28.5×18.0×30.0 cm / 带盒 1200 g",
        "remark": "",
        "status": 1,
        "createTime": "2026-11-01T13:09:39",
        "updateTime": "2026-11-01T13:09:39"
      }
    ],
    "total": 6,
    "page": 1,
    "size": 10,
    "pages": 1
  }
}
```

**错误码**

| code | message | 场景 |
|---|---|---|
| 401 | 见 §1.1 | 未登录 |
| 1003 | 页码不能小于 1 / 每页条数不能超过 500 | 入参校验 |

### PRO-02 商品详情

`GET /api/product/{id}` · 登录用户 · `FigureProductController#detail`

**入参（Path）**：`id`（Long，必填）

**出参 `Result<FigureProductVO>`**：结构见 §3.6

**错误码**

| code | message | 场景 |
|---|---|---|
| 401 | 见 §1.1 | 未登录 |
| 1001 | 商品不存在或已被删除 | id 无效或已逻辑删除 |
| 400 | 参数格式不正确：id | id 非数字 |

### PRO-03 新增商品

`POST /api/product` · 主管级及以上 · `FigureProductController#create`

**入参（JSON Body，`FigureProductSaveRequest`）**

| 字段 | 类型 | 必填 | 约束 | 说明 |
|---|---|---|---|---|
| name | String | 是 | 非空 | 商品名称（手办全称） |
| code | String | 否 | 填写后全局唯一 | 货号 / SKU |
| brand | String | 否 | — | 品牌 |
| series | String | 否 | — | 系列 |
| characterName | String | 否 | — | 角色名 |
| material | String | 否 | — | 材质，如 PVC/ABS |
| lengthCm | BigDecimal | 否 | 0~9999.9 | 长（cm） |
| widthCm | BigDecimal | 否 | 0~9999.9 | 宽（cm） |
| heightCm | BigDecimal | 否 | 0~9999.9 | 高（cm） |
| weightWithBox | Integer | **是** | > 0 | **带盒重量（g，克）**，含包装盒 |
| images | String[] | **是** | 3~4 张 | 图片 URL 列表，来自 `PRO-07` 上传返回 |
| status | Integer | 否 | 0/1，默认 1 | 状态 |
| remark | String | 否 | — | 备注 |

> `images` 的元素必须是 `PRO-07` 返回的真实 URL（形如 `/uploads/products/...`）；前端务必使用自定义上传收集服务端返回的 URL，不要传 `blob:` 临时地址。

**出参 `Result<Long>`**：新增商品 id

```json
{ "code": 200, "message": "新增成功", "data": 10 }
```

**错误码**

| code | message | 场景 |
|---|---|---|
| 403 | 没有权限维护手办商品，请联系管理员或主管 | 执行级操作 |
| 1003 | 请填写商品名称 / 请填写带盒重量 / 请至少上传 3 张商品图片 / 商品图片需 3~4 张 | 入参校验 |
| 1003 | 带盒重量必须大于 0 | 重量为 0 或负数（Service 兜底） |
| 1003 | 商品图片需 3~4 张 | 图片数量越界（Service 兜底） |
| 1002 | 货号已存在：{code} | 命中货号唯一约束 |

### PRO-04 编辑商品

`PUT /api/product/{id}` · 主管级及以上 · `FigureProductController#update`

**入参**

- Path：`id`（Long，必填）
- Body：同 `FigureProductSaveRequest`（§PRO-03），字段全量覆盖（含图片列表——传几张就存几张，仍需 3~4 张）

**出参**：`code = 200`，`data` 省略

```json
{ "code": 200, "message": "保存成功" }
```

**错误码**

| code | message | 场景 |
|---|---|---|
| 403 | 没有权限维护手办商品，请联系管理员或主管 | 执行级操作 |
| 1001 | 商品不存在或已被删除 | id 无效 |
| 1003 | 入参校验失败 | 同 PRO-03 |
| 1002 | 货号已存在：{code} | 改后与另一条记录撞货号（排除自身） |

### PRO-05 删除商品

`DELETE /api/product/{id}` · 主管级及以上 · `FigureProductController#delete`

逻辑删除，数据仍在库中（`deleted = 1`）。图片文件**不会被自动清理**（仅解除数据库关联）。

**入参（Path）**：`id`（Long，必填）

**出参**：`code = 200`，`data` 省略

```json
{ "code": 200, "message": "删除成功" }
```

**错误码**

| code | message | 场景 |
|---|---|---|
| 403 | 没有权限删除手办商品，请联系管理员或主管 | 执行级操作 |
| 1001 | 商品不存在或已被删除 | id 无效 |

### PRO-06 启用 / 停用商品

`PUT /api/product/{id}/status` · 主管级及以上 · `FigureProductController#updateStatus`

**入参**

| 位置 | 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| Path | id | Long | 是 | 商品 ID |
| Query | status | Integer | 是 | `1` 启用 / `0` 停用 |

示例：`PUT /api/product/3/status?status=0`

**出参**：`code = 200`，`data` 省略

```json
{ "code": 200, "message": "状态已更新" }
```

**错误码**

| code | message | 场景 |
|---|---|---|
| 403 | 没有权限修改手办商品状态，请联系管理员或主管 | 执行级操作 |
| 400 | 缺少必要参数：status | 未传 status |
| 1001 | 商品不存在或已被删除 | id 无效 |
| 1000 | 状态值只能是 0 或 1 | status 非 0/1 |

### PRO-07 上传商品图片

`POST /api/product/upload` · 登录用户（任意角色） · `FigureProductController#upload` · `Content-Type: multipart/form-data`

上传**单张**图片，返回可访问的 URL；前端收集到 3~4 个 URL 后随 `PRO-03/04` 的 `images` 提交。

**入参（Form-Data）**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| file | MultipartFile | 是 | 单张图片；一次请求只传一张 |

**校验（不通过返回 `1003`）**

- 文件非空；
- content-type 为 `image/jpeg`、`image/png`、`image/gif`、`image/webp` 之一；
- 扩展名在白名单 `jpg/jpeg/png/gif/webp`；
- 大小不超过 `app.upload.max-size-mb`（默认 5 MB，全局 `spring.servlet.multipart` 另有上限）。

**出参 `Result<String>`**：图片 URL

```json
{ "code": 200, "message": "上传成功", "data": "/uploads/products/4cc0d380-c60d-4b54-a081-98ee8b9f9702.png" }
```

> 返回的 URL 为相对路径，前端拼接 `Base URL`（或经 Vite 代理 `/uploads` → 后端）即可访问。

**错误码**

| code | message | 场景 |
|---|---|---|
| 401 | 请先登录 / 登录已过期，请重新登录 / 当前账号不存在或已被删除 | 未登录或账号已删 |
| 1003 | 上传文件为空 / 图片过大，单张不可超过 5 MB / 仅支持 JPG / PNG / GIF / WEBP 图片 / 不支持的图片格式：{ext} | 上传校验失败 |
| 500 | 文件保存失败：{msg} | 落盘 IO 异常 |

---

## 9. 库存管理接口（仓库 / 区域 / 货架 / 入库记录）

本节的 WH-* 是「库存管理 → 仓库设置 → 库位管理」页面的仓库档案（`warehouse` 表），AREA-* 是同一菜单下「区域配置」页面的区域档案（`area` 表），SHELF-* 是「货架管理」页面的货架档案（`shelf` 表）。三者构成 `仓库 → 区域 → 货架 → 库位` 的层级，是后续入库、库位、库存业务的底座主数据，所以放在同一章节。

**仓库（`warehouse`）**：原先 12 条仓库写死在前端，现已落库；页面的查询 / 新增 / 编辑 / 删除全部走接口，操作后列表会立即重新拉取，反映库中最新数据。

**关键业务规则（后端强制）：**

- **仓库代码唯一**：`warehouseCode` 必填；在**未删除记录**中不可重复（`1002`）。删除为逻辑删除，删除后同一代码可以重新创建。
- **状态与类型取值**：`status` ∈ `A`/`C`/`D`（使用中 / 盘库中 / 停用中），`isReturn` ∈ `N`/`Y`（正常仓 / 退货仓）；非法值返回 `1003`。
- **排序**：按 `sortNo` 升序、同序按 `id` 升序。初始 12 条数据由 `WarehouseDataInitializer` 在表为空时写入，排序号 10/20/…。
- **权限**：查询 / 详情——任意登录用户；新增 / 编辑 / 删除 / 改状态——仅 ADMIN 或 MANAGER。

### 9.1 共享结构 `WarehouseVO`

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long | 主键 |
| warehouseCode | String | 仓库代码，如 `3101` |
| warehouseName | String | 仓库名称，如 `上海库` |
| warehouseAddress | String | 仓库地址，可空 |
| isReturn | String | `N` 正常仓 / `Y` 退货仓 |
| isReturnText | String | 后端翻译好的类型文案：正常仓 / 退货仓 |
| status | String | `A` 使用中 / `C` 盘库中 / `D` 停用中 |
| statusText | String | 后端翻译好的状态文案：使用中 / 盘库中 / 停用中 |
| remark | String | 备注，可空 |
| sortNo | Integer | 排序号，越小越靠前 |
| createTime | String | 创建时间（ISO-8601） |
| updateTime | String | 更新时间（ISO-8601） |

### WH-01 分页查询仓库

`GET /api/warehouse/page` · 登录用户 · `WarehouseController#page`

**入参（Query，`WarehouseQuery`）**

| 字段 | 类型 | 必填 | 默认 | 约束 | 说明 |
|---|---|---|---|---|---|
| page | long | 否 | 1 | ≥1 | 页码 |
| size | long | 否 | 20 | 1~500 | 每页条数 |
| warehouseCode | String | 否 | — | 模糊 | 仓库代码模糊匹配 |
| warehouseName | String | 否 | — | 模糊 | 仓库名称模糊匹配 |
| isReturn | String | 否 | — | N/Y | 退货类型精确匹配 |
| status | String | 否 | — | A/C/D | 状态精确匹配 |

排序：`sortNo` 升序，同序按 `id` 升序。空字符串参数按「不筛选」处理。

**出参 `Result<PageResult<WarehouseVO>>`**：元素结构见 §9.1

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "records": [
      {
        "id": 1,
        "warehouseCode": "3101",
        "warehouseName": "上海库",
        "warehouseAddress": "上海市浦东新区新川路180号",
        "isReturn": "N",
        "isReturnText": "正常仓",
        "status": "A",
        "statusText": "使用中",
        "sortNo": 10,
        "createTime": "2026-11-05T07:31:38",
        "updateTime": "2026-11-05T07:31:38"
      }
    ],
    "total": 12,
    "page": 1,
    "size": 20,
    "pages": 1
  }
}
```

**错误码**

| code | message | 场景 |
|---|---|---|
| 401 | 见 §1.1 | 未登录 |
| 1003 | 页码不能小于 1 / 每页条数不能超过 500 | 入参校验 |

### WH-02 仓库详情

`GET /api/warehouse/{id}` · 登录用户 · `WarehouseController#detail`

**出参**：`Result<WarehouseVO>`，结构见 §9.1

**错误码**

| code | message | 场景 |
|---|---|---|
| 401 | 见 §1.1 | 未登录 |
| 1001 | 仓库不存在或已被删除 | id 无效或已逻辑删除 |

### WH-03 新增仓库

`POST /api/warehouse` · 主管级及以上 · `WarehouseController#create`

**入参（Body，`WarehouseSaveRequest`）**

| 字段 | 类型 | 必填 | 默认 | 约束 | 说明 |
|---|---|---|---|---|---|
| warehouseCode | String | 是 | — | ≤50；未删除记录中唯一 | 仓库代码 |
| warehouseName | String | 是 | — | ≤100 | 仓库名称 |
| warehouseAddress | String | 否 | — | ≤255 | 仓库地址 |
| isReturn | String | 否 | N | N/Y | 正常仓 / 退货仓 |
| status | String | 否 | A | A/C/D | 状态 |
| remark | String | 否 | — | ≤500 | 备注 |
| sortNo | Integer | 否 | 0 | — | 排序号 |

**出参**：`Result<Long>`，`data` 为新仓库 id

**错误码**

| code | message | 场景 |
|---|---|---|
| 401 | 见 §1.1 | 未登录 |
| 403 | 没有权限维护仓库，请联系管理员或主管 | 非 主管级及以上 |
| 1003 | 请填写仓库代码 / 请填写仓库名称 / 仓库状态只能是 A、C 或 D / 是否退货库只能是 N 或 Y | 校验失败 |
| 1002 | 仓库代码已存在：{code} | 代码重复（未删除记录中） |

### WH-04 编辑仓库

`PUT /api/warehouse/{id}` · 主管级及以上 · `WarehouseController#update`

入参与 WH-03 相同（全量覆盖）。`sortNo` 不传时保留原值，不会清零。

**出参**：`Result<Void>`

**错误码**：同 WH-03，另加 `1001 仓库不存在或已被删除`。

### WH-05 删除仓库

`DELETE /api/warehouse/{id}` · 主管级及以上 · `WarehouseController#delete`

逻辑删除：数据保留在库中，列表不再返回；删除后同一 `warehouseCode` 可以重新创建。

**出参**：`Result<Void>`

**错误码**

| code | message | 场景 |
|---|---|---|
| 401 | 见 §1.1 | 未登录 |
| 403 | 没有权限删除仓库，请联系管理员或主管 | 非 主管级及以上 |
| 1001 | 仓库不存在或已被删除 | id 无效或已删除 |
| 1000 | 仓库「上海库」下还有 3 个货架，请先删除货架 | 该仓库仍挂着未删除的货架 |
| 1000 | 仓库「上海库」下还有 2 个区域，请先删除区域 | 该仓库仍挂着未删除的区域 |

### WH-06 修改仓库状态

`PUT /api/warehouse/{id}/status?status=A` · 主管级及以上 · `WarehouseController#updateStatus`

**入参**：Query `status`，取值 `A` / `C` / `D`

**出参**：`Result<Void>`

**错误码**

| code | message | 场景 |
|---|---|---|
| 401 | 见 §1.1 | 未登录 |
| 403 | 没有权限修改仓库状态，请联系管理员或主管 | 非 主管级及以上 |
| 1003 | 仓库状态只能是 A、C 或 D | 非法状态值 |
| 1001 | 仓库不存在或已被删除 | id 无效或已删除 |

### 9.2 共享结构 `ShelfVO`

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long | 主键 |
| shelfCode | String | 货架编号，如 `01-1`；**同一仓库内**唯一 |
| warehouseId | Long | 所属仓库 ID（`warehouse.id`） |
| warehouseCode | String | 所属仓库代码，后端按 `warehouseId` 关联返回 |
| warehouseName | String | 所属仓库名称，后端按 `warehouseId` 关联返回 |
| areaId | Long | 所属区域 ID（`area.id`），可空（不填表示未分区） |
| areaName | String | 所属区域名称，后端按 `areaId` 关联返回；未分区时不返回该字段 |
| location | String | 库位信息，如 `01区-1层`，可空 |
| status | String | `A` 正常 / `B` 维护中 / `D` 停用 |
| statusText | String | 后端翻译好的状态文案 |
| remark | String | 状态描述 / 备注，可空 |
| sortNo | Integer | 排序号，越小越靠前 |
| createTime | String | 创建时间（ISO-8601） |
| updateTime | String | 更新时间（ISO-8601） |

> 列表里 `warehouseCode` / `warehouseName` 是后端批量关联查出来的，前端不需要为每一行再请求一次仓库接口。

**关键业务规则（后端强制）：**

- **货架编号唯一范围 = 同一仓库 + 未删除**：`3101 上海库` 的 `01-1` 和 `3301 杭州库` 的 `01-1` 可以并存，同一仓库内重复返回 `1002`。删除是逻辑删除，删除后同仓库下该编号可以重建。
- **所属仓库必须存在**：`warehouseId` 必填且必须指向未删除的仓库，否则 `1001`。
- **所属区域可空，但不能跨仓库**：`areaId` 不传表示未分区；传了就必须存在（否则 `1001`）且 `area.warehouseId` 必须等于 `warehouseId`（否则 `1003`「所属区域不属于所选仓库」）。
- **状态取值**：`status` ∈ `A`/`B`/`D`（正常 / 维护中 / 停用）；非法值返回 `1003`。注意与仓库状态的 `C` 盘库中区分，两套状态独立。
- **排序**：按 `sortNo` 升序、同序按 `id` 升序。初始 3 条数据由 `ShelfDataInitializer` 在表为空时按仓库代码关联写入。
- **删除保护**：仓库下还有未删除货架或区域时，`WH-05` 返回 `1000` 拦住；区域下还有未删除货架时，`AREA-05` 返回 `1000` 拦住，避免下层数据指向不存在的上层档案。
- **权限**：查询 / 详情——任意登录用户；新增 / 编辑 / 删除 / 改状态——仅 ADMIN 或 MANAGER。

### SHELF-01 分页查询货架

`GET /api/shelf/page` · 登录用户 · `ShelfController#page`

**入参（Query，`ShelfQuery`）**

| 字段 | 类型 | 必填 | 默认 | 约束 | 说明 |
|---|---|---|---|---|---|
| page | long | 否 | 1 | ≥1 | 页码 |
| size | long | 否 | 20 | 1~500 | 每页条数 |
| keyword | String | 否 | — | 模糊 | 同时匹配货架编号与库位信息 |
| warehouseId | Long | 否 | — | 精确 | 所属仓库 |
| areaId | Long | 否 | — | 精确 | 所属区域 |
| status | String | 否 | — | 精确 | `A` / `B` / `D` |

**出参**：`Result<PageResult<ShelfVO>>`，元素结构见 §9.2

**示例**

```http
GET /api/shelf/page?page=1&size=10&keyword=01区&warehouseId=1
```

```json
{ "code": 200, "message": "操作成功", "data": {
  "records": [
    { "id": 1, "shelfCode": "01-1", "warehouseId": 1, "warehouseCode": "3101",
      "warehouseName": "上海库", "location": "01区-1层", "status": "A", "statusText": "正常",
      "remark": "存储大件手办", "sortNo": 10,
      "createTime": "2026-11-06T07:53:49", "updateTime": "2026-11-06T07:53:49" }
  ],
  "total": 2, "page": 1, "size": 10, "pages": 1
} }
```

**错误码**：`401` 未登录

### SHELF-02 货架详情

`GET /api/shelf/{id}` · 登录用户 · `ShelfController#detail`

**出参**：`Result<ShelfVO>`，结构见 §9.2

**错误码**

| code | message | 场景 |
|---|---|---|
| 401 | 见 §1.1 | 未登录 |
| 1001 | 货架不存在或已被删除 | id 无效或已删除 |

### SHELF-03 新增货架

`POST /api/shelf` · 主管级及以上 · `ShelfController#create`

**入参（Body，`ShelfSaveRequest`）**

| 字段 | 类型 | 必填 | 约束 | 说明 |
|---|---|---|---|---|
| shelfCode | String | 是 | ≤50 | 货架编号，同一仓库内唯一 |
| warehouseId | Long | 是 | 必须存在 | 所属仓库 |
| areaId | Long | 否 | 必须属于同一仓库 | 所属区域，不传表示未分区 |
| location | String | 否 | ≤100 | 库位信息 |
| status | String | 否 | `A`/`B`/`D`，默认 `A` | 货架状态 |
| remark | String | 否 | ≤255 | 状态描述 / 备注 |
| sortNo | Integer | 否 | — | 排序号，默认 0 |

**出参**：`Result<Long>`，新货架 ID

**错误码**

| code | message | 场景 |
|---|---|---|
| 401 | 见 §1.1 | 未登录 |
| 403 | 没有权限维护货架，请联系管理员或主管 | 执行级 |
| 1003 | 请填写货架编号 / 货架状态只能是 A、B 或 D | 参数校验失败 |
| 1001 | 所属仓库不存在或已被删除 | `warehouseId` 无效 |
| 1002 | 该仓库下货架编号已存在：01-1 | 同仓库编号重复 |

### SHELF-04 编辑货架

`PUT /api/shelf/{id}` · 主管级及以上 · `ShelfController#update`

入参同 SHELF-03（全量提交）。改 `warehouseId` 时会按新仓库重新校验编号唯一性，所以可以把货架「搬到」另一个仓库，只要目标仓库下没有同号货架。

**出参**：`Result<Void>`

**错误码**：同 SHELF-03，另加 `1001` 货架不存在或已被删除

### SHELF-05 删除货架

`DELETE /api/shelf/{id}` · 主管级及以上 · `ShelfController#delete`

逻辑删除：数据保留在库中，列表不再返回；删除后同仓库下该编号可以重新使用。

**出参**：`Result<Void>`

**错误码**

| code | message | 场景 |
|---|---|---|
| 401 | 见 §1.1 | 未登录 |
| 403 | 没有权限删除货架，请联系管理员或主管 | 非 主管级及以上 |
| 1001 | 货架不存在或已被删除 | id 无效或已删除 |

### SHELF-06 修改货架状态

`PUT /api/shelf/{id}/status?status=B` · 主管级及以上 · `ShelfController#updateStatus`

**入参**：Query `status`，取值 `A` / `B` / `D`

**出参**：`Result<Void>`

**错误码**

| code | message | 场景 |
|---|---|---|
| 401 | 见 §1.1 | 未登录 |
| 403 | 没有权限修改货架状态，请联系管理员或主管 | 非 主管级及以上 |
| 1003 | 货架状态只能是 A、B 或 D | 非法状态值 |
| 1001 | 货架不存在或已被删除 | id 无效或已删除 |

---

### 9.3 共享结构 `AreaVO`

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long | 主键 |
| areaCode | String | 区域编号，如 `A`；**同一仓库内**唯一 |
| areaName | String | 区域名称，如 `A区-核心展示柜` |
| warehouseId | Long | 所属仓库 ID（`warehouse.id`） |
| warehouseCode | String | 所属仓库代码，后端按 `warehouseId` 关联返回 |
| warehouseName | String | 所属仓库名称，后端按 `warehouseId` 关联返回 |
| location | String | 物理位置，如 `一层-主墙面`，可空 |
| capacity | Integer | 容量：可容纳货架数（原页面「容量 / 格子数」） |
| shelfCount | Long | 该区域下**未删除**的货架数，后端算好直接返回，页面展示成 `2 / 20 个货架` |
| status | String | `A` 使用中 / `D` 停用 |
| statusText | String | 后端翻译好的状态文案 |
| remark | String | 备注，可空 |
| sortNo | Integer | 排序号，越小越靠前 |
| createTime | String | 创建时间（ISO-8601） |
| updateTime | String | 更新时间（ISO-8601） |

> `warehouseCode` / `warehouseName` / `shelfCount` 都是后端批量算好的，前端不需要为每一行再请求仓库或货架接口。

**关键业务规则（后端强制）：**

- **区域编号唯一范围 = 同一仓库 + 未删除**：`3101 上海库` 的 `A` 和 `3301 杭州库` 的 `A` 可以并存，同一仓库内重复返回 `1002`。删除是逻辑删除，删除后同仓库下该编号可以重建。
- **所属仓库必须存在**：`warehouseId` 必填且必须指向未删除的仓库，否则 `1001`。
- **状态取值**：`status` ∈ `A`/`D`（使用中 / 停用）；注意没有货架那样的 `B` 维护中，传 `B` 返回 `1003`。
- **排序**：按 `sortNo` 升序、同序按 `id` 升序。初始 3 条数据由 `AreaDataInitializer` 在表为空时按仓库代码关联写入。
- **删除与改仓库保护**：区域下还有未删除货架时，`AREA-05` 返回 `1000`；同样场景下 `AREA-04` 不许把区域改到其他仓库（否则货架的 `warehouseId` 会和区域的仓库对不上），也返回 `1000`。
- **权限**：查询 / 详情——任意登录用户；新增 / 编辑 / 删除 / 改状态——主管级及以上（ADMIN / GM / 各部门主管）。

### AREA-01 分页查询区域

`GET /api/area/page` · 登录用户 · `AreaController#page`

**入参（Query，`AreaQuery`）**

| 字段 | 类型 | 必填 | 默认 | 约束 | 说明 |
|---|---|---|---|---|---|
| page | long | 否 | 1 | ≥1 | 页码 |
| size | long | 否 | 20 | 1~500 | 每页条数 |
| keyword | String | 否 | — | 模糊 | 同时匹配区域编号与区域名称 |
| warehouseId | Long | 否 | — | 精确 | 所属仓库 |
| status | String | 否 | — | 精确 | `A` / `D` |

**出参**：`Result<PageResult<AreaVO>>`，元素结构见 §9.3

**示例**

```http
GET /api/area/page?page=1&size=10&warehouseId=1&status=A
```

```json
{ "code": 200, "message": "操作成功", "data": {
  "records": [
    { "id": 1, "areaCode": "A", "areaName": "A区-核心展示柜", "warehouseId": 1,
      "warehouseCode": "3101", "warehouseName": "上海库", "location": "一层-主墙面",
      "capacity": 20, "shelfCount": 2, "status": "A", "statusText": "使用中",
      "remark": "放置热门款", "sortNo": 10,
      "createTime": "2026-11-07T08:47:02", "updateTime": "2026-11-07T08:47:02" }
  ],
  "total": 2, "page": 1, "size": 10, "pages": 1
} }
```

**错误码**：`401` 未登录

### AREA-02 区域详情

`GET /api/area/{id}` · 登录用户 · `AreaController#detail`

**出参**：`Result<AreaVO>`，结构见 §9.3

**错误码**

| code | message | 场景 |
|---|---|---|
| 401 | 见 §1.1 | 未登录 |
| 1001 | 区域不存在或已被删除 | id 无效或已删除 |

### AREA-03 新增区域

`POST /api/area` · 主管级及以上 · `AreaController#create`

**入参（Body，`AreaSaveRequest`）**

| 字段 | 类型 | 必填 | 约束 | 说明 |
|---|---|---|---|---|
| areaCode | String | 是 | ≤50 | 区域编号，同一仓库内唯一 |
| areaName | String | 是 | ≤100 | 区域名称 |
| warehouseId | Long | 是 | 必须存在 | 所属仓库 |
| location | String | 否 | ≤100 | 物理位置 |
| capacity | Integer | 否 | 0~9999，默认 0 | 容量：可容纳货架数 |
| status | String | 否 | `A`/`D`，默认 `A` | 区域状态 |
| remark | String | 否 | ≤255 | 备注 |
| sortNo | Integer | 否 | — | 排序号，默认 0 |

**出参**：`Result<Long>`，新区域 ID

**错误码**

| code | message | 场景 |
|---|---|---|
| 401 | 见 §1.1 | 未登录 |
| 403 | 没有权限维护区域，请联系管理员或主管 | 执行级 |
| 1003 | 请填写区域编号 / 请填写区域名称 / 请选择所属仓库 / 区域状态只能是 A 或 D / 容量不能小于 0 | 参数校验失败 |
| 1001 | 所属仓库不存在或已被删除 | `warehouseId` 无效 |
| 1002 | 该仓库下区域编号已存在：A | 同仓库编号重复 |

### AREA-04 编辑区域

`PUT /api/area/{id}` · 主管级及以上 · `AreaController#update`

入参同 AREA-03（全量提交）。改 `warehouseId` 时会按新仓库重新校验编号唯一性；如果区域下已经有货架，改仓库会被拒绝（`1000`），需要先把货架移走。

**出参**：`Result<Void>`

**错误码**：同 AREA-03，另加 `1001` 区域不存在或已被删除、`1000` 该区域下还有 N 个货架，不能改到其他仓库

### AREA-05 删除区域

`DELETE /api/area/{id}` · 主管级及以上 · `AreaController#delete`

逻辑删除：数据保留在库中，列表不再返回；删除后同仓库下该编号可以重新使用。

**出参**：`Result<Void>`

**错误码**

| code | message | 场景 |
|---|---|---|
| 401 | 见 §1.1 | 未登录 |
| 403 | 没有权限删除区域，请联系管理员或主管 | 非 主管级及以上 |
| 1001 | 区域不存在或已被删除 | id 无效或已删除 |
| 1000 | 区域「A区-核心展示柜」下还有 2 个货架，请先删除货架或将货架改到其他区域 | 区域内仍有未删除货架 |

### AREA-06 修改区域状态

`PUT /api/area/{id}/status?status=D` · 主管级及以上 · `AreaController#updateStatus`

**入参**：Query `status`，取值 `A` / `D`

**出参**：`Result<Void>`

**错误码**

| code | message | 场景 |
|---|---|---|
| 401 | 见 §1.1 | 未登录 |
| 403 | 没有权限修改区域状态，请联系管理员或主管 | 非 主管级及以上 |
| 1003 | 区域状态只能是 A 或 D | 非法状态值（含货架专用的 B） |
| 1001 | 区域不存在或已被删除 | id 无效或已删除 |

### 9.4 共享结构 `InboundOrderVO` / `InboundItemVO`

`InboundOrderVO`（列表 / 详情共用，列表不返回 `items`）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long | 主键 |
| inboundNo | String | 入库单号，如 `RK202611070001`；**未删除记录内唯一** |
| inboundType | String | `P` 采购 / `R` 退货 / `T` 调拨 / `O` 其他（见 §13.14） |
| inboundTypeText | String | 后端翻译好的类型文案 |
| warehouseId | Long | 入库仓库 ID（`warehouse.id`） |
| warehouseCode | String | 仓库代码，后端关联返回 |
| warehouseName | String | 仓库名称，后端关联返回 |
| areaId | Long | 入库区域 ID，可空 |
| areaName | String | 区域名称，后端关联返回；`areaId` 为空时不返回 |
| shelfId | Long | 入库货架 ID，可空 |
| shelfCode | String | 货架编号，后端关联返回；`shelfId` 为空时不返回 |
| supplier | String | 供应商 / 来源单位，可空 |
| status | String | `D` 待入库 / `C` 已入库 / `X` 已作废（见 §13.15） |
| statusText | String | 后端翻译好的状态文案 |
| totalQuantity | Integer | 明细数量合计，**后端按明细重算** |
| totalAmount | BigDecimal | 明细金额合计，**后端按明细重算** |
| itemCount | Integer | 明细行数（几款手办） |
| productSummary | String | 手办摘要，如「初音未来 雪未来 1/7 手办 等 2 款」 |
| inboundTime | String | 入库时间（ISO-8601） |
| operator | String | 经办人，新增时默认当前登录用户姓名 |
| remark | String | 备注，可空 |
| createTime / updateTime | String | 审计时间（ISO-8601） |
| items | `InboundItemVO[]` | **仅详情接口返回**，列表不返回 |

`InboundItemVO`（明细）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long | 主键 |
| productId | Long | 手办商品 ID（`figure_product.id`） |
| productName | String | 手办名称**快照**，手办改名后历史单仍显示当时的名字 |
| productCode | String | 货号快照，可空 |
| quantity | Integer | 入库数量 |
| unitPrice | BigDecimal | 单价（元），可空表示不计金额 |
| amount | BigDecimal | 小计 = 数量 × 单价，后端计算 |
| remark | String | 明细备注，可空 |
| sortNo | Integer | 排序号 |

`InboundSummaryVO`（统计卡）

| 字段 | 类型 | 说明 |
|---|---|---|
| pendingCount | Long | 待入库单数（全量，不受月份限制） |
| monthOrderCount | Long | 本月已入库单数（`inboundTime` 落在当前自然月且状态为 `C`） |
| monthQuantity | Long | 本月已入库件数（明细数量合计） |
| monthAmount | BigDecimal | 本月已入库金额 |

**关键业务规则（后端强制）：**

- **单号**：`inboundNo` 留空时后端按 `RK + yyyyMMdd + 4 位序号` 自动生成（如 `RK202611070001`），当天从 `0001` 递增、撞号自动往后找；手填则在未删除记录内唯一，重复返回 `1002`。
- **仓库 / 区域 / 货架**：`warehouseId` 必填且必须未删除（否则 `1001`）；`areaId` / `shelfId` 可空，填了就必须存在且与 `warehouseId` 同仓库，否则 `1001` / `1003`。
- **明细**：至少一条（空数组返回 `1003`）；同一张单里同一款手办只能出现一次（重复返回 `1003`）；`quantity` 必须 ≥ 1（`1003`）；`unitPrice` 可空、不可为负。
- **合计不信任前端**：`totalQuantity` / `totalAmount` / `amount` 全部由后端按明细重算，请求里传了也会被忽略。
- **状态流转**：`D` 待入库 →（INB-08 确认）→ `C` 已入库；`D` / `C` →（INB-09 作废）→ `X`。**只有 `D` 可编辑 / 删除**（否则 `1000`）；`C` 不能重复确认；`X` 不能确认、不能再作废。
- **部分更新保护**：确认 / 作废只更新 `status`（必要时补 `inboundTime`），不会改动供应商、区域、货架、备注等其他字段。
- **库存联动**：目前尚无 `stock` 库存表，确认入库只改单据状态、不累加库存；库存表落地后在 `InboundService.confirm` 内补充扣加逻辑。
- **权限**：查询 / 详情 / 统计 / 字典——任意登录用户；新增 / 编辑 / 删除 / 确认 / 作废——主管级及以上（ADMIN / GM / 各部门主管）。
- **初始数据**：`InboundDataInitializer` 在入库单表为空时写入 4 条演示单（3 条已入库 / 1 条待入库，含区域与货架关联），依赖仓库(5)→区域(10)→货架(20)→手办(30) 的初始化顺序。

### INB-01 分页查询入库单

`GET /api/inbound/page` · 登录用户 · `InboundController#page`

**入参（Query，`InboundQuery`）**

| 字段 | 类型 | 必填 | 默认 | 约束 | 说明 |
|---|---|---|---|---|---|
| page | long | 否 | 1 | ≥1 | 页码 |
| size | long | 否 | 10 | 1~500 | 每页条数 |
| keyword | String | 否 | — | 模糊 | 同时匹配单号 / 供应商 / 经办人 / **明细里的手办名称** |
| warehouseId | Long | 否 | — | 精确 | 入库仓库 |
| status | String | 否 | — | 精确 | `D` / `C` / `X` |
| inboundType | String | 否 | — | 精确 | `P` / `R` / `T` / `O` |
| startDate | String | 否 | — | `yyyy-MM-dd` | 入库时间起，**含当天** |
| endDate | String | 否 | — | `yyyy-MM-dd` | 入库时间止，**含当天**（内部按次日 00:00 开区间） |

**出参**：`PageResult<InboundOrderVO>`，按 `inboundTime` 倒序、同时间按 `id` 倒序；**不含 `items`**。

**示例**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "records": [
      {
        "id": 3,
        "inboundNo": "RK202611070003",
        "inboundType": "P",
        "inboundTypeText": "采购入库",
        "warehouseId": 1,
        "warehouseCode": "3101",
        "warehouseName": "上海库",
        "areaId": 2,
        "areaName": "B区-收纳箱",
        "shelfId": 1,
        "shelfCode": "01-1",
        "supplier": "ALTER 官方渠道",
        "status": "D",
        "statusText": "待入库",
        "totalQuantity": 14,
        "totalAmount": 5540.00,
        "itemCount": 2,
        "productSummary": "鏈鋸人 帕瓦 1/7 手办 等 2 款",
        "inboundTime": "2026-11-07T10:30:00",
        "operator": "系统管理员"
      }
    ],
    "total": 4,
    "page": 1,
    "size": 10,
    "pages": 1
  }
}
```

> 关键字命中手办名称时，后端先按 `inbound_item.product_name` 反查出单号，再与主表字段做 OR，不会漏掉「只记得手办名、不记得单号」的查询。

**错误码**：`401` 未登录。

### INB-02 入库单详情

`GET /api/inbound/{id}` · 登录用户 · `InboundController#detail`

**出参**：`InboundOrderVO`，额外带 `items`（按 `sortNo`、`id` 升序）。

**错误码**

| code | message | 场景 |
|---|---|---|
| 401 | 见 §1.1 | 未登录 |
| 1001 | 入库单不存在或已被删除 | id 无效或已删除 |

### INB-03 入库记录统计

`GET /api/inbound/summary` · 登录用户 · `InboundController#summary`

**出参**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "pendingCount": 1,
    "monthOrderCount": 2,
    "monthQuantity": 27,
    "monthAmount": 7960.00
  }
}
```

> 「本月」= 当前自然月，只统计状态为 `C`（已入库）且 `inboundTime` 落在本月的单据；`pendingCount` 是全量待入库，避免待办被月份切掉。

**错误码**：`401`。

### INB-04 入库类型 / 状态字典

`GET /api/inbound/dicts` · 登录用户 · `InboundController#dicts`

**出参**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "types": [
      { "code": "P", "label": "采购入库" },
      { "code": "R", "label": "退货入库" },
      { "code": "T", "label": "调拨入库" },
      { "code": "O", "label": "其他入库" }
    ],
    "statuses": [
      { "code": "D", "label": "待入库" },
      { "code": "C", "label": "已入库" },
      { "code": "X", "label": "已作废" }
    ]
  }
}
```

**错误码**：`401`。

### INB-05 新增入库单

`POST /api/inbound` · 主管级及以上 · `InboundController#create`

**入参（Body，`InboundSaveRequest`）**

| 字段 | 类型 | 必填 | 默认 | 约束 | 说明 |
|---|---|---|---|---|---|
| inboundNo | String | 否 | 自动生成 | ≤50，未删除内唯一 | 留空按 `RK + 日期 + 序号` 生成 |
| inboundType | String | 否 | `P` | `^[PRTO]$` | 入库类型 |
| warehouseId | Long | **是** | — | 必须存在 | 入库仓库 |
| areaId | Long | 否 | — | 须与仓库一致 | 入库区域 |
| shelfId | Long | 否 | — | 须与仓库一致 | 入库货架 |
| supplier | String | 否 | — | ≤100 | 供应商 / 来源单位 |
| inboundTime | String | 否 | 当前时间 | ISO-8601 | 入库时间 |
| operator | String | 否 | 当前登录用户姓名 | ≤50 | 经办人 |
| remark | String | 否 | — | ≤500 | 备注 |
| items | `InboundItemRequest[]` | **是** | — | 至少 1 条 | 手办明细 |

`InboundItemRequest`：`productId`（**必填**，手办须存在）、`quantity`（**必填**，≥1）、`unitPrice`（可选，≥0）、`remark`（可选，≤255）。

**出参**：`Result<Long>`，`data` 为新单 id；状态固定为 `D` 待入库。

**示例请求**

```json
{
  "inboundType": "P",
  "warehouseId": 1,
  "areaId": 1,
  "shelfId": 1,
  "supplier": "Good Smile Company 中国代理",
  "inboundTime": "2026-11-07T10:30:00",
  "operator": "系统管理员",
  "remark": "双十一预热备货",
  "items": [
    { "productId": 1, "quantity": 5, "unitPrice": 520.00, "remark": "冬季限定款" },
    { "productId": 3, "quantity": 20, "unitPrice": 180.00 }
  ]
}
```

> `inboundTime` 用 ISO-8601（`yyyy-MM-ddTHH:mm:ss`）。传 `yyyy-MM-dd HH:mm:ss`（空格分隔）会被 Jackson 判为请求体格式错误，返回 `400 请求体格式不正确`。

**错误码**

| code | message | 场景 |
|---|---|---|
| 401 | 见 §1.1 | 未登录 |
| 403 | 没有权限维护入库记录，请联系管理员或主管 | 执行级账号 |
| 400 | 请求体格式不正确 | 字段类型 / 时间格式不合法 |
| 1003 | 请至少添加一条手办明细 | `items` 为空 |
| 1003 | 入库数量必须大于 0 | `quantity` < 1（或参数校验 `400`） |
| 1003 | 同一张入库单里同一款手办只能出现一次 | 明细里 `productId` 重复 |
| 1003 | 所选区域不属于该仓库，请重新选择 | 区域与仓库不匹配 |
| 1001 | 入库仓库不存在或已被删除 | `warehouseId` 无效 |
| 1001 | 手办不存在或已被删除，请重新选择 | `productId` 无效 |
| 1002 | 入库单号已存在：RK2026… | 手填单号重复 |

### INB-06 编辑入库单

`PUT /api/inbound/{id}` · 主管级及以上 · `InboundController#update`

**入参**：`id` + `InboundSaveRequest`（同 INB-05）。

**规则**

- **仅 `D` 待入库可编辑**；`C` / `X` 返回 `1000`。
- 明细**整体替换**：先逻辑删除旧明细再插入提交的明细，保证明细与提交内容完全一致。
- `inboundNo` 留空时沿用原单号；`inboundTime` 留空时沿用原时间；`operator` 留空时**沿用原经办人**（不要因为编辑把责任人抹掉）。
- `areaId` / `shelfId` / `supplier` / `remark` 传 `null` 或空串会真正清空（实体上标了 `FieldStrategy.ALWAYS`）。

**出参**：`Result<Void>`

**错误码**：同 INB-05，另加

| code | message | 场景 |
|---|---|---|
| 1000 | 只有「待入库」的单据可以编辑，当前状态：已入库 | 非草稿 |
| 1001 | 入库单不存在或已被删除 | id 无效 |

### INB-07 删除入库单

`DELETE /api/inbound/{id}` · 主管级及以上 · `InboundController#delete`

**规则**：仅 `D` 可删；逻辑删除主表 + 明细一起删，列表不再显示但数据留在库里。

**出参**：`Result<Void>`

**错误码**

| code | message | 场景 |
|---|---|---|
| 401 | 见 §1.1 | 未登录 |
| 403 | 没有权限删除入库单，请联系管理员或主管 | 执行级账号 |
| 1000 | 只有「待入库」的单据可以删除，当前状态：已入库 | 非草稿 |
| 1001 | 入库单不存在或已被删除 | id 无效 |

### INB-08 确认入库

`PUT /api/inbound/{id}/confirm` · 主管级及以上 · `InboundController#confirm`

**规则**：`D` → `C`；只改状态列（若 `inboundTime` 为空则补当前时间），**不会清空供应商 / 区域 / 货架 / 备注**；没有明细时返回 `1000`。

**出参**：`Result<Void>`，`message` 为「已确认入库」

**错误码**

| code | message | 场景 |
|---|---|---|
| 401 | 见 §1.1 | 未登录 |
| 403 | 没有权限确认入库，请联系管理员或主管 | 执行级账号 |
| 1000 | 只有「待入库」的单据可以确认，当前状态：已入库 | 非草稿（含已作废） |
| 1000 | 该入库单没有手办明细，不能确认入库 | 明细为空 |
| 1001 | 入库单不存在或已被删除 | id 无效 |

### INB-09 作废入库单

`PUT /api/inbound/{id}/cancel` · 主管级及以上 · `InboundController#cancel`

**规则**：`D` / `C` → `X`；同样只改状态列。已作废重复调用返回 `1000`。

**出参**：`Result<Void>`，`message` 为「已作废」

**错误码**

| code | message | 场景 |
|---|---|---|
| 401 | 见 §1.1 | 未登录 |
| 403 | 没有权限作废入库单，请联系管理员或主管 | 执行级账号 |
| 1000 | 该入库单已经作废，无需重复操作 | 状态已是 `X` |
| 1001 | 入库单不存在或已被删除 | id 无效 |

---

## 10. 目录维护接口（作品目录 / 厂商目录）

> 页面入口：左侧菜单 → **目录维护**，下含「作品目录及维护」（§10.1~§10.5 + ACG-01~ACG-12）与「厂商目录维护」（§10.6~§10.8 + MFG-01~MFG-10）。另有供应商、大客户两个占位页，暂未接入接口。

ACG 作品是**主数据**：一方面给商品、订单等业务表提供「作品」下拉选项（ACG-10），另一方面通过热度快照回答「哪些作品现在火、哪些更可能卖得好」（ACG-09）。

厂商目录同样是**主数据**：维护厂牌 Logo、公司名称、创立时间、国家地区，供商品、入库等业务作为「厂商」下拉选项；停用后档案保留但不再出现在下拉中。

### 10.1 数据模型

| 表 | 作用 | 关键约束 |
|---|---|---|
| `acg_work` | 作品档案，一部作品一行 | `name` 必填；`work_code` 未删除记录内唯一；`heat_score` 当前热度 0~100；`monthly_sales` 近 30 天销量；`status` 1 可选 / 0 停用 |
| `acg_heat_log` | 热度快照，作品 + 日期一行 | 同一作品同一天只有一条，重复录入**覆盖**；`work_id + record_date` 建普通索引 |

**为什么不用一张表只存当前值**：只存当前热度算不出「在涨还是在跌」，而选品判断恰恰依赖趋势。快照让每次录入都留痕，趋势图与环比直接由它算，不需要维护冗余字段。

**为什么 `work_code` 不建 UNIQUE**：本表逻辑删除，被删记录会长期占用索引导致无法重建同编号；唯一性由 Service 在「未删除记录」内校验（同 `warehouse`）。

### 10.2 潜力分与四象限口径

```
潜力分（0~100）= 热度 × 45% + 销量分 × 35% + 趋势分 × 20%
  销量分 = 本作品近 30 天销量 ÷ 全库最高月销量 × 100
  趋势分 = 50 + 热度环比涨跌幅(%) × 2，夹在 0~100（无历史快照按 50 中性）
等级：S ≥ 75 重点主推 / A ≥ 62 积极备货 / B ≥ 48 常规跟进 / C < 48 谨慎·淘汰
```

四象限的十字线取**全库平均热度 / 平均月销**，不是固定阈值——作品整体水平变化后象限自动重算，避免全部挤在一角：

| quadrant | 文案 | 含义 |
|---|---|---|
| STAR | 双高 · 主推 | 既火又好卖，优先备货 |
| HEAT_ONLY | 高热度 · 待转化 | 叫好不叫座，看是否缺货/定价问题 |
| SALES_ONLY | 低热度 · 闷声卖货 | 话题度低但卖得动，可加推 |
| LOW | 双低 · 谨慎 | 控制库存深度 |

系数、阈值集中在 `AcgPotentialCalculator`；调整口径只改这一处，页面展示的 `formula` 也来自同一套定义。

### 10.3 查询参数 `AcgWorkQuery`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| page / size | Integer | 否 | 默认 1 / 10，size ≤ 100 |
| name | String | 否 | 作品名或原名，模糊 |
| workCode | String | 否 | 编号，模糊 |
| workType | String | 否 | 类型精确匹配，见 §13.5 |
| releaseStatus | String | 否 | 连载状态精确匹配，见 §13.6 |
| tag | String | 否 | 标签模糊 |
| releaseYear | Integer | 否 | 发行年份精确匹配 |
| minHeat / maxHeat | Integer | 否 | 热度区间（含端点），0~100 |
| status | Integer | 否 | 1 可选 / 0 停用 |
| sortBy | String | 否 | `heatDesc`（默认）/ `heatAsc` / `salesDesc` / `salesAsc` / `yearDesc` / `createDesc` |

### 10.4 保存参数 `AcgWorkSaveRequest`

| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| name | String | **是** | ≤ 120 字符；同一 `workType` 下不可重名 |
| workType | String | **是** | §13.5 |
| workCode | String | 否 | ≤ 50；未删除记录内唯一 |
| originalName | String | 否 | ≤ 160 |
| ipOwner / studio / author | String | 否 | 各 ≤ 100 |
| releaseYear | Integer | 否 | 1950~2100 |
| releaseStatus | String | 否 | §13.6，默认 `FINISHED` |
| heatScore | Integer | 否 | 0~100；新增时填写会顺带写一条当日快照 |
| monthlySales / totalSales | Integer | 否 | ≥ 0 |
| tags | String[] | 否 | 去重后逗号拼接落库 |
| coverImage | String | 否 | 必须来自 ACG-12 返回的 URL，不要传 `blob:` |
| remark | String | 否 | ≤ 500 |
| status | Integer | 否 | 默认 1 |

> 编辑时若 `heatScore` 与库中不同，会额外写一条当日快照（同日覆盖），保证趋势与列表一致；不传 `heatScore` 则保持原值不动。

### 10.5 热度录入 `HeatRecordRequest`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| heatScore | Integer | **是** | 0~100 |
| monthlySales | Integer | 否 | ≥ 0；传入时同步作品的当前月销 |
| recordDate | Date | 否 | 默认今天，不可晚于今天；同日重复录入覆盖当天值 |
| remark | String | 否 | ≤ 255，如「新番开播」「剧场版上映」 |

---

### ACG-01 分页查询作品

`GET /api/acg/page` · 登录用户 · `AcgWorkController#page`

**入参**：Query，见 §10.3。

**出参**：`Result<PageResult<AcgWorkVO>>`，每条都带算好的 `trendRate` / `potentialScore` / `potentialLevel`。

**错误码**

| code | message | 场景 |
|---|---|---|
| 401 | 见 §1.1 | 未登录 |
| 1003 | 页码从 1 开始 / 每页最多 100 条 / 热度不能大于 100 | 分页或热度区间越界 |

### ACG-02 作品详情

`GET /api/acg/{id}` · 登录用户 · `AcgWorkController#detail`

**出参**：`Result<AcgWorkVO>`

**错误码**

| code | message | 场景 |
|---|---|---|
| 401 | 见 §1.1 | 未登录 |
| 1001 | 作品不存在或已被删除 | id 无效或已删除 |

### ACG-03 新增作品

`POST /api/acg` · 主管级及以上 · `AcgWorkController#create`

**入参**：Body `AcgWorkSaveRequest`（§10.4）

**出参**：`Result<Long>`（新作品 id）

**错误码**

| code | message | 场景 |
|---|---|---|
| 401 | 见 §1.1 | 未登录 |
| 403 | 没有权限维护 ACG 作品，请联系管理员或主管 | 执行级 |
| 1002 | 该作品已存在：xxx（动画） | 同类型下重名 |
| 1002 | 作品编号已存在：xxx | 编号重复 |
| 1003 | 作品类型不合法，可选值：… | 类型不在字典内 |
| 1003 | 连载状态不合法，可选值：… | 状态不在字典内 |
| 1003 | 热度不能大于 100 / 热度必须在 0~100 之间 | 热度越界 |

### ACG-04 编辑作品

`PUT /api/acg/{id}` · 主管级及以上 · `AcgWorkController#update`

**入参**：Path `id` + Body `AcgWorkSaveRequest`

**出参**：`Result<Void>`

**错误码**：同 ACG-03，另加 `1001 作品不存在或已被删除`。重名校验会排除自身。

### ACG-05 删除作品

`DELETE /api/acg/{id}` · 主管级及以上 · `AcgWorkController#delete`

逻辑删除作品，并**一并逻辑删除它的热度快照**（避免以后 id 复用时读到旧曲线）。

**出参**：`Result<Void>`

**错误码**

| code | message | 场景 |
|---|---|---|
| 401 | 见 §1.1 | 未登录 |
| 403 | 没有权限删除 ACG 作品，请联系管理员或主管 | 执行级 |
| 1001 | 作品不存在或已被删除 | id 无效或已删除 |

### ACG-06 启用 / 停用作品

`PUT /api/acg/{id}/status?status=1` · 主管级及以上 · `AcgWorkController#updateStatus`

停用后该作品不再出现在 ACG-10 的下拉选项中，但历史数据与统计仍保留。

**入参**：Query `status`，`1` 可选 / `0` 停用

**出参**：`Result<Void>`

**错误码**

| code | message | 场景 |
|---|---|---|
| 401 | 见 §1.1 | 未登录 |
| 403 | 没有权限修改 ACG 作品状态，请联系管理员或主管 | 执行级 |
| 1003 | 状态值只能是 0 或 1 | 非法状态 |
| 1001 | 作品不存在或已被删除 | id 无效 |

### ACG-07 录入当期热度

`POST /api/acg/{id}/heat` · 主管级及以上 · `AcgWorkController#recordHeat`

写一条热度快照（**同一天重复录入则覆盖当天那条**），并把作品的 `heatScore` / `monthlySales` / `heatUpdatedAt` 同步为最新值。这是趋势分析的唯一数据来源，日常运营应走这个接口而不是编辑档案。

**入参**：Path `id` + Body `HeatRecordRequest`（§10.5）

**出参**：`Result<Void>`

**错误码**

| code | message | 场景 |
|---|---|---|
| 401 | 见 §1.1 | 未登录 |
| 403 | 没有权限录入 ACG 作品热度，请联系管理员或主管 | 执行级 |
| 1003 | 记录日期不能晚于今天 | recordDate 在未来 |
| 1003 | 热度不能大于 100 | 热度越界 |
| 1001 | 作品不存在或已被删除 | id 无效 |

### ACG-08 作品热度趋势

`GET /api/acg/{id}/trend?days=90` · 登录用户 · `AcgWorkController#trend`

**入参**：Path `id`；Query `days` 可选，默认 90，范围 7~730（超出按边界截断）

**出参**：`Result<AcgHeatPointVO[]>`，按日期正序：

| 字段 | 类型 | 说明 |
|---|---|---|
| recordDate | Date | 记录日期，`yyyy-MM-dd` |
| heatScore | Integer | 当日热度 |
| monthlySales | Integer | 当日近 30 天销量 |
| remark | String | 备注 |

> 新建作品当日只录过一次时会只有一个点，图上表现为单点——这是数据事实，不是接口异常。

### ACG-09 选品分析总览

`GET /api/acg/analysis` · 登录用户 · `AcgWorkController#analysis`

**出参**：`Result<AcgAnalysisVO>`，结构见 §3.8。包含 KPI、潜力排行、四象限点、类型分布、中文结论与口径说明。

**示例（节选）**

```json
{
  "code": 200,
  "data": {
    "kpi": {
      "workCount": 14, "selectableCount": 14, "avgHeat": 76.4,
      "monthlySalesTotal": 64600, "avgPotential": 62.7,
      "levelSCount": 5, "levelACount": 3, "levelBCount": 2, "levelCCount": 4,
      "topTypeText": "动画", "topTypeCount": 5
    },
    "insights": [
      "当前带货王：《咒术回战》近 30 天卖出 9,200 件，热度 95，属于「重点主推」档，优先保证它的备货与排期。",
      "涨得最快：《蓝色监狱》热度环比 20.0%，是近期话题度上升最明显的作品，适合提前锁定下一批货。"
    ],
    "formula": "潜力分 = 热度 × 45% + 销量分 × 35% + 趋势分 × 20%；…"
  }
}
```

### ACG-10 作品下拉选项

`GET /api/acg/options?keyword=xxx` · 登录用户 · `AcgWorkController#options`

供其他业务表选择作品。**只返回 `status=1` 的作品**，按热度倒序，最多 200 条。

**出参**：`Result<AcgOptionVO[]>`

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long | 作品 ID（其他表存这个） |
| name | String | 作品名称 |
| workType / workTypeText | String | 类型码 / 中文 |
| releaseYear | Integer | 发行年份 |
| heatScore | Integer | 当前热度，选品时参考 |

### ACG-11 作品类型字典

`GET /api/acg/types` · 登录用户 · `AcgWorkController#types`

**出参**：`Result<Map>`，形如：

```json
{
  "code": 200,
  "data": {
    "workTypes": { "ANIME": "动画", "MANGA": "漫画", "GAME": "游戏", "NOVEL": "轻小说", "MOVIE": "剧场版", "OTHER": "其他" },
    "releaseStatuses": { "ONGOING": "连载中", "FINISHED": "已完结", "UPCOMING": "待播出" }
  }
}
```

> 前端也内置了一份同码值字典（`front/src/views/acg/acgDict.ts`），此接口供外部系统或后续做动态字典时使用。

### ACG-12 上传作品封面

`POST /api/acg/upload` · 登录用户 · `AcgWorkController#upload`

**入参**：Multipart `file`，仅图片（jpg / jpeg / png / gif / webp），单张 ≤ 5MB

**出参**：`Result<String>`，返回可直访 URL，形如 `/uploads/acg/<uuid>.png`

**错误码**

| code | message | 场景 |
|---|---|---|
| 401 | 见 §1.1 | 未登录 |
| 1003 | 上传文件为空 / 图片过大，单张不可超过 5 MB | 空文件或超限 |
| 1003 | 仅支持 JPG / PNG / GIF / WEBP 图片 | content-type 不是图片 |

> 文件落盘到 `server/uploads/acg/`，通过 `WebMvcConfig` 的 `/uploads/**` 静态映射对外访问；与商品图片（`/uploads/products/`）分目录存放，便于分别清理与备份。

### 10.6 厂商数据模型

| 表 | 作用 | 关键约束 |
|---|---|---|
| `manufacturer` | 厂商档案，一家厂商一行 | `name` 必填且未删除记录内唯一；`status` 1 启用 / 0 停用；`logo` / `founded_date` / `country` / `remark` 可空 |

**为什么 `name` 不建 UNIQUE**：本表逻辑删除，被删记录会长期占用索引导致无法重建同名厂商；唯一性由 Service 在「未删除记录」内校验（同 `warehouse` / `acg_work`）。

**为什么编辑用 UpdateWrapper 显式 set 每一列**：用户可能把 Logo / 国家 / 备注清空，`updateById` 默认跳过 null 会把「清空」当成「不修改」，因此编辑时显式写 null。

### 10.7 查询参数 `ManufacturerQuery`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| page / size | Integer | 否 | 默认 1 / 10，size ≤ 100 |
| keyword | String | 否 | 公司名称或国家地区，模糊 |
| country | String | 否 | 国家 / 地区，精确匹配 |
| status | Integer | 否 | 1 启用 / 0 停用 |
| sortBy | String | 否 | `createDesc`（默认）/ `nameAsc` / `foundedAsc` / `foundedDesc` |

### 10.8 保存参数 `ManufacturerSaveRequest`

| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| name | String | **是** | ≤ 120 字符；未删除记录内不可重名 |
| logo | String | 否 | ≤ 255；须来自 MFG-10 返回的 URL，不要传 `blob:` |
| foundedDate | String | 否 | `yyyy-MM-dd`，可空 |
| country | String | 否 | ≤ 50，如 日本 / 中国香港 / 美国 |
| remark | String | 否 | ≤ 500 |
| status | Integer | 否 | 0 / 1，新增默认 1；编辑不传则保持原状态 |

> 编辑时传空字符串（`logo` / `country` / `remark`）或 `null`（`foundedDate`）表示**清空**该字段。

### MFG-01 分页查询厂商

`GET /api/manufacturer/page` · 登录用户 · `ManufacturerController#page`

**入参**：`ManufacturerQuery`（见 §10.7）

**出参**：`Result<PageResult<ManufacturerVO>>`，`records` 为 §3.9 结构，按 `createDesc` 默认倒序。

**错误码**

| code | message | 场景 |
|---|---|---|
| 401 | 见 §1.1 | 未登录 |
| 1003 | 页码从 1 开始 / 每页最多 100 条 | page / size 越界 |

### MFG-02 厂商详情

`GET /api/manufacturer/{id}` · 登录用户 · `ManufacturerController#detail`

**出参**：`Result<ManufacturerVO>`

| code | message | 场景 |
|---|---|---|
| 1001 | 厂商不存在或已被删除 | id 无效 / 已删除 |

### MFG-03 新增厂商

`POST /api/manufacturer` · 主管级及以上 · `ManufacturerController#create`

**入参**：`ManufacturerSaveRequest`（见 §10.8）

**出参**：`Result<Long>`，返回新增厂商 ID。

| code | message | 场景 |
|---|---|---|
| 403 | 没有权限维护厂商目录，请联系管理员或主管 | 执行级账号 |
| 1002 | 该厂商已存在：{name} | 名称重复 |
| 1003 | 公司名称不能为空 / 状态值只能是 0 或 1 | 校验失败 |

### MFG-04 编辑厂商

`PUT /api/manufacturer/{id}` · 主管级及以上 · `ManufacturerController#update`

**入参**：`id` + `ManufacturerSaveRequest`（见 §10.8）

**出参**：`Result<Void>`

> 全量保存：传空字符串 / null 会**清空**对应字段；不传 `status` 则保持原状态。重名校验排除自身。

| code | message | 场景 |
|---|---|---|
| 403 | 同上 | 执行级账号 |
| 1001 | 厂商不存在或已被删除 | id 无效 |
| 1002 | 该厂商已存在：{name} | 改成与其他厂商同名 |

### MFG-05 删除厂商

`DELETE /api/manufacturer/{id}` · 主管级及以上 · `ManufacturerController#delete`

**出参**：`Result<Void>`（逻辑删除，`deleted=1`）

| code | message | 场景 |
|---|---|---|
| 403 | 同上 | 执行级账号 |
| 1001 | 厂商不存在或已被删除 | id 无效 |

### MFG-06 启用 / 停用厂商

`PUT /api/manufacturer/{id}/status?status=0|1` · 主管级及以上 · `ManufacturerController#updateStatus`

**出参**：`Result<Void>`

> 停用后不再出现在 MFG-09 下拉选项；详情 / 列表仍可查到。

| code | message | 场景 |
|---|---|---|
| 403 | 同上 | 执行级账号 |
| 1001 | 厂商不存在或已被删除 | id 无效 |
| 1003 | 状态值只能是 0 或 1 | status 非法 |

### MFG-07 厂商目录统计

`GET /api/manufacturer/summary` · 登录用户 · `ManufacturerController#summary`

**出参**：`Result<ManufacturerSummaryVO>`

| 字段 | 类型 | 说明 |
|---|---|---|
| total | Long | 厂商总数（未删除） |
| enabled | Long | 启用数量 |
| disabled | Long | 停用数量 |
| countryCount | Long | 覆盖国家 / 地区数 |

### MFG-08 国家地区筛选项

`GET /api/manufacturer/countries` · 登录用户 · `ManufacturerController#countries`

**出参**：`Result<String[]>`，已录入厂商出现过的国家地区，去重升序。

### MFG-09 厂商下拉选项

`GET /api/manufacturer/options?keyword=` · 登录用户 · `ManufacturerController#options`

**出参**：`Result<ManufacturerOptionVO[]>`，仅返回启用厂商（最多 200 条，按名称升序）：

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long | 厂商 ID（其他表存这个） |
| name | String | 公司 / 厂牌名称 |
| logo | String | 厂牌 Logo URL，可空 |
| country | String | 国家 / 地区 |

### MFG-10 上传厂商 Logo

`POST /api/manufacturer/upload` · 登录用户 · `ManufacturerController#upload`

**入参**：Multipart `file`，仅图片（jpg / jpeg / png / gif / webp），单张 ≤ 5MB

**出参**：`Result<String>`，返回可直访 URL，形如 `/uploads/manufacturer/<uuid>.png`

**错误码**

| code | message | 场景 |
|---|---|---|
| 401 | 见 §1.1 | 未登录 |
| 1003 | 上传文件为空 / 图片过大，单张不可超过 5 MB | 空文件或超限 |
| 1003 | 仅支持 JPG / PNG / GIF / WEBP 图片 | content-type 不是图片 |

> 文件落盘到 `server/uploads/manufacturer/`，与商品图片、作品封面分目录存放。

---

## 11. 个人消息接口

> 页面入口：右上角用户菜单 → **个人消息**（不占左侧菜单）。点开后是右侧抽屉：看列表 → 点某条看详情 → 填处理备注后「去处理」跳转到关联业务页面；抽屉底部「查看全部消息」进入 `/message/list` 列表页。

**隔离规则**：所有接口都限定「接收人 = 当前登录用户」，Service 逐条校验，查/改他人消息统一返回 `403`。入参里没有 `receiverId`，从设计上杜绝越权。

**状态流转**：`U 未读` → （查看详情 / 标记已读）→ `R 已读` → （去处理）→ `H 已处理`。已处理是终态，用来区分「看过了」和「办完了」；`handled=true` 的消息前端不再显示「去处理」按钮。

**给其他模块用**：审批流、订单、库存预警要推消息时，**不要**直接写 `sys_message` 表，调用 `MessageService.push(MessagePush)`：

```java
messageService.push(MessagePush.ofBiz(
        receiverId, "入库单待审批", "单号 RK20261106001，共 86 件",
        MessageDict.TYPE_APPROVAL, "INBOUND", inboundId, "/stock/inbound"));
```

`push` 不做权限校验（供内部流程调用），只做数据兜底：类型 / 优先级非法时回落到 `SYSTEM` / `N`，标题为空返回 `1003`。

### MSG-01 分页查询我的消息

`GET /api/message/page` · 登录用户 · `MessageController#page`

**入参**：Query

| 字段 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| page | long | 否 | 1 | 页码，从 1 开始 |
| size | long | 否 | 20 | 每页条数，1~200 |
| msgType | String | 否 | — | 类型精确匹配，见 §13.8 |
| status | String | 否 | — | `U` / `R` / `H`，见 §13.9 |
| priority | String | 否 | — | `N` 普通 / `U` 紧急 |
| keyword | String | 否 | — | 标题或正文模糊匹配 |

**出参**：`Result<PageResult<MessageVO>>`

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long | 消息 ID |
| title | String | 标题 |
| content | String | 正文 |
| msgType / msgTypeText | String | 类型码 / 中文 |
| bizType | String | 业务对象类型，如 `ORDER` / `PRODUCT` / `INBOUND` |
| bizId | Long | 业务对象 ID，用于从消息定位单据 |
| bizUrl | String | 「去处理」跳转的前端路由；为空表示纯通知 |
| senderName | String | 发送人姓名，系统产生的消息为 `系统` |
| priority / priorityText | String | 优先级码 / 中文 |
| status / statusText | String | 状态码 / 中文 |
| unread / handled / urgent | Boolean | 派生布尔值，前端打红点、控按钮直接用 |
| readTime | DateTime | 首次读取时间 |
| handleTime | DateTime | 处理完成时间 |
| handleRemark | String | 处理备注 |
| createTime | DateTime | 创建时间 |

> 排序固定为「创建时间倒序 + id 倒序」，同秒创建的记录分页也不会重复或漏项。

**错误码**：`401` 未登录

### MSG-02 未读统计

`GET /api/message/summary` · 登录用户 · `MessageController#summary`

右上角头像红点、菜单里的未读数、下拉面板与消息中心顶部统计都用这一个接口，前端默认 60 秒轮询一次。

**出参**：`Result<MessageSummaryVO>`

| 字段 | 类型 | 说明 |
|---|---|---|
| total | Long | 消息总数 |
| unread | Long | 未读数 |
| urgentUnread | Long | 紧急且未读的条数 |
| handled | Long | 已处理条数 |
| byType | Array | 按类型统计，**固定返回全部类型**（含 0），顺序为审批 → 订单 → 库存 → 系统 |

`byType` 元素：

| 字段 | 类型 | 说明 |
|---|---|---|
| type | String | 类型码 |
| typeText | String | 类型中文 |
| total | Long | 该类型总数 |
| unread | Long | 该类型未读数 |

### MSG-03 消息类型字典

`GET /api/message/types` · 登录用户 · `MessageController#types`

**出参**：`Result<Array<{code, text}>>`，顺序固定：

```json
{ "code": 200, "data": [
  { "code": "APPROVAL", "text": "审批消息" },
  { "code": "ORDER", "text": "订单消息" },
  { "code": "INVENTORY", "text": "库存消息" },
  { "code": "SYSTEM", "text": "系统消息" }
] }
```

> 以后新增消息种类只需在 `MessageDict` 里追加一项，表结构不用改；前端 Tab 也按这个接口渲染。

### MSG-04 消息详情

`GET /api/message/{id}` · 登录用户 · `MessageController#detail`

**未读消息查看详情会自动置为已读**（并写 `readTime`），前端不用再单独调一次标记已读。

**出参**：`Result<MessageVO>`，结构见 MSG-01。

**错误码**

| code | message | 场景 |
|---|---|---|
| 401 | 见 §1.1 | 未登录 |
| 403 | 无权操作他人的消息 | id 属于别人 |
| 1001 | 消息不存在或已被删除 | id 无效 |

### MSG-05 标记已读

`PUT /api/message/{id}/read` · 登录用户 · `MessageController#markRead`

幂等：已读 / 已处理的消息重复调用不报错，也不改 `readTime`。

**出参**：`Result<Void>`

### MSG-06 全部标为已读

`PUT /api/message/read-all?msgType=APPROVAL` · 登录用户 · `MessageController#markAllRead`

**入参**：Query `msgType` 可选；不传表示所有类型。只会把 `U` 的记录改成 `R`，不动已处理和已读的。

**出参**：`Result<Integer>`，实际更新条数（0 时 message 为「没有未读消息」）。

### MSG-07 处理消息

`PUT /api/message/{id}/handle` · 登录用户 · `MessageController#handle`

把状态置为 `H 已处理`、写 `handleTime`；如果消息从没读过，顺带补 `readTime`，避免出现「已处理但从未读」的矛盾数据。

**入参**：Path `id` + Body `MessageHandleRequest`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| remark | String | 否 | 处理备注 / 处理结果，≤ 255 字 |

**出参**：`Result<Void>`

### MSG-08 删除消息

`DELETE /api/message/{id}` · 登录用户 · `MessageController#delete`

逻辑删除，只能删自己的消息。

**出参**：`Result<Void>`

**错误码**：`403` 无权操作他人的消息 / `1001` 消息不存在或已被删除

### MSG-09 发送消息

`POST /api/message` · 主管级及以上 · `MessageController#send`

手工给指定员工发一条站内消息，用于运营通知与联调。业务模块内部的自动推送走 `MessageService.push`，不经过这个接口。

**入参**：Body `MessageSendRequest`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| receiverId | Long | 是 | 接收人 ID（`sys_user.id`），必须存在 |
| title | String | 是 | 标题，≤ 120 字 |
| content | String | 否 | 正文，≤ 1000 字 |
| msgType | String | 否 | `APPROVAL` / `ORDER` / `INVENTORY` / `SYSTEM`，默认 `SYSTEM` |
| bizType | String | 否 | 业务对象类型，≤ 30 字 |
| bizId | Long | 否 | 业务对象 ID |
| bizUrl | String | 否 | 「去处理」跳转路由，≤ 255 字 |
| priority | String | 否 | `N` 普通 / `U` 紧急，默认 `N` |
| remark | String | 否 | 备注，≤ 500 字 |

**出参**：`Result<Long>`，新消息 ID

**错误码**

| code | message | 场景 |
|---|---|---|
| 401 | 见 §1.1 | 未登录 |
| 403 | 没有权限发送消息，请联系管理员或主管 | 执行级 |
| 1003 | 请填写消息标题 / 标题不能超过 120 个字符 … | 参数校验失败 |
| 1001 | 接收人不存在或已被删除 | receiverId 无效 |

---

## 12. 个人待办接口

页面：**工作台**（`/home`）→ 今日工作计划。所有角色都能用，数据按当前登录用户隔离 —— 没有「查看别人待办」的接口，也没有「主管才能建待办」的限制，待办就是每个人的记事本。

表 `work_todo`，一条记录 = 某个人给自己排的一件事，用 `plan_date` 落到某一天。与 §11 个人消息的分工：

| | 个人消息 `sys_message` | 个人待办 `work_todo` |
|---|---|---|
| 谁产生 | 别人推给你（审批 / 订单 / 库存 / 系统） | 自己记下来 |
| 结束状态 | `H` 已处理 | `D` 已完成 |
| 在工作台的位置 | 右栏「待办事项」，点「去处理」跳业务页 | 左栏「今日工作计划」，勾选框直接完成 |

两者并排展示但**互不生成**：消息点「去处理」不会自动建待办，避免系统臆测「谁该干什么」。

### 12.1 共享结构 `TodoVO`

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long | 主键 |
| title | String | 待办标题 |
| content | String | 补充说明，可空 |
| planDate | String | 计划日期（`yyyy-MM-dd`） |
| priority | String | `H` 高 / `N` 普通 / `L` 低，见 §13.12 |
| priorityText | String | 后端翻译好的优先级文案 |
| status | String | `P` 待办 / `D` 已完成，见 §13.13 |
| statusText | String | 后端翻译好的状态文案 |
| done | Boolean | 是否已完成（= `status === 'D'`），前端勾选框直接用 |
| overdue | Boolean | 是否逾期（`planDate` 早于今天且未完成） |
| finishTime | String | 完成时间，未完成为空 |
| sortNo | Integer | 排序号 |
| createTime | String | 创建时间（ISO-8601） |

### 12.2 共享结构 `TodayTodoVO`

| 字段 | 类型 | 说明 |
|---|---|---|
| date | String | 今天日期（`yyyy-MM-dd`） |
| weekdayText | String | 星期文案，如 `星期六` |
| total | Long | 今日待办总数 |
| done | Long | 今日已完成 |
| pending | Long | 今日未完成（= `total - done`） |
| overdue | Long | 逾期未完成条数：`plan_date < 今天` 且 `status = 'P'`，**不含今天** |
| rate | Integer | 今日完成率 0~100，`total = 0` 时为 0 |
| items | `TodoVO[]` | 今日待办列表，排序规则见 TODO-01 |

**关键业务规则（后端强制）：**

- **数据只属于自己**：查询恒带 `user_id = 当前登录用户`；按 id 操作时先查出来比对归属，别人的待办返回 `403 只能操作自己的待办`。
- **状态只能通过 `TODO-07` 改**：编辑接口不接受 `status`，避免「编辑标题时顺手把已完成改回待办」。标记完成时写 `finish_time`，改回 `P` 时清空（字段策略 `ALWAYS`，否则 MyBatis-Plus 会跳过 null）。
- **优先级只能是 `H` / `N` / `L`**，不传默认 `N`；非法值返回 `1003 优先级只能是 H、N 或 L`。
- **排序**：未完成在前 → 优先级（高 → 普通 → 低）→ `sortNo` → 新建的在前（`id` 倒序）。分页接口用 SQL 的 `plan_date DESC, FIELD(status,'P','D'), sort_no ASC, id DESC`，口径一致。
- **计划日期默认今天**：新增时不传 `planDate` 就落到今天，工作台只展示当天；传昨天的日期会立刻计入 `overdue`。

### TODO-01 今日待办汇总

`GET /api/todo/today` · 登录用户 · `WorkTodoController#today`

工作台首屏只发这一个请求：列表 + 统计一次返回。

**出参**：`Result<TodayTodoVO>`，结构见 §12.2

**示例**

```json
{ "code": 200, "message": "操作成功", "data": {
  "date": "2026-11-07", "weekdayText": "星期六",
  "total": 5, "done": 2, "pending": 3, "overdue": 1, "rate": 40,
  "items": [
    { "id": 1, "title": "整理 11 月新品上架清单", "content": "按 IP 分类，标出限定款",
      "planDate": "2026-11-07", "priority": "H", "priorityText": "高",
      "status": "P", "statusText": "待办", "done": false, "overdue": false,
      "sortNo": 0, "createTime": "2026-11-07T09:07:29" }
  ]
} }
```

**错误码**：`401` 未登录

### TODO-02 分页查询我的待办

`GET /api/todo/page` · 登录用户 · `WorkTodoController#page`

**入参（Query，`TodoQuery`）**

| 字段 | 类型 | 必填 | 默认 | 约束 | 说明 |
|---|---|---|---|---|---|
| page | long | 否 | 1 | ≥1 | 页码 |
| size | long | 否 | 20 | 1~200 | 每页条数 |
| keyword | String | 否 | — | 模糊 | 同时匹配标题与补充说明 |
| status | String | 否 | — | 精确 | `P` / `D` |
| priority | String | 否 | — | 精确 | `H` / `N` / `L` |
| startDate | String | 否 | — | `yyyy-MM-dd` | 计划日期起（含） |
| endDate | String | 否 | — | `yyyy-MM-dd` | 计划日期止（含） |

**出参**：`Result<PageResult<TodoVO>>`，元素结构见 §12.1

**示例**

```http
GET /api/todo/page?page=1&size=20&startDate=2026-11-01&endDate=2026-11-30&status=P
```

**错误码**：`401` 未登录；`1003` 页码 / 每页条数越界

### TODO-03 待办字典

`GET /api/todo/dicts` · 登录用户 · `WorkTodoController#dicts`

**出参**：`Result<Map>`，两个固定顺序的数组

```json
{ "code": 200, "message": "操作成功", "data": {
  "priorities": [ { "code": "H", "text": "高" }, { "code": "N", "text": "普通" }, { "code": "L", "text": "低" } ],
  "statuses":   [ { "code": "P", "text": "待办" }, { "code": "D", "text": "已完成" } ]
} }
```

### TODO-04 待办详情

`GET /api/todo/{id}` · 登录用户 · `WorkTodoController#detail`

**出参**：`Result<TodoVO>`，结构见 §12.1

**错误码**

| code | message | 场景 |
|---|---|---|
| 401 | 见 §1.1 | 未登录 |
| 1001 | 待办不存在或已被删除 | id 无效或已删除 |
| 403 | 只能操作自己的待办 | 该待办属于别人 |

### TODO-05 新增待办

`POST /api/todo` · 登录用户 · `WorkTodoController#create`

**入参（Body，`TodoSaveRequest`）**

| 字段 | 类型 | 必填 | 约束 | 说明 |
|---|---|---|---|---|
| title | String | 是 | ≤120 | 待办标题，空白返回 `1003 请填写待办标题` |
| content | String | 否 | ≤500 | 补充说明 |
| planDate | String | 否 | `yyyy-MM-dd` | 不传默认今天 |
| priority | String | 否 | `H`/`N`/`L` | 不传默认 `N` |
| sortNo | Integer | 否 | — | 排序号，默认 0 |

**出参**：`Result<Long>`，新待办 ID

**错误码**

| code | message | 场景 |
|---|---|---|
| 401 | 见 §1.1 | 未登录 |
| 1003 | 请填写待办标题 / 标题不能超过 120 个字符 / 优先级只能是 H、N 或 L | 参数校验失败 |

### TODO-06 编辑待办

`PUT /api/todo/{id}` · 登录用户 · `WorkTodoController#update`

入参同 TODO-05（全量提交）。**不含 `status`**：勾选完成请用 TODO-07。`content` 传空字符串表示清空（后端写 NULL）。

**出参**：`Result<Void>`

**错误码**：同 TODO-05，另加 `1001` 待办不存在或已被删除、`403` 只能操作自己的待办

### TODO-07 勾选 / 取消完成

`PUT /api/todo/{id}/status?status=D` · 登录用户 · `WorkTodoController#updateStatus`

**入参**：Query `status`，`P` 待办 / `D` 已完成

**出参**：`Result<Void>`；message 为「已完成」或「已恢复为待办」

**错误码**

| code | message | 场景 |
|---|---|---|
| 401 | 见 §1.1 | 未登录 |
| 1003 | 待办状态只能是 P 或 D | 非法状态值 |
| 1001 | 待办不存在或已被删除 | id 无效或已删除 |
| 403 | 只能操作自己的待办 | 该待办属于别人 |

### TODO-08 删除待办

`DELETE /api/todo/{id}` · 登录用户 · `WorkTodoController#delete`

逻辑删除：数据保留在库中，列表不再返回。

**出参**：`Result<Void>`

**错误码**：`401` 未登录、`1001` 待办不存在或已被删除、`403` 只能操作自己的待办

---

## 13. 枚举与常量

> 13.2「涉密等级」已随业务调整移除（v1.9），后续小节编号保持不变，避免破坏既有引用。

### 13.1 角色 `role`

| code | 文案 | group 板块 | level 层级 |
|---|---|---|:--:|
| ADMIN | 管理员 | 系统 | 3 管理级 |
| GM | 总经理 | 管理层 | 3 管理级 |
| FINANCE_MANAGER | 财务主管 | 财务 | 2 主管级 |
| FINANCE_STAFF | 财务专员 | 财务 | 1 执行级 |
| SALES_MANAGER | 销售主管 | 销售 | 2 主管级 |
| SALES_STAFF | 销售专员 | 销售 | 1 执行级 |
| LOGISTICS_MANAGER | 物流主管 | 物流 | 2 主管级 |
| LOGISTICS_STAFF | 物流专员 | 物流 | 1 执行级 |
| HR_MANAGER | 人事主管 | 人事 | 2 主管级 |
| HR_STAFF | 人事专员 | 人事 | 1 执行级 |
| MANAGER | 主管（通用） | 通用 | 2 主管级 |
| STAFF | 员工（通用） | 通用 | 1 执行级 |

> `MANAGER` / `STAFF` 是旧版通用角色，只为兼容存量账号保留，新录入请选具体角色。
> 新增角色只需在 `UserRole` 枚举追加一行（指定 group 与 level），表结构不用改，字典接口与前端下拉自动带上。

### 13.3 物流供应商 `supplierCode`

| code | 名称 | 参考首重价（元） | 参考续重价（元/kg） |
|---|---|---|---|
| SF | 顺丰速运 | 12.00 | 2.00 |
| JD | 京东物流 | 10.00 | 2.00 |
| ZTO | 中通快递 | 6.00 | 1.50 |
| YTO | 圆通速递 | 6.00 | 1.50 |
| YUNDA | 韵达快递 | 5.50 | 1.50 |
| STO | 申通快递 | 5.50 | 1.50 |
| JTSD | 极兔速递 | 5.00 | 1.50 |
| EMS | 中国邮政EMS | 12.00 | 3.00 |

### 13.4 运费区域 `zone`

| code | 中文 | 首重加价 | 续重加价 | 时效加价 |
|---|---|---|---|---|
| EAST | 华东 | 0 | 0 | 0 |
| NORTH | 华北 | 2 | 1 | 1 |
| CENTRAL | 华中 | 2 | 1 | 1 |
| SOUTH | 华南 | 2 | 1 | 1 |
| NORTHEAST | 东北 | 4 | 2 | 2 |
| SOUTHWEST | 西南 | 4 | 2 | 2 |
| NORTHWEST | 西北 | 6 | 3 | 3 |
| XINJIANG | 新疆 | 10 | 5 | 4 |
| TIBET | 西藏 | 13 | 8 | 5 |
| HMT | 港澳台 | 20 | 10 | 4 |

> 偏移量仅在 `LogisticsDataInitializer` 首次生成全国参考价时叠加到供应商基准价上；之后一律以 `logistics_price` 表为准。

### 13.5 ACG 作品类型 `workType`

| code | 文案 | 说明 |
|---|---|---|
| ANIME | 动画 | TV 动画 / 网络动画 |
| MANGA | 漫画 | 连载漫画 / 已完结漫画 |
| GAME | 游戏 | 手游 / 主机游戏 / 音游 |
| NOVEL | 轻小说 | 轻小说原作 |
| MOVIE | 剧场版 | 剧场版动画 / 电影 |
| OTHER | 其他 | 虚拟歌手、舞台剧等不好归类的 IP |

### 13.6 ACG 连载状态 `releaseStatus`

| code | 文案 | 说明 |
|---|---|---|
| ONGOING | 连载中 | 仍在更新，热度通常波动大 |
| FINISHED | 已完结 | 已完结，周边多为常青款 |
| UPCOMING | 待播出 | 已公布未上线，适合提前备货 |

### 13.7 ACG 潜力等级 `potentialLevel`

| code | 文案 | 潜力分 | 建议动作 |
|---|---|---|---|
| S | 重点主推 | ≥ 75 | 优先备货、主推位、锁定量 |
| A | 积极备货 | ≥ 62 | 正常加量，观察趋势 |
| B | 常规跟进 | ≥ 48 | 按常规节奏补货 |
| C | 谨慎 / 淘汰 | < 48 | 控制库存深度，逐步清货 |

> 等级是 `potentialScore` 的派生值，不落库、不可手工修改；潜力分口径见 §10.2。想调整分级只改 `AcgPotentialCalculator` 的阈值常量。

### 13.8 消息类型 `msgType`

| code | 文案 | 典型来源 |
|---|---|---|
| APPROVAL | 审批消息 | 入库单、商品资料变更等待审批 |
| ORDER | 订单消息 | 新订单、发货、签收异常 |
| INVENTORY | 库存消息 | 库位容量预警、盘点任务 |
| SYSTEM | 系统消息 | 维护通知、账号与权限变更 |

> 扩展方式：只在 `MessageDict.TYPE_ENTRIES` 里追加一项，数据库 `msg_type` 是 varchar 不用改表，前端 Tab 由 MSG-03 自动生成。

### 13.9 消息状态 `status`

| code | 文案 | 含义 | 可执行动作 |
|---|---|---|---|
| U | 未读 | 还没看过 | 查看（自动已读）、标记已读、处理、删除 |
| R | 已读 | 看过了，还没办 | 处理、删除 |
| H | 已处理 | 已办完（终态） | 删除 |

### 13.10 消息优先级 `priority`

| code | 文案 | 前端表现 |
|---|---|---|
| N | 普通 | 无额外标识 |
| U | 紧急 | 卡片左侧红色边框 + 红色「紧急」标签；未读紧急数在 summary 里单独统计 |

### 13.11 货架状态 `status`

| code | 文案 | 含义 | 前端表现 |
|---|---|---|---|
| A | 正常 | 可正常存取 | 绿色标签 |
| B | 维护中 | 检修 / 整理中，暂不存取 | 橙色标签 |
| D | 停用 | 已停用 | 灰色标签 |

> 注意与仓库状态区分：仓库的「盘库中」是 `C`，货架的「维护中」是 `B`，两套状态互不影响。

### 13.12 待办优先级 `priority`

| code | 文案 | 前端表现 |
|---|---|---|
| H | 高 | 红色标签，排序最靠前 |
| N | 普通 | 蓝色标签 |
| L | 低 | 灰色标签，排在同状态最后 |

### 13.13 待办状态 `status`

| code | 文案 | 含义 | 前端表现 |
|---|---|---|---|
| P | 待办 | 还没做 | 复选框未勾选 |
| D | 已完成 | 已勾掉，`finish_time` 有值 | 复选框勾选 + 标题划线置灰 |

### 13.14 入库类型 `inboundType`

| code | 文案 | 含义 |
|---|---|---|
| P | 采购入库 | 向供应商采购到货 |
| R | 退货入库 | 客户 / 渠道退货入库 |
| T | 调拨入库 | 其他仓库调入 |
| O | 其他入库 | 盘点盈亏、赠品等 |

> 新增类型只需在 `InboundType` 常量类追加一行，字典接口与前端下拉自动带上；已入库的历史数据不要改 code。

### 13.15 入库单状态 `status`

| code | 文案 | 含义 | 可编辑 | 可删除 | 可确认 | 可作废 |
|---|---|:--:|:--:|:--:|:--:|
| D | 待入库 | 草稿，还没确认 | ✅ | ✅ | ✅ | ✅ |
| C | 已入库 | 已确认，计入本月统计 | ❌ | ❌ | ❌ | ✅ |
| X | 已作废 | 单据作废，仅留档 | ❌ | ❌ | ❌ | ❌ |

---

## 14. 变更记录

| 版本 | 日期 | 变更类型 | 涉及接口 | 内容 |
|---|---|---|---|---|
| v1.0 | 2026-11-01 | 初始 | 全部 | 建立契约文档，共 22 个接口：系统 3、认证 3、员工 7、物流 9 |
| v1.1 | 2026-11-03 | 新增 | PRO-01~PRO-07 | 新增「商品（手办）」模块：分页查询/详情/新增/编辑/删除/启停 + 图片上传，落库 `figure_product` 表；接口总数 22 → 29 |
| v1.2 | 2026-11-05 | 新增 | WH-01~WH-06 | 新增「仓库（库存）」模块：库存管理 → 仓库设置 → 库位管理 页面由前端写死改为接口驱动，落库 `warehouse` 表（代码/名称/地址/退货类型/状态）；接口总数 29 → 35 |
| v1.3 | 2026-11-05 | 新增 | ACG-01~ACG-12 | 新增「ACG 作品目录」模块：作品档案维护、热度快照录入与趋势、选品分析（KPI / 潜力排行 / 四象限 / 类型分布 / 文字结论）、供其他业务表使用的下拉选项、封面上传；落库 `acg_work` + `acg_heat_log`；接口总数 35 → 47 |
| v1.4 | 2026-11-06 | 新增 | MSG-01~MSG-09 | 新增「个人消息」：右上角用户菜单 → 个人消息，落库 `sys_message` 表；支持分页查询、未读统计、类型字典、详情自动已读、标记已读、全部已读、处理、删除与手工发送；消息按接收人隔离，`MessageService.push` 作为其他模块推送消息的统一入口；接口总数 47 → 56 |
| v1.5 | 2026-11-06 | 新增 | SHELF-01~SHELF-06 | 库存管理 → 仓库设置 → 货架管理 由前端写死改为接口驱动，落库 `shelf` 表（编号/所属仓库/库位信息/状态/状态描述）；货架编号在同一仓库内唯一；仓库下还有货架时禁止删除仓库；接口总数 56 → 62 |
| v1.6 | 2026-11-07 | 变更 | USER-03~07 / §1.6 / §13.1 | 角色由 3 个扩展为 12 个：新增总经理、财务/销售/物流/人事主管与专员，角色分「管理级 3 / 主管级 2 / 执行级 1」三层，权限判断改为按层级；员工录入时角色必填（DB 去默认值 + 枚举校验）；`UserVO` 新增 `roleGroup` / `roleLevel`，字典接口 roles 新增 `group` / `level`；接口总数不变（62） |
| v1.7 | 2026-11-07 | 新增 | AREA-01~AREA-06 / SHELF-01、03 / WH-05 | 库存管理 → 仓库设置 → 区域配置 由前端写死改为接口驱动，落库 `area` 表（编号/名称/所属仓库/位置/容量/状态），`AreaVO` 额外返回 `shelfCount`（已放货架数）；`shelf` 新增可空 `area_id`，货架可挂区域且必须与仓库一致，`ShelfVO` 新增 `areaId`/`areaName`、`ShelfQuery`/`ShelfSaveRequest` 新增 `areaId`；仓库/区域删除增加下层占用保护；接口总数 62 → 68 |
| v1.8 | 2026-11-07 | 新增 | TODO-01~TODO-08 / §1.6 / §12 / §13.12~13.13 | 新增「工作台」（`/home`）：今日工作计划 + 待办事项，落库 `work_todo` 表（标题/说明/计划日期/优先级/状态/完成时间），按 plan_date 统计今日总数/完成/未完成/逾期与完成率；待办按登录用户隔离，勾选完成走独立 status 接口；首页按角色分流：`/dashboard` 数据概览只作为总经理、销售主管的首页，其他角色进工作台，越权直连被守卫送回；接口总数 68 → 76 |
| v1.9 | 2026-11-07 | 变更（移除字段） | SYS-03 / AUTH-01~02 / USER-01~04 / §3.1 / §13.2 | 移除「涉密等级」：`UserVO` 去掉 `securityLevel` / `securityLevelLabel`，`UserQuery` / `UserSaveRequest` 去掉 `securityLevel`（含必填校验），字典接口不再返回 `securityLevels`，JWT 不再携带 `level` claim，实体与 `sys_user` 表删除 `security_level` 列及索引；前端员工列表/维护、右上角、个人信息同步移除；接口总数不变（76） |
| v2.0 | 2026-11-07 | 新增 | INB-01~INB-09 / §9.4 / §13.14~13.15 | 库存管理 → 入库记录 由占位页改为接口驱动：新增入库单主表 `inbound_order`（单号/类型/仓库/区域/货架/供应商/状态/数量金额合计/入库时间/经办人）与明细表 `inbound_item`（手办/数量/单价/小计，含名称与货号快照）；支持分页筛选（单号/供应商/经办人/手办名称模糊 + 仓库/类型/状态/日期区间）、详情含明细、顶部统计卡（待入库数、本月单数/件数/金额）、单号自动生成、明细整体替换、确认入库与作废流转；合计一律后端重算；接口总数 76 → 85 |
| v2.1 | 2026-11-08 | 新增 | MFG-01~MFG-10 / §3.9 / §10.6~10.8 | 目录维护 → 厂商目录维护 由占位页改为接口驱动：新增 `manufacturer` 表（公司名称/厂牌 Logo/创立时间/国家地区/备注/状态），支持分页筛选（名称或国家地区模糊 + 国家地区/状态/排序）、详情、新增、编辑、删除、启用/停用、统计卡（总数/启用/停用/覆盖国家地区数）、国家地区筛选项、供其他业务表使用的下拉选项、Logo 上传；同名校验在未删除记录内；接口总数 85 → 95 |

<!--
新增变更请追加在表格末尾，并把顶部「文档版本 / 最后更新」同步改掉。
模板：
| v1.1 | 2026-11-05 | 新增 | LOG-10 | 新增加油附加费字段 fuelSurcharge，出参新增同名字段 |
| v2.0 | 2026-11-10 | 破坏性 | LOG-08 | destProvince 改名为 destinationProvince，前端需同步 |
-->
