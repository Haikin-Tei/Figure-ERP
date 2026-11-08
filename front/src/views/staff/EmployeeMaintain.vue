<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Delete, Edit, Key, Plus, Refresh, Search, UserFilled } from '@element-plus/icons-vue'
import {
  createUserApi,
  deleteUserApi,
  pageUsersApi,
  resetUserPasswordApi,
  updateUserApi,
  updateUserStatusApi,
  type UserQueryParams
} from '@/api/user'
import type { UserInfo } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { isAdminRole } from '@/utils/role'
import { formatTime, roleTagType, useUserDict } from './useUserDict'

const userStore = useUserStore()
const { roleGroups, statuses, loadDicts } = useUserDict()

/** 管理级（管理员 / 总经理）能改，主管和专员在这里是只读 */
const canManage = computed(() => isAdminRole(userStore.userInfo))

/* ---------------- 列表 ---------------- */
const loading = ref(false)
const list = ref<UserInfo[]>([])
const total = ref(0)

const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  role: '' as string,
  status: undefined as number | undefined
})

async function fetchList() {
  loading.value = true
  try {
    const params: UserQueryParams = {
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      role: query.role || undefined,
      status: query.status
    }
    const data = await pageUsersApi(params)
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
  query.role = ''
  query.status = undefined
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
  username: '',
  realName: '',
  password: '',
  // 不给默认值：员工录入时必须明确选择角色，避免默认落到某个角色上
  role: '',
  status: 1,
  intro: '',
  remark: ''
})

const rules = computed<FormRules>(() => ({
  username: [
    { required: true, message: '请输入登录账号', trigger: 'blur' },
    {
      pattern: /^[a-zA-Z0-9_]{3,20}$/,
      message: '账号只能由字母、数字、下划线组成，长度 3-20 位',
      trigger: 'blur'
    }
  ],
  realName: [{ required: true, message: '请输入员工姓名', trigger: 'blur' }],
  // 编辑时不改密码，密码走「重置密码」
  password: isEdit.value
    ? []
    : [
        { required: true, message: '请设置初始密码', trigger: 'blur' },
        { min: 6, max: 64, message: '密码长度需在 6-64 个字符之间', trigger: 'blur' }
      ],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }]
}))

function openCreate() {
  isEdit.value = false
  editingId.value = null
  Object.assign(form, {
    username: '',
    realName: '',
    password: '',
    role: '',
    status: 1,
    intro: '',
    remark: ''
  })
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

function openEdit(row: UserInfo) {
  isEdit.value = true
  editingId.value = row.id
  Object.assign(form, {
    username: row.username,
    realName: row.realName,
    password: '',
    role: row.role ?? '',
    status: row.status ?? 1,
    intro: row.intro ?? '',
    remark: row.remark ?? ''
  })
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

async function submitForm() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const payload = {
      username: form.username.trim(),
      realName: form.realName.trim(),
      password: form.password || undefined,
      role: form.role,
      status: form.status,
      intro: form.intro,
      remark: form.remark
    }

    if (isEdit.value && editingId.value) {
      await updateUserApi(editingId.value, payload)
      ElMessage.success('保存成功')
    } else {
      await createUserApi(payload)
      ElMessage.success('新增成功')
    }

    dialogVisible.value = false
    fetchList()
  } catch {
    // 错误提示由请求拦截器统一处理（如账号已存在、不能修改自己的角色）
  } finally {
    submitting.value = false
  }
}

/* ---------------- 重置密码 ---------------- */
const pwdVisible = ref(false)
const pwdTarget = ref<UserInfo | null>(null)
const pwdSubmitting = ref(false)
const pwdFormRef = ref<FormInstance>()
const pwdForm = reactive({ newPassword: '', confirmPassword: '' })

const pwdRules: FormRules = {
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 64, message: '密码长度需在 6-64 个字符之间', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== pwdForm.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

function openResetPassword(row: UserInfo) {
  pwdTarget.value = row
  pwdForm.newPassword = ''
  pwdForm.confirmPassword = ''
  pwdVisible.value = true
  pwdFormRef.value?.clearValidate()
}

async function submitResetPassword() {
  if (!pwdFormRef.value || !pwdTarget.value) return
  const valid = await pwdFormRef.value.validate().catch(() => false)
  if (!valid) return

  pwdSubmitting.value = true
  try {
    await resetUserPasswordApi(pwdTarget.value.id, pwdForm.newPassword)
    ElMessage.success(`已重置「${pwdTarget.value.realName}」的密码`)
    pwdVisible.value = false
  } catch {
    // 拦截器已提示
  } finally {
    pwdSubmitting.value = false
  }
}

/* ---------------- 启停用 / 删除 ---------------- */
async function toggleStatus(row: UserInfo) {
  const next = row.status === 1 ? 0 : 1
  const action = next === 1 ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(`确定要${action}「${row.realName}（${row.username}）」的账号吗？`, '提示', {
      type: 'warning'
    })
  } catch {
    return
  }

  try {
    await updateUserStatusApi(row.id, next)
    ElMessage.success(`已${action}`)
    fetchList()
  } catch {
    // 拦截器已提示
  }
}

