<script setup lang="ts">
import { ref } from 'vue'
import { PieChart, TrendCharts } from '@element-plus/icons-vue'
// 引入刚才创建的配置文件和数据
import { menuData } from './menuConfig'

// 默认选中的菜单项
const activeIndex = ref('/dashboard')
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
      <!-- 1. 独立出来的“数据概览” (一级菜单) -->
      <el-menu-item index="/dashboard">
        <el-icon><TrendCharts /></el-icon>
        <span>数据概览</span>
      </el-menu-item>

      <!-- 2. 动态渲染其他菜单 -->
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
