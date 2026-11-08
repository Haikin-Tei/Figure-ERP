<template>
  <div class="location-manage-container p-4">
    <!-- 1. 顶部筛选栏 -->
    <el-card shadow="never" class="mb-4">
      <div class="filter-bar">
        <span class="filter-label">筛选条件：</span>

        <!-- 仓库类型筛选 (保留逻辑但界面不显示退货标签) -->
        <el-select v-model="filterType" placeholder="仓库类型" clearable style="width: 150px">
          <el-option label="正常仓库" value="N" />
          <el-option label="退货仓库" value="Y" />
        </el-select>

        <!-- 状态筛选 -->
        <el-select v-model="filterStatus" placeholder="仓库状态" clearable style="width: 150px">
          <el-option label="使用中" value="A" />
          <el-option label="盘库中" value="C" />
          <el-option label="停用中" value="D" />
        </el-select>

        <!-- 查询按钮 -->
        <el-button type="primary" @click="handleSearch">
          <el-icon>
            <Search />
          </el-icon>
          查询
        </el-button>

        <!-- 重置按钮 -->
        <el-button @click="handleReset">
          <el-icon>
            <RefreshLeft />
          </el-icon>
          重置
        </el-button>

        <div class="flex-grow"></div>

        <el-button type="primary" :disabled="!canManage" @click="handleAdd">
          <el-icon>
            <Plus />
          </el-icon>
          新增仓库
        </el-button>
      </div>
    </el-card>

    <el-alert
      v-if="!canManage"
      type="info"
      show-icon
      :closable="false"
      style="margin-bottom: 12px"
      title="当前账号只能查看仓库信息，新增或修改请联系管理员或主管"
    />

    <BaseTable :data="list" :columns="columns" :loading="loading" :pagination="true"
      :total="total" :current-page="query.page" :page-size="query.size"
      @pagination-change="handlePaginationChange">
      <!-- 插槽：渲染“当前状态”列 -->
      <template #status="scope">
        <el-tag :type="getStatusTag(scope.row.status)">
          {{ scope.row.statusText || getStatusText(scope.row.status) }}
        </el-tag>
      </template>
      <template #action="scope">
        <!-- 查看详情 -->
        <el-button link type="info" size="small" @click="handleViewDetails(scope.row)">
          查看详情
        </el-button>
        <!-- 编辑 -->
        <el-button link type="primary" size="small" :disabled="!canManage" @click="handleEdit(scope.row)">
          编辑
        </el-button>
        <!-- 删除 -->
        <el-button link type="danger" size="small" :disabled="!canManage" @click="handleDelete(scope.row)">
          删除
        </el-button>
      </template>
    </BaseTable>

    <!-- 4. 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="仓库详情" width="500px" :close-on-click-modal="false">
      <el-descriptions :column="1" border v-loading="detailLoading">
        <el-descriptions-item label="仓库代码">
          {{ currentDetail.warehouseCode }}
        </el-descriptions-item>
        <el-descriptions-item label="仓库名称">
          {{ currentDetail.warehouseName }}
        </el-descriptions-item>
        <el-descriptions-item label="仓库地址">
          {{ currentDetail.warehouseAddress || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="仓库类型">
          {{ currentDetail.isReturnText || (currentDetail.isReturn === 'Y' ? '退货仓' : '正常仓') }}
        </el-descriptions-item>
        <el-descriptions-item label="当前状态">
          <el-tag :type="getStatusTag(currentDetail.status)">
            {{ currentDetail.statusText || getStatusText(currentDetail.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="备注">
          {{ currentDetail.remark || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="更新时间">
          {{ currentDetail.updateTime || '-' }}
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="detailVisible = false">关闭</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 5. 新增/编辑弹窗 -->
    <el-dialog v-model="formVisible" :title="isEdit ? '编辑仓库信息' : '新增仓库'" width="500px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="仓库代码" prop="warehouseCode">
          <el-input v-model="form.warehouseCode" placeholder="请输入仓库代码，如 3101" maxlength="50" />
        </el-form-item>

        <el-form-item label="仓库名称" prop="warehouseName">
          <el-input v-model="form.warehouseName" placeholder="请输入仓库名称" maxlength="100" />
        </el-form-item>

        <el-form-item label="仓库地址" prop="warehouseAddress">
          <el-input v-model="form.warehouseAddress" placeholder="请输入详细地址" maxlength="255" />
        </el-form-item>

        <el-form-item label="仓库类型">
          <el-radio-group v-model="form.isReturn">
            <el-radio value="N">正常仓</el-radio>
            <el-radio value="Y">退货仓</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="当前状态" prop="status">
          <el-select v-model="form.status" placeholder="请选择状态" style="width: 100%">
            <el-option label="使用中" value="A" />
            <el-option label="盘库中" value="C" />
            <el-option label="停用中" value="D" />
          </el-select>
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="formVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { Plus, Search, RefreshLeft } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import BaseTable from '@/components/common/BaseTable.vue'
import type { Column } from '@/types/common'
import {
  createWarehouseApi,
  deleteWarehouseApi,
  getWarehouseApi,
  pageWarehousesApi,
  updateWarehouseApi,
  type Warehouse,
  type WarehouseSaveParams
} from '@/api/warehouse'
import { useUserStore } from '@/stores/user'
import { canManageBusiness } from '@/utils/role'

const userStore = useUserStore()

/** 管理员和主管可以维护仓库，普通员工只读 */
const canManage = computed(() => canManageBusiness(userStore.userInfo))

/* ---------------- 列表（服务端分页） ---------------- */
const loading = ref(false)
const list = ref<Warehouse[]>([])
const total = ref(0)

const query = reactive({ page: 1, size: 20 })
const filterType = ref('')
const filterStatus = ref('')

async function fetchList() {
  loading.value = true
  try {
    const data = await pageWarehousesApi({
      page: query.page,
      size: query.size,
      isReturn: filterType.value || undefined,
      status: filterStatus.value || undefined
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

const columns = ref<Column[]>([
  {
    prop: 'warehouseCode',
    label: '仓库代码',
    width: 120,
    align: 'center'
  },
  {
    prop: 'warehouseName',
    label: '仓库名称',
    minWidth: 180
  },
  {
    prop: 'warehouseAddress',
    label: '仓库地址',
    minWidth: 250,
    formatter: (row) => row.warehouseAddress || '-'
  },
  {
    prop: 'status',
    label: '当前状态',
    width: 120,
    align: 'center'
  },
  {
    prop: 'action',
    label: '操作',
    width: 220,
    align: 'center',
    fixed: 'right'
  }
])

/* ---------------- 查询 / 分页 ---------------- */
const handleSearch = () => {
  query.page = 1
  fetchList()
}

const handleReset = () => {
  filterType.value = ''
  filterStatus.value = ''
  query.page = 1
  fetchList()
}

// 分页变化：BaseTable 会把新的页码/每页条数回传过来
const handlePaginationChange = ({ page, limit }: { page: number; limit: number }) => {
  query.page = page
  query.size = limit
  fetchList()
}

/* ---------------- 详情 ---------------- */
const detailVisible = ref(false)
const detailLoading = ref(false)
const currentDetail = ref<Partial<Warehouse>>({})

const handleViewDetails = async (row: Warehouse) => {
  detailVisible.value = true
  detailLoading.value = true
  try {
    // 详情走接口，保证看到的是库里的最新值（别人刚改过也能看到）
    currentDetail.value = await getWarehouseApi(row.id)
  } catch {
    currentDetail.value = { ...row }
  } finally {
    detailLoading.value = false
  }
}

/* ---------------- 新增 / 编辑 ---------------- */
interface WarehouseForm {
  warehouseCode: string
  warehouseName: string
  warehouseAddress: string
  isReturn: string
  status: string
  remark: string
  sortNo?: number
}

const formVisible = ref(false)
const isEdit = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const saving = ref(false)

const form = reactive<WarehouseForm>({
  warehouseCode: '',
  warehouseName: '',
  warehouseAddress: '',
  isReturn: 'N',
  status: 'A',
  remark: ''
})

const rules = reactive<FormRules>({
  warehouseCode: [{ required: true, message: '请输入仓库代码', trigger: 'blur' }],
  warehouseName: [{ required: true, message: '请输入仓库名称', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
})

const handleAdd = () => {
  isEdit.value = false
  editingId.value = null
  Object.assign(form, {
    warehouseCode: '',
    warehouseName: '',
    warehouseAddress: '',
    isReturn: 'N',
    status: 'A',
    remark: '',
    sortNo: undefined
  })
  formVisible.value = true
}

const handleEdit = (row: Warehouse) => {
  isEdit.value = true
  editingId.value = row.id
  Object.assign(form, {
    warehouseCode: row.warehouseCode,
    warehouseName: row.warehouseName,
    warehouseAddress: row.warehouseAddress ?? '',
    isReturn: row.isReturn,
    status: row.status,
    remark: row.remark ?? '',
    sortNo: row.sortNo
  })
  formVisible.value = true
}

const handleSave = async () => {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  const payload: WarehouseSaveParams = {
    warehouseCode: form.warehouseCode.trim(),
    warehouseName: form.warehouseName.trim(),
    warehouseAddress: form.warehouseAddress.trim() || undefined,
    isReturn: form.isReturn || 'N',
    status: form.status || 'A',
    remark: form.remark.trim() || undefined,
    sortNo: form.sortNo
  }

  saving.value = true
  try {
    if (isEdit.value && editingId.value) {
      await updateWarehouseApi(editingId.value, payload)
      ElMessage.success('保存成功')
    } else {
      await createWarehouseApi(payload)
      ElMessage.success('新增成功')
    }
    formVisible.value = false
    // 保存后立即按当前筛选条件重新拉取，页面实时反映库里最新数据
    await fetchList()
  } catch {
    // 请求层已经弹过错误提示（如代码重复）
  } finally {
    saving.value = false
  }
}

/* ---------------- 删除 ---------------- */
const handleDelete = async (row: Warehouse) => {
  try {
    await ElMessageBox.confirm(
      `确定删除仓库「${row.warehouseName}」吗？删除后列表不再显示，数据仍保留在库中，仓库代码可以重新使用。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await deleteWarehouseApi(row.id)
    ElMessage.success('已删除')
    // 删掉当前页最后一条时回退一页，避免停在空页
    if (list.value.length === 1 && query.page > 1) {
      query.page -= 1
    }
    await fetchList()
  } catch {
    // 请求层已提示
  }
}

/* ---------------- 状态文案 ---------------- */
const getStatusText = (status?: string) => {
  const map: Record<string, string> = { A: '使用中', C: '盘库中', D: '停用中' }
  return status ? map[status] || status : '-'
}

const getStatusTag = (status?: string) => {
  if (status === 'A') return 'success'
  if (status === 'C') return 'warning'
  if (status === 'D') return 'info'
  return ''
}

onMounted(fetchList)
</script>

<style scoped>
.location-manage-container {
  background-color: #f5f7fa;
  min-height: 100%;
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px;
}

.filter-label {
  font-weight: bold;
  color: #303133;
  margin-right: 8px;
}
</style>
