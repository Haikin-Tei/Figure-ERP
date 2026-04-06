<!-- src/views/stock/ShelvesManage.vue -->
<template>
  <div class="shelves-manage-container p-4">
    <!-- 1. 顶部筛选栏 -->
    <el-card shadow="never" class="mb-4">
      <div class="filter-bar">
        <span class="filter-label">筛选条件：</span>

        <!-- 货架名称搜索 -->
        <el-input v-model="filterName" placeholder="请输入货架名称" clearable style="width: 200px"
          @keyup.enter="handleSearch" />

        <!-- 状态筛选 -->
        <el-select v-model="filterStatus" placeholder="货架状态" clearable style="width: 150px">
          <el-option label="正常" value="A" />
          <el-option label="维护中" value="B" />
          <el-option label="停用" value="D" />
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
          新增货架
        </el-button>
      </div>
    </el-card>

    <!-- 2. 数据展示表格 -->
    <el-table :data="paginatedData" stripe style="width: 100%" border v-loading="loading">
      <el-table-column prop="shelfCode" label="货架编号" width="140" align="center" />

      <!-- 修改：表头改为“库位编码” -->
      <el-table-column prop="warehouseCode" label="库位编码" width="160" align="center">
        <template #default="scope">
          <el-tag type="primary">{{ scope.row.warehouseCode }}</el-tag>
        </template>
      </el-table-column>

      <el-table-column prop="location" label="库位信息" min-width="160">
        <template #default="scope">
          <el-tag type="info">{{ scope.row.location }}</el-tag>
        </template>
      </el-table-column>

      <!-- 移除：层数列已删除 -->

      <el-table-column prop="status" label="当前状态" width="120" align="center">
        <template #default="scope">
          <el-tag :type="getStatusTag(scope.row.status)">
            {{ getStatusText(scope.row.status) }}
          </el-tag>
        </template>
      </el-table-column>

      <!-- 修改：表头改为“状态描述” -->
      <el-table-column prop="remark" label="状态描述" min-width="160" />

      <el-table-column label="操作" width="200" align="center" fixed="right">
        <template #default="scope">
          <el-button link type="primary" size="small" @click="handleEdit(scope.row)">编辑</el-button>
          <el-button link type="danger" size="small" @click="handleDelete(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 3. 分页器 -->
    <div class="pagination-container">
      <el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize" :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper" :total="filteredData.length" @size-change="handleSizeChange"
        @current-change="handleCurrentChange" />
    </div>

    <!-- 4. 新增/编辑弹窗 -->
    <el-dialog v-model="formVisible" :title="isEdit ? '编辑货架' : '新增货架'" width="500px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">

        <el-form-item label="货架编号" prop="shelfCode">
          <el-input v-model="form.shelfCode" placeholder="请输入唯一编号" :disabled="isEdit" />
        </el-form-item>

        <!-- 修改：标签改为“库位编码” -->
        <el-form-item label="库位编码" prop="warehouseCode">
          <el-select v-model="form.warehouseCode" placeholder="请选择库位编码" style="width: 100%">
            <!-- 这里需要替换为真实的仓库列表 -->
            <el-option label="上海库 (3101)" value="3101" />
            <el-option label="上海退货库 (3102)" value="3102" />
          </el-select>
        </el-form-item>

        <el-form-item label="库位信息" prop="location">
          <el-input v-model="form.location" placeholder="例如：01区-1层" />
        </el-form-item>

        <!-- 修改：标签改为“状态描述” -->
        <el-form-item label="状态描述" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入状态描述" />
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
import { Search, RefreshLeft, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'

// --- 1. 模拟数据 (已更新结构) ---
const shelvesList = ref([
  { id: 1, shelfCode: '01-1', location: '01区-1层', status: 'A', remark: '存储大件手办', warehouseCode: '3101', warehouseName: '上海库' },
  { id: 2, shelfCode: '01-2', location: '01区-2层', status: 'A', remark: '存储周边产品', warehouseCode: '3101', warehouseName: '上海库' },
  { id: 3, shelfCode: '02-1', location: '02区-1层', status: 'B', remark: '本周进行检修', warehouseCode: '3102', warehouseName: '上海退货库' }
])

// --- 2. 筛选与分页状态 ---
const filterName = ref('')
const filterStatus = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const loading = ref(false)

// --- 3. 表单状态 ---
const formVisible = ref(false)
const isEdit = ref(false)
const formRef = ref<FormInstance>()
const saving = ref(false)

// 表单数据模型 (已更新结构)
const form = reactive({
  id: undefined as number | undefined,
  shelfCode: '',
  location: '',
  remark: '',
  warehouseCode: '',
  warehouseName: ''
})

// 表单校验规则
const rules = reactive<FormRules>({
  shelfCode: [{ required: true, message: '请输入货架编号', trigger: 'blur' }],
  warehouseCode: [{ required: true, message: '请选择库位编码', trigger: 'change' }],
  location: [{ required: true, message: '请输入库位信息', trigger: 'blur' }]
})

// --- 4. 计算属性 ---
const filteredData = computed(() => {
  return shelvesList.value.filter(item => {
    const matchName = !filterName.value || item.shelfCode.includes(filterName.value)
    const matchStatus = !filterStatus.value || item.status === filterStatus.value
    return matchName && matchStatus
  })
})

const paginatedData = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return filteredData.value.slice(start, end)
})

