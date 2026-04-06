<template>
  <div class="page-container">
    <div class="card">
      <!-- 页面标题 -->
      <h2 class="page-title">
        <el-icon><UserFilled /></el-icon>
        用户权限管理
      </h2>

      <el-divider />

      <!-- 模拟数据展示区域 -->
      <div class="content-placeholder">
        <el-descriptions title="当前系统管理员" :column="1" border>
          <el-descriptions-item label="用户名">
            <el-tag size="small" type="primary">Admin</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="角色权限">
            <el-tag size="small" effect="dark">超级管理员</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="描述">
            该页面目前为占位状态。未来将在此处实现用户的增删改查及角色权限分配功能。
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { UserFilled } from '@element-plus/icons-vue'
</script>

<style scoped>
/* 保持和之前一样的样式 */
.page-container {
  padding: 20px;
}
.card {
  background-color: #fff;
  padding: 24px;
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
}
.page-title {
  margin: 0 0 20px;
  font-weight: 600;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 8px;
}
.content-placeholder {
  padding: 20px 0;
}
</style>
