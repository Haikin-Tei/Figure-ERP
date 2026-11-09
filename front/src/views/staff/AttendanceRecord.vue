<template>
  <div class="page-container">
    <!-- 1. 顶部统计 -->
    <el-row :gutter="16" class="stat-row">
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">本月考勤</div>
          <div class="stat-value">{{ summary.monthRecords ?? 0 }}</div>
          <div class="stat-tip">人 × 天</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">迟到</div>
          <div class="stat-value warn">{{ summary.lateCount ?? 0 }}</div>
          <div class="stat-tip">9:00 之后打卡</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">早退</div>
          <div class="stat-value warn">{{ summary.earlyCount ?? 0 }}</div>
          <div class="stat-tip">18:00 之前打卡</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">涉及员工</div>
          <div class="stat-value">{{ summary.staffCount ?? 0 }}</div>
          <div class="stat-tip">
            未打下班卡 {{ summary.missingCount ?? 0 }} 次
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 2. 筛选栏 -->
    <el-card shadow="never" class="filter-card">
      <div class="filter-bar">
        <el-input
          v-model="query.keyword"
          placeholder="员工姓名 / 登录账号"
          clearable
          style="width: 190px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />

        <el-select
          v-if="canOperateOthers"
          v-model="query.userId"
          placeholder="全部员工"
          clearable
          filterable
          style="width: 160px"
          @change="handleSearch"
        >
          <el-option
            v-for="u in employeeOptions"
            :key="u.id"
            :label="`${u.realName}（${u.roleLabel}）`"
            :value="u.id"
          />
        </el-select>

        <el-select
          v-model="query.checkInStatus"
          placeholder="上班状态"
          clearable
          style="width: 118px"
          @change="handleSearch"
        >
          <el-option v-for="s in dicts.checkInStatuses" :key="s.code" :label="s.label" :value="s.code" />
        </el-select>

        <el-select
          v-model="query.checkOutStatus"
          placeholder="下班状态"
          clearable
          style="width: 118px"
          @change="handleSearch"
        >
          <el-option v-for="s in dicts.checkOutStatuses" :key="s.code" :label="s.label" :value="s.code" />
        </el-select>

        <el-date-picker
          v-model="filterDateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="考勤开始日期"
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

        <el-button type="primary" :disabled="!canOperateOthers" @click="handleCreate">
          <el-icon><Plus /></el-icon>
          录入考勤
        </el-button>
      </div>
    </el-card>

    <el-alert
      v-if="!canOperateOthers"
      type="info"
      show-icon
      :closable="false"
      style="margin-bottom: 12px"
      title="当前账号只显示自己的考勤记录；为他人录入请联系主管或人事"
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
      <template #attendDate="scope">
        <span class="date-cell">{{ scope.row.attendDate }}</span>
        <span class="week-sub">{{ weekdayOf(scope.row.attendDate) }}</span>
      </template>

      <template #employee="scope">
        <div class="employee-cell">
          <span class="employee-name">{{ scope.row.userName }}</span>
          <el-tag size="small" type="info" effect="plain">{{ scope.row.roleLabel }}</el-tag>
        </div>
      </template>

      <template #checkIn="scope">
        <div class="time-cell">
          <span class="time-text">{{ clock(scope.row.checkInTime) }}</span>
          <el-tag size="small" :type="scope.row.checkInStatus === 'L' ? 'warning' : 'success'" effect="plain">
            {{ scope.row.checkInStatusText }}
          </el-tag>
        </div>
      </template>

      <template #checkOut="scope">
        <div class="time-cell">
          <span class="time-text">{{ clock(scope.row.checkOutTime) }}</span>
          <el-tag size="small" :type="outTagType(scope.row.checkOutStatus)" effect="plain">
            {{ scope.row.checkOutStatusText }}
          </el-tag>
        </div>
      </template>

      <template #workHours="scope">
        <span v-if="scope.row.workHours != null" class="hours">{{ scope.row.workHours }} 小时</span>
        <span v-else class="text-muted">—</span>
      </template>

      <template #remark="scope">
        <el-tooltip :content="scope.row.remark || '—'" placement="top" :show-after="400">
          <span class="remark-text">{{ scope.row.remark || '—' }}</span>
        </el-tooltip>
      </template>

      <template #action="scope">
        <el-button
          v-if="canEditRow(scope.row)"
          link
          type="primary"
          size="small"
          @click="handleEdit(scope.row)"
        >
          编辑
        </el-button>
        <el-button
          v-if="canEditRow(scope.row)"
          link
          type="danger"
          size="small"
          @click="handleDelete(scope.row)"
        >
          删除
        </el-button>
      </template>

      <template #empty>
        <el-empty description="没有符合条件的考勤记录" :image-size="80" />
      </template>
    </BaseTable>

    <!-- 4. 录入 / 编辑 -->
    <el-dialog
      v-model="formVisible"
      :title="isEdit ? '编辑考勤记录' : '录入考勤记录'"
      width="560px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="员工" prop="userId">
          <el-select
            v-model="form.userId"
            placeholder="请选择员工"
            filterable
            :disabled="isEdit || !canOperateOthers"
            style="width: 100%"
            @change="handleEmployeeChange"
          >
            <el-option
              v-for="u in employeeOptions"
              :key="u.id"
              :label="`${u.realName}（${u.roleLabel}）`"
              :value="u.id"
            />
          </el-select>
          <div v-if="isEdit" class="form-tip">编辑时不能更换员工，需要换人请删除后重新录入</div>
        </el-form-item>

        <el-form-item label="考勤日期" prop="attendDate">
          <el-date-picker
            v-model="form.attendDate"
            type="date"
            placeholder="请选择日期，不能晚于今天"
            value-format="YYYY-MM-DD"
            :disabled-date="isFutureDate"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="上班时间" prop="checkInClock">
          <el-time-picker
            v-model="form.checkInClock"
            placeholder="如 09:00"
            value-format="HH:mm:ss"
            format="HH:mm"
            :disabled="!form.attendDate"
            style="width: 100%"
          />
          <div class="form-tip">晚于 09:00 自动记为迟到</div>
        </el-form-item>

        <el-form-item label="下班时间">
          <el-time-picker
            v-model="form.checkOutClock"
            :placeholder="formExempt ? '销售岗免打下班卡' : '留空表示未打卡'"
            value-format="HH:mm:ss"
            format="HH:mm"
            :disabled="formExempt || !form.attendDate"
            clearable
            style="width: 100%"
          />
          <div v-if="formExempt" class="form-tip">
            销售岗只打上班卡，下班时间不记录，下班状态记为「免打卡」
          </div>
          <div v-else class="form-tip">
            早于 18:00 记早退；留空记「未打卡」
          </div>
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="form.remark" placeholder="如外勤、忘记打卡后补录" maxlength="255" show-word-limit />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
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
  createAttendanceApi,
  deleteAttendanceApi,
  getAttendanceDictsApi,
  getAttendanceSummaryApi,
  pageAttendancesApi,
  updateAttendanceApi,
  type AttendanceQueryParams,
  type AttendanceRecord,
  type AttendanceSaveParams,
  type AttendanceSummary
} from '@/api/attendance'
import { pageUsersApi } from '@/api/user'
import type { UserInfo } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { canManageBusiness } from '@/utils/role'

