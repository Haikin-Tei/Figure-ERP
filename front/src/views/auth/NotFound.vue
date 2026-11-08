<template>
  <div class="not-found-container">
    <div class="content">
      <!-- 大标题 -->
      <div class="error-code">404</div>

      <!-- 提示信息 -->
      <h1 class="error-title">页面迷失在次元裂缝中</h1>
      <p class="error-desc">
        抱歉，你访问的页面似乎被初音未来带去开演唱会了，或者它根本就不存在。
      </p>

      <!-- 返回首页按钮 -->
      <el-button type="primary" size="large" @click="goHome">
        <el-icon><HomeFilled /></el-icon>
        返回首页
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { HomeFilled } from '@element-plus/icons-vue'

const router = useRouter()

const goHome = () => {
  router.push('/')
}
</script>

<style scoped>
.not-found-container {
  /* 核心修改：不再强制占满整个屏幕高度，而是适应内容区域 */
  display: flex;
  justify-content: center;
  align-items: center;

  /* 计算高度：100vh 减去顶部标签栏和可能的页头高度，防止出现滚动条 */
  /* 这里预留了约 120px 的空间给顶部导航，你可以根据实际情况微调 */
  min-height: calc(100vh - 120px);

  /* 保持背景纹理，但去掉全屏遮罩感 */
  background-color: transparent; /* 改为透明，融入页面背景 */
  padding: 20px 0; /* 上下留一点空隙 */
}

.content {
  text-align: center;
  padding: 40px;
  background: white;
  border-radius: 16px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.08); /* 稍微柔和一点的阴影 */
  animation: fadeIn 0.8s ease-out;
  width: 100%;
  max-width: 500px; /* 限制最大宽度，在大屏幕上更好看 */
  margin: 0 20px; /* 移动端适配 */
}

/* 巨大的 404 数字 */
.error-code {
  font-size: 100px; /* 稍微缩小一点，避免在小屏幕溢出 */
  font-weight: 900;
  line-height: 1;
  margin-bottom: 20px;
  /* 渐变文字效果 */
  background: linear-gradient(135deg, #409EFF 0%, #764ba2 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.error-title {
  font-size: 24px;
  color: #333;
  margin-bottom: 10px;
  font-weight: bold;
}

.error-desc {
  font-size: 16px;
  color: #666;
  margin-bottom: 30px;
  line-height: 1.6;
}

/* 简单的淡入动画 */
@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
