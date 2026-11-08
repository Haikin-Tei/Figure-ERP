<!-- 个人消息抽屉：右上角用户菜单点进来，先看列表，再展开某条消息处理 -->
<template>
  <el-drawer
    v-model="visible"
    :size="drawerSize"
    :with-header="false"
    class="msg-drawer"
    @open="handleOpen"
    @close="handleClose"
  >
    <div class="mc">
      <!-- 头部 -->
      <header class="mc-header">
        <div class="mc-title">
          <el-icon class="mc-title-icon"><Bell /></el-icon>
          <span>个人消息</span>
          <el-tag v-if="messageStore.unread > 0" type="danger" size="small" effect="dark" round>
            {{ messageStore.unread }} 条未读
          </el-tag>
        </div>
        <div class="mc-head-actions">
          <el-button
            v-if="view === 'list'"
            link
            type="primary"
            :disabled="messageStore.unread === 0"
            @click="readAll"
          >
            全部已读
          </el-button>
          <el-button link :icon="Close" @click="visible = false" />
        </div>
      </header>

      <!-- ============ 列表视图 ============ -->
      <template v-if="view === 'list'">
        <div class="mc-tabs">
          <button
            v-for="t in tabs"
            :key="t.key"
            class="mc-tab"
            :class="{ active: tab === t.key }"
            @click="switchTab(t.key)"
          >
            {{ t.label }}
            <span v-if="t.count > 0" class="tab-count">{{ t.count > 99 ? '99+' : t.count }}</span>
          </button>
        </div>

        <div v-loading="loading" class="mc-list">
          <el-empty v-if="!loading && list.length === 0" description="暂时没有消息" :image-size="76" />

          <article
            v-for="m in list"
            :key="m.id"
            class="msg"
            :class="{ 'is-unread': m.unread, 'is-urgent': m.urgent && m.unread }"
            @click="openDetail(m)"
          >
            <div class="msg-avatar" :class="`t-${m.msgType.toLowerCase()}`">
              <el-icon><component :is="typeIcon(m.msgType)" /></el-icon>
            </div>

            <div class="msg-main">
              <div class="msg-top">
                <span class="msg-title">{{ m.title }}</span>
                <span class="msg-time">{{ relativeTime(m.createTime) }}</span>
              </div>
              <p class="msg-desc">{{ m.content || '（无正文）' }}</p>
              <div class="msg-meta">
                <el-tag size="small" effect="plain" :type="tagType(m.msgType)">{{ m.msgTypeText }}</el-tag>
                <el-tag v-if="m.urgent" size="small" type="danger" effect="plain">紧急</el-tag>
                <span class="msg-status" :class="`s-${m.status.toLowerCase()}`">{{ m.statusText }}</span>
                <span class="flex-1" />
                <el-button v-if="m.unread" link size="small" @click.stop="markRead(m)">标为已读</el-button>
                <el-button
                  v-if="m.bizUrl && !m.handled"
                  link
                  type="primary"
                  size="small"
                  @click.stop="goHandle(m)"
                >
                  去处理
                </el-button>
              </div>
            </div>
          </article>
        </div>

        <footer class="mc-footer">
          <el-pagination
            v-if="total > size"
            small
            background
            layout="prev, pager, next"
            :page-size="size"
            :total="total"
            :current-page="page"
            @current-change="handlePageChange"
          />
          <el-button link type="primary" @click="openList">查看全部消息 →</el-button>
        </footer>
      </template>

      <!-- ============ 详情视图 ============ -->
      <div v-else class="mc-detail">
        <el-button link :icon="ArrowLeft" class="back-btn" @click="backToList">返回消息列表</el-button>

        <template v-if="active">
          <h2 class="detail-title">{{ active.title }}</h2>
          <div class="detail-tags">
            <el-tag size="small" effect="plain" :type="tagType(active.msgType)">{{ active.msgTypeText }}</el-tag>
            <el-tag v-if="active.urgent" size="small" type="danger" effect="plain">紧急</el-tag>
            <el-tag size="small" :type="statusTagType(active.status)" effect="plain">{{ active.statusText }}</el-tag>
          </div>

          <div class="detail-meta">
            <span>发送人：{{ active.senderName || '系统' }}</span>
            <span>{{ formatTime(active.createTime) }}</span>
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

          <div class="detail-actions">
            <el-button v-if="active.unread" @click="markRead(active)">标为已读</el-button>
            <el-button v-if="!active.handled" type="primary" :icon="Promotion" @click="goHandle(active)">
              {{ active.bizUrl ? '去处理' : '标记处理完成' }}
            </el-button>
            <el-button type="danger" plain :icon="Delete" @click="removeMessage(active)">删除</el-button>
          </div>

          <div v-if="active.bizUrl" class="detail-tip">
            点击「去处理」会直接打开对应页面，并把这消息标记为已处理。
          </div>
        </template>
      </div>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft,
  Bell,
  Box,
  CircleCheck,
  Close,
  Delete,
  Document,
  Promotion,
  Tickets
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

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void }>()

