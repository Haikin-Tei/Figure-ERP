<template>
  <div class="page-container">
    <!-- 1. 顶部统计 -->
    <el-row :gutter="16" class="stat-row">
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">待审批</div>
          <div class="stat-value warn">{{ summary.pendingCount ?? 0 }}</div>
          <div class="stat-tip">等待主管处理的申请</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">本月已通过</div>
          <div class="stat-value">{{ summary.monthApprovedCount ?? 0 }}</div>
          <div class="stat-tip">本月的请假单数</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">本月请假天数</div>
          <div class="stat-value">{{ summary.monthDays ?? 0 }}</div>
          <div class="stat-tip">已通过单据天数合计</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">涉及员工</div>
          <div class="stat-value">{{ summary.monthStaffCount ?? 0 }}</div>
          <div class="stat-tip">本月请假的人数</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 2. 筛选栏 -->
    <el-card shadow="never" class="filter-card">
      <div class="filter-bar">
        <el-input
          v-model="query.keyword"
          placeholder="单号 / 姓名 / 事由"
          clearable
          style="width: 185px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />

        <el-select
          v-model="query.leaveType"
          placeholder="请假类型"
          clearable
          style="width: 120px"
          @change="handleSearch"
        >
          <el-option v-for="t in dicts.leaveTypes" :key="t.code" :label="t.label" :value="t.code" />
        </el-select>

        <el-select
          v-model="query.status"
          placeholder="单据状态"
          clearable
          style="width: 118px"
          @change="handleSearch"
        >
          <el-option v-for="s in dicts.statuses" :key="s.code" :label="s.label" :value="s.code" />
        </el-select>

        <el-date-picker
          v-model="filterDateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="请假开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          style="width: 235px"
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

        <el-button type="primary" @click="handleCreate">
          <el-icon><Plus /></el-icon>
          提交请假申请
        </el-button>
      </div>
    </el-card>

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
      <template #applyNo="scope">
        <div class="no-cell">
          <span class="apply-no">{{ scope.row.applyNo }}</span>
          <span class="time-sub">提交于 {{ formatDateTime(scope.row.createTime) }}</span>
        </div>
      </template>

      <template #employee="scope">
        <div class="employee-cell">
          <span class="employee-name">{{ scope.row.userName }}</span>
          <el-tag size="small" type="info" effect="plain">{{ scope.row.roleLabel }}</el-tag>
        </div>
      </template>

      <template #leaveType="scope">
        <el-tag size="small" type="primary" effect="plain">{{ scope.row.leaveTypeText }}</el-tag>
      </template>

      <template #range="scope">
        <div class="range-cell">
          <span class="range-text">{{ formatDateTime(scope.row.startTime) }}</span>
          <span class="range-arrow">→</span>
          <span class="range-text">{{ formatDateTime(scope.row.endTime) }}</span>
        </div>
      </template>

      <template #durationDays="scope">
        <span class="days">{{ scope.row.durationDays }}<span class="unit"> 天</span></span>
      </template>

      <template #reason="scope">
        <el-tooltip :content="scope.row.reason" placement="top" :show-after="400">
          <span class="reason-text">{{ scope.row.reason }}</span>
        </el-tooltip>
      </template>

      <template #status="scope">
        <el-tag size="small" :type="statusTagType(scope.row.status)">
          {{ scope.row.statusText }}
        </el-tag>
      </template>

      <template #approve="scope">
        <div v-if="scope.row.approverName" class="approve-cell">
          <span class="approve-name">{{ scope.row.approverName }}</span>
          <el-tooltip
            v-if="scope.row.approveRemark"
            :content="scope.row.approveRemark"
            placement="top"
            :show-after="400"
          >
            <span class="approve-remark">{{ scope.row.approveRemark }}</span>
          </el-tooltip>
        </div>
        <span v-else class="text-muted">—</span>
      </template>

      <template #action="scope">
        <el-button
          v-if="scope.row.canApprove"
          link
          type="primary"
          size="small"
          @click="handleApprove(scope.row)"
        >
          审批
        </el-button>
        <el-button
          v-if="scope.row.canEdit"
          link
          type="primary"
          size="small"
          @click="handleEdit(scope.row)"
        >
          编辑
        </el-button>
        <el-button
          v-if="scope.row.canCancel"
          link
          type="warning"
          size="small"
          @click="handleCancel(scope.row)"
        >
          撤销
        </el-button>
        <el-button
          v-if="scope.row.canDelete"
          link
          type="danger"
          size="small"
          @click="handleDelete(scope.row)"
        >
          删除
        </el-button>
      </template>

      <template #empty>
        <el-empty description="没有符合条件的请假申请" :image-size="80" />
      </template>
    </BaseTable>

    <!-- 4. 提交 / 编辑 -->
    <el-dialog
      v-model="formVisible"
      :title="isEdit ? '编辑请假申请' : '提交请假申请'"
      width="600px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="申请人" prop="userId">
          <el-select
            v-model="form.userId"
            placeholder="请选择申请人"
            filterable
            :disabled="isEdit || !canOperateOthers"
            style="width: 100%"
          >
            <el-option
              v-for="u in employeeOptions"
              :key="u.id"
              :label="`${u.realName}（${u.roleLabel}）`"
              :value="u.id"
            />
          </el-select>
          <div v-if="isEdit" class="form-tip">编辑时不能更换申请人</div>
          <div v-else-if="!canOperateOthers" class="form-tip">只能为自己提交申请</div>
        </el-form-item>

        <el-form-item label="请假类型" prop="leaveType">
          <el-select v-model="form.leaveType" placeholder="请选择类型" style="width: 100%">
            <el-option v-for="t in dicts.leaveTypes" :key="t.code" :label="t.label" :value="t.code" />
          </el-select>
        </el-form-item>

        <el-form-item label="请假时间" prop="startTime">
          <div class="dt-range">
            <el-date-picker
              v-model="form.startTime"
              type="datetime"
              placeholder="开始时间"
              value-format="YYYY-MM-DD HH:mm:ss"
              format="YYYY-MM-DD HH:mm"
              style="width: 215px"
            />
            <span class="dt-sep">至</span>
            <el-date-picker
              v-model="form.endTime"
              type="datetime"
              placeholder="结束时间"
              value-format="YYYY-MM-DD HH:mm:ss"
              format="YYYY-MM-DD HH:mm"
              style="width: 215px"
            />
          </div>
          <div class="form-tip">
            请假天数由系统计算：跨天按含首尾的自然日算；同一天超过 4 小时算 1 天，否则算 0.5 天
          </div>
        </el-form-item>

        <el-form-item label="请假事由" prop="reason">
          <el-input
            v-model="form.reason"
            type="textarea"
            :rows="3"
            placeholder="请说明请假原因，便于主管判断"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          {{ isEdit ? '保存' : '提交' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 5. 审批 -->
    <el-dialog v-model="approveVisible" title="审批请假申请" width="520px" :close-on-click-modal="false">
      <el-descriptions :column="1" border size="small" class="approve-detail">
        <el-descriptions-item label="单号">{{ current?.applyNo }}</el-descriptions-item>
        <el-descriptions-item label="申请人">
          {{ current?.userName }}（{{ current?.roleLabel }}）
        </el-descriptions-item>
        <el-descriptions-item label="请假类型">{{ current?.leaveTypeText }}</el-descriptions-item>
        <el-descriptions-item label="请假时间">
          {{ formatDateTime(current?.startTime) }} 至 {{ formatDateTime(current?.endTime) }}，共
          {{ current?.durationDays }} 天
        </el-descriptions-item>
        <el-descriptions-item label="请假事由">{{ current?.reason }}</el-descriptions-item>
      </el-descriptions>

      <el-form :model="approveForm" label-width="80px" style="margin-top: 16px">
        <el-form-item label="审批结果">
          <el-radio-group v-model="approveForm.result">
            <el-radio-button value="A">通过</el-radio-button>
            <el-radio-button value="R">驳回</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审批意见">
          <el-input
            v-model="approveForm.remark"
            type="textarea"
            :rows="3"
            :placeholder="approveForm.result === 'R' ? '驳回时必须填写原因' : '选填'"
            maxlength="255"
            show-word-limit
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="approveVisible = false">取消</el-button>
        <el-button type="primary" :loading="approving" @click="submitApprove">确认提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Plus, RefreshLeft, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import BaseTable from '@/components/common/BaseTable.vue'
import type { Column } from '@/types/common'
import {
  approveLeaveApi,
  cancelLeaveApi,
  createLeaveApi,
  deleteLeaveApi,
  getLeaveDictsApi,
  getLeaveSummaryApi,
  pageLeavesApi,
  updateLeaveApi,
  type LeaveApply,
  type LeaveQueryParams,
  type LeaveSaveParams,
  type LeaveSummary
} from '@/api/leave'
import { pageUsersApi } from '@/api/user'
import type { UserInfo } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { canManageBusiness } from '@/utils/role'

const userStore = useUserStore()

/** 主管级及以上，或人事岗：可以看全部申请、代人提交 */
const canOperateOthers = computed(() => {
  const user = userStore.userInfo
  return canManageBusiness(user) || (user?.role ?? '').startsWith('HR_')
})

/* ---------------- 展示格式化 ---------------- */

/** ISO → 2026-11-10 09:00 */
const formatDateTime = (value?: string | null) => {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 16)
}

const statusTagType = (status?: string): 'success' | 'warning' | 'danger' | 'info' => {
  if (status === 'A') return 'success'
  if (status === 'P') return 'warning'
  if (status === 'R') return 'danger'
  return 'info'
}

/* ---------------- 字典与员工下拉 ---------------- */

const dicts = reactive<{
  leaveTypes: { code: string; label: string }[]
  statuses: { code: string; label: string }[]
}>({ leaveTypes: [], statuses: [] })

const employeeOptions = ref<UserInfo[]>([])

async function fetchOptions() {
  const [d, users] = await Promise.all([
    getLeaveDictsApi().catch(() => null),
    pageUsersApi({ page: 1, size: 200, status: 1 }).catch(() => null)
  ])
  if (d) {
    dicts.leaveTypes = d.leaveTypes
    dicts.statuses = d.statuses
  }
  employeeOptions.value = users?.records ?? []
}

/* ---------------- 统计 ---------------- */

const summary = ref<LeaveSummary>({
  pendingCount: 0,
  monthApprovedCount: 0,
  monthDays: 0,
  monthStaffCount: 0
})

async function fetchSummary() {
  try {
    summary.value = await getLeaveSummaryApi()
  } catch {
    // 统计失败不影响列表
  }
}

/* ---------------- 列表 ---------------- */

const loading = ref(false)
const list = ref<LeaveApply[]>([])
const total = ref(0)
const filterDateRange = ref<[string, string] | null>(null)

const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  leaveType: '',
  status: ''
})

