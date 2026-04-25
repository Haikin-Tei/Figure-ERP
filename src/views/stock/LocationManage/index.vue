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

        <el-button type="primary" @click="handleAdd">
          <el-icon>
            <Plus />
          </el-icon>
          新增仓库
        </el-button>
      </div>
    </el-card>

    <BaseTable :data="paginatedData" :columns="columns" :loading="loading" @update:page="handleCurrentChange"
      @update:limit="handleSizeChange">
      <!-- 插槽：渲染“当前状态”列 -->
      <template #status="scope">
        <el-tag :type="getStatusTag(scope.row.status)">
          {{ getStatusText(scope.row.status) }}
        </el-tag>
      </template>
      <template #action="scope">
        <!-- 查看详情 -->
        <el-button link type="info" size="small" @click="handleViewDetails(scope.row)">
          查看详情
        </el-button>
        <!-- 编辑 -->
        <el-button link type="primary" size="small" @click="handleEdit(scope.row)">
          编辑
        </el-button>
        <!-- 删除 -->
        <el-button link type="danger" size="small">
          删除
        </el-button>
      </template>
    </BaseTable>


    <!-- 3. 分页器 -->
    <div class="pagination-container">
      <el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize" :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper" :total="filteredData.length" @size-change="handleSizeChange"
        @current-change="handleCurrentChange" />
    </div>

    <!-- 4. 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="仓库详情" width="500px" :close-on-click-modal="false">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="仓库代码">
          {{ currentDetail.warehouseCode }}
        </el-descriptions-item>
        <el-descriptions-item label="仓库名称">
          <!-- 修改：移除了详情页的 el-tag -->
          {{ currentDetail.warehouseName }}
        </el-descriptions-item>
        <el-descriptions-item label="仓库地址">
          {{ currentDetail.warehouseAddress || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="当前状态">
          <el-tag :type="getStatusTag(currentDetail.status)">
            {{ getStatusText(currentDetail.status) }}
          </el-tag>
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
          <el-input v-model="form.warehouseCode" placeholder="请输入仓库代码" />
        </el-form-item>

        <el-form-item label="仓库名称" prop="warehouseName">
          <el-input v-model="form.warehouseName" placeholder="请输入仓库名称" />
        </el-form-item>

        <el-form-item label="仓库地址" prop="warehouseAddress">
          <el-input v-model="form.warehouseAddress" placeholder="请输入详细地址" />
        </el-form-item>

        <el-form-item label="当前状态" prop="status">
          <el-select v-model="form.status" placeholder="请选择状态" style="width: 100%">
            <el-option label="使用中" value="A" />
            <el-option label="盘库中" value="C" />
            <el-option label="停用中" value="D" />
          </el-select>
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
import { ref, computed, reactive } from 'vue'
import { Plus, Search, RefreshLeft } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import BaseTable from '@/components/common/BaseTable.vue'
import type { Column } from './index.ts'

// --- 1. 模拟数据 ---
const warehouseList = ref([
  { warehouseCode: '3101', warehouseName: '上海库', warehouseAddress: '上海市浦东新区新川路180号', isReturn: 'N', status: 'A' },
  { warehouseCode: '3102', warehouseName: '上海退货库', warehouseAddress: '上海市浦东新区新川路180号', isReturn: 'Y', status: 'A' },
  { warehouseCode: '1101', warehouseName: '北京库', warehouseAddress: '北京市海淀区清华紫光大厦', isReturn: 'N', status: 'C' },
  { warehouseCode: '4401', warehouseName: '广州库', warehouseAddress: '广州市荔湾区黄沙大道144号', isReturn: 'N', status: 'D' },
])

// 定义表格列配置
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
    // 模拟格式化：如果地址太长，这里可以做截取处理，或者直接靠组件的 show-overflow-tooltip
    formatter: (row) => row.warehouseAddress
  },
  {
    prop: 'status',
    label: '当前状态',
    width: 120,
    align: 'center'
    // 注意：这里没有 formatter，因为我们需要渲染 el-tag，
    // 所以会在 template 中通过插槽 #status 来处理
  },
  {
    prop: 'action', // 虚拟字段，不对应具体数据
    label: '操作',
    width: 220,
    align: 'center',
    fixed: 'right'
    // 同样，操作按钮通过插槽 #action 渲染
  }
])

