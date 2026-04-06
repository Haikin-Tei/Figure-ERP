import {
  ShoppingBag,
  Document,
  Setting,
  User,
  Timer,
  Location,
  Box,
  OfficeBuilding
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
          { title: '货架管理', index: '/stock/shelves' },
          { title: '区域配置', index: '/stock/area' }
        ]
      }
    ]
  },
  {
    title: '订单管理',
    icon: Document,
    index: '2',
    children: [
      { title: '所有订单', index: '/order/list' }
    ]
  },
  {
    title: '系统设置',
    icon: Setting,
    index: '3',
    children: [
      { title: '用户权限', index: '/system/user' },
      { title: '操作日志', index: '/system/logs' }
    ]
  }
]
