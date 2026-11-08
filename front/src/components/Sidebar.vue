<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { PieChart, TrendCharts, HomeFilled } from '@element-plus/icons-vue'
// 引入刚才创建的配置文件和数据
import { menuData } from './menuConfig'
import { useUserStore } from '@/stores/user'
import { canViewDashboard } from '@/utils/role'

const route = useRoute()
const userStore = useUserStore()

// 当前高亮的菜单项：跟随路由变化，不再写死
const activeIndex = computed(() => route.path)

/**
 * 「数据概览」是经营看板，只有总经理、销售主管与管理员的菜单里出现；
 * 其他人菜单里只有「工作台」，避免点进去看到一个和自己无关的销售数据页。
 */
const showDashboard = computed(() => canViewDashboard(userStore.userInfo))
</script>

<template>
  <div class="sidebar">
    <!-- Logo 区域 -->
    <div class="logo">
      <el-icon :size="24"><PieChart /></el-icon>
      <span>手办管家</span>
    </div>

    <!-- 菜单区域 -->
    <el-menu
      :default-active="activeIndex"
      class="sidebar-menu"
      background-color="#001529"
      text-color="#bfcbd9"
      active-text-color="#409EFF"
      router
    >
      <!-- 1. 工作台（所有人的首页） -->
      <el-menu-item index="/home">
        <el-icon><HomeFilled /></el-icon>
        <span>工作台</span>
      </el-menu-item>

      <!-- 2. 数据概览（仅管理级 + 销售主管可见，且只有总经理/销售主管的首页是它） -->
      <el-menu-item v-if="showDashboard" index="/dashboard">
        <el-icon><TrendCharts /></el-icon>
        <span>数据概览</span>
      </el-menu-item>

      <!-- 3. 动态渲染其他菜单 -->
      <template v-for="item in menuData" :key="item.index">
        <el-sub-menu :index="item.index">
          <template #title>
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.title }}</span>
          </template>

          <!-- 递归渲染子菜单（支持三级） -->
          <template v-for="sub in item.children" :key="sub.index">
            <!-- 如果有 children，则是三级菜单的父级 -->
            <el-sub-menu v-if="sub.children" :index="sub.index">
              <template #title>{{ sub.title }}</template>
              <el-menu-item
                v-for="child in sub.children"
                :key="child.index"
                :index="child.index"
              >
                {{ child.title }}
              </el-menu-item>
            </el-sub-menu>

            <!-- 如果没有 children，则是普通的二级菜单项 -->
            <el-menu-item v-else :index="sub.index">
              {{ sub.title }}
            </el-menu-item>
          </template>
        </el-sub-menu>
      </template>
    </el-menu>
  </div>
</template>

<style scoped>
.sidebar {
  height: 100vh;
  background-color: #001529;
  display: flex;
  flex-direction: column;
  box-shadow: 2px 0 6px rgba(0, 21, 41, 0.35);
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 18px;
  font-weight: bold;
  border-bottom: 1px solid #002140;
  background-color: #001529;
}

.logo .el-icon {
  margin-right: 8px;
  color: #409EFF;
}

.sidebar-menu {
  border-right: none;
  flex: 1;
  overflow-y: auto;
}
</style>