// --- 2. 筛选状态 ---
const filterType = ref('')
const filterStatus = ref('')
const loading = ref(false)

// --- 3. 分页状态 ---
const currentPage = ref(1)
const pageSize = ref(20)

// --- 4. 详情弹窗状态 ---
const detailVisible = ref(false)
const currentDetail = ref<any>({})

// --- 5. 表单弹窗状态 ---
const formVisible = ref(false)
const isEdit = ref(false)
const formRef = ref<FormInstance>()
const saving = ref(false)

// 表单数据
const form = reactive({
  warehouseCode: '',
  warehouseName: '',
  warehouseAddress: '',
  status: '',
  isReturn: 'N'
})

// 表单校验规则
const rules = reactive<FormRules>({
  warehouseCode: [{ required: true, message: '请输入仓库代码', trigger: 'blur' }],
  warehouseName: [{ required: true, message: '请输入仓库名称', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
})

// --- 6. 计算属性 ---
const filteredData = computed(() => {
  return warehouseList.value.filter(item => {
    const matchType = filterType.value ? item.isReturn === filterType.value : true
    const matchStatus = filterStatus.value ? item.status === filterStatus.value : true
    return matchType && matchStatus
  })
})

const paginatedData = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return filteredData.value.slice(start, end)
})

// --- 7. 方法 ---
const handleSearch = () => {
  loading.value = true
  currentPage.value = 1
  setTimeout(() => {
    loading.value = false
    ElMessage.success('查询成功')
  }, 500)
}

const handleReset = () => {
  filterType.value = ''
  filterStatus.value = ''
  handleSearch()
}

const handleSizeChange = () => {
  currentPage.value = 1
}

const handleCurrentChange = () => {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

const handleViewDetails = (row: any) => {
  currentDetail.value = { ...row }
  detailVisible.value = true
}

// 打开编辑
const handleEdit = (row: any) => {
  Object.assign(form, row)
  isEdit.value = true
  formVisible.value = true
}

// 打开新增
const handleAdd = () => {
  // 重置表单
  form.warehouseCode = ''
  form.warehouseName = ''
  form.warehouseAddress = ''
  form.status = 'A' // 默认选中
  isEdit.value = false
  formVisible.value = true
}

// 保存逻辑 (新增/编辑通用)
const handleSave = async () => {
  if (!formRef.value) return

  await formRef.value.validate((valid) => {
    if (valid) {
      saving.value = true
      setTimeout(() => {
        if (isEdit.value) {
          // 编辑逻辑
          const index = warehouseList.value.findIndex(item => item.warehouseCode === form.warehouseCode)
          if (index !== -1) {
            warehouseList.value[index] = { ...form }
            ElMessage.success('更新成功')
          }
        } else {
          // 新增逻辑
          // 简单检查一下代码是否重复
          const exists = warehouseList.value.some(item => item.warehouseCode === form.warehouseCode)
          if (exists) {
            ElMessage.error('仓库代码已存在')
            saving.value = false
            return
          }
          // 默认新增的仓库 isReturn 为 N
          warehouseList.value.unshift({ ...form, isReturn: 'N' })
          ElMessage.success('新增成功')
        }
        formVisible.value = false
        saving.value = false
      }, 500)
    }
  })
}

const getStatusText = (status: string) => {
  const map: Record<string, string> = { A: '使用中', C: '盘库中', D: '停用中' }
  return map[status] || status
}

const getStatusTag = (status: string) => {
  if (status === 'A') return 'success'
  if (status === 'C') return 'warning'
  if (status === 'D') return 'info'
  return ''
}
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

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
