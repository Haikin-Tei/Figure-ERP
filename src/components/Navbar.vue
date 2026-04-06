<script setup lang="ts">
import { User } from '@element-plus/icons-vue'
</script>

<template>
  <div class="navbar">
    <div class="right-menu">
      <div class="avatar-wrapper">
        <img
          src="https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png"
          class="user-avatar"
        />
        <span class="username">Admin</span>
        <el-icon class="el-icon--right"><User /></el-icon>
      </div>
    </div>
  </div>
</template>

<style scoped>
.navbar {
  height: 60px;
  overflow: hidden;
  position: relative;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  display: flex;
  justify-content: flex-end;
  align-items: center;
  padding-right: 20px;
}

.right-menu {
  display: flex;
  align-items: center;
}

.avatar-wrapper {
  display: flex;
  align-items: center;
  cursor: pointer;
}

.user-avatar {
  cursor: pointer;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  margin-right: 10px;
  border: 2px solid #409EFF;
}

.username {
  font-weight: bold;
  color: #333;
  margin-right: 5px;
}
</style>
