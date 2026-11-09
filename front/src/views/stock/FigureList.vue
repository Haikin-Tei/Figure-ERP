<template>
  <div class="stock-container">
    <!-- 1. 顶部统计 -->
    <el-row :gutter="16" class="stat-row">
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">在库款式</div>
          <div class="stat-value">{{ summary.skuCount ?? 0 }}</div>
          <div class="stat-tip">有库存的手办款数</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">在库总件数</div>
          <div class="stat-value qty">{{ summary.totalQuantity ?? 0 }}</div>
          <div class="stat-tip">所有库位的数量合计</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">库存金额</div>
          <div class="stat-value amount">{{ money(summary.totalAmount) }}</div>
          <div class="stat-tip">按入库单价累计的成本总额</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">占用库位</div>
          <div class="stat-value">{{ summary.locationCount ?? 0 }}</div>
          <div class="stat-tip">分布在 {{ summary.warehouseCount ?? 0 }} 个仓库</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 2. 筛选栏 -->
    <el-card shadow="never" class="filter-card">
      <div class="filter-bar">
        <el-input
          v-model="filterKeyword"
          placeholder="手办名称 / 货号 / 品牌"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />

        <el-select
          v-model="filterWarehouseId"
          placeholder="全部仓库"
          clearable
          filterable
          style="width: 165px"
          @change="handleSearch"
        >
          <el-option
            v-for="w in warehouseOptions"
            :key="w.id"
            :label="`${w.warehouseName} (${w.warehouseCode})`"
            :value="w.id"
          />
        </el-select>

        <el-select v-model="filterSortBy" placeholder="排序" style="width: 130px" @change="handleSearch">
          <el-option v-for="s in SORT_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>

        <el-button type="primary" @click="handleSearch">
          <el-icon><Search /></el-icon>
          查询
        </el-button>
        <el-button @click="handleReset">
          <el-icon><RefreshLeft /></el-icon>
          重置
        </el-button>

        <div class="flex-grow"></div>

        <span class="data-tip">库存由「入库记录」确认入库后自动累加，不在此页直接改动</span>
      </div>
    </el-card>

    <!-- 3. 列表：一行 = 一款手办的库存汇总 -->
    <BaseTable
      :data="list"
      :columns="columns"
      :loading="loading"
      :pagination="true"
      :total="total"
      :current-page="query.page"
      :page-size="query.size"
      @pagination-change="handlePaginationChange"
    >
      <template #productName="scope">
        <div class="product-cell">
          <el-avatar shape="square" :size="40" :src="scope.row.imageUrl || undefined" class="thumb">
            {{ (scope.row.productName || '?').slice(0, 1) }}
          </el-avatar>
          <div class="product-main">
            <div class="product-name">
              <span class="name-text">{{ scope.row.productName || '—' }}</span>
              <el-tag v-if="scope.row.productDeleted" size="small" type="danger" effect="plain">已下架</el-tag>
              <el-tag
                v-else-if="scope.row.productStatus === 0"
                size="small"
                type="info"
                effect="plain"
              >
                已停用
              </el-tag>
            </div>
            <div class="product-sub">
              {{ scope.row.productCode || '无货号' }}
              <span class="dot">·</span>
              {{ scope.row.brand || '未填品牌' }}
            </div>
          </div>
        </div>
      </template>

      <template #totalQuantity="scope">
        <span class="qty">{{ scope.row.totalQuantity ?? 0 }}</span>
        <span class="unit"> 件</span>
      </template>

      <template #totalAmount="scope">
        <div class="amount-cell">
          <span class="amount-text">{{ money(scope.row.totalAmount) }}</span>
          <span class="amount-sub">均价 {{ money(scope.row.avgCost) }}</span>
        </div>
      </template>

      <template #locationCount="scope">
        <el-tag size="small" effect="plain" :type="scope.row.locationCount > 1 ? 'warning' : 'info'">
          {{ scope.row.locationCount ?? 0 }} 处
        </el-tag>
      </template>

      <template #lastInboundTime="scope">
        <span class="time-text">{{ formatTime(scope.row.lastInboundTime) }}</span>
      </template>

      <template #action="scope">
        <el-button link type="primary" size="small" @click="handleLocations(scope.row)">库位明细</el-button>
      </template>
    </BaseTable>

    <!-- 4. 库位明细抽屉 -->
    <el-drawer v-model="drawerVisible" title="库位明细" size="640px" destroy-on-close>
      <div v-if="currentProduct" class="drawer-head">
        <div class="drawer-title">{{ currentProduct.productName }}</div>
        <div class="drawer-sub">
          {{ currentProduct.productCode || '无货号' }}
          <span class="dot">·</span>
          {{ currentProduct.brand || '未填品牌' }}
        </div>
      </div>

      <el-descriptions v-if="currentProduct" :column="3" border size="small" class="drawer-desc">
        <el-descriptions-item label="在库件数">{{ currentProduct.totalQuantity }} 件</el-descriptions-item>
        <el-descriptions-item label="库存金额">{{ money(currentProduct.totalAmount) }}</el-descriptions-item>
        <el-descriptions-item label="占用库位">{{ currentProduct.locationCount }} 处</el-descriptions-item>
      </el-descriptions>

      <el-table :data="locationList" v-loading="locationLoading" border size="small" empty-text="暂无库位数据">
        <el-table-column label="仓库 / 货架" min-width="180">
          <template #default="{ row }">
            <div class="loc-main">{{ row.warehouseName || '仓库已删除' }}</div>
            <div class="loc-sub">
              {{ row.shelfCode || '未指定货架' }}
              <span v-if="row.shelfLocation" class="loc-extra">（{{ row.shelfLocation }}）</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="数量" width="80" align="right">
          <template #default="{ row }">{{ row.quantity }} 件</template>
        </el-table-column>
        <el-table-column label="均价" width="100" align="right">
          <template #default="{ row }">{{ money(row.avgCost) }}</template>
        </el-table-column>
        <el-table-column label="金额" width="110" align="right">
          <template #default="{ row }">{{ money(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="最近入库" width="145">
          <template #default="{ row }">{{ formatTime(row.lastInboundTime) }}</template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Search, RefreshLeft } from '@element-plus/icons-vue'
