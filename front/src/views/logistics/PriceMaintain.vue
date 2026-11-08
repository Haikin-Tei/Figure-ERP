<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  Delete,
  DocumentAdd,
  Edit,
  Plus,
  Refresh,
  Search,
  Van
} from '@element-plus/icons-vue'
import {
  batchSavePricesApi,
  createPriceApi,
  deletePriceApi,
  pagePricesApi,
  updatePriceApi,
  updatePriceStatusApi,
  type LogisticsPrice,
  type LogisticsPriceQueryParams,
  type LogisticsPriceSaveParams
} from '@/api/logistics'
import { useUserStore } from '@/stores/user'
import { canManageBusiness } from '@/utils/role'
import { citiesOf, formatTime, money, useLogisticsDict, zoneOf } from './useLogisticsDict'

const userStore = useUserStore()
const { suppliers, provinces, zones, loadDicts } = useLogisticsDict()

/** 管理员和主管可以维护价格，普通员工只读 */
const canManage = computed(() => canManageBusiness(userStore.userInfo))

/* ---------------- 列表 ---------------- */
const loading = ref(false)
const list = ref<LogisticsPrice[]>([])
const total = ref(0)

const query = reactive({
  page: 1,
  size: 10,
  supplierCode: '',
  destProvince: '',
  keyword: '',
  status: undefined as number | undefined
})

async function fetchList() {
  loading.value = true
  try {
    const params: LogisticsPriceQueryParams = {
      page: query.page,
      size: query.size,
      supplierCode: query.supplierCode || undefined,
      destProvince: query.destProvince || undefined,
      keyword: query.keyword || undefined,
      status: query.status
    }
    const data = await pagePricesApi(params)
    list.value = data.records
    total.value = data.total
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.page = 1
  fetchList()
}

function handleReset() {
  query.supplierCode = ''
  query.destProvince = ''
  query.keyword = ''
  query.status = undefined
  query.page = 1
  fetchList()
}

function handlePageChange(page: number) {
  query.page = page
  fetchList()
}

function handleSizeChange(size: number) {
  query.size = size
  query.page = 1
  fetchList()
}

/* ---------------- 新增 / 编辑 ---------------- */
const dialogVisible = ref(false)
const isEdit = ref(false)
const editingId = ref<number | null>(null)
const submitting = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  supplierCode: 'SF',
  originProvince: '上海市',
  originCity: '上海市',
  originDistrict: '浦东新区',
  destProvince: '',
  destCity: '',
  firstWeight: 1,
  firstPrice: 12,
  additionalWeight: 1,
  additionalPrice: 2,
  estimatedDays: 2,
  status: 1,
  remark: ''
})

/** 出发地城市候选，随省份变化 */
const originCities = computed(() => citiesOf(form.originProvince))
/** 目的地城市候选，可不选（表示全省统一价） */
const destCities = computed(() => citiesOf(form.destProvince))

const rules: FormRules = {
  supplierCode: [{ required: true, message: '请选择物流供应商', trigger: 'change' }],
  originProvince: [{ required: true, message: '请选择出发地省份', trigger: 'change' }],
  originCity: [{ required: true, message: '请选择出发地城市', trigger: 'change' }],
  destProvince: [{ required: true, message: '请选择目的地省份', trigger: 'change' }],
  firstPrice: [{ required: true, message: '请填写首重价格', trigger: 'blur' }],
  additionalPrice: [{ required: true, message: '请填写续重单价', trigger: 'blur' }]
}

function openCreate() {
  isEdit.value = false
  editingId.value = null
  Object.assign(form, {
    supplierCode: 'SF',
    originProvince: '上海市',
    originCity: '上海市',
    originDistrict: '浦东新区',
    destProvince: '',
    destCity: '',
    firstWeight: 1,
    firstPrice: 12,
    additionalWeight: 1,
    additionalPrice: 2,
    estimatedDays: 2,
    status: 1,
    remark: ''
  })
  dialogVisible.value = true
}

