<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Location, Refresh, Search, Van } from '@element-plus/icons-vue'
import { quoteApi, type QuoteItem, type QuoteResult } from '@/api/logistics'
import { citiesOf, money, useLogisticsDict } from './useLogisticsDict'

const { provinces, origins, loadDicts } = useLogisticsDict()

const form = reactive({
  originProvince: '上海市',
  originCity: '上海市',
  originDistrict: '浦东新区',
  destProvince: '广东省',
  destCity: '',
  weight: 1
})

const originCities = computed(() => citiesOf(form.originProvince))
const destCities = computed(() => citiesOf(form.destProvince))

const loading = ref(false)
const result = ref<QuoteResult | null>(null)

/** 快捷重量，手办包裹常见 1~20kg */
const quickWeights = [1, 2, 3, 5, 10, 20]

function handleOriginProvinceChange(value: string) {
  const cities = citiesOf(value)
  form.originCity = cities[0] ?? value
  form.originDistrict = ''
}

function handleDestProvinceChange() {
  form.destCity = ''
}

function useOrigin(province: string, city: string, district?: string) {
  form.originProvince = province
  form.originCity = city
  form.originDistrict = district ?? ''
}

function setWeight(value: number) {
  form.weight = value
  doQuery()
}

async function doQuery() {
  if (!form.destProvince) {
    ElMessage.warning('请先选择目的地省份')
    return
  }
  if (!form.weight || form.weight <= 0) {
    ElMessage.warning('请填写大于 0 的重量')
    return
  }

  loading.value = true
  try {
    result.value = await quoteApi({
      originProvince: form.originProvince,
      originCity: form.originCity,
      originDistrict: form.originDistrict || undefined,
      destProvince: form.destProvince,
      destCity: form.destCity || undefined,
      weight: form.weight
    })
  } catch {
    result.value = null
  } finally {
    loading.value = false
  }
}

function handleReset() {
  form.originProvince = '上海市'
  form.originCity = '上海市'
  form.originDistrict = '浦东新区'
  form.destProvince = '广东省'
  form.destCity = ''
  form.weight = 1
  doQuery()
}

/** 时效最快的供应商（可能不止一家，取报价列表里第一个） */
const fastestItem = computed<QuoteItem | undefined>(() =>
  result.value?.items.find((item) => item.fastest)
)

/** 以最低价为基准，算每家贵多少，用于卡片上的差价提示 */
function diffLabel(item: QuoteItem): string {
  const lowest = result.value?.lowestPrice
  if (lowest === undefined || item.cheapest) return ''
  const diff = item.totalPrice - lowest
  if (diff <= 0) return ''
  return `+${diff.toFixed(2)} 元`
}

onMounted(async () => {
  await loadDicts()
  doQuery()
})
</script>

