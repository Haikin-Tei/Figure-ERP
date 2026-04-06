<template>
  <div class="login-container">
    <el-card class="login-card" header="🔒 系统登录">
      <div class="login-body">
        <p>这里是登录页面（占位）</p>
        <el-button type="primary" @click="handleLogin" style="width: 100%">
          点击模拟登录
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

const router = useRouter()

const handleLogin = () => {
  // 模拟登录成功，跳转到首页
  ElMessage.success('登录成功！')
  router.push('/')
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background-color: #f0f2f5;
}
.login-card {
  width: 400px;
}
.login-body {
  padding: 20px 0;
  text-align: center;
}
</style>