const columns: Column[] = [
  { prop: 'applyNo', label: '单号', minWidth: 190 },
  { prop: 'employee', label: '申请人', minWidth: 150 },
  { prop: 'leaveType', label: '类型', width: 100 },
  { prop: 'range', label: '请假时间', minWidth: 210 },
  { prop: 'durationDays', label: '天数', width: 90 },
  { prop: 'reason', label: '事由', minWidth: 180 },
  { prop: 'status', label: '状态', width: 100 },
  { prop: 'approve', label: '审批', minWidth: 160 },
  { prop: 'action', label: '操作', width: 200, fixed: 'right' }
]

async function fetchList() {
  loading.value = true
  try {
    const params: LeaveQueryParams = {
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      leaveType: query.leaveType || undefined,
      status: query.status || undefined,
      startDate: filterDateRange.value?.[0],
      endDate: filterDateRange.value?.[1]
    }
    const data = await pageLeavesApi(params)
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
  query.keyword = ''
  query.leaveType = ''
  query.status = ''
  filterDateRange.value = null
  query.page = 1
  fetchList()
}

function handlePaginationChange({ page, limit }: { page: number; limit: number }) {
  query.page = page
  query.size = limit
  fetchList()
}

/* ---------------- 表单 ---------------- */

const formVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

interface LeaveForm {
  userId: number | undefined
  leaveType: string
  startTime: string
  endTime: string
  reason: string
}

const form = reactive<LeaveForm>({
  userId: undefined,
  leaveType: '',
  startTime: '',
  endTime: '',
  reason: ''
})

const rules: FormRules = {
  userId: [{ required: true, message: '请选择申请人', trigger: 'change' }],
  leaveType: [{ required: true, message: '请选择请假类型', trigger: 'change' }],
  startTime: [{ required: true, message: '请选择请假开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择请假结束时间', trigger: 'change' }],
  reason: [{ required: true, message: '请填写请假事由', trigger: 'blur' }]
}

function handleCreate() {
  isEdit.value = false
  editingId.value = null
  Object.assign(form, {
    userId: userStore.userInfo?.id,
    leaveType: '',
    startTime: '',
    endTime: '',
    reason: ''
  })
  formVisible.value = true
}

function handleEdit(row: LeaveApply) {
  isEdit.value = true
  editingId.value = row.id
  Object.assign(form, {
    userId: row.userId,
    leaveType: row.leaveType,
    startTime: toPicker(row.startTime),
    endTime: toPicker(row.endTime),
    reason: row.reason
  })
  formVisible.value = true
}

/** 后端 ISO（2026-11-10T09:00:00）→ picker 的空格格式 */
const toPicker = (value?: string | null) => (value ? value.replace('T', ' ').slice(0, 19) : '')

/** picker 的空格格式 → 后端 ISO */
const toIso = (value: string) => value.replace(' ', 'T')

async function handleSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  if (form.endTime <= form.startTime) {
    ElMessage.warning('请假结束时间必须晚于开始时间')
    return
  }

  const payload: LeaveSaveParams = {
    userId: form.userId,
    leaveType: form.leaveType,
    startTime: toIso(form.startTime),
    endTime: toIso(form.endTime),
    reason: form.reason.trim()
  }

  submitting.value = true
  try {
    if (isEdit.value && editingId.value != null) {
      await updateLeaveApi(editingId.value, payload)
      ElMessage.success('保存成功')
    } else {
      await createLeaveApi(payload)
      ElMessage.success('提交成功，等待主管审批')
    }
    formVisible.value = false
    fetchList()
    fetchSummary()
  } catch {
    // 请求层已提示
  } finally {
    submitting.value = false
  }
}