<template>
  <div class="page-container">
    <!-- 查询条件 -->
    <div class="card">
      <div class="card-head">
        <h2 class="page-title">
          <el-icon><Van /></el-icon>
          物流价格查询
        </h2>
        <span class="head-tip">同一条路线自动对比各家快递，按总价从低到高排序</span>
      </div>

      <div class="query-form">
        <div class="field">
          <label>出发地</label>
          <div class="inline-group">
            <el-select
              v-model="form.originProvince"
              filterable
              style="width: 150px"
              @change="handleOriginProvinceChange"
            >
              <el-option v-for="p in provinces" :key="p.name" :label="p.name" :value="p.name" />
            </el-select>
            <el-select v-model="form.originCity" filterable style="width: 130px">
              <el-option v-for="c in originCities" :key="c" :label="c" :value="c" />
            </el-select>
            <el-input v-model="form.originDistrict" placeholder="区/县" style="width: 120px" />
          </div>
        </div>

        <div class="field">
          <label>目的地</label>
          <div class="inline-group">
            <el-select
              v-model="form.destProvince"
              placeholder="选择省份"
              filterable
              style="width: 170px"
              @change="handleDestProvinceChange"
            >
              <el-option v-for="p in provinces" :key="p.name" :label="p.name" :value="p.name" />
            </el-select>
            <el-select
              v-model="form.destCity"
              placeholder="全省统一价"
              filterable
              clearable
              style="width: 150px"
            >
              <el-option v-for="c in destCities" :key="c" :label="c" :value="c" />
            </el-select>
          </div>
        </div>

        <div class="field">
          <label>重量</label>
          <div class="inline-group">
            <el-input-number
              v-model="form.weight"
              :min="0.1"
              :max="500"
              :precision="1"
              :step="0.5"
              style="width: 130px"
            />
            <span class="unit">kg</span>
          </div>
        </div>

        <div class="field actions">
          <el-button type="primary" :loading="loading" @click="doQuery">
            <el-icon><Search /></el-icon>
            查询运费
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>
            重置
          </el-button>
        </div>
      </div>

      <div class="quick-row">
        <span class="quick-label">常用重量：</span>
        <el-tag
          v-for="w in quickWeights"
          :key="w"
          class="quick-tag"
          :type="form.weight === w ? 'primary' : 'info'"
          :effect="form.weight === w ? 'dark' : 'plain'"
          @click="setWeight(w)"
        >
          {{ w }} kg
        </el-tag>

        <span v-if="origins.length" class="quick-label" style="margin-left: 16px">已配置出发地：</span>
        <el-tag
          v-for="(o, idx) in origins"
          :key="idx"
          class="quick-tag"
          type="success"
          effect="plain"
          @click="useOrigin(o.province, o.city, o.district)"
        >
          {{ o.label }}
        </el-tag>
      </div>
    </div>

    <!-- 查询结果 -->
    <div v-loading="loading" class="result-area">
      <template v-if="result">
        <div class="summary">
          <div class="summary-route">
            <el-icon><Location /></el-icon>
            <span class="route-text">{{ result.originLabel }} → {{ result.destLabel }}</span>
            <el-tag size="small" effect="plain">{{ result.zoneLabel }}</el-tag>
            <el-tag size="small" type="warning" effect="plain">{{ result.weight }} kg</el-tag>
          </div>

          <div v-if="result.count" class="summary-stats">
            <div class="stat">
              <span class="stat-label">最低价</span>
              <span class="stat-value green">¥{{ money(result.lowestPrice) }}</span>
              <span class="stat-sub">{{ result.lowestSupplier }}</span>
            </div>
            <div class="stat">
              <span class="stat-label">最快</span>
              <span class="stat-value blue">{{ result.fastestDays ?? '—' }} 天</span>
              <span class="stat-sub">{{ fastestItem?.supplierName ?? '—' }}</span>
            </div>
            <div class="stat">
              <span class="stat-label">可选供应商</span>
              <span class="stat-value">{{ result.count }} 家</span>
              <span class="stat-sub">按总价排序</span>
            </div>
          </div>
        </div>

        <el-alert
          v-if="result.advice"
          class="advice"
          type="success"
          :closable="false"
          show-icon
          :title="result.advice"
        />

        <div v-if="result.count" class="quote-grid">
          <div
            v-for="item in result.items"
            :key="item.supplierCode"
            class="quote-card"
            :class="{ cheapest: item.cheapest, fastest: item.fastest && !item.cheapest }"
          >
            <div class="qc-head">
              <span class="qc-name">{{ item.supplierName }}</span>
              <div class="qc-badges">
                <el-tag v-if="item.cheapest" type="success" size="small" effect="dark">最低价</el-tag>
                <el-tag v-if="item.fastest" type="primary" size="small" effect="dark">最快</el-tag>
              </div>
            </div>

            <div class="qc-price">
              <span class="currency">¥</span>{{ money(item.totalPrice) }}
              <span v-if="diffLabel(item)" class="qc-diff">{{ diffLabel(item) }}</span>
            </div>

            <div class="qc-meta">
              <span>预计 {{ item.estimatedDays ?? '—' }} 天送达</span>
            </div>

            <div class="qc-formula">{{ item.formula }}</div>

            <div v-if="item.remark" class="qc-remark">{{ item.remark }}</div>
          </div>
        </div>

        <el-empty v-else description="该路线暂未配置价格">
          <p class="empty-tip">
            可以到「物流价格维护」里补一条，或用「批量设置全国价」一键生成这个供应商的全国价格
          </p>
        </el-empty>
      </template>

      <el-empty v-else-if="!loading" description="选择出发地与目的地后点击查询" />
    </div>
  </div>
</template>

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
  margin-bottom: 20px;
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

.head-tip {
  font-size: 12px;
  color: #909399;
}

.query-form {
  display: flex;
  flex-wrap: wrap;
  gap: 20px 28px;
  align-items: flex-end;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.field label {
  font-size: 13px;
  color: #606266;
  font-weight: 500;
}

.inline-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.unit {
  font-size: 13px;
  color: #606266;
}

.quick-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px dashed #ebeef5;
}

.quick-label {
  font-size: 13px;
  color: #909399;
}

.quick-tag {
  cursor: pointer;
  user-select: none;
}

.result-area {
  margin-top: 18px;
  min-height: 120px;
}

.summary {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 22px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
}

.summary-route {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.summary-stats {
  display: flex;
  gap: 36px;
}

.stat {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  line-height: 1.4;
}

.stat-label {
  font-size: 12px;
  color: #909399;
}

.stat-value {
  font-size: 20px;
  font-weight: 700;
  color: #303133;
}

.stat-value.green {
  color: #67c23a;
}

.stat-value.blue {
  color: #409eff;
}

.stat-sub {
  font-size: 12px;
  color: #909399;
}

.advice {
  margin: 14px 0;
}

.quote-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.quote-card {
  position: relative;
  padding: 18px 20px;
  background: #fff;
  border-radius: 10px;
  border: 1px solid #ebeef5;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.04);
  transition: transform 0.15s, box-shadow 0.15s;
}

.quote-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 18px 0 rgba(0, 0, 0, 0.09);
}

.quote-card.cheapest {
  border-color: #67c23a;
  background: linear-gradient(180deg, #f0f9eb 0%, #ffffff 55%);
}

.quote-card.fastest {
  border-color: #409eff;
  background: linear-gradient(180deg, #ecf5ff 0%, #ffffff 55%);
}

.qc-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.qc-name {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.qc-badges {
  display: flex;
  gap: 6px;
}

.qc-price {
  margin: 12px 0 6px;
  font-size: 30px;
  font-weight: 700;
  color: #303133;
  line-height: 1.1;
}

.qc-price .currency {
  font-size: 16px;
  margin-right: 2px;
}

.qc-diff {
  margin-left: 8px;
  font-size: 12px;
  font-weight: 500;
  color: #f56c6c;
  vertical-align: middle;
}

.qc-meta {
  font-size: 13px;
  color: #606266;
}

.qc-formula {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed #ebeef5;
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
}

.qc-remark {
  margin-top: 6px;
  font-size: 12px;
  color: #e6a23c;
}

.empty-tip {
  font-size: 12px;
  color: #909399;
}
</style>
