<!-- 个人消息中心：抽屉里的「查看全部消息」跳到这里，支持筛选、批量已读与处理 -->
<template>
  <div class="message-page">
    <!-- 统计 -->
    <div class="stat-row">
      <div class="stat-card">
        <div class="stat-icon c-blue"><el-icon><Bell /></el-icon></div>
        <div class="stat-body">
          <div class="stat-value">{{ summary?.unread ?? 0 }}</div>
          <div class="stat-label">未读消息</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon c-red"><el-icon><Warning /></el-icon></div>
        <div class="stat-body">
          <div class="stat-value">{{ summary?.urgentUnread ?? 0 }}</div>
          <div class="stat-label">紧急未读</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon c-green"><el-icon><CircleCheck /></el-icon></div>
        <div class="stat-body">
          <div class="stat-value">{{ summary?.handled ?? 0 }}</div>
          <div class="stat-label">已处理</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon c-gray"><el-icon><Document /></el-icon></div>
        <div class="stat-body">
          <div class="stat-value">{{ summary?.total ?? 0 }}</div>
          <div class="stat-label">消息总数</div>
        </div>
      </div>
    </div>

    <el-card shadow="never" class="list-card">
      <!-- 类型 Tab + 筛选 -->
      <div class="toolbar">
        <el-tabs v-model="activeType" class="type-tabs" @tab-change="handleTypeChange">
          <el-tab-pane label="全部" name="ALL" />
          <el-tab-pane
            v-for="t in typeTabs"
            :key="t.type"
            :name="t.type"
            :label="t.unread > 0 ? `${t.typeText} (${t.unread})` : t.typeText"
          />
        </el-tabs>

        <div class="filters">
          <el-select
            v-model="query.status"
            placeholder="全部状态"
            clearable
            style="width: 118px"
            @change="handleSearch"
          >
            <el-option label="未读" value="U" />
            <el-option label="已读" value="R" />
            <el-option label="已处理" value="H" />
          </el-select>
          <el-input
            v-model="query.keyword"
            placeholder="搜索标题或内容"
            clearable
            :prefix-icon="Search"
            style="width: 200px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="fetchList">刷新</el-button>
          <el-button :disabled="(summary?.unread ?? 0) === 0" @click="readAll">全部已读</el-button>
        </div>
      </div>

      <el-table
        v-loading="loading"
        :data="list"
        row-key="id"
        :row-class-name="rowClassName"
        class="message-table"
        @row-click="openDetail"
      >
        <el-table-column label="消息" min-width="320">
          <template #default="{ row }">
            <div class="cell-title">
              <span v-if="row.unread" class="unread-dot" />
              <span class="title-text" :class="{ strong: row.unread }">{{ row.title }}</span>
            </div>
            <div class="cell-sub">{{ row.content }}</div>
          </template>
        </el-table-column>

        <el-table-column label="类型" width="106">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" :type="tagType(row.msgType)">{{ row.msgTypeText }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="优先级" width="88">
          <template #default="{ row }">
            <el-tag v-if="row.urgent" size="small" type="danger" effect="plain">紧急</el-tag>
            <span v-else class="muted">普通</span>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="92">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTagType(row.status)" effect="plain">{{ row.statusText }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="senderName" label="发送人" width="100" show-overflow-tooltip />

        <el-table-column label="时间" width="152">
          <template #default="{ row }">
            <span class="muted">{{ formatTime(row.createTime) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="206" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click.stop="openDetail(row)">查看</el-button>
            <el-button
              v-if="row.bizUrl && !row.handled"
              link
              type="primary"
              size="small"
              @click.stop="goHandle(row)"
            >
              去处理
            </el-button>
            <el-button v-if="row.unread" link size="small" @click.stop="markRead(row)">标为已读</el-button>
            <el-button link type="danger" size="small" @click.stop="remove(row)">删除</el-button>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="没有符合条件的消息" :image-size="90" />
        </template>
      </el-table>

      <div class="pager">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="total"
          :page-size="query.size"
          :current-page="query.page"
          :page-sizes="[10, 20, 50]"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" width="560px" class="message-detail-dialog" :show-close="true">
      <template #header>
        <div class="detail-head">
          <span class="detail-head-title">{{ active?.title || '消息详情' }}</span>
          <div class="detail-head-tags">
            <el-tag v-if="active" size="small" effect="plain" :type="tagType(active.msgType)">
              {{ active.msgTypeText }}
            </el-tag>
            <el-tag v-if="active?.urgent" size="small" type="danger" effect="plain">紧急</el-tag>
            <el-tag v-if="active" size="small" :type="statusTagType(active.status)" effect="plain">
              {{ active.statusText }}
            </el-tag>
          </div>
        </div>
      </template>

      <template v-if="active">
        <div class="detail-meta">
          <span>发送人：{{ active.senderName || '系统' }}</span>
          <span>时间：{{ formatTime(active.createTime) }}</span>
        </div>

        <div class="detail-content">{{ active.content || '（无正文）' }}</div>

        <div v-if="active.handled" class="detail-handled">
          <el-icon><CircleCheck /></el-icon>
          <div>
            <div>已于 {{ formatTime(active.handleTime) }} 处理完成</div>
            <div v-if="active.handleRemark" class="handled-remark">处理备注：{{ active.handleRemark }}</div>
          </div>
        </div>

        <div v-else class="detail-remark">
          <div class="remark-label">处理备注（可选）</div>
          <el-input
            v-model="handleRemark"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
            placeholder="例如：已核对库存，同意入库"
          />
        </div>

        <div v-if="active.bizUrl" class="detail-link">
          <el-icon><Link /></el-icon>
          关联页面：{{ active.bizUrl }}
        </div>
      </template>

      <template #footer>
        <div class="detail-footer">
          <el-button type="danger" plain :icon="Delete" @click="active && remove(active)">删除</el-button>
          <div class="flex-1" />
          <el-button @click="detailVisible = false">关闭</el-button>
          <el-button v-if="active?.unread" @click="active && markRead(active)">标为已读</el-button>
          <el-button v-if="active && !active.handled" type="primary" :icon="Promotion" @click="goHandle(active)">
            {{ active.bizUrl ? '去处理' : '标记处理完成' }}
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Bell,
  CircleCheck,
  Delete,
  Document,
  Link,
  Promotion,
  Refresh,
  Search,
  Warning
} from '@element-plus/icons-vue'
import {
  deleteMessageApi,
  getMessageApi,
  handleMessageApi,
  markAllMessagesReadApi,
  markMessageReadApi,
  pageMessagesApi,
  type MessageItem,
  type MessageType,
  type MessageTypeCount
} from '@/api/message'
import { useMessageStore } from '@/stores/message'

const router = useRouter()
const messageStore = useMessageStore()

const summary = computed(() => messageStore.summary)

/* ---------------- 类型 Tab ---------------- */
const FALLBACK_TYPES: MessageTypeCount[] = [
  { type: 'APPROVAL', typeText: '审批消息', total: 0, unread: 0 },
  { type: 'ORDER', typeText: '订单消息', total: 0, unread: 0 },
  { type: 'INVENTORY', typeText: '库存消息', total: 0, unread: 0 },
  { type: 'SYSTEM', typeText: '系统消息', total: 0, unread: 0 }
]

const typeTabs = computed(() =>
  messageStore.summary?.byType?.length ? messageStore.summary.byType : FALLBACK_TYPES
)

const activeType = ref<'ALL' | MessageType>('ALL')

/* ---------------- 列表 ---------------- */
const loading = ref(false)
const list = ref<MessageItem[]>([])
const total = ref(0)

const query = reactive({
  page: 1,
  size: 10,
  status: '' as '' | 'U' | 'R' | 'H',
  keyword: ''
})

async function fetchList() {
  loading.value = true
  try {
    const data = await pageMessagesApi({
      page: query.page,
      size: query.size,
      msgType: activeType.value === 'ALL' ? undefined : activeType.value,
      status: query.status || undefined,
      keyword: query.keyword || undefined
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

function handleTypeChange() {
  query.page = 1
  void fetchList()
}

function handleSearch() {
  query.page = 1
  void fetchList()
}

function handlePageChange(p: number) {
  query.page = p
  void fetchList()
}

function handleSizeChange(s: number) {
  query.size = s
  query.page = 1
  void fetchList()
}

/* ---------------- 详情 ---------------- */
const detailVisible = ref(false)
const active = ref<MessageItem | null>(null)
const handleRemark = ref('')

async function openDetail(row: MessageItem) {
  try {
    const detail = await getMessageApi(row.id)
    active.value = detail
    handleRemark.value = ''
    detailVisible.value = true

    const idx = list.value.findIndex((it) => it.id === row.id)
    const current = idx >= 0 ? list.value[idx] : undefined
    if (current && !detail.unread) {
      list.value[idx] = { ...current, status: 'R', statusText: '已读', unread: false }
    }
    void messageStore.refresh()
  } catch {
    /* 拦截器已提示 */
  }
}

/* ---------------- 动作 ---------------- */
async function markRead(row: MessageItem) {
  try {
    await markMessageReadApi(row.id)
    ElMessage.success('已标记为已读')
    patchStatus(row.id, 'R', '已读')
    if (active.value?.id === row.id) {
      active.value = { ...active.value, status: 'R', statusText: '已读', unread: false }
    }
    await messageStore.refresh()
  } catch {
    /* 已提示 */
  }
}

async function readAll() {
  try {
    const updated = await markAllMessagesReadApi(activeType.value === 'ALL' ? undefined : activeType.value)
    ElMessage.success(updated > 0 ? `已标记 ${updated} 条为已读` : '没有未读消息')
    await messageStore.refresh()
    await fetchList()
  } catch {
    /* 已提示 */
  }
}

/** 去处理：先标记已处理（带上备注），再跳到关联页面 */
async function goHandle(row: MessageItem) {
  const remark = active.value?.id === row.id ? handleRemark.value : ''
  try {
    await handleMessageApi(row.id, remark || undefined)
    ElMessage.success('已标记为处理完成')
    detailVisible.value = false
    await messageStore.refresh()

    if (row.bizUrl) {
      router.push(row.bizUrl)
      return
    }
    if (active.value?.id === row.id) {
      active.value = await getMessageApi(row.id)
    }
    await fetchList()
  } catch {
    /* 已提示 */
  }
}

async function remove(row: MessageItem) {
  try {
    await ElMessageBox.confirm('删除后不可恢复，确定删除这条消息吗？', '删除消息', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await deleteMessageApi(row.id)
    ElMessage.success('已删除')
    detailVisible.value = false
    active.value = null
    await messageStore.refresh()
    await fetchList()
  } catch {
    /* 已提示 */
  }
}

function patchStatus(id: number, status: MessageItem['status'], statusText: string) {
  const idx = list.value.findIndex((it) => it.id === id)
  const current = idx >= 0 ? list.value[idx] : undefined
  if (current) {
    list.value[idx] = { ...current, status, statusText, unread: false }
  }
}

/* ---------------- 展示辅助 ---------------- */
function rowClassName({ row }: { row: MessageItem }) {
  return row.unread ? 'row-unread' : ''
}

function tagType(type: MessageType): 'warning' | 'primary' | 'success' | 'info' {
  if (type === 'APPROVAL') return 'warning'
  if (type === 'ORDER') return 'primary'
  if (type === 'INVENTORY') return 'success'
  return 'info'
}

function statusTagType(status: MessageItem['status']): 'warning' | 'primary' | 'success' {
  if (status === 'U') return 'warning'
  if (status === 'H') return 'success'
  return 'primary'
}

function formatTime(value?: string | null) {
  if (!value) return '—'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return value
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

onMounted(() => {
  void messageStore.refresh()
  void fetchList()
})
</script>

<style scoped>
.message-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* ---- 统计卡片 ---- */
.stat-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.06);
}

.stat-icon {
  width: 38px;
  height: 38px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  font-size: 19px;
}

.stat-icon.c-blue {
  color: #409eff;
  background: #ecf5ff;
}

.stat-icon.c-red {
  color: #f56c6c;
  background: #fef0f0;
}

.stat-icon.c-green {
  color: #67c23a;
  background: #f0f9eb;
}

.stat-icon.c-gray {
  color: #909399;
  background: #f4f4f5;
}

.stat-value {
  font-size: 21px;
  font-weight: 600;
  line-height: 1.2;
  color: #303133;
}

.stat-label {
  margin-top: 2px;
  font-size: 12px;
  color: #909399;
}

/* ---- 工具栏 ---- */
.list-card :deep(.el-card__body) {
  padding: 6px 16px 14px;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.type-tabs {
  flex: 1;
  min-width: 320px;
}

.type-tabs :deep(.el-tabs__header) {
  margin-bottom: 6px;
}

.type-tabs :deep(.el-tabs__item) {
  font-size: 13px;
}

.filters {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding-bottom: 6px;
}

/* ---- 表格 ---- */
.message-table :deep(.row-unread) {
  background: #fafcff;
}

.message-table :deep(.el-table__row) {
  cursor: pointer;
}

.cell-title {
  display: flex;
  align-items: center;
  gap: 6px;
}

.unread-dot {
  flex: none;
  width: 6px;
  height: 6px;
  background: #f56c6c;
  border-radius: 50%;
}

.title-text {
  font-size: 13.5px;
  color: #606266;
}

.title-text.strong {
  font-weight: 600;
  color: #303133;
}

.cell-sub {
  margin-top: 2px;
  font-size: 12px;
  color: #a8abb2;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.muted {
  font-size: 12px;
  color: #909399;
}

.pager {
  display: flex;
  justify-content: flex-end;
  padding-top: 14px;
}

/* ---- 详情弹窗 ---- */
.detail-head-title {
  display: block;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.detail-head-tags {
  display: flex;
  gap: 6px;
  margin-top: 8px;
}

.detail-meta {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  font-size: 12px;
  color: #a8abb2;
  padding-bottom: 12px;
  border-bottom: 1px solid #f2f3f5;
}

.detail-content {
  margin-top: 14px;
  font-size: 14px;
  line-height: 1.75;
  color: #303133;
  white-space: pre-wrap;
  word-break: break-word;
}

.detail-handled {
  display: flex;
  gap: 8px;
  margin-top: 16px;
  padding: 12px;
  font-size: 13px;
  line-height: 1.6;
  color: #529b2e;
  background: #f0f9eb;
  border-radius: 8px;
}

.handled-remark {
  color: #606266;
}

.detail-remark {
  margin-top: 18px;
}

.remark-label {
  margin-bottom: 6px;
  font-size: 13px;
  color: #606266;
}

.detail-link {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 14px;
  font-size: 12px;
  color: #a8abb2;
}

.detail-footer {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.flex-1 {
  flex: 1;
}

/* ---- 窄屏 ---- */
@media (max-width: 992px) {
  .stat-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .filters {
    width: 100%;
  }

  .filters :deep(.el-input),
  .filters :deep(.el-select) {
    width: 100% !important;
  }
}
</style>
