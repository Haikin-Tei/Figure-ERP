<!-- src/components/Breadcrumb.vue -->
<template>
  <div class="breadcrumb-bar">
    <el-breadcrumb class="breadcrumb" separator="/">
      <el-breadcrumb-item v-for="(crumb, i) in crumbs" :key="`${i}-${crumb.title}`" :to="crumb.to">
        <span :class="['crumb-text', { current: i === crumbs.length - 1 }]">{{ crumb.title }}</span>
      </el-breadcrumb-item>
    </el-breadcrumb>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { menuData, type MenuItem } from './menuConfig'

interface Crumb {
  title: string
  /** 可点击跳转的目标；为空表示纯分组节点或当前页 */
  to?: string
}

const route = useRoute()

/**
 * 在菜单树里按路由 path 反查祖先链。
 *
 * 为什么不用 route.matched：菜单里的二级分组（如「库存管理 → 仓库设置」）没有独立路由，
 * matched 只能拿到「模块 / 页面」两层，拿不到这一层分组，只有菜单树里才是完整的 1/2/3 级。
 */
function findMenuChain(items: MenuItem[], target: string, chain: MenuItem[] = []): MenuItem[] | null {
  for (const item of items) {
    const next = [...chain, item]
    if (item.index === target) return next
    if (item.children?.length) {
      const found = findMenuChain(item.children, target, next)
      if (found) return found
    }
  }
  return null
}

/** 当前页面所属一级模块的路由（如 /stock），用于点击一级目录回到该模块默认页 */
const modulePath = computed(() => {
  const first = route.matched[0]
  return route.matched.length > 1 && first ? first.path : ''
})

const crumbs = computed<Crumb[]>(() => {
  const chain = findMenuChain(menuData, route.path)

  if (chain && chain.length) {
    return chain.map((item, index) => {
      const isCurrent = index === chain.length - 1
      return {
        title: item.title,
        // 只有一级目录可点（回到模块默认页）；中间的分组节点（index 形如 '1' / '1-3'）和当前页不可点
        to: !isCurrent && index === 0 && modulePath.value ? modulePath.value : undefined
      }
    })
  }

  // 不在左侧菜单里的页面（例如「个人消息」），可以在路由 meta.breadcrumb 里直接声明层级
  const trail = (route.meta?.breadcrumb as string[] | undefined) ?? []
  if (trail.length) {
    return trail.map((title) => ({ title }))
  }

  // 不在菜单里的页面：首页（工作台 / 数据概览）、404
  if (route.path === '/home') return [{ title: '工作台' }]
  if (route.path === '/dashboard') return [{ title: '数据概览' }]
  return [{ title: (route.meta?.title as string) || '页面不存在' }]
})
</script>

<style scoped>
.breadcrumb-bar {
  flex: none;
  display: flex;
  align-items: center;
  height: 38px;
  padding: 0 20px;
  background: #fff;
  border-bottom: 1px solid #f0f0f0;
  overflow: hidden;
}

.breadcrumb {
  display: flex;
  flex-wrap: nowrap;
  font-size: 13px;
  line-height: 1;
  white-space: nowrap;
  min-width: 0;
  overflow: hidden;
}

/* 层级项不压缩，超长时整体裁切而不是折行 */
.breadcrumb :deep(.el-breadcrumb__item) {
  flex: 0 0 auto;
  white-space: nowrap;
}

.breadcrumb :deep(.el-breadcrumb__inner) {
  color: #909399;
  font-weight: 400;
}

/* 可点击的一级目录 */
.breadcrumb :deep(.el-breadcrumb__inner.is-link) {
  color: #909399;
  font-weight: 400;
  transition: color 0.2s;
}

.breadcrumb :deep(.el-breadcrumb__inner.is-link:hover) {
  color: #409eff;
}

.crumb-text.current {
  color: #303133;
  font-weight: 600;
}

/* 窄屏：缩小字号与内边距；层级过长时改为横向滑动，保证每一级都能看到 */
@media (max-width: 768px) {
  .breadcrumb-bar {
    padding: 0 12px;
  }

  .breadcrumb {
    font-size: 12px;
    overflow-x: auto;
    overflow-y: hidden;
    scrollbar-width: none;
  }

  .breadcrumb::-webkit-scrollbar {
    display: none;
  }
}
</style>