function openEdit(row: LogisticsPrice) {
  isEdit.value = true
  editingId.value = row.id
  Object.assign(form, {
    supplierCode: row.supplierCode,
    originProvince: row.originProvince,
    originCity: row.originCity,
    originDistrict: row.originDistrict ?? '',
    destProvince: row.destProvince,
    destCity: row.destCity ?? '',
    firstWeight: row.firstWeight,
    firstPrice: row.firstPrice,
    additionalWeight: row.additionalWeight,
    additionalPrice: row.additionalPrice,
    estimatedDays: row.estimatedDays ?? 2,
    status: row.status,
    remark: row.remark ?? ''
  })
  dialogVisible.value = true
}

/** 切换出发省时，城市自动跟着变，避免留下不属于该省的城市 */
function handleOriginProvinceChange(value: string) {
  const cities = citiesOf(value)
  form.originCity = cities[0] ?? value
  if (!form.originDistrict) {
    form.originDistrict = ''
  }
}

function handleDestProvinceChange() {
  form.destCity = ''
}

async function submitForm() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const payload: LogisticsPriceSaveParams = {
      supplierCode: form.supplierCode,
      originProvince: form.originProvince,
      originCity: form.originCity,
      originDistrict: form.originDistrict || undefined,
      destProvince: form.destProvince,
      destCity: form.destCity || undefined,
      firstWeight: form.firstWeight,
      firstPrice: form.firstPrice,
      additionalWeight: form.additionalWeight,
      additionalPrice: form.additionalPrice,
      estimatedDays: form.estimatedDays || undefined,
      status: form.status,
      remark: form.remark || undefined
    }
    if (isEdit.value && editingId.value) {
      await updatePriceApi(editingId.value, payload)
      ElMessage.success('保存成功')
    } else {
      await createPriceApi(payload)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchList()
  } finally {
    submitting.value = false
  }
}

async function toggleStatus(row: LogisticsPrice) {
  const next = row.status === 1 ? 0 : 1
  try {
    await updatePriceStatusApi(row.id, next)
    row.status = next
    ElMessage.success(next === 1 ? '已启用' : '已停用')
  } catch {
    // 拦截器已提示
  }
}