/* ---------------- 撤销 / 删除 / 审批 ---------------- */

async function handleCancel(row: LeaveApply) {
  try {
    await ElMessageBox.confirm(`确定撤销请假单 ${row.applyNo} 吗？撤销后需要重新提交。`, '撤销确认', {
      type: 'warning',
      confirmButtonText: '撤销',
      cancelButtonText: '返回'
    })
  } catch {
    return
  }
  try {
    await cancelLeaveApi(row.id)
    ElMessage.success('已撤销')
    fetchList()
    fetchSummary()
  } catch {
    // 请求层已提示
  }
}

async function handleDelete(row: LeaveApply) {
  try {
    await ElMessageBox.confirm(`确定删除请假单 ${row.applyNo} 吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await deleteLeaveApi(row.id)
    ElMessage.success('已删除')
    fetchList()
    fetchSummary()
  } catch {
    // 请求层已提示
  }
}

const approveVisible = ref(false)
const approving = ref(false)
const current = ref<LeaveApply | null>(null)
const approveForm = reactive({ result: 'A', remark: '' })

function handleApprove(row: LeaveApply) {
  current.value = row
  approveForm.result = 'A'
  approveForm.remark = ''
  approveVisible.value = true
}

async function submitApprove() {
  if (!current.value) return
  if (approveForm.result === 'R' && !approveForm.remark.trim()) {
    ElMessage.warning('驳回时请填写驳回原因')
    return
  }
  approving.value = true
  try {
    await approveLeaveApi(current.value.id, {
      result: approveForm.result,
      remark: approveForm.remark.trim() || undefined
    })
    ElMessage.success(approveForm.result === 'A' ? '已通过' : '已驳回')
    approveVisible.value = false
    fetchList()
    fetchSummary()
  } catch {
    // 请求层已提示（如「不能审批自己提交的请假申请」）
  } finally {
    approving.value = false
  }
}

onMounted(async () => {
  await fetchOptions()
  await Promise.all([fetchList(), fetchSummary()])
})
</script>

<style scoped>
.page-container {
  padding: 20px;
}

.stat-row {
  margin-bottom: 16px;
}

.stat-card {
  border-radius: 8px;
  border: 1px solid #ebeef5;
}

.stat-label {
  font-size: 13px;
  color: #909399;
}

.stat-value {
  font-size: 26px;
  font-weight: 600;
  color: #303133;
  margin: 6px 0 2px;
  line-height: 1.2;
}

.stat-value.warn {
  color: #e6a23c;
}

.stat-tip {
  font-size: 12px;
  color: #a8abb2;
}

.filter-card {
  margin-bottom: 16px;
  border-radius: 8px;
  border: 1px solid #ebeef5;
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.flex-grow {
  flex-grow: 1;
}

.no-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.apply-no {
  color: #303133;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}

.time-sub {
  font-size: 12px;
  color: #909399;
}

.employee-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.employee-name {
  color: #303133;
  font-weight: 500;
}

.range-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  font-variant-numeric: tabular-nums;
}

.range-text {
  color: #303133;
}

.range-arrow {
  color: #c0c4cc;
}

.days {
  font-variant-numeric: tabular-nums;
  color: #303133;
  font-weight: 500;
}

.unit {
  font-size: 12px;
  color: #909399;
  font-weight: 400;
}

.reason-text {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #606266;
}

.approve-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.approve-name {
  color: #303133;
}

.approve-remark {
  font-size: 12px;
  color: #909399;
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.text-muted {
  color: #c0c4cc;
}

.form-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
  margin-top: 2px;
}

.dt-range {
  display: flex;
  align-items: center;
  gap: 8px;
}

.dt-sep {
  color: #909399;
}

.approve-detail {
  margin-bottom: 4px;
}
</style>
