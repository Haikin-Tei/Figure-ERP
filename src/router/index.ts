import { createRouter, createWebHistory } from 'vue-router'
import Layout from '@/components/Layout.vue'

const routes = [
  // 1. 登录页（独立页面，不走 Layout）
  {
    path: '/login',
    component: () => import('@/views/auth/Login.vue')
  },

  // 2. 首页 Dashboard（走 Layout）
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/Dashboard.vue'),
        meta: { title: '数据概览', icon: 'Histogram' }
      }
    ]
  },

  // 3. 库存管理模块（走 Layout）
  {
    path: '/stock',
    component: Layout,
    redirect: '/stock/figure',
    name: 'Stock',
    meta: { title: '库存管理', icon: 'Box' },
    children: [
      {
        path: 'figure',
        name: 'FigureList',
        component: () => import('@/views/stock/FigureList.vue'),
        meta: { title: '手办列表', icon: 'Goods' }
      },
      // {
      //   path: 'inbound',
      //   name: 'InboundRecord',
      //   component: () => import('@/views/stock/InboundRecord.vue'), // 假设文件已存在
      //   meta: { title: '入库记录', icon: 'Document' }
      // },
      // --- 新增：货架管理路由 ---
      {
        path: 'shelves',
        name: 'ShelvesManage',
        component: () => import('@/views/stock/ShelvesManage.vue'), // 对应你刚刚创建的文件
        meta: { title: '货架管理', icon: 'Grid' } // 使用 Grid 图标代表货架
      },
      // --- 新增结束 ---
      {
        path: 'location',
        name: 'LocationManage',
        component: () => import('@/views/stock/LocationManage/index.vue'),
        meta: { title: '库位管理', icon: 'Location' }
      },
      {
        path: 'area',
        name: 'AreaConfig',
        component: () => import('@/views/stock/AreaConfig.vue'),
        meta: { title: '区域配置', icon: 'OfficeBuilding' }
      }
    ]
  },

  // 4. 订单管理模块（走 Layout）
  {
    path: '/order',
    component: Layout,
    name: 'Order',
    meta: { title: '订单管理', icon: 'List' },
    children: [
      {
        path: 'list',
        name: 'OrderList',
        component: () => import('@/views/order/OrderList.vue'),
        meta: { title: '所有订单', icon: 'Tickets' }
      }
    ]
  },

  // 5. 系统设置模块（走 Layout）
  {
    path: '/system',
    component: Layout,
    name: 'System',
    meta: { title: '系统设置', icon: 'Setting' },
    children: [
      {
        path: 'user',
        name: 'UserManage',
        component: () => import('@/views/system/UserManage.vue'),
        meta: { title: '用户权限', icon: 'User' }
      },
      {
        path: 'logs',
        name: 'OperationLogs',
        component: () => import('@/views/system/OperationLogs.vue'),
        meta: { title: '操作日志', icon: 'Document' }
      }
    ]
  },

  // 6. 404 页面
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: Layout,
    children: [
      {
        path: '',
        component: () => import('@/views/auth/NotFound.vue')
      }
    ],
    meta: { hidden: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
