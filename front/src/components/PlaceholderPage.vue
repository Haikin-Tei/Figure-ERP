<!-- src/components/PlaceholderPage.vue
     通用占位页：仅给「已建目录、暂未做后台数据 / 页面设计」的页面使用。
     后续接入真实功能时，直接替换对应 views 里的包装页面即可。 -->
<template>
  <div class="page-container">
    <div class="card">
      <div class="card-head">
        <h2 class="page-title">
          <el-icon><FolderOpened /></el-icon>
          {{ title }}
        </h2>
      </div>
      <p class="page-desc">该目录页面已创建，后台数据与页面功能正在规划中，敬请期待。</p>
      <el-empty description="功能建设中" :image-size="120" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { FolderOpened } from '@element-plus/icons-vue'

defineProps<{
  title: string
}>()
</script>

<style scoped>
.page-container {
  padding: 20px;
}
.card {
  background-color: #fff;
  padding: 24px;
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
}
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.page-title {
  margin: 0;
  font-weight: 600;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
}
.page-desc {
  margin: 8px 0 18px;
  font-size: 13px;
  color: #909399;
  line-height: 1.6;
}
</style>