import BaseTable from '@/components/common/BaseTable.vue'
import type { Column } from '@/types/common'
import {
  getStockLocationsApi,
  getStockSummaryApi,
  pageStocksApi,
  type StockItem,
  type StockLocation,
  type StockSummary
} from '@/api/stock'
import { pageWarehousesApi, type Warehouse } from '@/api/warehouse'

/* ---------------- 通用格式化 ---------------- */
const money = (value?: number | string | null) => {
  const num = Number(value ?? 0)
  return `¥${(Number.isFinite(num) ? num : 0).toFixed(2)}`
}

/** 后端返回 ISO-8601（2026-11-07T10:30:00），列表里换成 2026-11-07 10:30:00 */
const formatTime = (value?: string | null) => (value ? value.replace('T', ' ').slice(0, 19) : '-')

/* ---------------- 统计 ---------------- */
const summary = ref<StockSummary>({
  skuCount: 0,
  totalQuantity: 0,
  totalAmount: 0,
  warehouseCount: 0,
  locationCount: 0
})

async function fetchSummary() {
  try {
    summary.value = await getStockSummaryApi()
  } catch {
    // 统计失败不影响列表展示，保留上一次的值
  }
}

/* ---------------- 筛选 ---------------- */
const SORT_OPTIONS = [
  { value: 'QUANTITY', label: '库存件数↓' },
  { value: 'AMOUNT', label: '库存金额↓' },
  { value: 'RECENT', label: '最近入库↓' },
  { value: 'NAME', label: '名称 A→Z' }
]

const query = reactive({ page: 1, size: 10 })
const filterKeyword = ref('')
const filterWarehouseId = ref<number | undefined>(undefined)
const filterSortBy = ref('QUANTITY')

const warehouseOptions = ref<Warehouse[]>([])

async function fetchWarehouses() {
  try {
    // 主数据量不大，一次取回来做筛选下拉（与入库记录页一致）
    const data = await pageWarehousesApi({ page: 1, size: 200 })
    warehouseOptions.value = data.records
  } catch {
    warehouseOptions.value = []
  }
}