async function removeUser(row: UserInfo) {
  try {
    await ElMessageBox.confirm(
      `确定删除员工「${row.realName}（${row.username}）」吗？删除后该账号无法登录，历史数据仍保留。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }

  try {
    await deleteUserApi(row.id)
    ElMessage.success('删除成功')
    // 删掉当前页最后一条时回退一页，避免停在空白页
    if (list.value.length === 1 && query.page > 1) {
      query.page -= 1
    }
    fetchList()
  } catch {
    // 拦截器已提示
  }
}

const isSelf = (row: UserInfo) => row.id === userStore.userInfo?.id

onMounted(() => {
  loadDicts()
  fetchList()
})
</script>

<template>
  <div class="page-container">
    <div class="card">
      <div class="card-head">
        <h2 class="page-title">
          <el-icon><UserFilled /></el-icon>
          员工维护
        </h2>
        <el-button v-if="canManage" type="primary" :icon="Plus" @click="openCreate">
          新增员工
        </el-button>
      </div>

      <el-alert
        v-if="!canManage"
        type="info"
        :closable="false"
        show-icon
        title="当前账号没有员工管理权限"
        description="只有管理员或总经理可以新增、编辑、删除员工。如需调整权限，请联系他们操作。"
        style="margin-bottom: 16px"
      />

      <!-- 筛选 -->
      <div class="filter-bar">
        <el-input
          v-model="query.keyword"
          placeholder="账号 / 姓名"
          clearable
          style="width: 200px"
          @keyup.enter="handleSearch"
        />
        <el-select v-model="query.role" placeholder="角色" clearable style="width: 150px">
          <el-option-group v-for="g in roleGroups" :key="g.label" :label="g.label">
            <el-option v-for="r in g.options" :key="r.code" :label="r.label" :value="r.code" />
          </el-option-group>
        </el-select>
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 110px">
          <el-option v-for="s in statuses" :key="s.code" :label="s.label" :value="s.code" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      </div>

      <!-- 表格 -->
      <el-table v-loading="loading" :data="list" stripe style="width: 100%">
        <el-table-column prop="username" label="登录账号" min-width="120" />
        <el-table-column prop="realName" label="员工姓名" min-width="110" />
        <el-table-column label="角色" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="roleTagType(row.role, row.roleLevel)" effect="plain">
              {{ row.roleLabel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最后登录" width="170">
          <template #default="{ row }">{{ formatTime(row.lastLoginTime) }}</template>
        </el-table-column>
        <el-table-column v-if="canManage" label="操作" width="290" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" :icon="Key" @click="openResetPassword(row)">
              重置密码
            </el-button>
            <el-button
              link
              :type="row.status === 1 ? 'warning' : 'success'"
              :disabled="isSelf(row)"
              @click="toggleStatus(row)"
            >
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button
              link
              type="danger"
              :icon="Delete"
              :disabled="isSelf(row)"
              @click="removeUser(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="没有符合条件的员工" :image-size="80" />
        </template>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="fetchList"
          @size-change="handleSearch"
        />
      </div>
    </div>

    <!-- 新增 / 编辑 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑员工' : '新增员工'"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
        <el-form-item label="登录账号" prop="username">
          <el-input
            v-model="form.username"
            :disabled="isEdit"
            placeholder="3-20 位字母、数字或下划线"
          />
          <div v-if="isEdit" class="form-tip">账号是登录身份，创建后不可修改</div>
        </el-form-item>

        <el-form-item label="员工姓名" prop="realName">
          <el-input v-model="form.realName" placeholder="请输入真实姓名" maxlength="50" />
        </el-form-item>

        <el-form-item v-if="!isEdit" label="初始密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="至少 6 位" />
        </el-form-item>

        <el-form-item label="角色" prop="role">
          <el-select
            v-model="form.role"
            placeholder="请选择角色（必填）"
            filterable
            popper-class="role-select-popper"
            style="width: 100%"
          >
            <el-option-group v-for="g in roleGroups" :key="g.label" :label="g.label">
              <el-option v-for="r in g.options" :key="r.code" :label="r.label" :value="r.code" />
            </el-option-group>
          </el-select>
          <div class="form-tip">
            管理级（管理员 / 总经理）：可维护员工账号；主管级：可维护业务数据；专员级：只读业务数据
          </div>
        </el-form-item>

        <el-form-item label="账号状态">
          <el-switch
            v-model="form.status"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="停用"
          />
        </el-form-item>

        <el-form-item label="员工介绍">
          <el-input
            v-model="form.intro"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit
            placeholder="岗位、负责范围等"
          />
        </el-form-item>

        <el-form-item label="备注">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit
            placeholder="选填"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码 -->
    <el-dialog v-model="pwdVisible" title="重置密码" width="420px" :close-on-click-modal="false">
      <p class="pwd-target">
        正在重置：<b>{{ pwdTarget?.realName }}</b>（{{ pwdTarget?.username }}）
      </p>
      <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="90px">
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="至少 6 位" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="pwdForm.confirmPassword"
            type="password"
            show-password
            placeholder="再次输入新密码"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="pwdSubmitting" @click="submitResetPassword">
          确定重置
        </el-button>
      </template>
    </el-dialog>
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

.form-tip {
  font-size: 12px;
  color: #a8abb2;
  line-height: 1.6;
  margin-top: 2px;
}

.pwd-target {
  margin: 0 0 16px;
  font-size: 13px;
  color: #606266;
}
</style>

<style>
/* 角色下拉有 7 个分组 12 项，Element 默认 274px 太矮，放宽到 420px 少滚动
   （popper 是 teleport 到 body 的，scoped 样式打不到，所以单独放一个全局块） */
.role-select-popper .el-select-dropdown__wrap {
  max-height: 420px;
}
</style>
