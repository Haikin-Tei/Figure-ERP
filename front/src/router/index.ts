import { createRouter, createWebHistory } from 'vue-router'
import Layout from '@/components/Layout.vue'
import { useUserStore } from '@/stores/user'
import { canViewDashboard, homePathFor } from '@/utils/role'

const routes = [
  // 1. 登录页（独立页面，不走 Layout）
  {
    path: '/login',
    component: () => import('@/views/auth/Login.vue')
  },

  // 2. 首页（走 Layout）：工作台 / 数据概览
  {
    path: '/',
    component: Layout,
    // 先落到数据概览，守卫里再按角色改写目标：
    // 只有总经理、销售主管的首页是它，其他人会被送到 /home（工作台）
    redirect: '/dashboard',
    children: [
      {
        path: 'home',
        name: 'Workbench',
        component: () => import('@/views/home/Workbench.vue'),
        meta: { title: '工作台', icon: 'HomeFilled' }
      },
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
      {
        path: 'inbound',
        name: 'InboundRecord',
        component: () => import('@/views/stock/InboundRecord.vue'),
        meta: { title: '入库记录', icon: 'Document' }
      },
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
      }
    ]
  },

  // 4. 物流管理模块（走 Layout）
  {
    path: '/logistics',
    component: Layout,
    redirect: '/logistics/price',
    name: 'Logistics',
    meta: { title: '物流管理', icon: 'Van' },
    children: [
      {
        path: 'price',
        name: 'LogisticsPriceMaintain',
        component: () => import('@/views/logistics/PriceMaintain.vue'),
        meta: { title: '物流价格维护', icon: 'Edit' }
      },
      {
        path: 'query',
        name: 'LogisticsPriceQuery',
        component: () => import('@/views/logistics/PriceQuery.vue'),
        meta: { title: '物流价格查询', icon: 'Search' }
      }
    ]
  },

  // 5. 订单管理模块（走 Layout）
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

  // 5. 员工权限管理（走 Layout）
  {
    path: '/staff',
    component: Layout,
    redirect: '/staff/list',
    name: 'Staff',
    meta: { title: '员工权限管理', icon: 'UserFilled' },
    children: [
      {
        path: 'list',
        name: 'EmployeeList',
        component: () => import('@/views/staff/EmployeeList.vue'),
        meta: { title: '员工列表', icon: 'User' }
      },
      {
        path: 'maintain',
        name: 'EmployeeMaintain',
        component: () => import('@/views/staff/EmployeeMaintain.vue'),
        meta: { title: '员工维护', icon: 'Setting' }
      },
      {
        path: 'attendance',
        name: 'AttendanceRecord',
        component: () => import('@/views/staff/AttendanceRecord.vue'),
        meta: { title: '员工考勤记录', icon: 'Timer' }
      },
      {
        path: 'leave',
        name: 'LeaveApply',
        component: () => import('@/views/staff/LeaveApply.vue'),
        meta: { title: '员工请假申请', icon: 'Calendar' }
      },
      {
        path: 'overtime',
        name: 'OvertimeApply',
        component: () => import('@/views/staff/OvertimeApply.vue'),
        meta: { title: '员工加班申请', icon: 'AlarmClock' }
      }
    ]
  },

  // 6. 系统设置模块（走 Layout）
  {
    path: '/system',
    component: Layout,
    name: 'System',
    meta: { title: '系统设置', icon: 'Setting' },
    children: [
      {
        path: 'logs',
        name: 'OperationLogs',
        component: () => import('@/views/system/OperationLogs.vue'),
        meta: { title: '操作日志', icon: 'Document' }
      }
    ]
  },

  // 7. 商品维护模块（走 Layout）
  {
    path: '/product',
    component: Layout,
    redirect: '/product/list',
    name: 'Product',
    meta: { title: '商品维护', icon: 'Goods' },
    children: [
      {
        path: 'list',
        name: 'ProductList',
        component: () => import('@/views/product/ProductList.vue'),
        meta: { title: '商品列表', icon: 'Goods' }
      }
    ]
  },

  // 8. 目录维护（走 Layout）：作品 + 供应商 / 大客户 / 厂商
  {
    path: '/acg',
    component: Layout,
    redirect: '/acg/catalog',
    name: 'Acg',
    meta: { title: '目录维护', icon: 'Reading' },
    children: [
      {
        path: 'catalog',
        name: 'AcgWorkCatalog',
        component: () => import('@/views/acg/WorkCatalog.vue'),
        meta: { title: '作品目录及维护', icon: 'Reading' }
      },
      // 以下三个为占位页：仅新增页面，暂不接入后台数据与页面设计
      {
        path: 'supplier',
        name: 'SupplierCatalog',
        component: () => import('@/views/acg/SupplierCatalog.vue'),
        meta: { title: '供应商目录维护', icon: 'OfficeBuilding' }
      },
      {
        path: 'key-customer',
        name: 'KeyCustomerCatalog',
        component: () => import('@/views/acg/KeyCustomerCatalog.vue'),
        meta: { title: '大客户目录维护', icon: 'User' }
      },
      {
        path: 'manufacturer',
        name: 'ManufacturerCatalog',
        component: () => import('@/views/acg/ManufacturerCatalog.vue'),
        meta: { title: '厂商目录维护', icon: 'Goods' }
      }
    ]
  },

  // 9. 个人消息（从右上角用户菜单进入，不占左侧菜单）
  {
    path: '/message',
    component: Layout,
    redirect: '/message/list',
    name: 'Message',
    meta: { title: '个人消息', hidden: true },
    children: [
      {
        path: 'list',
        name: 'MessageList',
        component: () => import('@/views/message/MessageList.vue'),
        // breadcrumb：不在左侧菜单里的页面，直接在路由上声明面包屑层级
        meta: { title: '个人消息', breadcrumb: ['个人中心', '个人消息'] }
      }
    ]
  },

  // 7. 404 页面
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

/** 无需登录即可访问的页面 */
const WHITE_LIST = ['/login']

/**
 * 全局前置守卫：
 * 1. 白名单直接放行；
 * 2. 没有令牌 → 跳登录页，并把原地址放进 redirect，登录后自动回来；
 * 3. 有令牌但用户信息还没拉（刷新页面）→ 拉一次 /auth/me 恢复登录态，失败则清空重登；
 * 4. 首页按角色分流：数据概览只给总经理/销售主管，其他人一律进工作台。
 */
router.beforeEach(async (to) => {
  const userStore = useUserStore()

  if (WHITE_LIST.includes(to.path)) {
    // 已登录的人再访问登录页，直接送回他自己的首页
    if (userStore.isLoggedIn && userStore.userInfo) {
      return { path: homePathFor(userStore.userInfo) }
    }
    return true
  }

  if (!userStore.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  if (!userStore.userInfo) {
    try {
      await userStore.fetchUserInfo()
    } catch {
      userStore.reset()
      return { path: '/login', query: { redirect: to.fullPath } }
    }
  }

  // / 会先按路由配置落到 /dashboard，这里再把无权查看的人换成工作台。
  // 用 replace，避免用户在浏览器里「后退」又被弹回同一个地址。
  if (to.path === '/dashboard' && !canViewDashboard(userStore.userInfo)) {
    return { path: '/home', replace: true }
  }

  return true
})

export default router
