<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Refresh, Search, UserFilled, View } from '@element-plus/icons-vue'
import { pageUsersApi, type UserQueryParams } from '@/api/user'
import type { UserInfo } from '@/api/auth'
import { formatTime, roleTagType, useUserDict } from './useUserDict'

const { roleGroups, statuses, loadDicts } = useUserDict()

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

/* 详情弹窗：列表接口已经把全部字段带回来了，直接用行数据，不再发请求 */
const detailVisible = ref(false)
const detail = ref<UserInfo | null>(null)

function openDetail(row: UserInfo) {
  detail.value = row
  detailVisible.value = true
}

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
          员工列表
        </h2>
        <span class="sub">共 {{ total }} 名员工</span>
      </div>

      <!-- 筛选条件 -->
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
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="View" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="没有符合条件的员工" :image-size="80" />
        </template>
      </el-table>

      <!-- 分页 -->
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

    <!-- 详情 -->
    <el-dialog v-model="detailVisible" title="员工详情" width="580px">
      <el-descriptions v-if="detail" :column="2" border>
        <el-descriptions-item label="登录账号">{{ detail.username }}</el-descriptions-item>
        <el-descriptions-item label="员工姓名">{{ detail.realName }}</el-descriptions-item>
        <el-descriptions-item label="角色">
          <el-tag size="small" :type="roleTagType(detail.role, detail.roleLevel)" effect="plain">
            {{ detail.roleLabel }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="账号状态">
          <el-tag size="small" :type="detail.status === 1 ? 'success' : 'info'">
            {{ detail.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="最后登录">
          {{ formatTime(detail.lastLoginTime) }}
        </el-descriptions-item>
        <el-descriptions-item label="员工介绍" :span="2">
          <span class="pre-wrap">{{ detail.intro || '—' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">
          <span class="pre-wrap">{{ detail.remark || '—' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间" :span="2">
          {{ formatTime(detail.createTime) }}
        </el-descriptions-item>
      </el-descriptions>
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

.sub {
  font-size: 13px;
  color: #909399;
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

.pre-wrap {
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