const userStore = useUserStore()

/** 主管级及以上，或人事岗：可以看全部员工、代录别人的考勤 */
const canOperateOthers = computed(() => {
  const user = userStore.userInfo
  return canManageBusiness(user) || (user?.role ?? '').startsWith('HR_')
})

/* ---------------- 展示格式化 ---------------- */

/** 后端返回 ISO-8601（2026-11-09T09:00:00），列表只显示时分 */
const clock = (value?: string | null) => (value ? value.slice(11, 16) : '—')

const WEEKDAYS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
const weekdayOf = (date?: string) => {
  if (!date) return ''
  const d = new Date(`${date}T00:00:00`)
  return Number.isNaN(d.getTime()) ? '' : WEEKDAYS[d.getDay()]
}

/** 下班状态标签颜色：正常绿、早退橙、未打卡红、免打卡灰 */
const outTagType = (status: string): 'success' | 'warning' | 'danger' | 'info' => {
  if (status === 'N') return 'success'
  if (status === 'E') return 'warning'
  if (status === 'M') return 'danger'
  return 'info'
}

/* ---------------- 字典与员工下拉 ---------------- */

const dicts = reactive<{ checkInStatuses: { code: string; label: string }[]; checkOutStatuses: { code: string; label: string }[] }>({
  checkInStatuses: [],
  checkOutStatuses: []
})

const employeeOptions = ref<UserInfo[]>([])

async function fetchOptions() {
  const [d, users] = await Promise.all([
    getAttendanceDictsApi().catch(() => null),
    pageUsersApi({ page: 1, size: 200, status: 1 }).catch(() => null)
  ])
  if (d) {
    dicts.checkInStatuses = d.checkInStatuses
    dicts.checkOutStatuses = d.checkOutStatuses
  }
  employeeOptions.value = users?.records ?? []
}

const roleOf = (userId?: number) => employeeOptions.value.find((u) => u.id === userId)?.role ?? ''

/** 销售岗（销售主管 / 销售专员）：只打上班卡 */
const isSalesRole = (role?: string) => role === 'SALES_MANAGER' || role === 'SALES_STAFF'

/* ---------------- 统计 ---------------- */

const summary = ref<AttendanceSummary>({
  monthRecords: 0,
  lateCount: 0,
  earlyCount: 0,
  missingCount: 0,
  staffCount: 0
})

async function fetchSummary() {
  try {
    summary.value = await getAttendanceSummaryApi()
  } catch {
    // 统计失败不影响列表，保留上一次的值
  }
}

/* ---------------- 列表 ---------------- */

const loading = ref(false)
const list = ref<AttendanceRecord[]>([])
const total = ref(0)
const filterDateRange = ref<[string, string] | null>(null)

const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  userId: undefined as number | undefined,
  checkInStatus: '',
  checkOutStatus: ''
})

