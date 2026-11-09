import {
  ShoppingBag,
  Document,
  Setting,
  UserFilled,
  Timer,
  Location,
  Box,
  OfficeBuilding,
  Van,
  Goods,
  Reading
} from '@element-plus/icons-vue'

// 定义菜单项的通用类型接口
export interface MenuItem {
  title: string
  index: string
  icon?: any // 图标组件
  children?: MenuItem[] // 子菜单（支持无限嵌套）
}

/**
 * 侧边栏菜单配置
 * 这里的 index 必须和 router/index.ts 中的 path 完全一致
 */
export const menuData: MenuItem[] = [
  {
    title: '库存管理',
    icon: ShoppingBag,
    index: '1',
    children: [
      { title: '手办列表', index: '/stock/figure' },
      { title: '入库记录', index: '/stock/inbound' },
      {
        title: '仓库设置',
        index: '1-3',
        children: [
          { title: '库位管理', index: '/stock/location'},
          { title: '货架管理', index: '/stock/shelves' }
        ]
      }
    ]
  },
  {
    title: '物流管理',
    icon: Van,
    index: '2',
    children: [
      { title: '物流价格维护', index: '/logistics/price' },
      { title: '物流价格查询', index: '/logistics/query' }
    ]
  },
  {
    title: '订单管理',
    icon: Document,
    index: '3',
    children: [
      { title: '所有订单', index: '/order/list' }
    ]
  },
  {
    title: '员工权限管理',
    icon: UserFilled,
    index: '4',
    children: [
      { title: '员工列表', index: '/staff/list' },
      { title: '员工维护', index: '/staff/maintain' },
      { title: '员工考勤记录', index: '/staff/attendance' },
      { title: '员工请假申请', index: '/staff/leave' },
      { title: '员工加班申请', index: '/staff/overtime' }
    ]
  },
  {
    title: '系统设置',
    icon: Setting,
    index: '5',
    children: [{ title: '操作日志', index: '/system/logs' }]
  },
  {
    title: '商品维护',
    icon: Goods,
    index: '6',
    children: [
      { title: '商品列表', index: '/product/list' }
    ]
  },
  {
    title: '目录维护',
    icon: Reading,
    index: '7',
    children: [
      { title: '作品目录及维护', index: '/acg/catalog' },
      { title: '供应商目录维护', index: '/acg/supplier' },
      { title: '大客户目录维护', index: '/acg/key-customer' },
      { title: '厂商目录维护', index: '/acg/manufacturer' }
    ]
  }
]
