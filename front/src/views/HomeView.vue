<script setup lang="ts">
import {
  Goods,
  ShoppingBag,
  Wallet,
  TrendCharts,
  Document,
  User,
  Bell
} from '@element-plus/icons-vue'
</script>

<template>
  <div class="home-container">
    <!-- 顶部欢迎语 -->
    <div class="welcome-section">
      <h2>👋 欢迎回来，管理员！</h2>
      <p>这里是手办管理系统 Dashboard，今天也是元气满满的一天！</p>
    </div>

    <!-- 第一行：核心数据概览 (4个卡片) -->
    <el-row :gutter="20" class="data-cards">
      <el-col :span="6">
        <el-card shadow="hover" class="card-item card-blue">
          <div class="card-content">
            <div class="text-group">
              <div class="title">库存总数</div>
              <div class="value">1,240 <span class="unit">件</span></div>
            </div>
            <el-icon class="card-icon"><Goods /></el-icon>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card shadow="hover" class="card-item card-green">
          <div class="card-content">
            <div class="text-group">
              <div class="title">今日入库</div>
              <div class="value">56 <span class="unit">件</span></div>
            </div>
            <el-icon class="card-icon"><ShoppingBag /></el-icon>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card shadow="hover" class="card-item card-orange">
          <div class="card-content">
            <div class="text-group">
              <div class="title">待发货订单</div>
              <div class="value">12 <span class="unit">单</span></div>
            </div>
            <el-icon class="card-icon"><Wallet /></el-icon>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card shadow="hover" class="card-item card-purple">
          <div class="card-content">
            <div class="text-group">
              <div class="title">本月销售额</div>
              <div class="value">¥ 8.4w</div>
            </div>
            <el-icon class="card-icon"><TrendCharts /></el-icon>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 第二行：快捷入口与公告 -->
    <el-row :gutter="20" class="bottom-section">
      <!-- 左侧：快捷功能 -->
      <el-col :span="16">
        <el-card shadow="never" header="快捷功能">
          <div class="grid-box">
            <div class="grid-item" v-for="i in 6" :key="i">
              <el-button type="primary" :icon="Document" circle class="icon-btn" />
              <span class="label">功能入口 {{ i }}</span>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 右侧：系统公告 -->
      <el-col :span="8">
        <el-card shadow="never" header="系统公告">
          <el-timeline>
            <el-timeline-item timestamp="2026/03/28" placement="top">
              <el-card>
                <h4>系统升级通知</h4>
                <p>今晚凌晨 2:00 进行数据库维护，请提前保存数据。</p>
              </el-card>
            </el-timeline-item>
            <el-timeline-item timestamp="2026/03/25" placement="top">
              <el-card>
                <h4>新功能上线</h4>
                <p>手办图片批量上传功能已上线。</p>
              </el-card>
            </el-timeline-item>
          </el-timeline>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.home-container {
  padding: 20px;
  background-color: #f0f2f5;
  min-height: 100vh;
}

.welcome-section {
  margin-bottom: 20px;
}

/* 卡片通用样式 */
.card-item {
  border: none;
  color: #fff;
  height: 100px;
}

.card-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 100%;
}

.text-group .title {
  font-size: 14px;
  opacity: 0.8;
  margin-bottom: 5px;
}

.text-group .value {
  font-size: 24px;
  font-weight: bold;
}

.unit {
  font-size: 12px;
  font-weight: normal;
  margin-left: 4px;
}

.card-icon {
  font-size: 40px;
  opacity: 0.8;
}

/* 卡片颜色定义 */
.card-blue { background: linear-gradient(135deg, #409eff, #66b1ff); }
.card-green { background: linear-gradient(135deg, #67c23a, #85ce61); }
.card-orange { background: linear-gradient(135deg, #e6a23c, #ebb563); }
.card-purple { background: linear-gradient(135deg, #9c27b0, #ba68c8); }

/* 底部区域 */
.bottom-section .el-card {
  height: 100%;
}

/* 快捷入口网格 */
.grid-box {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
}

.grid-item {
  width: 30%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 15px 0;
  cursor: pointer;
  transition: background 0.3s;
  border-radius: 8px;
}

.grid-item:hover {
  background-color: #f5f7fa;
}

.icon-btn {
  margin-bottom: 10px;
  width: 40px;
  height: 40px;
}

.label {
  font-size: 14px;
  color: #606266;
}
</style>