// --- 5. 核心方法 ---

// 状态显示转换
const getStatusText = (status: string) => {
  const map: Record<string, string> = { A: '正常', B: '维护中', D: '停用' }
  return map[status] || '未知'
}
const getStatusTag = (status: string) => {
  if (status === 'A') return 'success'
  if (status === 'B') return 'warning'
  if (status === 'D') return 'info'
  return 'danger'
}

// 新增与编辑
const handleAdd = () => {
  form.id = undefined
  form.shelfCode = ''
  form.location = ''
  form.remark = ''
  form.warehouseCode = ''
  isEdit.value = false
  formVisible.value = true
}

const handleEdit = (row: any) => {
  form.id = row.id
  form.shelfCode = row.shelfCode
  form.location = row.location
  form.remark = row.remark
  form.warehouseCode = row.warehouseCode
  isEdit.value = true
  formVisible.value = true
}

// 保存逻辑
const handleSave = async () => {
  if (!formRef.value) return
  await formRef.value.validate((valid) => {
    if (valid) {
      saving.value = true
      // 模拟API请求
      setTimeout(() => {
        const warehouseMap: Record<string, string> = {
          '3101': '上海库',
          '3102': '上海退货库'
        }
        const selectedName = warehouseMap[form.warehouseCode] || '未知仓库'

        if (isEdit.value) {
          const index = shelvesList.value.findIndex(item => item.id === form.id)
          if (index !== -1) {
            shelvesList.value[index] = {
              ...shelvesList.value[index],
              ...form,
              warehouseName: selectedName
            }
            ElMessage.success('更新成功')
          }
        } else {
          const newId = shelvesList.value.length ? Math.max(...shelvesList.value.map(i => i.id!)) + 1 : 1
          shelvesList.value.unshift({
            id: newId,
            ...form,
            status: 'A',
            warehouseName: selectedName
          })
          ElMessage.success('新增成功')
        }
        formVisible.value = false
        saving.value = false
      }, 500)
    }
  })
}

// 删除逻辑
const handleDelete = (row: any) => {
  ElMessageBox.confirm(`确定要删除货架 "${row.shelfCode}" 吗？`, '警告', {
    type: 'warning',
    confirmButtonText: '确定',
    cancelButtonText: '取消'
  }).then(() => {
    const index = shelvesList.value.findIndex(item => item.id === row.id)
    if (index !== -1) {
      shelvesList.value.splice(index, 1)
      ElMessage.success('删除成功')
    }
  }).catch(() => {
    ElMessage.info('已取消删除')
  })
}

// 其他方法保持原样
const handleSearch = () => {
  currentPage.value = 1
}
const handleReset = () => {
  filterName.value = ''
  filterStatus.value = ''
}
const handleSizeChange = () => { }
const handleCurrentChange = (page: number) => { }
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

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
