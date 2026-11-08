-- ============================================================
-- 手办管家 Figure-ERP 数据库初始化脚本
-- 说明：可重复执行（IF NOT EXISTS），不会删除已有数据
-- ============================================================

CREATE DATABASE IF NOT EXISTS `figure_erp`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE `figure_erp`;

-- ------------------------------------------------------------
-- 系统用户（员工）
-- 一条记录 = 一名员工，登录用 username + password，
-- 其余字段用于展示。
-- 初始管理员由后端 DataInitializer 在用户表为空时自动创建。
-- ------------------------------------------------------------
-- 角色（role）说明：
--   角色的可选值与权限层级由后端 UserRole 枚举维护，表里只存编码，不存文案：
--     管理级 3：ADMIN 管理员、GM 总经理           → 可维护员工账号 + 全部业务
--     主管级 2：FINANCE/SALES/LOGISTICS/HR_MANAGER → 可维护业务数据
--     执行级 1：对应的 *_STAFF 专员                → 只读业务数据
--     MANAGER / STAFF 是旧版通用角色，仅为兼容存量账号保留
--   新增角色只在枚举里加一行，不用改表结构（varchar 存编码）。
--   role 不设默认值：员工录入时必须明确选择，避免新账号默认拿到某个角色。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_user`
(
    `id`             bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`       varchar(50)  NOT NULL COMMENT '登录账号',
    `real_name`      varchar(50)  NOT NULL COMMENT '员工姓名',
    `password`       varchar(100) NOT NULL COMMENT '密码（BCrypt 哈希，非明文）',
    `role`           varchar(20)  NOT NULL COMMENT '角色（必填）：ADMIN管理员 GM总经理 / 财务、销售、物流、人事各自主管与专员 / MANAGER主管(旧) STAFF员工(旧)，取值见 UserRole 枚举',
    `avatar`         varchar(255)          DEFAULT NULL COMMENT '头像地址',
    `intro`          varchar(500)          DEFAULT NULL COMMENT '员工介绍',
    `remark`         varchar(500)          DEFAULT NULL COMMENT '备注',
    `status`         tinyint      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    `last_login_time` datetime             DEFAULT NULL COMMENT '最后登录时间',
    `create_time`    datetime              DEFAULT NULL COMMENT '创建时间',
    `update_time`    datetime              DEFAULT NULL COMMENT '更新时间',
    `deleted`        tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_role` (`role`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='系统用户（员工）';

-- ------------------------------------------------------------
-- 物流价格
-- 一条记录 = 某供应商 从某出发地 到某目的地 的一档价格。
--
-- 计价模型：国内快递通用的「首重 + 续重」
--   重量 ≤ first_weight                 → 总价 = first_price
--   重量 > first_weight                 → 总价 = first_price
--                                        + ceil((重量 - first_weight) / additional_weight) * additional_price
--
-- dest_city 为空表示「该省全省统一价」，填了城市则只对该城市生效
-- （查询时城市级优先于省级，用于新疆/西藏等需要按城市细调的场景）。
--
-- 初始数据由后端 LogisticsDataInitializer 在表为空时按区域基准价生成，
-- 覆盖 8 家供应商 × 34 个省级行政区。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `logistics_price`
(
    `id`                bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `supplier_code`     varchar(20)  NOT NULL COMMENT '物流供应商编码，见 LogisticsSupplier 枚举',
    `origin_province`   varchar(30)  NOT NULL DEFAULT '上海市' COMMENT '出发地-省',
    `origin_city`       varchar(30)  NOT NULL DEFAULT '上海市' COMMENT '出发地-市',
    `origin_district`   varchar(30)           DEFAULT '浦东新区' COMMENT '出发地-区/县',
    `dest_province`     varchar(30)  NOT NULL COMMENT '目的地-省',
    `dest_city`         varchar(30)           DEFAULT NULL COMMENT '目的地-市，为空表示全省统一价',
    `first_weight`      decimal(6, 2) NOT NULL DEFAULT 1.00 COMMENT '首重重量（kg）',
    `first_price`       decimal(8, 2) NOT NULL COMMENT '首重价格（元）',
    `additional_weight` decimal(6, 2) NOT NULL DEFAULT 1.00 COMMENT '续重单位（kg）',
    `additional_price`  decimal(8, 2) NOT NULL COMMENT '续重单价（元）',
    `estimated_days`    int                   DEFAULT NULL COMMENT '预计时效（天）',
    `status`            tinyint      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    `remark`            varchar(255)          DEFAULT NULL COMMENT '备注',
    `create_time`       datetime              DEFAULT NULL COMMENT '创建时间',
    `update_time`       datetime              DEFAULT NULL COMMENT '更新时间',
    `deleted`           tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    KEY `idx_supplier_dest` (`supplier_code`, `dest_province`, `dest_city`),
    KEY `idx_origin` (`origin_province`, `origin_city`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='物流价格（首重+续重）';

-- ------------------------------------------------------------
-- 手办商品（档案 / 主数据）
-- 一条记录 = 一个手办商品（含图片、长宽高、带盒重量等档案信息）。
--
-- images 以英文逗号分隔的 URL 列表存储（最多 4 张，至少 3 张），
-- 由后端在保存时做数量校验，查询时拆成数组返回给前端。
-- 图片文件本身由后端上传接口落到 uploads/products/ 目录并返回 URL。
--
-- 初始演示数据由后端 ProductDataInitializer 在表为空时写入。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `figure_product`
(
    `id`              bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`            varchar(200)  NOT NULL COMMENT '商品名称（手办全称）',
    `code`            varchar(50)            DEFAULT NULL COMMENT '货号 / SKU，可空，填写后不可与其他商品重复',
    `brand`           varchar(100)           DEFAULT NULL COMMENT '品牌，如 Good Smile Company',
    `series`          varchar(100)           DEFAULT NULL COMMENT '系列，如 Nendoroid / Scale',
    `character_name`  varchar(100)           DEFAULT NULL COMMENT '角色名',
    `material`        varchar(50)            DEFAULT NULL COMMENT '材质，如 PVC/ABS',
    `length_cm`       decimal(6, 1)          DEFAULT NULL COMMENT '长（cm）',
    `width_cm`        decimal(6, 1)          DEFAULT NULL COMMENT '宽（cm）',
    `height_cm`       decimal(6, 1)          DEFAULT NULL COMMENT '高（cm）',
    `weight_with_box` int           NOT NULL COMMENT '带盒重量（g），含包装盒',
    `images`          text                   DEFAULT NULL COMMENT '商品图片 URL 列表，逗号分隔，3~4 张',
    `remark`          varchar(500)           DEFAULT NULL COMMENT '备注',
    `status`          tinyint       NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    `create_time`     datetime               DEFAULT NULL COMMENT '创建时间',
    `update_time`     datetime               DEFAULT NULL COMMENT '更新时间',
    `deleted`         tinyint       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`),
    KEY `idx_name` (`name`),
    KEY `idx_brand_series` (`brand`, `series`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='手办商品档案';

-- ------------------------------------------------------------
-- 仓库档案（库存管理 → 仓库设置 → 库位管理 页面维护的数据）
-- 一条记录 = 一个仓库（正常仓或退货仓）。
--
-- 为什么 warehouse_code 用普通索引而不是 UNIQUE：
--   本表使用逻辑删除（deleted=1），若建 UNIQUE(code)，删掉的记录仍占用索引，
--   用户就无法再新建同代码的仓库（改错后重建是常见操作）。
--   唯一性改由 Service 层校验「未删除记录中代码不重复」来保证。
--
-- status 用单字符，与前端 el-tag 映射保持一致：
--   A 使用中 / C 盘库中 / D 停用中
-- is_return：
--   N 正常仓 / Y 退货仓
--
-- 初始 12 条数据由后端 WarehouseDataInitializer 在表为空时写入，
-- 之后页面上新增/编辑/删除都直接写库，重启不会还原。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `warehouse`
(
    `id`                bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `warehouse_code`    varchar(50)  NOT NULL COMMENT '仓库代码，如 3101，未删除记录中唯一',
    `warehouse_name`    varchar(100) NOT NULL COMMENT '仓库名称',
    `warehouse_address` varchar(255)          DEFAULT NULL COMMENT '仓库地址',
    `is_return`         char(1)      NOT NULL DEFAULT 'N' COMMENT '是否退货库：N正常仓 Y退货仓',
    `status`            char(1)      NOT NULL DEFAULT 'A' COMMENT '状态：A使用中 C盘库中 D停用中',
    `remark`            varchar(500)          DEFAULT NULL COMMENT '备注',
    `sort_no`           int          NOT NULL DEFAULT 0 COMMENT '排序号，数值越小越靠前',
    `create_time`       datetime              DEFAULT NULL COMMENT '创建时间',
    `update_time`       datetime              DEFAULT NULL COMMENT '更新时间',
    `deleted`           tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    KEY `idx_warehouse_code` (`warehouse_code`),
    KEY `idx_warehouse_name` (`warehouse_name`),
    KEY `idx_status` (`status`),
    KEY `idx_is_return` (`is_return`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='仓库档案';

-- ------------------------------------------------------------
-- 区域档案（菜单：库存管理 → 仓库设置 → 区域配置）
-- 一条记录 = 某个仓库下的一片区域（如 A区-核心展示柜），是「仓库 → 区域 → 货架 → 库位」里的中间一层。
--
-- 为什么必须挂 warehouse_id：
--   原页面把 3 条区域写死在前端（A区/B区/C区），没有归属仓库的概念；
--   落到库里如果不挂仓库，区域就是查不到上下文的孤立数据，所以和货架一样必填。
--
-- 唯一性：area_code 只在「同一仓库 + 未删除」范围内唯一（3101 的 A 和 3301 的 A 可以并存），
--   与 shelf 一致不建 UNIQUE 索引（逻辑删除会让已删记录继续占用索引），由 AreaService 校验。
--
-- status：A 使用中 / D 停用
-- capacity：可容纳的货架数（即原页面「容量 / 格子数」字段）
-- 初始 3 条数据由后端 AreaDataInitializer 在表为空时写入，之后页面上的增删改都直接写库。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `area`
(
    `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `area_code`    varchar(50)  NOT NULL COMMENT '区域编号，如 A，同一仓库未删除记录中唯一',
    `area_name`    varchar(100) NOT NULL COMMENT '区域名称，如 A区-核心展示柜',
    `warehouse_id` bigint       NOT NULL COMMENT '所属仓库，warehouse.id',
    `location`     varchar(100)          DEFAULT NULL COMMENT '物理位置，如 一层靠窗',
    `capacity`     int          NOT NULL DEFAULT 0 COMMENT '可容纳货架数（原页面「容量/格子数」）',
    `status`       char(1)      NOT NULL DEFAULT 'A' COMMENT '状态：A使用中 D停用',
    `remark`       varchar(255)          DEFAULT NULL COMMENT '备注',
    `sort_no`      int          NOT NULL DEFAULT 0 COMMENT '排序号，数值越小越靠前',
    `create_time`  datetime              DEFAULT NULL COMMENT '创建时间',
    `update_time`  datetime              DEFAULT NULL COMMENT '更新时间',
    `deleted`      tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    KEY `idx_area_code` (`area_code`),
    KEY `idx_warehouse_id` (`warehouse_id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='区域档案';

-- ------------------------------------------------------------
-- 货架档案（菜单：库存管理 → 仓库设置 → 货架管理）
-- 一条记录 = 某个仓库下的一个货架，是「仓库 → 区域 → 货架 → 库位」层级里的中间一层。
--
-- area_id 为什么可空：
--   货架先于区域落库，已有 3 条货架数据没有区域；给可空列后老数据不用洗，
--   新货架可以在页面上选「所属区域」。填写时必须与 warehouse_id 属于同一仓库，由 ShelfService 校验。
--
-- 唯一性：shelf_code 只在「同一仓库 + 未删除」范围内唯一（3101 的 01-1 和 3301 的 01-1 可以并存），
--   所以不建 UNIQUE 索引（逻辑删除会让已删记录继续占用索引），由 ShelfService 校验。
--
-- status：A 正常 / B 维护中 / D 停用
-- 初始 3 条数据由后端 ShelfDataInitializer 在表为空时写入，之后页面上的增删改都直接写库。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `shelf`
(
    `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `shelf_code`   varchar(50)  NOT NULL COMMENT '货架编号，如 01-1，同一仓库未删除记录中唯一',
    `warehouse_id` bigint       NOT NULL COMMENT '所属仓库，warehouse.id',
    `area_id`      bigint                DEFAULT NULL COMMENT '所属区域，area.id，可空；填写时必须与 warehouse_id 同仓库',
    `location`     varchar(100)          DEFAULT NULL COMMENT '库位信息，如 01区-1层',
    `status`       char(1)      NOT NULL DEFAULT 'A' COMMENT '状态：A正常 B维护中 D停用',
    `remark`       varchar(255)          DEFAULT NULL COMMENT '状态描述 / 备注',
    `sort_no`      int          NOT NULL DEFAULT 0 COMMENT '排序号，数值越小越靠前',
    `create_time`  datetime              DEFAULT NULL COMMENT '创建时间',
    `update_time`  datetime              DEFAULT NULL COMMENT '更新时间',
    `deleted`      tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    KEY `idx_shelf_code` (`shelf_code`),
    KEY `idx_warehouse_id` (`warehouse_id`),
    KEY `idx_area_id` (`area_id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='货架档案';

-- 升级提示（已经建过 shelf 表的老库）：本脚本用的是 CREATE TABLE IF NOT EXISTS，
-- 表已存在时不会自动补列，需要手动执行一次下面这条（新库可忽略）：
--   ALTER TABLE `shelf`
--       ADD COLUMN `area_id` bigint DEFAULT NULL
--           COMMENT '所属区域，area.id，可空；填写时必须与 warehouse_id 同仓库' AFTER `warehouse_id`,
--       ADD KEY `idx_area_id` (`area_id`);

-- ------------------------------------------------------------
-- 入库单（菜单：库存管理 → 入库记录）
-- 一条记录 = 一次入库业务（一张入库单），手办明细放在 inbound_item。
--
-- 为什么拆主表 + 明细：
--   一次到货通常包含多款手办，若把「手办 + 数量」塞进主表，
--   同一批货要么记成多条互不关联的记录，要么把商品名拼成一坨文本，无法统计单品入库量。
--
-- inbound_no 不建 UNIQUE：本表逻辑删除，已删单号仍占用索引，
--   唯一性由 InboundService 校验「未删除记录中单号不重复」。留空时后端按 RK + 日期 + 序号自动生成。
--
-- status：D 待入库（草稿，可改可删）/ C 已入库（已确认，不可改删）/ X 已作废
-- inbound_type：P 采购入库 / R 退货入库 / T 调拨入库 / O 其他
-- total_quantity / total_amount 是明细的合计，由后端按明细重算后写入，
--   不接受前端传值，避免出现「列表数量和明细对不上」。
--
-- warehouse_id 必填；area_id / shelf_id 可空，填写时必须是同仓库下的区域 / 货架。
-- product_name 在明细里做了快照：手办改名或下架后，历史入库单仍显示当时的名字。
-- 初始 4 条演示单由 InboundDataInitializer 在表为空时写入，之后页面上的增删改都直接写库。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `inbound_order`
(
    `id`             bigint         NOT NULL AUTO_INCREMENT COMMENT '主键',
    `inbound_no`     varchar(50)    NOT NULL COMMENT '入库单号，如 RK202611070001，未删除记录中唯一',
    `inbound_type`   char(1)        NOT NULL DEFAULT 'P' COMMENT '入库类型：P采购 R退货 T调拨 O其他',
    `warehouse_id`   bigint         NOT NULL COMMENT '入库仓库，warehouse.id',
    `area_id`        bigint                  DEFAULT NULL COMMENT '入库区域，area.id，可空；须与 warehouse_id 同仓库',
    `shelf_id`       bigint                  DEFAULT NULL COMMENT '入库货架，shelf.id，可空；须与 warehouse_id 同仓库',
    `supplier`       varchar(100)            DEFAULT NULL COMMENT '供应商 / 来源单位',
    `status`         char(1)        NOT NULL DEFAULT 'D' COMMENT '状态：D待入库 C已入库 X已作废',
    `total_quantity` int            NOT NULL DEFAULT 0 COMMENT '明细数量合计，后端按明细重算',
    `total_amount`   decimal(12, 2) NOT NULL DEFAULT 0.00 COMMENT '明细金额合计，后端按明细重算',
    `inbound_time`   datetime                DEFAULT NULL COMMENT '入库时间',
    `operator`       varchar(50)             DEFAULT NULL COMMENT '经办人姓名，默认取当前登录用户',
    `remark`         varchar(500)            DEFAULT NULL COMMENT '备注',
    `create_time`    datetime                DEFAULT NULL COMMENT '创建时间',
    `update_time`    datetime                DEFAULT NULL COMMENT '更新时间',
    `deleted`        tinyint        NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    KEY `idx_inbound_no` (`inbound_no`),
    KEY `idx_warehouse_id` (`warehouse_id`),
    KEY `idx_status` (`status`),
    KEY `idx_inbound_time` (`inbound_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='入库单';

-- ------------------------------------------------------------
-- 入库单明细（一行 = 一款手办 + 数量 + 单价）
-- inbound_id 关联 inbound_order.id；明细随单保存，编辑时先删后插（同一事务内）。
-- product_id 指向 figure_product.id；product_name / product_code 是下单时的快照。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `inbound_item`
(
    `id`           bigint         NOT NULL AUTO_INCREMENT COMMENT '主键',
    `inbound_id`   bigint         NOT NULL COMMENT '所属入库单，inbound_order.id',
    `product_id`   bigint         NOT NULL COMMENT '手办商品，figure_product.id',
    `product_name` varchar(200)   NOT NULL COMMENT '手办名称快照',
    `product_code` varchar(50)             DEFAULT NULL COMMENT '货号快照',
    `quantity`     int            NOT NULL COMMENT '入库数量，必须大于 0',
    `unit_price`   decimal(10, 2)          DEFAULT NULL COMMENT '单价（元）',
    `amount`       decimal(12, 2) NOT NULL DEFAULT 0.00 COMMENT '小计金额 = 数量 × 单价，后端计算',
    `remark`       varchar(255)            DEFAULT NULL COMMENT '备注',
    `sort_no`      int            NOT NULL DEFAULT 0 COMMENT '排序号',
    `create_time`  datetime                DEFAULT NULL COMMENT '创建时间',
    `update_time`  datetime                DEFAULT NULL COMMENT '更新时间',
    `deleted`      tinyint        NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    KEY `idx_inbound_id` (`inbound_id`),
    KEY `idx_product_id` (`product_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='入库单明细';

-- 后续业务表规划（暂不创建，先对齐字段再落库）：
--   location       库位（货架 → 库位）
--   stock          库存（手办 + 库位 + 数量）
--   outbound_order 出库单 / outbound_item 出库明细
--   order_info     订单
--   sys_oper_log   操作日志

-- ------------------------------------------------------------
-- ACG 作品目录（菜单：ACG 作品目录 → 作品目录）
-- 用途：
--   1) 维护作品主数据，供其他业务表（手办商品、订单等）作为下拉选项引用；
--   2) 记录作品当前热度，并通过 acg_heat_log 保留历史快照，支撑趋势与选品分析。
--
-- 为什么单独存 heat_log：
--   只存当前值无法算「热度是在涨还是在跌」，而"哪个作品会更畅销"恰恰取决于趋势。
--   每次录入热度写一条快照，主表的 heat_score / monthly_sales 保留最新值用于列表展示。
--
-- 为什么 work_code 不建 UNIQUE：同 warehouse，本表逻辑删除，唯一性由 Service 校验。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `acg_work`
(
    `id`              bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `work_code`       varchar(50)           DEFAULT NULL COMMENT '作品编号，如 ACG-001，未删除记录中唯一',
    `name`            varchar(120) NOT NULL COMMENT '作品名称（中文）',
    `original_name`   varchar(160)          DEFAULT NULL COMMENT '原名（日文 / 英文）',
    `work_type`       varchar(20)  NOT NULL DEFAULT 'ANIME' COMMENT '类型：ANIME动画 MANGA漫画 GAME游戏 NOVEL轻小说 MOVIE剧场版 OTHER其他',
    `ip_owner`        varchar(100)          DEFAULT NULL COMMENT '版权方 / 发行方',
    `studio`          varchar(100)          DEFAULT NULL COMMENT '制作公司 / 开发商',
    `author`          varchar(100)          DEFAULT NULL COMMENT '作者 / 原作',
    `release_year`    int                   DEFAULT NULL COMMENT '首播 / 发行年份',
    `release_status`  varchar(20)  NOT NULL DEFAULT 'FINISHED' COMMENT '连载状态：ONGOING连载中 FINISHED已完结 UPCOMING待播出',
    `heat_score`      int          NOT NULL DEFAULT 0 COMMENT '当前热度指数 0-100',
    `monthly_sales`   int          NOT NULL DEFAULT 0 COMMENT '近 30 天周边销量（件）',
    `total_sales`     int          NOT NULL DEFAULT 0 COMMENT '累计销量（件）',
    `tags`            varchar(255)          DEFAULT NULL COMMENT '标签，逗号分隔，如 热血,战斗,少年漫',
    `cover_image`     varchar(255)          DEFAULT NULL COMMENT '封面图 URL',
    `heat_updated_at` datetime              DEFAULT NULL COMMENT '热度最近更新时间',
    `remark`          varchar(500)          DEFAULT NULL COMMENT '备注',
    `status`          tinyint      NOT NULL DEFAULT 1 COMMENT '状态：1可选（出现在下拉选项） 0停用',
    `create_time`     datetime              DEFAULT NULL COMMENT '创建时间',
    `update_time`     datetime              DEFAULT NULL COMMENT '更新时间',
    `deleted`         tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    KEY `idx_work_code` (`work_code`),
    KEY `idx_name` (`name`),
    KEY `idx_work_type` (`work_type`),
    KEY `idx_heat_score` (`heat_score`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='ACG 作品档案';



CREATE TABLE IF NOT EXISTS `acg_heat_log`
(
    `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `work_id`       bigint       NOT NULL COMMENT '作品 ID（acg_work.id）',
    `record_date`   date         NOT NULL COMMENT '记录日期，同一天重复录入会覆盖当天值',
    `heat_score`    int          NOT NULL COMMENT '当日热度指数 0-100',
    `monthly_sales` int          NOT NULL DEFAULT 0 COMMENT '当日近 30 天销量（件）',
    `remark`        varchar(255)          DEFAULT NULL COMMENT '备注',
    `create_time`   datetime              DEFAULT NULL COMMENT '创建时间',
    `update_time`   datetime              DEFAULT NULL COMMENT '更新时间',
    `deleted`       tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    KEY `idx_work_date` (`work_id`, `record_date`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='ACG 作品热度快照（趋势分析用）';

-- ------------------------------------------------------------
-- 个人消息（右上角用户菜单 → 个人消息）
-- 一条记录 = 一条发给某个人的站内消息（审批 / 订单 / 库存 / 系统通知）。
--
-- 设计要点：
--   1) 消息按「接收人」隔离，接口只能查到 receiver_id = 当前登录用户 的记录；
--   2) msg_type 预留扩展：以后新增消息种类只需在 MessageType 枚举 + 前端字典里加一项，
--      不用改表结构；biz_type/biz_id 指向来源单据，biz_url 是「去处理」的前端路由；
--   3) status 三态：U未读 / R已读 / H已处理。已处理是终态，用于区分「看过了」和「办完了」；
--   4) 本表逻辑删除，故没有业务唯一键，也不需要 UNIQUE 索引。
--
-- 初始演示数据由后端 MessageDataInitializer 在表为空时写入。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_message`
(
    `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `title`         varchar(120) NOT NULL COMMENT '消息标题',
    `content`       varchar(1000)         DEFAULT NULL COMMENT '消息正文',
    `msg_type`      varchar(20)  NOT NULL DEFAULT 'SYSTEM' COMMENT '类型：APPROVAL审批 ORDER订单 INVENTORY库存 SYSTEM系统',
    `biz_type`      varchar(30)           DEFAULT NULL COMMENT '业务对象类型，如 PRODUCT/ORDER/WAREHOUSE',
    `biz_id`        bigint                DEFAULT NULL COMMENT '业务对象 ID',
    `biz_url`       varchar(255)          DEFAULT NULL COMMENT '「去处理」跳转的前端路由，为空表示纯通知',
    `receiver_id`   bigint       NOT NULL COMMENT '接收人（sys_user.id）',
    `sender_id`     bigint                DEFAULT NULL COMMENT '发送人（sys_user.id），系统消息为空',
    `sender_name`   varchar(50)           DEFAULT NULL COMMENT '发送人姓名快照，避免用户删除后显示空白',
    `priority`      char(1)      NOT NULL DEFAULT 'N' COMMENT '优先级：N普通 U紧急',
    `status`        char(1)      NOT NULL DEFAULT 'U' COMMENT '状态：U未读 R已读 H已处理',
    `read_time`     datetime              DEFAULT NULL COMMENT '首次读取时间',
    `handle_time`   datetime              DEFAULT NULL COMMENT '处理完成时间',
    `handle_remark` varchar(255)          DEFAULT NULL COMMENT '处理备注 / 处理结果',
    `remark`        varchar(500)          DEFAULT NULL COMMENT '备注',
    `create_time`   datetime              DEFAULT NULL COMMENT '创建时间',
    `update_time`   datetime              DEFAULT NULL COMMENT '更新时间',
    `deleted`       tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    KEY `idx_receiver_status` (`receiver_id`, `status`),
    KEY `idx_receiver_type` (`receiver_id`, `msg_type`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='个人消息（站内通知）';

-- ------------------------------------------------------------
-- 个人待办 / 今日工作计划（页面：工作台 → 今日工作计划）
--
-- 一条记录 = 某个员工给自己排的一件事，落在 plan_date 这一天。
-- 与 sys_message 的分工：
--   sys_message 是「别人推给你的」，处理完状态变 H；
--   work_todo   是「你自己记下来的」，勾掉后状态变 D。
-- 两者都在工作台出现，但互不生成 —— 消息点「去处理」不会自动建待办，
-- 避免把「谁该干什么」这件事变成系统臆测。
--
-- 为什么按 user_id + plan_date 存：
--   工作台只查「我 + 今天」，这个组合是最高频查询，直接给联合索引；
--   历史记录按日期区间翻页也走同一个索引，不需要额外的归档表。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `work_todo`
(
    `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     bigint       NOT NULL COMMENT '归属员工（sys_user.id），只能看自己的待办',
    `title`       varchar(120) NOT NULL COMMENT '待办标题',
    `content`     varchar(500)          DEFAULT NULL COMMENT '补充说明',
    `plan_date`   date         NOT NULL COMMENT '计划日期，工作台只展示当天',
    `priority`    char(1)      NOT NULL DEFAULT 'N' COMMENT '优先级：H高 N普通 L低',
    `status`      char(1)      NOT NULL DEFAULT 'P' COMMENT '状态：P待办 D已完成',
    `finish_time` datetime              DEFAULT NULL COMMENT '完成时间，状态回到 P 时清空',
    `sort_no`     int          NOT NULL DEFAULT 0 COMMENT '排序号，越小越靠前',
    `create_time` datetime              DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime              DEFAULT NULL COMMENT '更新时间',
    `deleted`     tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    KEY `idx_user_date` (`user_id`, `plan_date`),
    KEY `idx_user_status` (`user_id`, `status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='个人待办 / 今日工作计划';

-- 初始演示待办由后端 TodoDataInitializer 在表为空时写入。

-- ------------------------------------------------------------
-- 厂商目录（页面：目录维护 → 厂商目录维护）
-- 一条记录 = 一家手办 / 景品厂商（万代、万代南梦宫、BANPRESTO 眼镜厂…）。
-- 用途：维护厂商主数据（厂牌 Logo、公司名称、创立时间、国家地区），
--      供商品、入库等业务作为「厂商」下拉选项引用；停用后不再出现在下拉里。
--
-- 为什么 name 不建 UNIQUE：本表逻辑删除，唯一性由 Service 在未删除记录内校验，
-- 避免删掉一条后同名厂商再也建不回来（同 acg_work / warehouse 的做法）。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `manufacturer`
(
    `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`         varchar(120) NOT NULL COMMENT '公司 / 厂牌名称，如 万代、BANPRESTO 眼镜厂，未删除记录内唯一',
    `logo`         varchar(255)          DEFAULT NULL COMMENT '公司厂牌 Logo 图片 URL',
    `founded_date` date                  DEFAULT NULL COMMENT '创立时间（年月日）',
    `country`      varchar(50)           DEFAULT NULL COMMENT '国家 / 地区，如 日本、中国、中国香港、美国',
    `remark`       varchar(500)          DEFAULT NULL COMMENT '备注',
    `status`       tinyint      NOT NULL DEFAULT 1 COMMENT '状态：1启用（出现在下拉选项） 0停用',
    `create_time`  datetime              DEFAULT NULL COMMENT '创建时间',
    `update_time`  datetime              DEFAULT NULL COMMENT '更新时间',
    `deleted`      tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    KEY `idx_name` (`name`),
    KEY `idx_country` (`country`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='厂商目录（手办 / 景品公司）';

-- 初始演示厂商由后端 ManufacturerDataInitializer 在表为空时写入。
