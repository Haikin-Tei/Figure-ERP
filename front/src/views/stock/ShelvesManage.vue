<template>
  <div class="shelves-manage-container p-4">
    <!-- 1. 顶部筛选栏 -->
    <el-card shadow="never" class="mb-4">
      <div class="filter-bar">
        <span class="filter-label">筛选条件：</span>

        <!-- 货架编号 / 库位信息搜索 -->
        <el-input
          v-model="filterKeyword"
          placeholder="货架编号 / 库位信息"
          clearable
          style="width: 200px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />

        <!-- 所属仓库筛选 -->
        <el-select
          v-model="filterWarehouseId"
          placeholder="所属仓库"
          clearable
          filterable
          style="width: 190px"
          @change="handleSearch"
        >
          <el-option
            v-for="w in warehouseOptions"
            :key="w.id"
            :label="`${w.warehouseName} (${w.warehouseCode})`"
            :value="w.id"
          />
        </el-select>

        <!-- 所属区域筛选（选项跟着仓库筛选走） -->
        <el-select
          v-model="filterAreaId"
          placeholder="所属区域"
          clearable
          filterable
          style="width: 180px"
          @change="handleSearch"
        >
          <el-option v-for="a in filterAreaOptions" :key="a.id" :label="a.areaName" :value="a.id" />
        </el-select>

        <!-- 状态筛选 -->
        <el-select v-model="filterStatus" placeholder="货架状态" clearable style="width: 150px" @change="handleSearch">
          <el-option label="正常" value="A" />
          <el-option label="维护中" value="B" />
          <el-option label="停用" value="D" />
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

        <el-button type="primary" :disabled="!canManage" @click="handleAdd">
          <el-icon><Plus /></el-icon>
          新增货架
        </el-button>
      </div>
    </el-card>

    <el-alert
      v-if="!canManage"
      type="info"
      show-icon
      :closable="false"
      style="margin-bottom: 12px"
      title="当前账号只能查看货架信息，新增或修改请联系管理员或主管"
    />

    <!-- 2. 数据表格（服务端分页） -->
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
      <template #warehouse="scope">
        <el-tag v-if="scope.row.warehouseCode" type="primary" effect="plain">
          {{ scope.row.warehouseCode }}
        </el-tag>
        <span class="warehouse-name">{{ scope.row.warehouseName || '仓库已删除' }}</span>
      </template>

      <template #area="scope">
        <el-tag v-if="scope.row.areaName" type="warning" effect="plain">{{ scope.row.areaName }}</el-tag>
        <span v-else class="text-muted">未分区</span>
      </template>

      <template #location="scope">
        <el-tag v-if="scope.row.location" type="info">{{ scope.row.location }}</el-tag>
        <span v-else>-</span>
      </template>

      <template #status="scope">
        <el-tag :type="getStatusTag(scope.row.status)">
          {{ scope.row.statusText || getStatusText(scope.row.status) }}
        </el-tag>
      </template>

      <template #action="scope">
        <el-button link type="primary" size="small" :disabled="!canManage" @click="handleEdit(scope.row)">
          编辑
        </el-button>
        <el-button link type="danger" size="small" :disabled="!canManage" @click="handleDelete(scope.row)">
          删除
        </el-button>
      </template>
    </BaseTable>

    <!-- 3. 新增/编辑弹窗 -->
    <el-dialog v-model="formVisible" :title="isEdit ? '编辑货架' : '新增货架'" width="500px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="货架编号" prop="shelfCode">
          <el-input v-model="form.shelfCode" placeholder="如 01-1，同一仓库内唯一" maxlength="50" />
        </el-form-item>

        <el-form-item label="所属仓库" prop="warehouseId">
          <el-select v-model="form.warehouseId" placeholder="请选择仓库" filterable style="width: 100%">
            <el-option
              v-for="w in warehouseOptions"
              :key="w.id"
              :label="`${w.warehouseName} (${w.warehouseCode})`"
              :value="w.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="所属区域" prop="areaId">
          <el-select
            v-model="form.areaId"
            placeholder="可不选；先选仓库再选区域"
            clearable
            filterable
            :disabled="!form.warehouseId"
            style="width: 100%"
          >
            <el-option v-for="a in formAreaOptions" :key="a.id" :label="a.areaName" :value="a.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="库位信息" prop="location">
          <el-input v-model="form.location" placeholder="例如：01区-1层" maxlength="100" />
        </el-form-item>

        <el-form-item label="当前状态" prop="status">
          <el-select v-model="form.status" placeholder="请选择状态" style="width: 100%">
            <el-option label="正常" value="A" />
            <el-option label="维护中" value="B" />
            <el-option label="停用" value="D" />
          </el-select>
        </el-form-item>

        <el-form-item label="状态描述" prop="remark">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="2"
            maxlength="255"
            show-word-limit
            placeholder="例如：存储大件手办 / 本周进行检修"
          />
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
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { Search, RefreshLeft, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import BaseTable from '@/components/common/BaseTable.vue'
import type { Column } from '@/types/common'
import {
  createShelfApi,
  deleteShelfApi,
  pageShelvesApi,
  updateShelfApi,
  type Shelf,
  type ShelfSaveParams
} from '@/api/shelf'
import { pageWarehousesApi, type Warehouse } from '@/api/warehouse'
import { pageAreasApi, type Area } from '@/api/area'
import { useUserStore } from '@/stores/user'
import { canManageBusiness } from '@/utils/role'

const userStore = useUserStore()

/** 管理员和主管可以维护货架，普通员工只读 */
const canManage = computed(() => canManageBusiness(userStore.userInfo))

/* ---------------- 仓库下拉（新增/编辑都要选，筛选也用同一份） ---------------- */
const warehouseOptions = ref<Warehouse[]>([])

async function fetchWarehouseOptions() {
  try {
    // 仓库数量有限，一次取足够多的条数做下拉，省得再单独开一个 options 接口
    const data = await pageWarehousesApi({ page: 1, size: 200 })
    warehouseOptions.value = data.records
  } catch {
    warehouseOptions.value = []
  }
}

/* ---------------- 区域下拉（筛选与新增/编辑共用一份，按仓库过滤） ---------------- */
const areaOptions = ref<Area[]>([])

async function fetchAreaOptions() {
  try {
    const data = await pageAreasApi({ page: 1, size: 500 })
    areaOptions.value = data.records
  } catch {
    areaOptions.value = []
  }
}

/** 筛选栏的区域选项：选了仓库就只显示该仓库的区域 */
const filterAreaOptions = computed(() =>
  filterWarehouseId.value
    ? areaOptions.value.filter((a) => a.warehouseId === filterWarehouseId.value)
    : areaOptions.value
)

/** 表单里的区域选项：必须与所选仓库一致，否则后端会报「不属于所选仓库」 */
const formAreaOptions = computed(() =>
  form.warehouseId ? areaOptions.value.filter((a) => a.warehouseId === form.warehouseId) : []
)

/* ---------------- 列表（服务端分页） ---------------- */
const loading = ref(false)
const list = ref<Shelf[]>([])
const total = ref(0)

const query = reactive({ page: 1, size: 10 })
const filterKeyword = ref('')
const filterWarehouseId = ref<number | undefined>(undefined)
const filterAreaId = ref<number | undefined>(undefined)
const filterStatus = ref('')

async function fetchList() {
  loading.value = true
  try {
    const data = await pageShelvesApi({
      page: query.page,
      size: query.size,
      keyword: filterKeyword.value.trim() || undefined,
      warehouseId: filterWarehouseId.value || undefined,
      areaId: filterAreaId.value || undefined,
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
  { prop: 'shelfCode', label: '货架编号', width: 140, align: 'center' },
  { prop: 'warehouse', label: '所属仓库', minWidth: 190 },
  { prop: 'area', label: '所属区域', minWidth: 150 },
  { prop: 'location', label: '库位信息', minWidth: 160 },
  { prop: 'status', label: '当前状态', width: 120, align: 'center' },
  {
    prop: 'remark',
    label: '状态描述',
    minWidth: 180,
    formatter: (row) => row.remark || '-'
  },
  {
    prop: 'updateTime',
    label: '更新时间',
    width: 170,
    // 后端返回 ISO-8601（2026-11-06T07:53:49），列表里换成更易读的 2026-11-06 07:53:49
    formatter: (row) => (row.updateTime ? row.updateTime.replace('T', ' ').slice(0, 19) : '-')
  },
  { prop: 'action', label: '操作', width: 160, align: 'center', fixed: 'right' }
])

/* ---------------- 查询 / 分页 ---------------- */
const handleSearch = () => {
  query.page = 1
  fetchList()
}

const handleReset = () => {
  filterKeyword.value = ''
  filterWarehouseId.value = undefined
  filterAreaId.value = undefined
  filterStatus.value = ''
  query.page = 1
  fetchList()
}

const handlePaginationChange = ({ page, limit }: { page: number; limit: number }) => {
  query.page = page
  query.size = limit
  fetchList()
}

/* ---------------- 新增 / 编辑 ---------------- */
interface ShelfForm {
  shelfCode: string
  warehouseId: number | undefined
  areaId: number | undefined
  location: string
  status: string
  remark: string
}

const formVisible = ref(false)
const isEdit = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const saving = ref(false)

const form = reactive<ShelfForm>({
  shelfCode: '',
  warehouseId: undefined,
  areaId: undefined,
  location: '',
  status: 'A',
  remark: ''
})

const rules = reactive<FormRules>({
  shelfCode: [{ required: true, message: '请输入货架编号', trigger: 'blur' }],
  warehouseId: [{ required: true, message: '请选择所属仓库', trigger: 'change' }],
  location: [{ required: true, message: '请输入库位信息', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
})

const handleAdd = () => {
  isEdit.value = false
  editingId.value = null
  Object.assign(form, {
    shelfCode: '',
    // 默认带上当前筛选的仓库/区域，连续录入同一区域的货架时少点一次
    warehouseId: filterWarehouseId.value,
    areaId: filterAreaId.value,
    location: '',
    status: 'A',
    remark: ''
  })
  formVisible.value = true
}

const handleEdit = (row: Shelf) => {
  isEdit.value = true
  editingId.value = row.id
  Object.assign(form, {
    shelfCode: row.shelfCode,
    warehouseId: row.warehouseId,
    areaId: row.areaId ?? undefined,
    location: row.location ?? '',
    status: row.status,
    remark: row.remark ?? ''
  })
  formVisible.value = true
}

const handleSave = async () => {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  if (!form.warehouseId) return

  const payload: ShelfSaveParams = {
    shelfCode: form.shelfCode.trim(),
    warehouseId: form.warehouseId,
    areaId: form.areaId ?? null,
    location: form.location.trim() || undefined,
    status: form.status || 'A',
    remark: form.remark.trim() || undefined
  }

  saving.value = true
  try {
    if (isEdit.value && editingId.value) {
      await updateShelfApi(editingId.value, payload)
      ElMessage.success('保存成功')
    } else {
      await createShelfApi(payload)
      ElMessage.success('新增成功')
    }
    formVisible.value = false
    // 保存后按当前筛选条件重新拉取，页面实时反映库里最新数据
    await fetchList()
  } catch {
    // 请求层已经弹过错误提示（如编号重复）
  } finally {
    saving.value = false
  }
}

/* ---------------- 删除 ---------------- */
const handleDelete = async (row: Shelf) => {
  try {
    await ElMessageBox.confirm(
      `确定删除货架「${row.shelfCode}」吗？删除后列表不再显示，数据仍保留在库中，同仓库下编号可以重新使用。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await deleteShelfApi(row.id)
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
  const map: Record<string, string> = { A: '正常', B: '维护中', D: '停用' }
  return status ? map[status] || status : '-'
}

const getStatusTag = (status?: string) => {
  if (status === 'A') return 'success'
  if (status === 'B') return 'warning'
  if (status === 'D') return 'info'
  return ''
}

/* ---------------- 仓库联动 ----------------
 * 仓库一改，原来选的区域可能属于旧仓库，直接清掉，避免提交时报「不属于所选仓库」。
 * 筛选栏同理：清掉区域筛选，否则会查出空列表。 */
watch(filterWarehouseId, (id, oldId) => {
  if (oldId !== undefined && filterAreaId.value) {
    const stillValid = areaOptions.value.some(
      (a) => a.id === filterAreaId.value && a.warehouseId === id
    )
    if (!stillValid) filterAreaId.value = undefined
  }
})

watch(
  () => form.warehouseId,
  (id, oldId) => {
    if (oldId === undefined || form.areaId === undefined) return
    const stillValid = areaOptions.value.some((a) => a.id === form.areaId && a.warehouseId === id)
    if (!stillValid) form.areaId = undefined
  }
)

onMounted(async () => {
  await fetchWarehouseOptions()
  await fetchAreaOptions()
  await fetchList()
})
</script>

<style scoped>
.shelves-manage-container {
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

.warehouse-name {
  margin-left: 6px;
  color: #606266;
}

.text-muted {
  color: #909399;
}
</style>
