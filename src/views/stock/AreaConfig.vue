<template>
  <div class="area-config-container">
    <!-- 顶部搜索与操作栏 -->
    <el-card class="header-card" shadow="never">
      <div class="header-content">
        <div class="search-group">
          <el-input
            v-model="searchQuery"
            placeholder="搜索区域名称..."
            style="width: 200px"
            clearable
            @clear="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>
            搜索
          </el-button>
        </div>
        <el-button type="primary" @click="handleOpenDialog('add')">
          <el-icon><Plus /></el-icon>
          新增区域
        </el-button>
      </div>
    </el-card>

    <!-- 主体内容：区域列表 -->
    <el-card class="table-card" shadow="never">
      <el-table :data="filteredTableData" style="width: 100%" v-loading="loading">
        <el-table-column prop="id" label="区域ID" width="100" />
        <el-table-column prop="name" label="区域名称" min-width="150">
          <template #default="{ row }">
            <span class="area-name-tag">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="location" label="物理位置" min-width="150" />
        <el-table-column prop="capacity" label="容量/格子数" width="120">
          <template #default="{ row }">
            <el-tag type="info" effect="plain">{{ row.capacity }} 个</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'danger'" size="small">
              {{ row.status === 'active' ? '使用中' : '已停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleOpenDialog('edit', row)">
              编辑
            </el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑 对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogType === 'add' ? '新增区域' : '编辑区域'"
      width="500px"
      @close="handleDialogClose"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="100px"
      >
        <el-form-item label="区域名称" prop="name">
          <el-input v-model="formData.name" placeholder="例如：A区-展示柜" />
        </el-form-item>
        <el-form-item label="物理位置" prop="location">
          <el-input v-model="formData.location" placeholder="例如：客厅靠窗" />
        </el-form-item>
        <el-form-item label="容量(格子)" prop="capacity">
          <el-input-number v-model="formData.capacity" :min="1" :max="100" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-switch
            v-model="statusSwitch"
            active-value="active"
            inactive-value="inactive"
            active-text="使用中"
            inactive-text="已停用"
          />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="formData.remark"
            type="textarea"
            :rows="3"
            placeholder="请输入备注信息"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSubmit" :loading="submitLoading">
            确认
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'

// --- 类型定义 ---
interface AreaItem {
  id: string
  name: string
  location: string
  capacity: number
  status: 'active' | 'inactive'
  remark: string
}

// --- 状态数据 ---
const loading = ref(false)
const searchQuery = ref('')
const dialogVisible = ref(false)
const dialogType = ref<'add' | 'edit'>('add')
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const statusSwitch = ref('active') // 用于 switch 组件的双向绑定

// 模拟表格数据
const tableData = ref<AreaItem[]>([
  { id: '1001', name: 'A区-核心展示柜', location: '客厅-主墙面', capacity: 20, status: 'active', remark: '放置热门款' },
  { id: '1002', name: 'B区-收纳箱', location: '储藏室', capacity: 50, status: 'active', remark: '未拆封库存' },
  { id: '1003', name: 'C区-办公桌', location: '书房', capacity: 5, status: 'inactive', remark: '临时摆放' },
])

// 表单数据
const formData = reactive({
  name: '',
  location: '',
  capacity: 10,
  status: 'active' as 'active' | 'inactive',
  remark: ''
})

// 表单校验规则
const formRules: FormRules = {
  name: [{ required: true, message: '请输入区域名称', trigger: 'blur' }],
  location: [{ required: true, message: '请输入物理位置', trigger: 'blur' }],
  capacity: [{ required: true, message: '请输入容量', trigger: 'blur' }]
}

// --- 计算属性 ---
// 前端简单过滤搜索
const filteredTableData = computed(() => {
  if (!searchQuery.value) return tableData.value
  return tableData.value.filter(item =>
    item.name.includes(searchQuery.value) ||
    item.location.includes(searchQuery.value)
  )
})

// --- 方法 ---

// 搜索
const handleSearch = () => {
  // 实际项目中这里可能会触发后端搜索 API
  ElMessage.success('搜索完成')
}

// 打开弹窗
const handleOpenDialog = (type: 'add' | 'edit', row?: AreaItem) => {
  dialogType.value = type
  dialogVisible.value = true

  if (type === 'edit' && row) {
    // 回填数据
    Object.assign(formData, row)
    statusSwitch.value = row.status
  }
}

// 关闭弹窗重置表单
const handleDialogClose = () => {
  formRef.value?.resetFields()
  statusSwitch.value = 'active'
}

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate((valid) => {
    if (valid) {
      submitLoading.value = true
      // 模拟异步请求
      setTimeout(() => {
        submitLoading.value = false
        dialogVisible.value = false

        if (dialogType.value === 'add') {
          // 模拟新增
          const newItem: AreaItem = {
            id: Math.floor(Math.random() * 10000).toString(),
            ...formData
          }
          tableData.value.unshift(newItem)
          ElMessage.success('新增成功')
        } else {
          // 模拟编辑
          const index = tableData.value.findIndex(item => item.id === formData.id)
          if (index !== -1) {
            tableData.value[index] = { ...formData }
            ElMessage.success('更新成功')
          }
        }
      }, 800)
    }
  })
}

// 删除
const handleDelete = (row: AreaItem) => {
  ElMessageBox.confirm(
    `确定要删除区域 "${row.name}" 吗？`,
    '警告',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    }
  ).then(() => {
    const index = tableData.value.findIndex(item => item.id === row.id)
    if (index !== -1) {
      tableData.value.splice(index, 1)
      ElMessage.success('删除成功')
    }
  }).catch(() => {})
}
</script>

<style scoped>
.area-config-container {
  padding: 20px;
  background-color: #f0f2f5;
  min-height: calc(100vh - 88px);
}

.header-card {
  margin-bottom: 20px;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.search-group {
  display: flex;
  gap: 10px;
}

.area-name-tag {
  font-weight: 500;
  color: #409EFF;
}

/* 对话框微调 */
:deep(.el-dialog__footer) {
  border-top: 1px solid #f0f0f0;
  padding-top: 10px;
}
</style>
