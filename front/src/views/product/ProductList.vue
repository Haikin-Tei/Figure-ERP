<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules, type UploadUserFile, type UploadRawFile } from 'element-plus'
import { Delete, Edit, Plus, Refresh, Search, Goods, ScaleToOriginal } from '@element-plus/icons-vue'
import {
  createProductApi,
  deleteProductApi,
  pageProductsApi,
  updateProductApi,
  updateProductStatusApi,
  type FigureProduct,
  type FigureProductQueryParams,
  type FigureProductSaveParams
} from '@/api/product'
import { useUserStore } from '@/stores/user'
import { canManageBusiness } from '@/utils/role'
import { getToken } from '@/utils/auth'
import axios from 'axios'
import { formatTime } from '@/views/logistics/useLogisticsDict'

const userStore = useUserStore()

/** 管理员和主管可以维护商品，普通员工只读 */
const canManage = computed(() => canManageBusiness(userStore.userInfo))

/* ---------------- 列表 ---------------- */
const loading = ref(false)
const list = ref<FigureProduct[]>([])
const total = ref(0)

const query = reactive({
  page: 1,
  size: 10,
  name: '',
  code: '',
  brand: '',
  series: '',
  characterName: '',
  status: undefined as number | undefined
})

async function fetchList() {
  loading.value = true
  try {
    const params: FigureProductQueryParams = {
      page: query.page,
      size: query.size,
      name: query.name || undefined,
      code: query.code || undefined,
      brand: query.brand || undefined,
      series: query.series || undefined,
      characterName: query.characterName || undefined,
      status: query.status
    }
    const data = await pageProductsApi(params)
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
  query.name = ''
  query.code = ''
  query.brand = ''
  query.series = ''
  query.characterName = ''
  query.status = undefined
  query.page = 1
  fetchList()
}

function handlePageChange(page: number) {
  query.page = page
  fetchList()
}

function handleSizeChange(size: number) {
  query.size = size
  query.page = 1
  fetchList()
}

/* ---------------- 新增 / 编辑 ---------------- */
const dialogVisible = ref(false)
const isEdit = ref(false)
const editingId = ref<number | null>(null)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const fileList = ref<UploadUserFile[]>([])
/** 真正要落库的图片 URL 列表（与 el-upload 内部状态解耦，避免拿到 blob: 临时地址） */
const imageUrls = ref<string[]>([])

const form = reactive({
  name: '',
  code: '',
  brand: '',
  series: '',
  characterName: '',
  material: '',
  lengthCm: undefined as number | undefined,
  widthCm: undefined as number | undefined,
  heightCm: undefined as number | undefined,
  weightWithBox: undefined as number | undefined,
  status: 1,
  remark: ''
})

const rules: FormRules = {
  name: [{ required: true, message: '请填写商品名称', trigger: 'blur' }],
  weightWithBox: [{ required: true, message: '请填写带盒重量', trigger: 'blur' }]
}

/**
 * 用自定义上传替代 el-upload 的 action：自己发请求，token 取自 getToken()，
 * 与列表接口走同一套鉴权；axios 对 FormData 会自动设置 multipart 边界。
 * 成功时把后端返回的图片 URL 交给 el-upload，失败则提示并移除该文件。
 */
async function customUpload(options: any) {
  const fd = new FormData()
  fd.append('file', options.file)
  try {
    const resp = await axios.post('/api/product/upload', fd, {
      headers: { Authorization: `Bearer ${getToken() ?? ''}` }
    })
    const body = resp.data
    if (body && body.code === 200) {
      // 记录可落库的服务端 URL，并同步给 el-upload 用于展示
      imageUrls.value.push(body.data)
      options.file.response = body.data
      options.onSuccess(body.data)
    } else {
      options.onError(new Error(body?.message || '上传失败'))
      ElMessage.error(body?.message || '图片上传失败')
    }
  } catch (e: any) {
    options.onError(e)
    ElMessage.error(e?.response?.data?.message || '图片上传失败，请重试')
  }
}

function buildFileList(images?: string[] | null): UploadUserFile[] {
  return (images || []).map((url, i) => ({
    name: `图${i + 1}`,
    url,
    status: 'success',
    response: url
  }))
}

function openCreate() {
  isEdit.value = false
  editingId.value = null
  Object.assign(form, {
    name: '',
    code: '',
    brand: '',
    series: '',
    characterName: '',
    material: '',
    lengthCm: undefined,
    widthCm: undefined,
    heightCm: undefined,
    weightWithBox: undefined,
    status: 1,
    remark: ''
  })
  fileList.value = []
  imageUrls.value = []
  dialogVisible.value = true
}

function openEdit(row: FigureProduct) {
  isEdit.value = true
  editingId.value = row.id
  Object.assign(form, {
    name: row.name,
    code: row.code ?? '',
    brand: row.brand ?? '',
    series: row.series ?? '',
    characterName: row.characterName ?? '',
    material: row.material ?? '',
    lengthCm: row.lengthCm ?? undefined,
    widthCm: row.widthCm ?? undefined,
    heightCm: row.heightCm ?? undefined,
    weightWithBox: row.weightWithBox,
    status: row.status,
    remark: row.remark ?? ''
  })
  fileList.value = buildFileList(row.images)
  imageUrls.value = (row.images || []).filter((u) => u.startsWith('/uploads/'))
  dialogVisible.value = true
}

/** 真正要落库的图片 URL（3~4 张，来自 imageUrls） */
function collectImages(): string[] {
  return imageUrls.value.filter((u) => u.startsWith('/uploads/'))
}

function beforeUpload(raw: UploadRawFile): boolean {
  if (!raw.type?.startsWith('image/')) {
    ElMessage.error('只能上传图片文件')
    return false
  }
  if (raw.size / 1024 / 1024 > 5) {
    ElMessage.error('单张图片不能超过 5MB')
    return false
  }
  return true
}

function onExceed() {
  ElMessage.warning('最多上传 4 张商品图片')
}

/** 用户移除某张图片时，从落库列表中同步删除（用 URL 反查） */
function handleRemove(file: UploadUserFile) {
  const url = (file.response || file.url) as string | undefined
  if (url) {
    imageUrls.value = imageUrls.value.filter((u) => u !== url)
  }
}

async function submitForm() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  const images = collectImages()
  if (images.length < 3) {
    ElMessage.warning('请至少上传 3 张商品图片（最多 4 张）')
    return
  }

  submitting.value = true
  try {
    const payload: FigureProductSaveParams = {
      name: form.name.trim(),
      code: form.code.trim() || undefined,
      brand: form.brand.trim() || undefined,
      series: form.series.trim() || undefined,
      characterName: form.characterName.trim() || undefined,
      material: form.material.trim() || undefined,
      lengthCm: form.lengthCm,
      widthCm: form.widthCm,
      heightCm: form.heightCm,
      weightWithBox: form.weightWithBox as number,
      images,
      status: form.status,
      remark: form.remark.trim() || undefined
    }
    if (isEdit.value && editingId.value) {
      await updateProductApi(editingId.value, payload)
      ElMessage.success('保存成功')
    } else {
      await createProductApi(payload)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchList()
  } finally {
    submitting.value = false
  }
}

async function toggleStatus(row: FigureProduct) {
  const next = row.status === 1 ? 0 : 1
  try {
    await updateProductStatusApi(row.id, next)
    row.status = next
    ElMessage.success(next === 1 ? '已启用' : '已停用')
  } catch {
    // 拦截器已提示
  }
}

async function handleDelete(row: FigureProduct) {
  try {
    await ElMessageBox.confirm(
      `确定删除「${row.name}」这个商品吗？删除后列表不再显示（数据仍保留在库中）。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await deleteProductApi(row.id)
    ElMessage.success('已删除')
    if (list.value.length === 1 && query.page > 1) {
      query.page -= 1
    }
    fetchList()
  } catch {
    // 拦截器已提示
  }
}

onMounted(fetchList)
</script>

<template>
  <div class="page-container">
    <div class="card">
      <div class="card-head">
        <h2 class="page-title">
          <el-icon><Goods /></el-icon>
          商品维护 · 商品列表
        </h2>
        <div>
          <el-button type="primary" :disabled="!canManage" @click="openCreate">
            <el-icon><Plus /></el-icon>
            新增商品
          </el-button>
        </div>
      </div>

      <el-alert
        v-if="!canManage"
        type="info"
        show-icon
        :closable="false"
        style="margin-bottom: 16px"
        title="当前账号只能查看商品，录入或修改请联系管理员或主管"
      />

      <div class="filter-bar">
        <el-input v-model="query.name" placeholder="商品名称" clearable style="width: 160px" @keyup.enter="handleSearch" @clear="handleSearch" />
        <el-input v-model="query.code" placeholder="货号 / SKU" clearable style="width: 140px" @keyup.enter="handleSearch" @clear="handleSearch" />
        <el-input v-model="query.brand" placeholder="品牌" clearable style="width: 140px" @keyup.enter="handleSearch" @clear="handleSearch" />
        <el-input v-model="query.series" placeholder="系列" clearable style="width: 140px" @keyup.enter="handleSearch" @clear="handleSearch" />
        <el-input v-model="query.characterName" placeholder="角色名" clearable style="width: 140px" @keyup.enter="handleSearch" @clear="handleSearch" />
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 120px" @change="handleSearch">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>

        <el-button type="primary" @click="handleSearch">
          <el-icon><Search /></el-icon>
          查询
        </el-button>
        <el-button @click="handleReset">
          <el-icon><Refresh /></el-icon>
          重置
        </el-button>
      </div>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column label="图片" width="170">
          <template #default="{ row }">
            <div v-if="row.images && row.images.length" class="img-gallery">
              <el-image
                v-for="(url, i) in row.images.slice(0, 3)"
                :key="i"
                :src="url"
                :preview-src-list="row.images"
                :initial-index="i"
                preview-teleported
                fit="cover"
                class="thumb"
              />
              <span v-if="row.images.length > 3" class="more">+{{ row.images.length - 3 }}</span>
            </div>
            <span v-else class="no-img">暂无图片</span>
          </template>
        </el-table-column>
        <el-table-column label="商品名称" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="name-cell">
              <span class="pname">{{ row.name }}</span>
              <span v-if="row.code" class="pcode">{{ row.code }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="品牌 / 系列" width="160">
          <template #default="{ row }">
            <div class="sub-cell">
              <span>{{ row.brand || '—' }}</span>
              <span class="muted">{{ row.series || '—' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="规格（长×宽×高）" width="180">
          <template #default="{ row }">{{ row.specLabel || '—' }}</template>
        </el-table-column>
        <el-table-column label="带盒重量" width="130" align="center">
          <template #default="{ row }">
            <el-tooltip content="录入的重量包含外包装盒" placement="top">
              <span class="weight-tag">{{ row.weightWithBox }} <small>g</small></span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="170">
          <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="190" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="!canManage" @click="openEdit(row)">
              <el-icon><Edit /></el-icon>编辑
            </el-button>
            <el-button link type="primary" :disabled="!canManage" @click="toggleStatus(row)">
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button link type="danger" :disabled="!canManage" @click="handleDelete(row)">
              <el-icon><Delete /></el-icon>删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          :current-page="query.page"
          :page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </div>

    <!-- 新增 / 编辑 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑商品' : '新增商品'"
      width="720px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="商品名称" prop="name">
          <el-input v-model="form.name" placeholder="手办全称，如：初音未来 雪未来 1/7 手办" maxlength="200" show-word-limit />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="货号 / SKU">
              <el-input v-model="form.code" placeholder="选填，填写后不可重复" maxlength="50" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="品牌">
              <el-input v-model="form.brand" placeholder="如 Good Smile Company" maxlength="100" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="系列">
              <el-input v-model="form.series" placeholder="如 Scale / Nendoroid" maxlength="100" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="角色名">
              <el-input v-model="form.characterName" placeholder="如 初音未来" maxlength="100" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="材质">
          <el-input v-model="form.material" placeholder="如 PVC/ABS" maxlength="50" />
        </el-form-item>

        <el-form-item label="尺寸（cm）">
          <div class="inline-group">
            <span class="unit">长</span>
            <el-input-number v-model="form.lengthCm" :min="0" :precision="1" :step="0.5" :controls="false" style="width: 110px" />
            <span class="unit">宽</span>
            <el-input-number v-model="form.widthCm" :min="0" :precision="1" :step="0.5" :controls="false" style="width: 110px" />
            <span class="unit">高</span>
            <el-input-number v-model="form.heightCm" :min="0" :precision="1" :step="0.5" :controls="false" style="width: 110px" />
          </div>
        </el-form-item>

        <el-form-item label="带盒重量" prop="weightWithBox">
          <div class="inline-group">
            <el-input-number v-model="form.weightWithBox" :min="1" :step="50" :controls="true" style="width: 160px" />
            <span class="unit">g（含包装盒）</span>
          </div>
          <div class="form-tip">⚠️ 这里填写的是<strong>含外包装盒</strong>的重量，不是手办净重，用于物流计费与仓储估算</div>
        </el-form-item>

        <el-form-item label="商品图片">
          <div class="upload-wrap">
            <el-upload
              v-model:file-list="fileList"
              list-type="picture-card"
              :limit="4"
              :multiple="true"
              accept="image/*"
              :before-upload="beforeUpload"
              :on-exceed="onExceed"
              :on-remove="handleRemove"
              :http-request="customUpload"
            >
              <el-icon><Plus /></el-icon>
            </el-upload>
            <div class="form-tip">
              <el-icon><ScaleToOriginal /></el-icon>
              需上传 <strong>3~4 张</strong> 商品图片（JPG / PNG / GIF / WEBP，单张 ≤ 5MB），用于详情与展示
            </div>
          </div>
        </el-form-item>

        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit placeholder="规格差异、限定版说明等" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
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

.inline-group {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.unit {
  font-size: 13px;
  color: #606266;
}

.form-tip {
  font-size: 12px;
  color: #a8abb2;
  line-height: 1.6;
  margin-top: 6px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.weight-tag {
  font-weight: 600;
  color: #e6a23c;
  font-size: 15px;
}

.img-gallery {
  display: flex;
  align-items: center;
  gap: 4px;
}

.thumb {
  width: 44px;
  height: 44px;
  border-radius: 4px;
  overflow: hidden;
  border: 1px solid #ebeef5;
}

.more {
  font-size: 12px;
  color: #909399;
  background: #f4f4f5;
  border-radius: 4px;
  padding: 0 4px;
}

.no-img {
  font-size: 12px;
  color: #c0c4cc;
}

.name-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.pname {
  font-weight: 500;
  color: #303133;
}

.pcode {
  font-size: 12px;
  color: #909399;
}

.sub-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.muted {
  font-size: 12px;
  color: #909399;
}

.upload-wrap {
  width: 100%;
}
</style>
