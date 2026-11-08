<template>
  <div class="tags-view-container" ref="containerRef">
    <el-scrollbar class="scrollbar" ref="scrollbarRef">
      <router-link
        v-for="tag in visitedViews"
        :key="tag.path"
        :to="{ path: tag.path, query: tag.query }"
        class="tags-view-item"
        :class="isActive(tag) ? 'active' : ''"
        @click.middle="!isAffix(tag) ? closeSelectedTag(tag) : ''"
        @contextmenu.prevent="openMenu(tag, $event)"
      >
        {{ tag.title }}
        <!-- 只有非固定标签才显示关闭按钮 -->
        <el-icon
          v-if="!isAffix(tag)"
          @click.prevent.stop="closeSelectedTag(tag)"
          class="close-icon"
        >
          <Close />
        </el-icon>
      </router-link>
    </el-scrollbar>

    <!-- 右键菜单 -->
    <ul v-show="menuVisible" class="contextmenu" :style="{ left: `${menuLeft}px` }">
      <li @click="closeSelectedTag(selectedTag)">关闭当前</li>
      <li @click="closeOtherTags">关闭其他</li>
      <li @click="closeAllTags">关闭全部</li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { RouteLocationNormalizedLoaded } from 'vue-router'
import { Close } from '@element-plus/icons-vue'

// 定义标签的类型
interface TagView {
  title: string
  path: string
  query?: any
  affix?: boolean
}

const route = useRoute()
const router = useRouter()
const visitedViews = ref<TagView[]>([])

// 容器引用：用于计算右键菜单的相对位置
const containerRef = ref<HTMLElement>()

// 右键菜单状态
const menuVisible = ref(false)
const menuLeft = ref(0)
const selectedTag = ref<TagView | null>(null)

// 判断当前标签是否激活
const isActive = (tag: TagView) => {
  return tag.path === route.path
}

// 判断是否是固定标签
const isAffix = (tag: TagView) => {
  return tag.affix || false
}

// 添加标签逻辑
const addTags = (current: RouteLocationNormalizedLoaded) => {
  if (current.meta.title) {
    if (current.name === 'Login' || current.name === 'NotFound') {
      return false
    }
    if (visitedViews.value.some((v) => v.path === current.path)) {
      return false
    }
    visitedViews.value.push({
      title: current.meta.title as string,
      path: current.path,
      query: current.query
    })
  }
  return true
}

// 关闭标签逻辑
const closeSelectedTag = (view: TagView | null) => {
  menuVisible.value = false
  if (!view) return

  visitedViews.value = visitedViews.value.filter((v) => v.path !== view.path)
  // 关掉的是当前页时，跳到剩下的最后一个标签
  if (isActive(view)) {
    const latestView = visitedViews.value.slice(-1)[0]
    router.push(latestView ? latestView.path : '/')
  }
}

// 关闭其他标签（保留固定标签和当前右键选中的标签）
const closeOtherTags = () => {
  const view = selectedTag.value
  if (!view) return

  visitedViews.value = visitedViews.value.filter((v) => isAffix(v) || v.path === view.path)
  if (!isActive(view)) {
    router.push(view.path)
  }
  menuVisible.value = false
}

// 关闭全部标签（保留固定标签）
const closeAllTags = () => {
  visitedViews.value = visitedViews.value.filter((v) => isAffix(v))
  const latestView = visitedViews.value.slice(-1)[0]
  router.push(latestView ? latestView.path : '/')
  menuVisible.value = false
}

const openMenu = (tag: TagView, e: MouseEvent) => {
  const rect = containerRef.value?.getBoundingClientRect()
  const rawLeft = e.clientX - (rect?.left ?? 0)
  // 避免菜单超出容器右边界（菜单宽约 100px）
  const maxLeft = Math.max((rect?.width ?? 0) - 110, 0)

  menuLeft.value = Math.min(rawLeft, maxLeft)
  selectedTag.value = tag
  menuVisible.value = true
}

const closeMenu = () => {
  menuVisible.value = false
}

onMounted(() => {
  document.addEventListener('click', closeMenu)
})

onUnmounted(() => {
  document.removeEventListener('click', closeMenu)
})

// 监听路由变化
watch(
  () => route.path,
  () => {
    addTags(route)
    closeMenu()
  },
  { immediate: true }
)
</script>

<style scoped>
.tags-view-container {
  height: 40px;
  width: 100%;
  background: #fff;
  /* 移除原本生硬的底部边框，改用极淡的阴影 */
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  position: relative;
  z-index: 9;

  .scrollbar {
    height: 100%;
    white-space: nowrap;

    :deep(.el-scrollbar__view) {
      height: 100%;
      display: flex;
      align-items: center;
      padding-left: 10px; /* 左侧留白 */
    }
  }

  .tags-view-item {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    position: relative;
    cursor: pointer;
    height: 28px;
    line-height: 28px;
    border: 1px solid #e0e0e0; /* 柔和的灰色边框 */
    color: #666; /* 柔和的文字颜色 */
    background: #f5f5f5; /* 未选中时的淡灰背景 */
    padding: 0 10px;
    font-size: 13px;
    margin-left: 6px;
    border-radius: 4px; /* 圆角 */
    transition: all 0.3s cubic-bezier(0.645, 0.045, 0.355, 1);
    text-decoration: none; /* 去掉下划线 */

    /* 鼠标悬停效果 */
    &:hover {
      color: #409eff; /* 悬停变为主题蓝 */
      border-color: #b3d8ff;
      background: #e8f3ff;
    }

    /* 选中状态样式 - 核心改动在这里 */
    &.active {
      background-color: #ecf5ff; /* 柔和的淡蓝色背景 */
      color: #409eff; /* 主题蓝色文字 */
      border-color: #b3d8ff; /* 柔和的蓝色边框 */
      font-weight: 600;

      /* 选中时前面加个小竖条或小圆点装饰（可选，这里用了圆点） */
      &::before {
        content: '';
        background-color: #409eff;
        display: inline-block;
        width: 6px;
        height: 6px;
        border-radius: 50%;
        margin-right: 6px;
        transition: background-color 0.3s;
      }
    }

    /* 关闭图标样式 */
    .close-icon {
      border-radius: 50%;
      text-align: center;
      width: 14px;
      height: 14px;
      font-size: 12px;
      margin-left: 6px; /* 图标与文字间距 */
      color: #999; /* 默认灰色 */
      transition: all 0.2s;

      /* 只有鼠标悬停在标签上，或者标签处于激活状态时，才明显显示关闭图标 */
      &:hover {
        background-color: #ff4d4f; /* 悬停变红，提示删除 */
        color: #fff;
      }
    }

    /* 激活状态下，关闭图标默认也是浅色，除非悬停 */
    &.active .close-icon {
      color: #409eff;
    }
  }

  /* 右键菜单 */
  .contextmenu {
    position: absolute;
    top: 40px;
    margin: 0;
    padding: 6px 0;
    list-style: none;
    background: #fff;
    border-radius: 6px;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.15);
    z-index: 3000;
    min-width: 100px;

    li {
      padding: 8px 16px;
      font-size: 13px;
      color: #555;
      cursor: pointer;
      transition: background-color 0.2s;

      &:hover {
        background-color: #ecf5ff;
        color: #409eff;
      }
    }
  }
}
</style>
