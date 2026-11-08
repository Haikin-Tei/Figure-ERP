<!-- 目录维护 → 厂商目录维护 -->
<template>
  <div class="page-container">
    <div class="card">
      <!-- 头部 -->
      <div class="card-head">
        <h2 class="page-title">
          <el-icon><OfficeBuilding /></el-icon>
          厂商目录维护
        </h2>
        <div class="head-actions">
          <el-button :icon="Refresh" :loading="loading" @click="refreshAll">刷新</el-button>
          <el-button v-if="canManage" type="primary" :icon="Plus" @click="openCreate">新增厂商</el-button>
        </div>
      </div>
      <p class="page-desc">
        维护手办 / 景品厂商主数据（厂牌 Logo、公司名称、创立时间、国家地区），供商品、入库等业务作为可选项；停用后档案保留但不再出现在下拉选项中。
      </p>

      <!-- 关键指标 -->
      <div class="kpi-row">
        <div class="kpi">
          <div class="kpi-label">厂商总数</div>
          <div class="kpi-value">{{ summary.total }}<span class="kpi-unit">家</span></div>
          <div class="kpi-sub">目录内全部厂商</div>
        </div>
        <div class="kpi">
          <div class="kpi-label">启用中</div>
          <div class="kpi-value tone-green">{{ summary.enabled }}<span class="kpi-unit">家</span></div>
          <div class="kpi-sub">可在业务下拉中选择</div>
        </div>
        <div class="kpi">
          <div class="kpi-label">已停用</div>
          <div class="kpi-value tone-red">{{ summary.disabled }}<span class="kpi-unit">家</span></div>
          <div class="kpi-sub">保留档案但不再可选</div>
        </div>
        <div class="kpi">
          <div class="kpi-label">覆盖国家 / 地区</div>
          <div class="kpi-value tone-blue">{{ summary.countryCount }}<span class="kpi-unit">个</span></div>
          <div class="kpi-sub">已录入厂商的分布</div>
        </div>
      </div>

      <!-- 筛选栏 -->
      <div class="filter-bar">
        <el-input
          v-model="query.keyword"
          placeholder="公司名称 / 国家地区"
          clearable
          style="width: 200px"
          :prefix-icon="Search"
          @keyup.enter="handleSearch"
        />
        <el-select v-model="query.country" placeholder="国家 / 地区" clearable style="width: 140px">
          <el-option v-for="c in countryOptions" :key="c" :label="c" :value="c" />
        </el-select>
        <el-select v-model="query.status" placeholder="启用状态" clearable style="width: 120px">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
        <el-select v-model="query.sortBy" style="width: 168px">
          <el-option label="最新创建" value="createDesc" />
          <el-option label="名称 A→Z" value="nameAsc" />
          <el-option label="创立时间 早→晚" value="foundedAsc" />
          <el-option label="创立时间 晚→早" value="foundedDesc" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      </div>

      <!-- 表格 -->
      <el-table v-loading="loading" :data="list" stripe border row-key="id" class="mfg-table">
        <el-table-column label="Logo" width="84">
          <template #default="{ row }">
            <el-image
              v-if="row.logo"
              :src="row.logo"
              :preview-src-list="[row.logo]"
              preview-teleported
              fit="contain"
              class="logo"
            />
            <div v-else class="logo logo-empty" :style="{ background: avatarColor(row.name) }">
              {{ row.name.slice(0, 1) }}
            </div>
          </template>
        </el-table-column>

        <el-table-column label="公司 / 厂牌" min-width="200">
          <template #default="{ row }">
            <div class="name-cell">
              <span class="w-name">{{ row.name }}</span>
              <span class="muted">
                {{ row.country || '未填国家地区' }}<template v-if="row.foundedDate"> · 创立于 {{ row.foundedDate.slice(0, 4) }}</template>
              </span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="国家 / 地区" width="116">
          <template #default="{ row }"><span>{{ row.country || '—' }}</span></template>
        </el-table-column>

        <el-table-column label="创立时间" width="120">
          <template #default="{ row }"><span>{{ row.foundedDate || '—' }}</span></template>
        </el-table-column>

        <el-table-column label="备注" min-width="180">
          <template #default="{ row }">
            <span class="muted ellipsis" :title="row.remark || ''">{{ row.remark || '—' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="84" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small" effect="light">
              {{ row.statusText }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="176" align="center" fixed="right">
          <template #default="{ row }">
            <template v-if="canManage">
              <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
              <el-button
                link
                :type="row.status === 1 ? 'warning' : 'success'"
                size="small"
                @click="toggleStatus(row)"
              >
                {{ row.status === 1 ? '停用' : '启用' }}
              </el-button>
              <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
            </template>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="fetchList"
        />
      </div>
    </div>

    <!-- 新增 / 编辑 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑厂商' : '新增厂商'"
      width="560px"
      destroy-on-close
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="公司名称" prop="name">
          <el-input v-model="form.name" maxlength="120" show-word-limit placeholder="如：万代 BANDAI、BANPRESTO 眼镜厂" />
        </el-form-item>

        <el-form-item label="厂牌 Logo">
          <div class="logo-uploader">
            <el-image
              v-if="form.logo"
              :src="form.logo"
              :preview-src-list="[form.logo]"
              preview-teleported
              fit="contain"
              class="logo-preview"
            />
            <div
              v-else
              class="logo-preview logo-empty"
              :style="{ background: form.name ? avatarColor(form.name) : '#f5f7fa' }"
            >
              {{ form.name ? form.name.slice(0, 1) : 'Logo' }}
            </div>
            <div class="logo-actions">
              <el-upload
                :show-file-list="false"
                :http-request="uploadLogo"
                :before-upload="beforeLogoUpload"
                accept="image/jpeg,image/png,image/gif,image/webp"
              >
                <el-button size="small" :loading="logoUploading">
                  {{ form.logo ? '更换 Logo' : '上传 Logo' }}
                </el-button>
              </el-upload>
              <el-button v-if="form.logo" size="small" text type="danger" @click="form.logo = ''">移除</el-button>
            </div>
            <div class="form-tip">JPG / PNG / WEBP，单张不超过 5MB</div>
          </div>
        </el-form-item>

        <el-form-item label="创立时间" prop="foundedDate">
          <el-date-picker
            v-model="form.foundedDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择创立日期"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="国家 / 地区" prop="country">
          <el-select
            v-model="form.country"
            filterable
            allow-create
            default-first-option
            clearable
            placeholder="选择或输入国家 / 地区"
            style="width: 100%"
          >
            <el-option v-for="c in countryOptions" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>

        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
          <div class="form-tip">停用后档案保留，但不再出现在业务下拉选项中。</div>
        </el-form-item>

        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="如：主打景品与扭蛋，万代南梦宫旗下"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { OfficeBuilding, Plus, Refresh, Search } from '@element-plus/icons-vue'
import axios from 'axios'
import {
  createManufacturerApi,
  deleteManufacturerApi,
  getManufacturerCountriesApi,
  getManufacturerSummaryApi,
  pageManufacturersApi,
  updateManufacturerApi,
  updateManufacturerStatusApi,
  type Manufacturer,
  type ManufacturerQueryParams,
  type ManufacturerSaveParams,
  type ManufacturerSummary
} from '@/api/manufacturer'
import { useUserStore } from '@/stores/user'
import { canManageBusiness } from '@/utils/role'
import { getToken } from '@/utils/auth'

const userStore = useUserStore()
/** 管理员和主管可以维护厂商，普通员工只读 */
const canManage = computed(() => canManageBusiness(userStore.userInfo))

/* ==================== 统计 ==================== */

const summary = ref<ManufacturerSummary>({ total: 0, enabled: 0, disabled: 0, countryCount: 0 })

/* ==================== 列表 ==================== */

const loading = ref(false)
const list = ref<Manufacturer[]>([])
const total = ref(0)

const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  country: '',
  status: undefined as number | undefined,
  sortBy: 'createDesc'
})

/** 国家/地区筛选与表单候选：预置常见项 + 已录入项，后续可自由输入 */
const PRESET_COUNTRIES = ['日本', '中国', '中国香港', '中国台湾', '中国澳门', '美国', '韩国', '英国', '法国']
const countryOptions = ref<string[]>([...PRESET_COUNTRIES])

async function fetchList() {
  loading.value = true
  try {
    const params: ManufacturerQueryParams = {
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      country: query.country || undefined,
      // el-select 清空后可能给到 ''，这里只接受数字，避免后端整型绑定报错
      status: typeof query.status === 'number' ? query.status : undefined,
      sortBy: query.sortBy
    }
    const data = await pageManufacturersApi(params)
    list.value = data.records
    total.value = data.total
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function fetchSummary() {
  try {
    summary.value = await getManufacturerSummaryApi()
  } catch {
    /* 统计失败不影响列表使用 */
  }
}

async function fetchCountries() {
  try {
    const data = await getManufacturerCountriesApi()
    countryOptions.value = Array.from(new Set([...PRESET_COUNTRIES, ...data]))
  } catch {
    countryOptions.value = [...PRESET_COUNTRIES]
  }
}

/** 刷新列表 + 统计 + 国家候选（增删改后统一调用） */
async function refreshAll() {
  await Promise.all([fetchList(), fetchSummary(), fetchCountries()])
}

function handleSearch() {
  query.page = 1
  fetchList()
}

function handleReset() {
  query.keyword = ''
  query.country = ''
  query.status = undefined
  query.sortBy = 'createDesc'
  query.page = 1
  fetchList()
}

function handleSizeChange(size: number) {
  query.size = size
  query.page = 1
  fetchList()
}

/* ==================== 新增 / 编辑 ==================== */

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const submitting = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  name: '',
  logo: '',
  foundedDate: null as string | null,
  country: '',
  remark: '',
  status: 1
})

const rules: FormRules = {
  name: [
    { required: true, message: '请填写公司名称', trigger: 'blur' },
    { max: 120, message: '公司名称最多 120 个字符', trigger: 'blur' }
  ],
  country: [{ max: 50, message: '国家地区最多 50 个字符', trigger: 'change' }],
  remark: [{ max: 500, message: '备注最多 500 个字符', trigger: 'blur' }]
}

function resetForm() {
  form.name = ''
  form.logo = ''
  form.foundedDate = null
  form.country = ''
  form.remark = ''
  form.status = 1
  editingId.value = null
  formRef.value?.clearValidate()
}

function openCreate() {
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: Manufacturer) {
  resetForm()
  editingId.value = row.id
  form.name = row.name
  form.logo = row.logo || ''
  form.foundedDate = row.foundedDate || null
  form.country = row.country || ''
  form.remark = row.remark || ''
  form.status = row.status
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  const payload: ManufacturerSaveParams = {
    name: form.name.trim(),
    logo: form.logo || '',
    foundedDate: form.foundedDate || null,
    country: form.country || '',
    remark: form.remark || '',
    status: form.status
  }

  submitting.value = true
  try {
    if (editingId.value) {
      await updateManufacturerApi(editingId.value, payload)
      ElMessage.success('保存成功')
    } else {
      await createManufacturerApi(payload)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    await refreshAll()
  } catch {
    /* 错误提示由请求拦截器统一弹出 */
  } finally {
    submitting.value = false
  }
}

/* ==================== 启用 / 停用 / 删除 ==================== */

async function toggleStatus(row: Manufacturer) {
  const next = row.status === 1 ? 0 : 1
  const action = next === 1 ? '启用' : '停用'
  const tip =
    next === 1
      ? `确定启用「${row.name}」？启用后会重新出现在业务下拉选项中。`
      : `确定停用「${row.name}」？停用后档案保留，但不再出现在业务下拉选项中。`
  try {
    await ElMessageBox.confirm(tip, `${action}厂商`, {
      type: 'warning',
      confirmButtonText: action,
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await updateManufacturerStatusApi(row.id, next)
    ElMessage.success(`已${action}`)
    await Promise.all([fetchList(), fetchSummary()])
  } catch {
    /* 拦截器已提示 */
  }
}

async function handleDelete(row: Manufacturer) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.name}」？删除后不可恢复，请谨慎操作。`, '删除厂商', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await deleteManufacturerApi(row.id)
    ElMessage.success('删除成功')
    // 删掉当前页最后一条时回退一页，避免停在空页
    if (list.value.length === 1 && query.page > 1) query.page -= 1
    await refreshAll()
  } catch {
    /* 拦截器已提示 */
  }
}

/* ==================== Logo 上传 ==================== */

const logoUploading = ref(false)

function beforeLogoUpload(file: File) {
  if (!file.type.startsWith('image/')) {
    ElMessage.error('只能上传图片文件')
    return false
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.error('图片不能超过 5MB')
    return false
  }
  return true
}

async function uploadLogo(options: any) {
  const fd = new FormData()
  fd.append('file', options.file)
  logoUploading.value = true
  try {
    const resp = await axios.post('/api/manufacturer/upload', fd, {
      headers: { Authorization: `Bearer ${getToken() ?? ''}` }
    })
    const body = resp.data
    if (body && body.code === 200) {
      form.logo = body.data
      options.onSuccess?.(body.data)
      ElMessage.success('Logo 上传成功')
    } else {
      options.onError?.(new Error(body?.message || '上传失败'))
      ElMessage.error(body?.message || 'Logo 上传失败')
    }
  } catch {
    options.onError?.(new Error('上传失败'))
    ElMessage.error('Logo 上传失败')
  } finally {
    logoUploading.value = false
  }
}

/** 无 Logo 时用名称首字 + 稳定底色兜底 */
const AVATAR_COLORS = ['#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#8b5cf6', '#14b8a6', '#f97316', '#0ea5e9']
function avatarColor(name: string) {
  let hash = 0
  for (let i = 0; i < name.length; i++) hash = (hash * 31 + name.charCodeAt(i)) >>> 0
  return AVATAR_COLORS[hash % AVATAR_COLORS.length]
}

onMounted(refreshAll)
</script>

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
  gap: 12px;
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

.head-actions {
  display: flex;
  gap: 8px;
}

.page-desc {
  margin: 8px 0 18px;
  font-size: 13px;
  color: #909399;
  line-height: 1.6;
}

/* 指标卡 */
.kpi-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(150px, 1fr));
  gap: 12px;
  margin-bottom: 18px;
}

.kpi {
  border-radius: 8px;
  padding: 14px 16px;
  border: 1px solid #f0f2f5;
  background: #fafcff;
}

.kpi-label {
  font-size: 12px;
  color: #909399;
}

.kpi-value {
  font-size: 24px;
  font-weight: 700;
  color: #303133;
  margin: 6px 0 4px;
  line-height: 1.1;
}

.kpi-value.tone-green {
  color: #67c23a;
}
.kpi-value.tone-red {
  color: #f56c6c;
}
.kpi-value.tone-blue {
  color: #409eff;
}

.kpi-unit {
  font-size: 12px;
  font-weight: 400;
  color: #909399;
  margin-left: 2px;
}

.kpi-sub {
  font-size: 11px;
  color: #a8abb2;
  line-height: 1.5;
}

/* 筛选栏 */
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 16px;
}

/* 表格 */
.mfg-table {
  width: 100%;
}

.logo {
  width: 44px;
  height: 44px;
  border-radius: 6px;
  border: 1px solid #ebeef5;
  overflow: hidden;
  background: #fff;
}

.logo-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 18px;
  font-weight: 600;
}

.name-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.w-name {
  font-weight: 500;
  color: #303133;
}

.muted {
  font-size: 12px;
  color: #909399;
}

.ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: block;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}

/* 表单 */
.logo-uploader {
  display: flex;
  align-items: flex-end;
  gap: 14px;
  flex-wrap: wrap;
}

.logo-preview {
  width: 64px;
  height: 64px;
  border-radius: 6px;
  border: 1px dashed #dcdfe6;
  overflow: hidden;
  background: #fff;
}

.logo-actions {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-tip {
  font-size: 12px;
  color: #a8abb2;
  line-height: 1.6;
  margin-top: 4px;
  width: 100%;
}

@media (max-width: 768px) {
  .kpi-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .filter-bar {
    gap: 8px;
  }
}
</style>