const router = useRouter()
const messageStore = useMessageStore()

const visible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v)
})

/* ---------------- 抽屉宽度：窄屏铺满 ---------------- */
const isMobile = ref(window.innerWidth < 768)
const drawerSize = computed(() => (isMobile.value ? '100%' : '470px'))

function syncViewport() {
  isMobile.value = window.innerWidth < 768
}

onMounted(() => window.addEventListener('resize', syncViewport))
onBeforeUnmount(() => window.removeEventListener('resize', syncViewport))

/* ---------------- 列表 ---------------- */
type TabKey = 'ALL' | 'UNREAD' | MessageType

const view = ref<'list' | 'detail'>('list')
const loading = ref(false)
const list = ref<MessageItem[]>([])
const total = ref(0)
const page = ref(1)
const size = 8
const tab = ref<TabKey>('ALL')

/** summary 还没拿到时先用这份兜底，保证 Tab 不会闪一下再加出来 */
const FALLBACK_TYPES: MessageTypeCount[] = [
  { type: 'APPROVAL', typeText: '审批消息', total: 0, unread: 0 },
  { type: 'ORDER', typeText: '订单消息', total: 0, unread: 0 },
  { type: 'INVENTORY', typeText: '库存消息', total: 0, unread: 0 },
  { type: 'SYSTEM', typeText: '系统消息', total: 0, unread: 0 }
]

const tabs = computed(() => {
  const types = messageStore.summary?.byType?.length ? messageStore.summary.byType : FALLBACK_TYPES
  return [
    { key: 'ALL' as TabKey, label: '全部', count: 0 },
    { key: 'UNREAD' as TabKey, label: '未读', count: messageStore.summary?.unread ?? 0 },
    ...types.map((t) => ({
      key: t.type as TabKey,
      label: t.typeText.replace('消息', ''),
      count: t.unread
    }))
  ]
})