const columns: Column[] = [
  { prop: 'attendDate', label: '考勤日期', width: 130 },
  { prop: 'employee', label: '员工', minWidth: 160 },
  { prop: 'checkIn', label: '上班', minWidth: 150 },
  { prop: 'checkOut', label: '下班', minWidth: 150 },
  { prop: 'workHours', label: '工时', width: 110 },
  { prop: 'remark', label: '备注', minWidth: 140 },
  { prop: 'action', label: '操作', width: 130, fixed: 'right' }
]

async function fetchList() {
  loading.value = true
  try {
    const params: AttendanceQueryParams = {
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      userId: query.userId,
      checkInStatus: query.checkInStatus || undefined,
      checkOutStatus: query.checkOutStatus || undefined,
      startDate: filterDateRange.value?.[0],
      endDate: filterDateRange.value?.[1]
    }
    const data = await pageAttendancesApi(params)
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
  query.userId = undefined
  query.checkInStatus = ''
  query.checkOutStatus = ''
  filterDateRange.value = null
  query.page = 1
  fetchList()
}

function handlePaginationChange({ page, limit }: { page: number; limit: number }) {
  query.page = page
  query.size = limit
  fetchList()
}

/** 本人可改自己的记录；改别人的需要主管 / 人事权限 */
function canEditRow(row: AttendanceRecord) {
  return canOperateOthers.value || row.userId === userStore.userInfo?.id
}

/* ---------------- 表单 ---------------- */

const formVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

interface AttendanceForm {
  userId: number | undefined
  attendDate: string
  /** 上班时分 HH:mm:ss，与考勤日期拼成完整时间后提交 */
  checkInClock: string
  /** 下班时分，销售岗为空 */
  checkOutClock: string
  remark: string
}

const form = reactive<AttendanceForm>({
  userId: undefined,
  attendDate: '',
  checkInClock: '09:00:00',
  checkOutClock: '18:00:00',
  remark: ''
})

/** 当前表单选中的员工是不是销售岗：销售岗隐藏并禁用下班时间 */
const formExempt = computed(() => isSalesRole(roleOf(form.userId)))

const rules: FormRules = {
  userId: [{ required: true, message: '请选择员工', trigger: 'change' }],
  attendDate: [{ required: true, message: '请选择考勤日期', trigger: 'change' }],
  checkInClock: [{ required: true, message: '请填写上班时间', trigger: 'change' }]
}

/** 不能录未来日期 */
const isFutureDate = (date: Date) => date.getTime() > Date.now()

function resetForm() {
  Object.assign(form, {
    userId: userStore.userInfo?.id,
    attendDate: today(),
    checkInClock: '09:00:00',
    checkOutClock: '18:00:00',
    remark: ''
  })
}

function today() {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

function handleCreate() {
  isEdit.value = false
  editingId.value = null
  resetForm()
  formVisible.value = true
}

function handleEdit(row: AttendanceRecord) {
  isEdit.value = true
  editingId.value = row.id
  Object.assign(form, {
    userId: row.userId,
    attendDate: row.attendDate,
    // 后端 ISO（2026-11-09T09:00:00）取时分部分
    checkInClock: row.checkInTime ? row.checkInTime.slice(11, 19) : '',
    checkOutClock: row.checkOutTime ? row.checkOutTime.slice(11, 19) : '',
    remark: row.remark ?? ''
  })
  formVisible.value = true
}

/** 选员工后如果是销售岗，把已填的下班时间清掉，避免提交后被后端忽略造成困惑 */
function handleEmployeeChange() {
  if (formExempt.value) {
    form.checkOutClock = ''
  }
}

async function handleSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  // 销售岗强制不提交下班时间，与后端「免打卡」口径保持一致
  const payload: AttendanceSaveParams = {
    userId: form.userId,
    attendDate: form.attendDate,
    checkInTime: `${form.attendDate}T${form.checkInClock}`,
    checkOutTime: formExempt.value || !form.checkOutClock
      ? null
      : `${form.attendDate}T${form.checkOutClock}`,
    remark: form.remark.trim() || undefined
  }

  submitting.value = true
  try {
    if (isEdit.value && editingId.value != null) {
      await updateAttendanceApi(editingId.value, payload)
      ElMessage.success('保存成功')
    } else {
      await createAttendanceApi(payload)
      ElMessage.success('录入成功')
    }
    formVisible.value = false
    fetchList()
    fetchSummary()
  } catch {
    // 请求层已提示（如「已有考勤记录」「下班时间必须晚于上班时间」）
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row: AttendanceRecord) {
  try {
    await ElMessageBox.confirm(
      `确定删除 ${row.userName} 在 ${row.attendDate} 的考勤记录吗？`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await deleteAttendanceApi(row.id)
    ElMessage.success('已删除')
    fetchList()
    fetchSummary()
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

.date-cell {
  display: block;
  color: #303133;
}

.week-sub {
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

.time-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.time-text {
  font-variant-numeric: tabular-nums;
  color: #303133;
}

.hours {
  font-variant-numeric: tabular-nums;
  color: #303133;
}

.remark-text {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #606266;
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
</style>
