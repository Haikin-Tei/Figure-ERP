<template>
  <div class="dashboard-container">
    <!-- 1. 顶部关键指标 -->
    <el-row :gutter="20" class="top-cards">
      <el-col :span="6">
        <el-card shadow="hover" class="card-item card-blue">
          <div class="card-content">
            <div class="text-group">
              <div class="title">库存总数</div>
              <div class="value">1,240</div>
            </div>
            <el-icon class="card-icon"><ShoppingBag /></el-icon>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="card-item card-green">
          <div class="card-content">
            <div class="text-group">
              <div class="title">今日入库</div>
              <div class="value">56</div>
            </div>
            <el-icon class="card-icon"><Upload /></el-icon>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="card-item card-orange">
          <div class="card-content">
            <div class="text-group">
              <div class="title">待发货订单</div>
              <div class="value">12</div>
            </div>
            <el-icon class="card-icon"><Files /></el-icon>
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

    <!-- 2. 图表区域 -->
    <el-row :gutter="20" class="chart-row">
      <!-- 左侧：趋势图 -->
      <el-col :span="16">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>📈 近 7 天出入库趋势</span>
            </div>
          </template>
          <!-- 注意：ECharts 需要指定一个固定高度的容器 -->
          <div ref="lineChartRef" style="width: 100%; height: 350px"></div>
        </el-card>
      </el-col>

      <!-- 右侧：饼图 -->
      <el-col :span="8">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>🍩 库存分类占比</span>
            </div>
          </template>
          <div ref="pieChartRef" style="width: 100%; height: 350px"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 3. 底部：最新入库记录 -->
    <el-row :gutter="20" class="table-row">
      <el-col :span="24">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>📦 最新入库记录</span>
              <el-button type="primary" size="small" link>查看全部</el-button>
            </div>
          </template>
          <el-table :data="tableData" stripe style="width: 100%">
            <el-table-column prop="date" label="入库日期" width="180" />
            <el-table-column prop="name" label="手办名称" />
            <el-table-column prop="category" label="分类" width="120" />
            <el-table-column prop="quantity" label="数量" width="100" />
            <el-table-column label="状态" width="120">
              <template #default="scope">
                <el-tag type="success" effect="plain">已入库</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import * as echarts from 'echarts'
import {
  ShoppingBag,
  Upload,
  Files,
  TrendCharts
} from '@element-plus/icons-vue'

// --- 1. 图表 DOM 引用 ---
const lineChartRef = ref<HTMLElement>()
const pieChartRef = ref<HTMLElement>()

// --- 2. 模拟表格数据 ---
const tableData = [
  { date: '2026-03-28', name: '初音未来 雪初音 2026', category: '景品', quantity: 20 },
  { date: '2026-03-28', name: '雷姆 拉姆 双子', category: '手办', quantity: 5 },
  { date: '2026-03-27', name: 'EVA 初号机 暴走', category: '模型', quantity: 10 },
  { date: '2026-03-27', name: '原神 雷电将军', category: '手办', quantity: 15 },
]

// --- 3. 初始化图表逻辑 ---
onMounted(() => {
  initLineChart()
  initPieChart()
})

// 折线图配置
const initLineChart = () => {
  if (!lineChartRef.value) return
  const myChart = echarts.init(lineChartRef.value)
  const option = {
    tooltip: { trigger: 'axis' },
    legend: { data: ['入库数量', '出库数量'] },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: ['3-22', '3-23', '3-24', '3-25', '3-26', '3-27', '3-28']
    },
    yAxis: { type: 'value' },
    series: [
      {
        name: '入库数量',
        type: 'line',
        smooth: true, // 平滑曲线
        data: [120, 132, 101, 134, 90, 230, 210],
        itemStyle: { color: '#409EFF' },
        areaStyle: { color: 'rgba(64, 158, 255, 0.1)' } // 区域填充色
      },
      {
        name: '出库数量',
        type: 'line',
        smooth: true,
        data: [220, 182, 191, 234, 290, 330, 310],
        itemStyle: { color: '#67C23A' },
        areaStyle: { color: 'rgba(103, 194, 58, 0.1)' }
      }
    ]
  }
  myChart.setOption(option)
}

// 饼图配置
const initPieChart = () => {
  if (!pieChartRef.value) return
  const myChart = echarts.init(pieChartRef.value)
  const option = {
    tooltip: { trigger: 'item' },
    legend: { bottom: '0%', left: 'center' },
    series: [
      {
        name: '分类',
        type: 'pie',
        radius: ['40%', '70%'], // 环形图
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 10,
          borderColor: '#fff',
          borderWidth: 2
        },
        label: { show: false, position: 'center' },
        emphasis: {
          label: { show: true, fontSize: 20, fontWeight: 'bold' }
        },
        data: [
          { value: 1048, name: '手办' },
          { value: 735, name: '景品' },
          { value: 580, name: '模型' },
          { value: 484, name: '周边' }
        ]
      }
    ]
  }
  myChart.setOption(option)
}
</script>

<style scoped>
.dashboard-container {
  padding: 20px;
  background-color: #f0f2f5;
  min-height: 100%;
}

/* 卡片通用样式 */
.top-cards {
  margin-bottom: 20px;
}
.card-item {
  color: white;
  border: none;
}
.card-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.text-group .title {
  font-size: 14px;
  opacity: 0.9;
  margin-bottom: 5px;
}
.text-group .value {
  font-size: 24px;
  font-weight: bold;
}
.card-icon {
  font-size: 40px;
  opacity: 0.8;
}

/* 渐变色定义 */
.card-blue { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); }
.card-green { background: linear-gradient(135deg, #2af598 0%, #009efd 100%); }
.card-orange { background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%); }
.card-purple { background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%); }

/* 图表区域 */
.chart-row {
  margin-bottom: 20px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: bold;
}
</style>
