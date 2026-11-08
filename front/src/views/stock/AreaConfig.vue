<template>
  <div class="area-config-container p-4">
    <!-- 1. 顶部筛选栏 -->
    <el-card shadow="never" class="mb-4">
      <div class="filter-bar">
        <span class="filter-label">筛选条件：</span>

        <!-- 区域编号 / 名称搜索 -->
        <el-input
          v-model="filterKeyword"
          placeholder="区域编号 / 区域名称"
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

        <!-- 状态筛选 -->
        <el-select v-model="filterStatus" placeholder="区域状态" clearable style="width: 150px" @change="handleSearch">
          <el-option label="使用中" value="A" />
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
          新增区域
        </el-button>
      </div>
    </el-card>

    <el-alert
      v-if="!canManage"
      type="info"
      show-icon
      :closable="false"
      style="margin-bottom: 12px"
      title="当前账号只能查看区域信息，新增或修改请联系管理员或主管"
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
      <template #areaCode="scope">
        <el-tag type="primary" effect="plain">{{ scope.row.areaCode }}</el-tag>
      </template>

      <template #areaName="scope">
        <span class="area-name">{{ scope.row.areaName }}</span>
      </template>

      <template #warehouse="scope">
        <el-tag v-if="scope.row.warehouseCode" type="info" effect="plain">
          {{ scope.row.warehouseCode }}
        </el-tag>
        <span class="warehouse-name">{{ scope.row.warehouseName || '仓库已删除' }}</span>
      </template>

      <template #capacity="scope">
        <span class="capacity-text">{{ scope.row.shelfCount ?? 0 }} / {{ scope.row.capacity ?? 0 }}</span>
        <span class="capacity-unit">个货架</span>
      </template>

      <template #status="scope">
        <el-tag :type="scope.row.status === 'A' ? 'success' : 'info'">
          {{ scope.row.statusText || (scope.row.status === 'A' ? '使用中' : '停用') }}
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
    <el-dialog
      v-model="formVisible"
      :title="isEdit ? '编辑区域' : '新增区域'"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="区域编号" prop="areaCode">
          <el-input v-model="form.areaCode" placeholder="如 A，同一仓库内唯一" maxlength="50" />
        </el-form-item>

        <el-form-item label="区域名称" prop="areaName">
          <el-input v-model="form.areaName" placeholder="如 A区-核心展示柜" maxlength="100" />
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

        <el-form-item label="物理位置" prop="location">
          <el-input v-model="form.location" placeholder="例如：一层-主墙面" maxlength="100" />
        </el-form-item>

        <el-form-item label="容量" prop="capacity">
          <el-input-number v-model="form.capacity" :min="0" :max="9999" :step="1" />
          <span class="form-tip">可容纳的货架数</span>
        </el-form-item>

        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status" placeholder="请选择状态" style="width: 100%">
            <el-option label="使用中" value="A" />
            <el-option label="停用" value="D" />
          </el-select>
        </el-form-item>

        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="2"
            maxlength="255"
            show-word-limit
            placeholder="例如：放置热门款"
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
import { ref, reactive, computed, onMounted } from 'vue'
import { Search, RefreshLeft, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import BaseTable from '@/components/common/BaseTable.vue'
import type { Column } from '@/types/common'
import {
  createAreaApi,
  deleteAreaApi,
  pageAreasApi,
  updateAreaApi,
  type Area,
  type AreaSaveParams
} from '@/api/area'
import { pageWarehousesApi, type Warehouse } from '@/api/warehouse'
import { useUserStore } from '@/stores/user'
import { canManageBusiness } from '@/utils/role'

const userStore = useUserStore()

/** 管理员和主管可以维护区域，普通员工只读 */
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

/* ---------------- 列表（服务端分页） ---------------- */
const loading = ref(false)
const list = ref<Area[]>([])
const total = ref(0)

const query = reactive({ page: 1, size: 10 })
const filterKeyword = ref('')
const filterWarehouseId = ref<number | undefined>(undefined)
const filterStatus = ref('')

async function fetchList() {
  loading.value = true
  try {
    const data = await pageAreasApi({
      page: query.page,
      size: query.size,
      keyword: filterKeyword.value.trim() || undefined,
      warehouseId: filterWarehouseId.value || undefined,
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
  { prop: 'areaCode', label: '区域编号', width: 110, align: 'center' },
  { prop: 'areaName', label: '区域名称', minWidth: 180 },
  { prop: 'warehouse', label: '所属仓库', minWidth: 190 },
  { prop: 'location', label: '物理位置', minWidth: 150, formatter: (row) => row.location || '-' },
  { prop: 'capacity', label: '容量', width: 150, align: 'center' },
  { prop: 'status', label: '状态', width: 110, align: 'center' },
  {
    prop: 'remark',
    label: '备注',
    minWidth: 160,
    formatter: (row) => row.remark || '-'
  },
  {
    prop: 'updateTime',
    label: '更新时间',
    width: 170,
    // 后端返回 ISO-8601（2026-11-07T08:53:49），列表里换成更易读的 2026-11-07 08:53:49
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
interface AreaForm {
  areaCode: string
  areaName: string
  warehouseId: number | undefined
  location: string
  capacity: number
  status: string
  remark: string
}

const formVisible = ref(false)
const isEdit = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const saving = ref(false)

const form = reactive<AreaForm>({
  areaCode: '',
  areaName: '',
  warehouseId: undefined,
  location: '',
  capacity: 10,
  status: 'A',
  remark: ''
})

const rules = reactive<FormRules>({
  areaCode: [{ required: true, message: '请输入区域编号', trigger: 'blur' }],
  areaName: [{ required: true, message: '请输入区域名称', trigger: 'blur' }],
  warehouseId: [{ required: true, message: '请选择所属仓库', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
})

const handleAdd = () => {
  isEdit.value = false
  editingId.value = null
  Object.assign(form, {
    areaCode: '',
    areaName: '',
    // 默认带上当前筛选的仓库，连续录入同一仓库的区域时少点一次
    warehouseId: filterWarehouseId.value,
    location: '',
    capacity: 10,
    status: 'A',
    remark: ''
  })
  formVisible.value = true
}

const handleEdit = (row: Area) => {
  isEdit.value = true
  editingId.value = row.id
  Object.assign(form, {
    areaCode: row.areaCode,
    areaName: row.areaName,
    warehouseId: row.warehouseId,
    location: row.location ?? '',
    capacity: row.capacity ?? 0,
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

  const payload: AreaSaveParams = {
    areaCode: form.areaCode.trim(),
    areaName: form.areaName.trim(),
    warehouseId: form.warehouseId,
    location: form.location.trim() || undefined,
    capacity: form.capacity ?? 0,
    status: form.status || 'A',
    remark: form.remark.trim() || undefined
  }

  saving.value = true
  try {
    if (isEdit.value && editingId.value) {
      await updateAreaApi(editingId.value, payload)
      ElMessage.success('保存成功')
    } else {
      await createAreaApi(payload)
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
const handleDelete = async (row: Area) => {
  try {
    await ElMessageBox.confirm(
      `确定删除区域「${row.areaName}」吗？删除后列表不再显示，数据仍保留在库中。区域下还有货架时不允许删除。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await deleteAreaApi(row.id)
    ElMessage.success('已删除')
    // 删掉当前页最后一条时回退一页，避免停在空页
    if (list.value.length === 1 && query.page > 1) {
      query.page -= 1
    }
    await fetchList()
  } catch {
    // 请求层已提示（如区域内还有货架）
  }
}

onMounted(async () => {
  await fetchWarehouseOptions()
  await fetchList()
})
</script>

<style scoped>
.area-config-container {
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

.flex-grow {
  flex: 1;
}

.area-name {
  font-weight: 500;
  color: #303133;
}

.warehouse-name {
  margin-left: 6px;
  color: #606266;
}

.capacity-text {
  color: #303133;
  font-weight: 500;
}

.capacity-unit {
  margin-left: 4px;
  color: #909399;
  font-size: 12px;
}

.form-tip {
  margin-left: 10px;
  color: #909399;
  font-size: 12px;
}
</style>
