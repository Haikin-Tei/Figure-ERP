<!-- src/components/Layout.vue -->
<template>
  <div class="layout-container">
    <!-- 左侧侧边栏 -->
    <Sidebar />

    <div class="main-container">
      <!-- 顶部导航栏 -->
      <Navbar />

      <!-- ✅ 在这里插入 TagsView 组件 -->
      <TagsView />

      <!-- 主要内容区域 -->
      <main class="app-main">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import Sidebar from './Sidebar.vue'
import Navbar from './Navbar.vue'
// 1. 引入 TagsView
import TagsView from './TagsView.vue'
</script>

<style scoped>
/* 保持原有样式，只需要确保 .app-main 的 margin-top 足够或者不需要调整，
   因为 TagsView 高度是固定的，会自然把 main 挤下去 */
.layout-container {
  display: flex;
  height: 100vh;
}

.main-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background-color: #f0f2f5;
}

/* 确保 main 区域可以滚动 */
.app-main {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}
</style>