async function fetchList() {
  loading.value = true
  try {
    const data = await pageMessagesApi({
      page: page.value,
      size,
      msgType: tab.value !== 'ALL' && tab.value !== 'UNREAD' ? tab.value : undefined,
      status: tab.value === 'UNREAD' ? 'U' : undefined
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

function switchTab(key: TabKey) {
  if (tab.value === key) return
  tab.value = key
  page.value = 1
  void fetchList()
}

function handlePageChange(p: number) {
  page.value = p
  void fetchList()
}

/* ---------------- 详情 ---------------- */
const active = ref<MessageItem | null>(null)
const handleRemark = ref('')

async function openDetail(m: MessageItem) {
  try {
    const detail = await getMessageApi(m.id)
    active.value = detail
    handleRemark.value = ''
    view.value = 'detail'

    // 详情接口已把未读改成已读，本地同步一下，避免列表还挂着红点
    const idx = list.value.findIndex((it) => it.id === m.id)
    const current = idx >= 0 ? list.value[idx] : undefined
    if (current && detail.unread === false) {
      list.value[idx] = { ...current, status: 'R', statusText: '已读', unread: false }
    }
    void messageStore.refresh()
  } catch {
    // 拦截器已经提示
  }
}

function backToList() {
  view.value = 'list'
  active.value = null
  void fetchList()
}

/* ---------------- 动作 ---------------- */
async function markRead(m: MessageItem) {
  try {
    await markMessageReadApi(m.id)
    ElMessage.success('已标记为已读')
    patchStatus(m.id, 'R', '已读')
    await messageStore.refresh()
    if (view.value === 'detail' && active.value?.id === m.id) {
      active.value = { ...active.value, status: 'R', statusText: '已读', unread: false }
    }
  } catch {
    /* 已提示 */
  }
}

async function readAll() {
  try {
    const updated = await markAllMessagesReadApi(
      tab.value !== 'ALL' && tab.value !== 'UNREAD' ? tab.value : undefined
    )
    ElMessage.success(updated > 0 ? `已标记 ${updated} 条为已读` : '没有未读消息')
    await messageStore.refresh()
    await fetchList()
  } catch {
    /* 已提示 */
  }
}

async function goHandle(m: MessageItem) {
  const remark = view.value === 'detail' && active.value?.id === m.id ? handleRemark.value : ''
  try {
    await handleMessageApi(m.id, remark || undefined)
    ElMessage.success('已标记为处理完成')
    await messageStore.refresh()

    if (m.bizUrl) {
      visible.value = false
      router.push(m.bizUrl)
      return
    }
    if (view.value === 'detail') {
      active.value = await getMessageApi(m.id)
    } else {
      await fetchList()
    }
  } catch {
    /* 已提示 */
  }
}

async function removeMessage(m: MessageItem) {
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
    await deleteMessageApi(m.id)
    ElMessage.success('已删除')
    active.value = null
    view.value = 'list'
    await messageStore.refresh()
    await fetchList()
  } catch {
    /* 已提示 */
  }
}

function openList() {
  visible.value = false
  router.push('/message/list')
}

/* ---------------- 生命周期 ---------------- */
function handleOpen() {
  view.value = 'list'
  active.value = null
  page.value = 1
  void fetchList()
  void messageStore.refresh()
}

function handleClose() {
  view.value = 'list'
  active.value = null
}

/* ---------------- 展示辅助 ---------------- */
function patchStatus(id: number, status: MessageItem['status'], statusText: string) {
  const idx = list.value.findIndex((it) => it.id === id)
  const current = idx >= 0 ? list.value[idx] : undefined
  if (current) {
    list.value[idx] = { ...current, status, statusText, unread: false }
  }
}

function typeIcon(type: MessageType) {
  if (type === 'APPROVAL') return Document
  if (type === 'ORDER') return Tickets
  if (type === 'INVENTORY') return Box
  return Bell
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
  if (!value) return ''
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return value
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

/** 消息时间用相对值更好读：刚刚 / 12 分钟前 / 3 小时前 / 昨天 / 09-28 */
function relativeTime(value?: string | null) {
  if (!value) return ''
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return ''
  const minutes = Math.floor((Date.now() - d.getTime()) / 60000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  const days = Math.floor(hours / 24)
  if (days === 1) return '昨天'
  if (days < 7) return `${days} 天前`
  const p = (n: number) => String(n).padStart(2, '0')
  return `${p(d.getMonth() + 1)}-${p(d.getDate())}`
}
</script>

<style scoped>
/* 抽屉 body 默认有 20px 内边距，这里去掉，让头部/底部的分割线贴边 */
:global(.el-drawer.msg-drawer .el-drawer__body) {
  padding: 0;
  display: flex;
  overflow: hidden;
}

.mc {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
}

/* ---- 头部 ---- */
.mc-header {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 16px 18px;
  border-bottom: 1px solid #ebeef5;
  background: #fff;
}

.mc-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.mc-title-icon {
  font-size: 18px;
  color: #409eff;
}

.mc-head-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

/* ---- 分类 Tab ---- */
.mc-tabs {
  flex: none;
  display: flex;
  gap: 6px;
  padding: 10px 14px;
  overflow-x: auto;
  background: #fff;
  border-bottom: 1px solid #f2f3f5;
  scrollbar-width: none;
}

.mc-tabs::-webkit-scrollbar {
  display: none;
}

.mc-tab {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 11px;
  font-size: 13px;
  color: #606266;
  background: #f5f7fa;
  border: none;
  border-radius: 14px;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
}

.mc-tab:hover {
  color: #409eff;
}

.mc-tab.active {
  color: #fff;
  background: #409eff;
}

.tab-count {
  min-width: 16px;
  padding: 0 4px;
  font-size: 11px;
  line-height: 16px;
  text-align: center;
  color: #fff;
  background: #f56c6c;
  border-radius: 8px;
}

.mc-tab.active .tab-count {
  color: #409eff;
  background: #fff;
}

/* ---- 列表 ---- */
.mc-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px 12px;
  background: #f7f8fa;
}

.msg {
  display: flex;
  gap: 10px;
  padding: 12px;
  margin-bottom: 8px;
  background: #fff;
  border-radius: 10px;
  border: 1px solid transparent;
  cursor: pointer;
  transition: all 0.18s;
}

.msg:hover {
  border-color: #d9ecff;
  box-shadow: 0 2px 10px rgba(64, 158, 255, 0.12);
}

.msg.is-unread {
  background: #fcfeff;
  border-left: 3px solid #409eff;
}

.msg.is-urgent {
  border-left-color: #f56c6c;
}

.msg-avatar {
  flex: none;
  width: 34px;
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 9px;
  font-size: 17px;
}

.msg-avatar.t-approval {
  color: #e6a23c;
  background: #fdf6ec;
}

.msg-avatar.t-order {
  color: #409eff;
  background: #ecf5ff;
}

.msg-avatar.t-inventory {
  color: #67c23a;
  background: #f0f9eb;
}

.msg-avatar.t-system {
  color: #909399;
  background: #f4f4f5;
}

.msg-main {
  flex: 1;
  min-width: 0;
}

.msg-top {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.msg-title {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.msg.is-unread .msg-title {
  color: #1f2d3d;
}

.msg-time {
  flex: none;
  font-size: 11px;
  color: #a8abb2;
}

.msg-desc {
  margin: 4px 0 8px;
  font-size: 12.5px;
  line-height: 1.5;
  color: #909399;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.msg-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.msg-status {
  font-size: 12px;
  color: #c0c4cc;
}

.msg-status.s-u {
  color: #e6a23c;
}

.msg-status.s-h {
  color: #67c23a;
}

.flex-1 {
  flex: 1;
}

/* ---- 底部 ---- */
.mc-footer {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 16px;
  background: #fff;
  border-top: 1px solid #ebeef5;
}

/* ---- 详情 ---- */
.mc-detail {
  flex: 1;
  overflow-y: auto;
  padding: 14px 20px 24px;
  background: #fff;
}

.back-btn {
  margin-bottom: 10px;
}

.detail-title {
  margin: 0 0 10px;
  font-size: 17px;
  line-height: 1.45;
  color: #1f2d3d;
}

.detail-tags {
  display: flex;
  gap: 6px;
  margin-bottom: 10px;
}

.detail-meta {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  padding-bottom: 12px;
  margin-bottom: 14px;
  font-size: 12px;
  color: #a8abb2;
  border-bottom: 1px solid #f2f3f5;
}

.detail-content {
  font-size: 14px;
  line-height: 1.75;
  color: #303133;
  white-space: pre-wrap;
  word-break: break-word;
}

.detail-handled {
  display: flex;
  gap: 8px;
  padding: 12px;
  margin-top: 18px;
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
  margin-top: 20px;
}

.remark-label {
  margin-bottom: 6px;
  font-size: 13px;
  color: #606266;
}

.detail-actions {
  display: flex;
  gap: 8px;
  margin-top: 18px;
}

.detail-tip {
  margin-top: 10px;
  font-size: 12px;
  color: #a8abb2;
}
</style>