async function handleDelete(row: LogisticsPrice) {
  try {
    await ElMessageBox.confirm(
      `确定删除「${row.supplierName} ${row.originLabel} → ${row.destLabel}」这条价格吗？删除后报价将不再包含该供应商。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await deletePriceApi(row.id)
    ElMessage.success('已删除')
    // 删掉最后一页的最后一条时回退一页
    if (list.value.length === 1 && query.page > 1) {
      query.page -= 1
    }
    fetchList()
  } catch {
    // 拦截器已提示
  }
}

/* ---------------- 批量设置全国价 ---------------- */
interface BatchRow {
  destProvince: string
  zoneLabel: string
  zoneCode: string
  firstWeight: number
  firstPrice?: number
  additionalWeight: number
  additionalPrice?: number
  estimatedDays?: number
  status: number
  remark: string
  /** 库里已有的记录 id，仅用于提示「已存在会被覆盖」 */
  existingId?: number
}

const batchVisible = ref(false)
const batchLoading = ref(false)
const batchSaving = ref(false)
const batch = reactive({
  supplierCode: 'SF',
  originProvince: '上海市',
  originCity: '上海市',
  originDistrict: '浦东新区'
})
const batchRows = ref<BatchRow[]>([])
const fill = reactive({ firstPrice: 10, additionalPrice: 2 })
const batchOriginCities = computed(() => citiesOf(batch.originProvince))
const filledCount = computed(() => batchRows.value.filter((r) => r.firstPrice !== undefined).length)

function buildEmptyRows(): BatchRow[] {
  return provinces.value.map((p) => ({
    destProvince: p.name,
    zoneLabel: p.zoneLabel,
    zoneCode: p.zone,
    firstWeight: 1,
    firstPrice: undefined,
    additionalWeight: 1,
    additionalPrice: undefined,
    estimatedDays: undefined,
    status: 1,
    remark: ''
  }))
}

/** 读取该供应商 + 出发地已存在的省级价格，回填到表格 */
async function loadExistingBatch() {
  batchLoading.value = true
  const rows = buildEmptyRows()
  try {
    const data = await pagePricesApi({
      page: 1,
      size: 500,
      supplierCode: batch.supplierCode,
      originProvince: batch.originProvince,
      originCity: batch.originCity
    })
    for (const price of data.records) {
      // 只回填「全省统一价」，城市级价格在列表页单独维护
      if (price.destCity) continue
      const row = rows.find((r) => r.destProvince === price.destProvince)
      if (!row) continue
      row.firstWeight = price.firstWeight
      row.firstPrice = price.firstPrice
      row.additionalWeight = price.additionalWeight
      row.additionalPrice = price.additionalPrice
      row.estimatedDays = price.estimatedDays ?? undefined
      row.status = price.status
      row.remark = price.remark ?? ''
      row.existingId = price.id
    }
    batchRows.value = rows
  } catch {
    batchRows.value = rows
  } finally {
    batchLoading.value = false
  }
}

function openBatch() {
  Object.assign(batch, {
    supplierCode: 'SF',
    originProvince: '上海市',
    originCity: '上海市',
    originDistrict: '浦东新区'
  })
  loadExistingBatch()
  batchVisible.value = true
}

function handleBatchOriginProvinceChange(value: string) {
  const cities = citiesOf(value)
  batch.originCity = cities[0] ?? value
  loadExistingBatch()
}

/** 按「基准价 + 区域偏移」一键填充，之后还可以逐行微调 */
function applyZoneFill() {
  for (const row of batchRows.value) {
    const zone = zones.value.find((z) => z.code === row.zoneCode)
    if (!zone) continue
    row.firstPrice = Number((fill.firstPrice + zone.firstOffset).toFixed(2))
    row.additionalPrice = Number((fill.additionalPrice + zone.additionalOffset).toFixed(2))
    row.estimatedDays = Math.max(1, 2 + zone.daysOffset)
  }
  ElMessage.success('已按区域填充，可继续逐行调整')
}

async function submitBatch() {
  const items = batchRows.value
    .filter((r) => r.firstPrice !== undefined && r.additionalPrice !== undefined)
    .map((r) => ({
      destProvince: r.destProvince,
      firstWeight: r.firstWeight,
      firstPrice: r.firstPrice as number,
      additionalWeight: r.additionalWeight,
      additionalPrice: r.additionalPrice as number,
      estimatedDays: r.estimatedDays,
      status: r.status,
      remark: r.remark || undefined
    }))

  if (!items.length) {
    ElMessage.warning('请至少填写一个省份的价格')
    return
  }

  batchSaving.value = true
  try {
    const affected = await batchSavePricesApi({
      supplierCode: batch.supplierCode,
      originProvince: batch.originProvince,
      originCity: batch.originCity,
      originDistrict: batch.originDistrict || undefined,
      items
    })
    ElMessage.success(`已保存 ${affected} 条价格`)
    batchVisible.value = false
    handleSearch()
  } finally {
    batchSaving.value = false
  }
}

onMounted(async () => {
  await loadDicts()
  fetchList()
})
</script>

<template>
  <div class="page-container">
    <div class="card">
      <div class="card-head">
        <h2 class="page-title">
          <el-icon><Van /></el-icon>
          物流价格维护
        </h2>
        <div>
          <el-button type="primary" :disabled="!canManage" @click="openCreate">
            <el-icon><Plus /></el-icon>
            新增价格
          </el-button>
          <el-button type="success" :disabled="!canManage" @click="openBatch">
            <el-icon><DocumentAdd /></el-icon>
            批量设置全国价
          </el-button>
        </div>
      </div>

      <el-alert
        v-if="!canManage"
        type="info"
        show-icon
        :closable="false"
        style="margin-bottom: 16px"
        title="当前账号只能查看价格，修改请联系管理员或主管"
      />

      <div class="filter-bar">
        <el-select
          v-model="query.supplierCode"
          placeholder="全部供应商"
          clearable
          style="width: 150px"
          @change="handleSearch"
        >
          <el-option v-for="s in suppliers" :key="s.code" :label="s.label" :value="s.code" />
        </el-select>

        <el-select
          v-model="query.destProvince"
          placeholder="全部目的地"
          clearable
          filterable
          style="width: 180px"
          @change="handleSearch"
        >
          <el-option v-for="p in provinces" :key="p.name" :label="p.name" :value="p.name" />
        </el-select>

        <el-select
          v-model="query.status"
          placeholder="全部状态"
          clearable
          style="width: 120px"
          @change="handleSearch"
        >
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>

        <el-input
          v-model="query.keyword"
          placeholder="搜索目的地省份 / 城市"
          clearable
          style="width: 200px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />

        <el-button type="primary" @click="handleSearch">
          <el-icon><Search /></el-icon>
          查询
        </el-button>
        <el-button @click="handleReset">
          <el-icon><Refresh /></el-icon>
          重置
        </el-button>
      </div>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="supplierName" label="供应商" width="120" />
        <el-table-column prop="originLabel" label="出发地" width="160" />
        <el-table-column label="目的地" min-width="220">
          <template #default="{ row }">
            <span>{{ row.destLabel }}</span>
            <el-tag
              v-if="row.destCity"
              size="small"
              type="success"
              effect="plain"
              style="margin-left: 6px"
            >
              城市级
            </el-tag>
            <el-tag v-else size="small" type="info" effect="plain" style="margin-left: 6px">
              {{ row.zoneLabel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="首重" width="130" align="right">
          <template #default="{ row }">
            {{ row.firstWeight }}kg / {{ money(row.firstPrice) }} 元
          </template>
        </el-table-column>
        <el-table-column label="续重" width="130" align="right">
          <template #default="{ row }">
            {{ row.additionalWeight }}kg / {{ money(row.additionalPrice) }} 元
          </template>
        </el-table-column>
        <el-table-column label="时效" width="90" align="center">
          <template #default="{ row }">
            {{ row.estimatedDays ? row.estimatedDays + ' 天' : '—' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="170">
          <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="190" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="!canManage" @click="openEdit(row)">
              <el-icon><Edit /></el-icon>编辑
            </el-button>
            <el-button link type="primary" :disabled="!canManage" @click="toggleStatus(row)">
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button link type="danger" :disabled="!canManage" @click="handleDelete(row)">
              <el-icon><Delete /></el-icon>删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          :current-page="query.page"
          :page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </div>

    <!-- 新增 / 编辑 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑物流价格' : '新增物流价格'"
      width="660px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="供应商" prop="supplierCode">
          <el-select v-model="form.supplierCode" placeholder="请选择供应商" style="width: 100%">
            <el-option v-for="s in suppliers" :key="s.code" :label="s.label" :value="s.code" />
          </el-select>
        </el-form-item>

        <el-form-item label="出发地" required>
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
        </el-form-item>

        <el-form-item label="目的地" prop="destProvince">
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
          <div class="form-tip">城市留空 = 该省所有城市统一价；填了城市则只对这个城市生效，查询时优先于省级价</div>
        </el-form-item>

        <el-form-item label="首重" required>
          <div class="inline-group">
            <el-input-number v-model="form.firstWeight" :min="0.1" :precision="2" :step="0.5" style="width: 130px" />
            <span class="unit">kg 内</span>
            <el-input-number v-model="form.firstPrice" :min="0" :precision="2" :step="1" style="width: 130px" />
            <span class="unit">元</span>
          </div>
        </el-form-item>

        <el-form-item label="续重" required>
          <div class="inline-group">
            <el-input-number v-model="form.additionalWeight" :min="0.1" :precision="2" :step="0.5" style="width: 130px" />
            <span class="unit">kg 加收</span>
            <el-input-number v-model="form.additionalPrice" :min="0" :precision="2" :step="0.5" style="width: 130px" />
            <span class="unit">元</span>
          </div>
        </el-form-item>

        <el-form-item label="预计时效">
          <el-input-number v-model="form.estimatedDays" :min="1" :max="60" style="width: 130px" />
          <span class="unit">天</span>
        </el-form-item>

        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="200" show-word-limit placeholder="如：协议价、大客户折扣" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- 批量设置全国价 -->
    <el-drawer v-model="batchVisible" size="68%" destroy-on-close>
      <template #header>
        <div>
          <h3 class="drawer-title">批量设置全国价</h3>
          <p class="drawer-sub">一次填完 34 个省级行政区，已存在的省级价格会被覆盖；城市级价格不受影响</p>
        </div>
      </template>

      <div class="batch-toolbar">
        <el-select v-model="batch.supplierCode" style="width: 140px" @change="loadExistingBatch">
          <el-option v-for="s in suppliers" :key="s.code" :label="s.label" :value="s.code" />
        </el-select>
        <el-select
          v-model="batch.originProvince"
          filterable
          style="width: 150px"
          @change="handleBatchOriginProvinceChange"
        >
          <el-option v-for="p in provinces" :key="p.name" :label="p.name" :value="p.name" />
        </el-select>
        <el-select v-model="batch.originCity" filterable style="width: 130px" @change="loadExistingBatch">
          <el-option v-for="c in batchOriginCities" :key="c" :label="c" :value="c" />
        </el-select>
        <el-input v-model="batch.originDistrict" placeholder="区/县" style="width: 110px" />
        <el-button :loading="batchLoading" @click="loadExistingBatch">
          <el-icon><Refresh /></el-icon>
          读取现有价格
        </el-button>
      </div>

      <div class="batch-fill">
        <span class="fill-label">按区域一键填充：</span>
        <span class="unit">华东首重</span>
        <el-input-number v-model="fill.firstPrice" :min="0" :precision="2" size="small" style="width: 110px" />
        <span class="unit">元，续重</span>
        <el-input-number v-model="fill.additionalPrice" :min="0" :precision="2" size="small" style="width: 110px" />
        <span class="unit">元/kg</span>
        <el-button size="small" type="primary" plain @click="applyZoneFill">填充全部省份</el-button>
        <span class="fill-tip">其余区域按「华东 + 区域差价」自动算出，填完可逐行微调</span>
      </div>

      <el-table
        v-loading="batchLoading"
        :data="batchRows"
        border
        size="small"
        height="calc(100vh - 400px)"
      >
        <el-table-column label="省份" prop="destProvince" width="150" fixed />
        <el-table-column label="区域" prop="zoneLabel" width="80" align="center" />
        <el-table-column label="首重价（元）" width="140">
          <template #default="{ row }">
            <el-input-number
              v-model="row.firstPrice"
              :min="0"
              :precision="2"
              :step="1"
              size="small"
              controls-position="right"
              style="width: 100%"
            />
          </template>
        </el-table-column>
        <el-table-column label="续重价（元/kg）" width="150">
          <template #default="{ row }">
            <el-input-number
              v-model="row.additionalPrice"
              :min="0"
              :precision="2"
              :step="0.5"
              size="small"
              controls-position="right"
              style="width: 100%"
            />
          </template>
        </el-table-column>
        <el-table-column label="时效（天）" width="120">
          <template #default="{ row }">
            <el-input-number
              v-model="row.estimatedDays"
              :min="1"
              :max="60"
              size="small"
              controls-position="right"
              style="width: 100%"
            />
          </template>
        </el-table-column>
        <el-table-column label="启用" width="80" align="center">
          <template #default="{ row }">
            <el-switch v-model="row.status" :active-value="1" :inactive-value="0" />
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="150">
          <template #default="{ row }">
            <el-input v-model="row.remark" size="small" maxlength="50" placeholder="选填" />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.existingId" size="small" type="warning" effect="plain">已存在</el-tag>
            <el-tag v-else size="small" type="info" effect="plain">新增</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <template #footer>
        <div class="drawer-footer">
          <span class="fill-tip">已填写 {{ filledCount }} / {{ batchRows.length }} 个省份</span>
          <div>
            <el-button @click="batchVisible = false">取消</el-button>
            <el-button type="primary" :loading="batchSaving" @click="submitBatch">保存</el-button>
          </div>
        </div>
      </template>
    </el-drawer>
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

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 16px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}

.inline-group {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.unit {
  font-size: 13px;
  color: #606266;
}

.form-tip {
  font-size: 12px;
  color: #a8abb2;
  line-height: 1.6;
  margin-top: 2px;
}

.drawer-title {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
  color: #303133;
}

.drawer-sub {
  margin: 4px 0 0;
  font-size: 12px;
  color: #909399;
}

.batch-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.batch-fill {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  margin-bottom: 14px;
  background: #f5f7fa;
  border-radius: 6px;
}

.fill-label {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}

.fill-tip {
  font-size: 12px;
  color: #909399;
}

.drawer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}
</style>