/* ---------------- 列表 ---------------- */
const loading = ref(false)
const list = ref<StockItem[]>([])
const total = ref(0)

const columns = ref<Column[]>([
  { prop: 'productName', label: '手办 / 货号', minWidth: 260 },
  { prop: 'totalQuantity', label: '在库数量', width: 110, align: 'right' },
  { prop: 'totalAmount', label: '库存金额', width: 140, align: 'right' },
  { prop: 'locationCount', label: '库位', width: 90, align: 'center' },
  { prop: 'lastInboundTime', label: '最近入库', width: 175 },
  { prop: 'action', label: '操作', width: 100, align: 'center', fixed: 'right' }
])

async function fetchList() {
  loading.value = true
  try {
    const data = await pageStocksApi({
      page: query.page,
      size: query.size,
      keyword: filterKeyword.value.trim() || undefined,
      warehouseId: filterWarehouseId.value || undefined,
      sortBy: filterSortBy.value || undefined
    })
    list.value = data.records
    total.value = data.total
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  query.page = 1
  fetchList()
}

const handleReset = () => {
  filterKeyword.value = ''
  filterWarehouseId.value = undefined
  filterSortBy.value = 'QUANTITY'
  query.page = 1
  fetchList()
}

const handlePaginationChange = ({ page, limit }: { page: number; limit: number }) => {
  query.page = page
  query.size = limit
  fetchList()
}

/* ---------------- 库位明细 ---------------- */
const drawerVisible = ref(false)
const locationLoading = ref(false)
const currentProduct = ref<StockItem | null>(null)
const locationList = ref<StockLocation[]>([])

async function handleLocations(row: StockItem) {
  currentProduct.value = row
  locationList.value = []
  drawerVisible.value = true
  locationLoading.value = true
  try {
    locationList.value = await getStockLocationsApi(row.productId)
  } catch {
    locationList.value = []
  } finally {
    locationLoading.value = false
  }
}

onMounted(() => {
  fetchSummary()
  fetchWarehouses()
  fetchList()
})
</script>

<style scoped>
.stock-container {
  padding: 16px;
  background-color: #f5f7fa;
  min-height: 100%;
}

.stat-row {
  margin-bottom: 16px;
}

.stat-card {
  border-radius: 6px;
}

.stat-label {
  color: #909399;
  font-size: 13px;
}

.stat-value {
  margin-top: 6px;
  font-size: 26px;
  font-weight: 600;
  color: #303133;
  line-height: 1.2;
}

.stat-value.qty {
  color: #409eff;
}

.stat-value.amount {
  font-size: 22px;
  color: #f56c6c;
}

.stat-tip {
  margin-top: 4px;
  color: #b1b3b8;
  font-size: 12px;
}

.filter-card {
  margin-bottom: 12px;
  border-radius: 6px;
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.flex-grow {
  flex: 1;
  min-width: 0;
}

.data-tip {
  color: #b1b3b8;
  font-size: 12px;
}

.product-cell {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.thumb {
  flex: none;
  background-color: #f0f2f5;
  color: #909399;
  font-size: 15px;
}

.product-main {
  min-width: 0;
}

.product-name {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.name-text {
  font-weight: 600;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-sub {
  margin-top: 2px;
  color: #909399;
  font-size: 12px;
  line-height: 1.4;
}

.dot {
  margin: 0 4px;
  color: #c0c4cc;
}

.qty {
  font-weight: 600;
  color: #303133;
}

.unit {
  color: #909399;
  font-size: 12px;
}

.amount-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.amount-text {
  font-weight: 600;
  color: #303133;
}

.amount-sub {
  color: #909399;
  font-size: 12px;
}

.time-text {
  color: #606266;
  white-space: nowrap;
}

.drawer-head {
  margin-bottom: 12px;
}

.drawer-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.drawer-sub {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
}

.drawer-desc {
  margin-bottom: 14px;
}

.loc-main {
  color: #303133;
}

.loc-sub {
  color: #909399;
  font-size: 12px;
  line-height: 1.6;
}

.loc-extra {
  color: #c0c4cc;
}
</style>
