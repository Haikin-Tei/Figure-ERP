<template>
  <div class="inbound-container">
    <!-- 1. 顶部统计 -->
    <el-row :gutter="16" class="stat-row">
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">待入库单</div>
          <div class="stat-value pending">{{ summary.pendingCount ?? 0 }}</div>
          <div class="stat-tip">还没确认入库的单据</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">本月入库单</div>
          <div class="stat-value">{{ summary.monthOrderCount ?? 0 }}</div>
          <div class="stat-tip">本月已确认的单据数</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">本月入库件数</div>
          <div class="stat-value">{{ summary.monthQuantity ?? 0 }}</div>
          <div class="stat-tip">明细数量合计</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">本月入库金额</div>
          <div class="stat-value amount">{{ money(summary.monthAmount) }}</div>
          <div class="stat-tip">按单价 × 数量累计</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 2. 筛选栏 -->
    <el-card shadow="never" class="filter-card">
      <div class="filter-bar">
        <el-input
          v-model="filterKeyword"
          placeholder="单号 / 供应商 / 手办名称"
          clearable
          style="width: 205px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />

        <el-select
          v-model="filterWarehouseId"
          placeholder="入库仓库"
          clearable
          filterable
          style="width: 155px"
          @change="handleSearch"
        >
          <el-option
            v-for="w in warehouseOptions"
            :key="w.id"
            :label="`${w.warehouseName} (${w.warehouseCode})`"
            :value="w.id"
          />
        </el-select>

        <el-select v-model="filterType" placeholder="入库类型" clearable style="width: 120px" @change="handleSearch">
          <el-option v-for="t in TYPE_OPTIONS" :key="t.code" :label="t.label" :value="t.code" />
        </el-select>

        <el-select v-model="filterStatus" placeholder="单据状态" clearable style="width: 112px" @change="handleSearch">
          <el-option v-for="s in STATUS_OPTIONS" :key="s.code" :label="s.label" :value="s.code" />
        </el-select>

        <el-date-picker
          v-model="filterDateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="入库开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          style="width: 225px"
          @change="handleSearch"
        />

        <el-button type="primary" @click="handleSearch">
          <el-icon><Search /></el-icon>
          查询
        </el-button>
        <el-button @click="handleReset">
          <el-icon><RefreshLeft /></el-icon>
          重置
        </el-button>

        <div class="flex-grow"></div>

        <el-button type="primary" :disabled="!canManage" @click="handleCreate">
          <el-icon><Plus /></el-icon>
          新增入库单
        </el-button>
      </div>
    </el-card>

    <el-alert
      v-if="!canManage"
      type="info"
      show-icon
      :closable="false"
      style="margin-bottom: 12px"
      title="当前账号只能查看入库记录，新增 / 修改 / 确认请联系管理员或主管"
    />

    <!-- 3. 列表 -->
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
      <template #inboundNo="scope">
        <div class="no-cell">
          <span class="order-no">{{ scope.row.inboundNo }}</span>
          <el-tag v-if="scope.row.status === 'D'" size="small" type="warning" effect="plain">草稿</el-tag>
        </div>
        <div class="time-sub">{{ formatTime(scope.row.inboundTime) }}</div>
      </template>

      <template #inboundType="scope">
        <el-tag size="small" :type="typeTagType(scope.row.inboundType)" effect="plain">
          {{ scope.row.inboundTypeText || typeLabel(scope.row.inboundType) }}
        </el-tag>
      </template>

      <template #productSummary="scope">
        <span class="product-summary">{{ scope.row.productSummary || '-' }}</span>
      </template>

      <template #totalQuantity="scope">
        <div class="amount-cell">
          <span class="qty">{{ scope.row.totalQuantity ?? 0 }}<span class="unit"> 件</span></span>
          <span class="amount-text">{{ money(scope.row.totalAmount) }}</span>
        </div>
      </template>

      <template #warehouse="scope">
        <div class="warehouse-cell">
          <span class="warehouse-name">{{ scope.row.warehouseName || '仓库已删除' }}</span>
          <span class="warehouse-sub">
            {{ scope.row.shelfCode || '未指定货架' }}
          </span>
        </div>
      </template>

      <template #supplier="scope">
        <el-tooltip :content="scope.row.supplier || '—'" placement="top" :show-after="400">
          <span class="supplier-text">{{ scope.row.supplier || '-' }}</span>
        </el-tooltip>
      </template>

      <template #status="scope">
        <el-tag size="small" :type="statusTagType(scope.row.status)">
          {{ scope.row.statusText || statusLabel(scope.row.status) }}
        </el-tag>
      </template>

      <template #action="scope">
        <el-button link type="primary" size="small" @click="handleDetail(scope.row)">详情</el-button>
        <el-button
          v-if="scope.row.status === 'D'"
          link
          type="primary"
          size="small"
          :disabled="!canManage"
          @click="handleEdit(scope.row)"
        >
          编辑
        </el-button>
        <!-- 状态类操作收进「更多」，一是避免误点，二是别把表格挤到横向滚动 -->
        <el-dropdown
          v-if="canManage && scope.row.status !== 'X'"
          trigger="click"
          @command="(cmd: string) => handleCommand(cmd, scope.row)"
        >
          <el-button link type="primary" size="small">
            更多
            <el-icon class="el-icon--right"><ArrowDown /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-if="scope.row.status === 'D'" command="confirm">确认入库</el-dropdown-item>
              <el-dropdown-item command="cancel" :divided="scope.row.status === 'D'">作废</el-dropdown-item>
              <el-dropdown-item v-if="scope.row.status === 'D'" command="delete" divided>删除</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </template>
    </BaseTable>

    <!-- 4. 新增 / 编辑入库单 -->
    <el-dialog
      v-model="formVisible"
      :title="isEdit ? '编辑入库单' : '新增入库单'"
      width="1000px"
      top="6vh"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="入库单号" prop="inboundNo">
              <el-input v-model="form.inboundNo" placeholder="留空自动生成，如 RK202611070001" maxlength="50" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="入库类型" prop="inboundType">
              <el-select v-model="form.inboundType" placeholder="请选择类型" style="width: 100%">
                <el-option v-for="t in TYPE_OPTIONS" :key="t.code" :label="t.label" :value="t.code" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="入库时间" prop="inboundTime">
              <el-date-picker
                v-model="form.inboundTime"
                type="datetime"
                placeholder="默认当前时间"
                value-format="YYYY-MM-DD HH:mm:ss"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="入库仓库" prop="warehouseId">
              <el-select
                v-model="form.warehouseId"
                placeholder="请选择仓库"
                filterable
                style="width: 100%"
                @change="handleWarehouseChange"
              >
                <el-option
                  v-for="w in warehouseOptions"
                  :key="w.id"
                  :label="`${w.warehouseName} (${w.warehouseCode})`"
                  :value="w.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="入库货架">
              <el-select
                v-model="form.shelfId"
                placeholder="可不选"
                clearable
                filterable
                :disabled="!form.warehouseId"
                style="width: 100%"
              >
                <el-option v-for="s in shelfOptions" :key="s.id" :label="s.shelfCode" :value="s.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="供应商">
              <el-input v-model="form.supplier" placeholder="如 Good Smile Company 中国代理" maxlength="100" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="经办人">
              <el-input v-model="form.operator" placeholder="默认当前登录用户" maxlength="50" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="备注">
              <el-input v-model="form.remark" placeholder="选填" maxlength="500" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <!-- 手办明细 -->
      <div class="items-head">
        <span class="items-title">手办明细</span>
        <span class="items-tip">同一款手办只能添加一行，金额由数量 × 单价自动计算</span>
        <div class="flex-grow"></div>
        <el-button type="primary" plain size="small" style="min-width: 96px" @click="addItem">
          添加手办
        </el-button>
      </div>

      <el-table :data="form.items" border size="small" class="items-table">
        <el-table-column label="手办" min-width="260">
          <template #default="{ row }">
            <el-select
              v-model="row.productId"
              placeholder="搜索手办名称或货号"
              filterable
              clearable
              style="width: 100%"
              @change="(v: number) => handleProductChange(row, v)"
            >
              <el-option
                v-for="p in productOptions"
                :key="p.id"
                :label="p.code ? `${p.name}（${p.code}）` : p.name"
                :value="p.id"
              >
                <span class="option-name">{{ p.name }}</span>
                <span v-if="p.code" class="option-code">{{ p.code }}</span>
              </el-option>
            </el-select>
          </template>
        </el-table-column>

        <el-table-column label="数量" width="120" align="center">
          <template #default="{ row }">
            <el-input-number v-model="row.quantity" :min="1" :max="99999" size="small" controls-position="right" />
          </template>
        </el-table-column>

        <el-table-column label="单价（元）" width="140" align="center">
          <template #default="{ row }">
            <el-input-number
              v-model="row.unitPrice"
              :min="0"
              :max="9999999"
              :precision="2"
              :step="10"
              size="small"
              controls-position="right"
            />
          </template>
        </el-table-column>

        <el-table-column label="小计" width="110" align="right">
          <template #default="{ row }">
            <span class="amount-text">{{ money(rowAmount(row)) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="备注" min-width="150">
          <template #default="{ row }">
            <el-input v-model="row.remark" placeholder="选填" maxlength="255" size="small" />
          </template>
        </el-table-column>

        <el-table-column label="操作" width="70" align="center">
          <template #default="{ $index }">
            <el-button link type="danger" size="small" @click="removeItem($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="items-total">
        共 <b>{{ form.items.length }}</b> 款、<b>{{ formTotalQuantity }}</b> 件，合计
        <b class="amount-text">{{ money(formTotalAmount) }}</b>
      </div>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="formVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 5. 详情抽屉 -->
    <el-drawer v-model="detailVisible" title="入库单详情" size="760px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="入库单号">{{ detail.inboundNo }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag size="small" :type="statusTagType(detail.status)">
                {{ detail.statusText || statusLabel(detail.status) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="入库类型">
              {{ detail.inboundTypeText || typeLabel(detail.inboundType) }}
            </el-descriptions-item>
            <el-descriptions-item label="入库时间">{{ formatTime(detail.inboundTime) }}</el-descriptions-item>
            <el-descriptions-item label="入库仓库">
              {{ detail.warehouseName || '仓库已删除' }}
              <span v-if="detail.warehouseCode" class="muted">({{ detail.warehouseCode }})</span>
            </el-descriptions-item>
            <el-descriptions-item label="入库货架">
              {{ detail.shelfCode || '未指定货架' }}
            </el-descriptions-item>
            <el-descriptions-item label="供应商">{{ detail.supplier || '-' }}</el-descriptions-item>
            <el-descriptions-item label="经办人">{{ detail.operator || '-' }}</el-descriptions-item>
            <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatTime(detail.createTime) }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ formatTime(detail.updateTime) }}</el-descriptions-item>
          </el-descriptions>

          <div class="detail-title">手办明细</div>
          <el-table :data="detail.items || []" border size="small">
            <el-table-column label="手办" min-width="220">
              <template #default="{ row }">
                <div class="detail-product">{{ row.productName }}</div>
                <div v-if="row.productCode" class="muted">{{ row.productCode }}</div>
              </template>
            </el-table-column>
            <el-table-column label="数量" width="90" align="center" prop="quantity" />
            <el-table-column label="单价" width="120" align="right">
              <template #default="{ row }">{{ money(row.unitPrice) }}</template>
            </el-table-column>
            <el-table-column label="小计" width="120" align="right">
              <template #default="{ row }">
                <span class="amount-text">{{ money(row.amount) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="备注" min-width="130">
              <template #default="{ row }">{{ row.remark || '-' }}</template>
            </el-table-column>
          </el-table>

          <div class="items-total">
            共 <b>{{ detail.itemCount ?? 0 }}</b> 款、<b>{{ detail.totalQuantity ?? 0 }}</b> 件，合计
            <b class="amount-text">{{ money(detail.totalAmount) }}</b>
          </div>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { Search, RefreshLeft, Plus, ArrowDown } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import BaseTable from '@/components/common/BaseTable.vue'
import type { Column } from '@/types/common'
import {
  cancelInboundApi,
  confirmInboundApi,
  createInboundApi,
  deleteInboundApi,
  getInboundApi,
  getInboundSummaryApi,
  pageInboundsApi,
  updateInboundApi,
  type InboundOrder,
  type InboundSaveParams,
  type InboundSummary
} from '@/api/inbound'
import { pageWarehousesApi, type Warehouse } from '@/api/warehouse'
import { pageShelvesApi, type Shelf } from '@/api/shelf'
import { pageProductsApi, type FigureProduct } from '@/api/product'
import { useUserStore } from '@/stores/user'
import { canManageBusiness } from '@/utils/role'

const userStore = useUserStore()

/** 管理员和主管可以维护入库记录，普通员工只读 */
const canManage = computed(() => canManageBusiness(userStore.userInfo))

/* ---------------- 字典 ---------------- */
const TYPE_OPTIONS = [
  { code: 'P', label: '采购入库' },
  { code: 'R', label: '退货入库' },
  { code: 'T', label: '调拨入库' },
  { code: 'O', label: '其他入库' }
]

const STATUS_OPTIONS = [
  { code: 'D', label: '待入库' },
  { code: 'C', label: '已入库' },
  { code: 'X', label: '已作废' }
]

const typeLabel = (code?: string) => TYPE_OPTIONS.find((t) => t.code === code)?.label || code || '-'
const statusLabel = (code?: string) => STATUS_OPTIONS.find((s) => s.code === code)?.label || code || '-'

const typeTagType = (code?: string): 'primary' | 'success' | 'warning' | 'info' => {
  if (code === 'P') return 'primary'
  if (code === 'R') return 'warning'
  if (code === 'T') return 'success'
  return 'info'
}

const statusTagType = (code?: string): 'success' | 'warning' | 'info' | 'danger' => {
  if (code === 'C') return 'success'
  if (code === 'D') return 'warning'
  return 'info'
}

/* ---------------- 展示格式化 ---------------- */
const money = (value?: number | string | null) => {
  const num = Number(value ?? 0)
  return `¥${(Number.isFinite(num) ? num : 0).toFixed(2)}`
}

/** 后端返回 ISO-8601（2026-11-07T10:30:00），列表里换成 2026-11-07 10:30:00 */
const formatTime = (value?: string | null) => (value ? value.replace('T', ' ').slice(0, 19) : '-')

/** 表单默认入库时间：当前时间，格式与后端保持一致 */
function nowText() {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(
    d.getMinutes()
  )}:${pad(d.getSeconds())}`
}

/* ---------------- 下拉选项 ---------------- */
const warehouseOptions = ref<Warehouse[]>([])
const shelfOptionsAll = ref<Shelf[]>([])
const productOptions = ref<FigureProduct[]>([])

const shelfOptions = computed(() =>
  shelfOptionsAll.value.filter((s) => !form.warehouseId || s.warehouseId === form.warehouseId)
)

async function fetchOptions() {
  // 这几个都是主数据，量不大，一次取回来做下拉；筛选和表单共用同一份
  const [warehouses, shelves, products] = await Promise.all([
    pageWarehousesApi({ page: 1, size: 200 }).catch(() => null),
    pageShelvesApi({ page: 1, size: 500 }).catch(() => null),
    pageProductsApi({ page: 1, size: 500 }).catch(() => null)
  ])
  warehouseOptions.value = warehouses?.records ?? []
  shelfOptionsAll.value = shelves?.records ?? []
  productOptions.value = products?.records ?? []
}

/* ---------------- 统计 ---------------- */
const summary = ref<InboundSummary>({
  pendingCount: 0,
  monthOrderCount: 0,
  monthQuantity: 0,
  monthAmount: 0
})

async function fetchSummary() {
  try {
    summary.value = await getInboundSummaryApi()
  } catch {
    // 统计失败不影响列表展示，保持上一次的值
  }
}

/* ---------------- 列表 ---------------- */
const loading = ref(false)
const list = ref<InboundOrder[]>([])
const total = ref(0)

const query = reactive({ page: 1, size: 10 })
const filterKeyword = ref('')
const filterWarehouseId = ref<number | undefined>(undefined)
const filterType = ref('')
const filterStatus = ref('')
const filterDateRange = ref<[string, string] | null>(null)

const columns = ref<Column[]>([
  { prop: 'inboundNo', label: '入库单号 / 时间', minWidth: 190 },
  { prop: 'inboundType', label: '类型', width: 100, align: 'center' },
  { prop: 'status', label: '状态', width: 95, align: 'center' },
  { prop: 'productSummary', label: '手办', minWidth: 190 },
  { prop: 'totalQuantity', label: '数量 / 金额', width: 135, align: 'right' },
  { prop: 'warehouse', label: '仓库 / 货架', minWidth: 170 },
  { prop: 'supplier', label: '供应商', minWidth: 130 },
  { prop: 'action', label: '操作', width: 150, align: 'center', fixed: 'right' }
])

async function fetchList() {
  loading.value = true
  try {
    const [start, end] = filterDateRange.value ?? []
    const data = await pageInboundsApi({
      page: query.page,
      size: query.size,
      keyword: filterKeyword.value.trim() || undefined,
      warehouseId: filterWarehouseId.value || undefined,
      inboundType: filterType.value || undefined,
      status: filterStatus.value || undefined,
      startDate: start || undefined,
      endDate: end || undefined
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
  filterType.value = ''
  filterStatus.value = ''
  filterDateRange.value = null
  query.page = 1
  fetchList()
}

const handlePaginationChange = ({ page, limit }: { page: number; limit: number }) => {
  query.page = page
  query.size = limit
  fetchList()
}

/* ---------------- 新增 / 编辑 ---------------- */
interface ItemRow {
  productId?: number
  quantity: number
  unitPrice: number | null
  remark: string
}

interface InboundForm {
  inboundNo: string
  inboundType: string
  warehouseId: number | undefined
  shelfId: number | null
  supplier: string
  inboundTime: string
  operator: string
  remark: string
  items: ItemRow[]
}

const formVisible = ref(false)
const isEdit = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const saving = ref(false)

const form = reactive<InboundForm>({
  inboundNo: '',
  inboundType: 'P',
  warehouseId: undefined,
  shelfId: null,
  supplier: '',
  inboundTime: '',
  operator: '',
  remark: '',
  items: []
})

const rules = reactive<FormRules>({
  inboundType: [{ required: true, message: '请选择入库类型', trigger: 'change' }],
  warehouseId: [{ required: true, message: '请选择入库仓库', trigger: 'change' }]
})

const newItemRow = (): ItemRow => ({ productId: undefined, quantity: 1, unitPrice: null, remark: '' })

function resetForm() {
  Object.assign(form, {
    inboundNo: '',
    inboundType: 'P',
    // 默认带上当前筛选的仓库，连续录入同一个仓库时少点一次
    warehouseId: filterWarehouseId.value,
    shelfId: null,
    supplier: '',
    inboundTime: nowText(),
    operator: userStore.displayName || '',
    remark: '',
    items: [newItemRow()]
  })
}

const handleCreate = () => {
  isEdit.value = false
  editingId.value = null
  resetForm()
  formVisible.value = true
}

const handleEdit = async (row: InboundOrder) => {
  try {
    const data = await getInboundApi(row.id)
    isEdit.value = true
    editingId.value = row.id
    Object.assign(form, {
      inboundNo: data.inboundNo,
      inboundType: data.inboundType,
      warehouseId: data.warehouseId,
      shelfId: data.shelfId ?? null,
      supplier: data.supplier ?? '',
      // 后端返回的 ISO 时间去掉 T，和日期选择器的格式对齐
      inboundTime: data.inboundTime ? data.inboundTime.replace('T', ' ').slice(0, 19) : nowText(),
      operator: data.operator ?? '',
      remark: data.remark ?? '',
      items: (data.items ?? []).map((i) => ({
        productId: i.productId,
        quantity: i.quantity,
        unitPrice: i.unitPrice ?? null,
        remark: i.remark ?? ''
      }))
    })
    if (form.items.length === 0) form.items = [newItemRow()]
    formVisible.value = true
  } catch {
    // 请求层已提示
  }
}

/** 换仓库时清掉不属于新仓库的货架，避免提交后被后端打回 */
function handleWarehouseChange() {
  if (form.shelfId && !shelfOptions.value.some((s) => s.id === form.shelfId)) {
    form.shelfId = null
  }
}

function addItem() {
  form.items.push(newItemRow())
}

function removeItem(index: number) {
  form.items.splice(index, 1)
}

/** 选择手办后不自动带价格：不同批次的进货价可能不同，由录入人自己填 */
function handleProductChange(row: ItemRow, value: number) {
  if (!value) {
    row.productId = undefined
  }
}

const rowAmount = (row: ItemRow) => (row.unitPrice ?? 0) * (row.quantity || 0)

const formTotalQuantity = computed(() => form.items.reduce((sum, i) => sum + (i.quantity || 0), 0))
const formTotalAmount = computed(() => form.items.reduce((sum, i) => sum + rowAmount(i), 0))

/** 明细校验：至少一行、每行都要选手办、不能重复、数量必须大于 0 */
function validateItems(): boolean {
  if (form.items.length === 0) {
    ElMessage.warning('请至少添加一条手办明细')
    return false
  }
  const seen = new Set<number>()
  for (let i = 0; i < form.items.length; i++) {
    const row = form.items[i] as ItemRow
    if (!row.productId) {
      ElMessage.warning(`第 ${i + 1} 行：请选择手办`)
      return false
    }
    if (!row.quantity || row.quantity < 1) {
      ElMessage.warning(`第 ${i + 1} 行：入库数量必须大于 0`)
      return false
    }
    if (seen.has(row.productId)) {
      ElMessage.warning(`第 ${i + 1} 行：同一款手办不能重复添加`)
      return false
    }
    seen.add(row.productId)
  }
  return true
}

const handleSave = async () => {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  if (!form.warehouseId) return
  if (!validateItems()) return

  const payload: InboundSaveParams = {
    inboundNo: form.inboundNo.trim() || undefined,
    inboundType: form.inboundType,
    warehouseId: form.warehouseId,
    shelfId: form.shelfId || null,
    supplier: form.supplier.trim() || undefined,
    // 日期选择器给的是「2026-11-07 10:30:00」，后端按 ISO-8601 解析，提交前把空格换成 T
    inboundTime: form.inboundTime ? form.inboundTime.replace(' ', 'T') : undefined,
    operator: form.operator.trim() || undefined,
    remark: form.remark.trim() || undefined,
    items: form.items.map((i) => ({
      productId: i.productId as number,
      quantity: i.quantity,
      unitPrice: i.unitPrice ?? null,
      remark: i.remark.trim() || undefined
    }))
  }

  saving.value = true
  try {
    if (isEdit.value && editingId.value) {
      await updateInboundApi(editingId.value, payload)
      ElMessage.success('保存成功')
    } else {
      await createInboundApi(payload)
      ElMessage.success('新增成功')
    }
    formVisible.value = false
    await Promise.all([fetchList(), fetchSummary()])
  } catch {
    // 请求层已提示（如单号重复、货架不属于该仓库）
  } finally {
    saving.value = false
  }
}

/* ---------------- 详情 ---------------- */
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<InboundOrder | null>(null)

async function handleDetail(row: InboundOrder) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getInboundApi(row.id)
  } catch {
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

/* ---------------- 确认 / 作废 / 删除 ---------------- */

/** 「更多」下拉里的状态类操作统一在这里分发 */
function handleCommand(command: string, row: InboundOrder) {
  if (command === 'confirm') handleConfirm(row)
  else if (command === 'cancel') handleCancel(row)
  else if (command === 'delete') handleDelete(row)
}

const handleConfirm = async (row: InboundOrder) => {
  try {
    await ElMessageBox.confirm(
      `确认将入库单「${row.inboundNo}」入库吗？确认后不能再编辑或删除。`,
      '确认入库',
      { type: 'warning', confirmButtonText: '确认入库', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await confirmInboundApi(row.id)
    ElMessage.success('已确认入库')
    await Promise.all([fetchList(), fetchSummary()])
  } catch {
    // 请求层已提示
  }
}

const handleCancel = async (row: InboundOrder) => {
  try {
    await ElMessageBox.confirm(
      `确定作废入库单「${row.inboundNo}」吗？作废后单据仍保留在列表中，但不能再次确认。`,
      '作废确认',
      { type: 'warning', confirmButtonText: '作废', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await cancelInboundApi(row.id)
    ElMessage.success('已作废')
    await Promise.all([fetchList(), fetchSummary()])
  } catch {
    // 请求层已提示
  }
}

const handleDelete = async (row: InboundOrder) => {
  try {
    await ElMessageBox.confirm(
      `确定删除入库单「${row.inboundNo}」吗？删除后列表不再显示，数据仍保留在库中。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await deleteInboundApi(row.id)
    ElMessage.success('已删除')
    // 删掉当前页最后一条时回退一页，避免停在空页
    if (list.value.length === 1 && query.page > 1) {
      query.page -= 1
    }
    await Promise.all([fetchList(), fetchSummary()])
  } catch {
    // 请求层已提示
  }
}

onMounted(async () => {
  await fetchOptions()
  await Promise.all([fetchList(), fetchSummary()])
})
</script>

<style scoped>
.inbound-container {
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

.stat-value.pending {
  color: #e6a23c;
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

.no-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.order-no {
  font-weight: 600;
  color: #303133;
}

.time-sub {
  margin-top: 2px;
  color: #909399;
  font-size: 12px;
  line-height: 1.4;
}

.product-summary {
  color: #606266;
}

.qty {
  font-weight: 600;
  color: #303133;
}

.unit {
  color: #909399;
  font-size: 12px;
  font-weight: 400;
}

/* 数量在上、金额在下，两列信息压成一列，省出横向空间 */
.amount-cell {
  display: flex;
  flex-direction: column;
  line-height: 1.5;
}

.supplier-text {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}

.amount-text {
  color: #f56c6c;
  font-weight: 600;
}

.warehouse-cell {
  display: flex;
  flex-direction: column;
  line-height: 1.5;
}

.warehouse-name {
  color: #303133;
}

.warehouse-sub {
  color: #909399;
  font-size: 12px;
}

.items-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 4px 0 10px;
}

.items-title {
  font-weight: 600;
  color: #303133;
}

.items-tip {
  color: #909399;
  font-size: 12px;
}

.items-table {
  width: 100%;
}

.items-total {
  margin-top: 10px;
  text-align: right;
  color: #606266;
  font-size: 13px;
}

.detail-title {
  margin: 18px 0 10px;
  font-weight: 600;
  color: #303133;
}

.detail-product {
  color: #303133;
}

.muted {
  color: #909399;
  font-size: 12px;
}

.option-name {
  color: #303133;
}

.option-code {
  float: right;
  margin-left: 12px;
  color: #909399;
  font-size: 12px;
}
</style>
