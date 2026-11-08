<template>
  <div class="workbench">
    <!-- 1. 问候 + 今日完成率 -->
    <section class="hero">
      <div class="hero-main">
        <p class="hero-date">{{ dateText }}</p>
        <h2 class="hero-greeting">{{ greeting }}，{{ userStore.displayName || '同事' }}</h2>
        <div class="hero-tags">
          <el-tag v-if="roleLabel" size="small" effect="dark" class="role-tag">{{ roleLabel }}</el-tag>
          <span class="hero-hint">{{ pendingHint }}</span>
        </div>
      </div>

      <div class="hero-progress">
        <el-progress
          type="circle"
          :percentage="today?.rate ?? 0"
          :width="94"
          :stroke-width="8"
          color="#67c23a"
        >
          <template #default="{ percentage }">
            <div class="progress-value">{{ percentage }}%</div>
            <div class="progress-label">今日完成</div>
          </template>
        </el-progress>
      </div>
    </section>

    <!-- 2. 关键数字 -->
    <el-row :gutter="16" class="stat-row">
      <el-col v-for="card in statCards" :key="card.label" :xs="12" :sm="12" :md="6">
        <div class="stat-card">
          <div class="stat-icon" :class="card.tone">
            <el-icon><component :is="card.icon" /></el-icon>
          </div>
          <div class="stat-text">
            <div class="stat-label">{{ card.label }}</div>
            <div class="stat-value">
              {{ card.value }}<span class="unit">{{ card.unit }}</span>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 3. 今日计划 + 待办消息 / 快捷入口 -->
    <div class="main-grid">
      <el-card class="panel plan-panel" shadow="never">
        <template #header>
          <div class="panel-head">
            <span class="panel-title">今日工作计划</span>
            <span class="panel-sub">
              已完成 {{ today?.done ?? 0 }} / {{ today?.total ?? 0 }}
            </span>
          </div>
        </template>

        <!-- 快速添加：回车即建 -->
        <div class="quick-add">
          <el-input
            v-model="newTitle"
            placeholder="记一件今天要做的事，回车即可添加"
            :prefix-icon="Plus"
            maxlength="120"
            clearable
            @keyup.enter="addTodo"
          />
          <el-select v-model="newPriority" class="priority-select" aria-label="优先级">
            <el-option label="高" value="H" />
            <el-option label="普通" value="N" />
            <el-option label="低" value="L" />
          </el-select>
          <el-button type="primary" :loading="adding" @click="addTodo">添加</el-button>
        </div>

        <ul v-loading="loading" class="todo-list">
          <li
            v-for="item in today?.items ?? []"
            :key="item.id"
            class="todo-item"
            :class="{ 'is-done': item.done }"
          >
            <el-checkbox :model-value="item.done" @change="toggleTodo(item)" />

            <div class="todo-main">
              <div class="todo-title">{{ item.title }}</div>
              <div v-if="item.content" class="todo-desc">{{ item.content }}</div>
            </div>

            <el-tag v-if="item.overdue" size="small" type="danger" effect="plain">逾期</el-tag>
            <el-tag size="small" :type="priorityType(item.priority)" effect="plain">
              {{ item.priorityText }}
            </el-tag>

            <el-button
              class="todo-del"
              link
              type="danger"
              :icon="Delete"
              title="删除"
              @click="removeTodo(item)"
            />
          </li>
        </ul>

        <el-empty
          v-if="!loading && !(today?.items?.length)"
          description="今天还没有安排，先加一件小事吧"
          :image-size="72"
        />

        <div v-if="(today?.total ?? 0) > 0" class="plan-footer">
          <el-progress
            :percentage="today?.rate ?? 0"
            :stroke-width="6"
            :show-text="false"
            color="#409eff"
          />
          <span class="plan-footer-text">{{ footerText }}</span>
        </div>
      </el-card>

      <div class="side">
        <!-- 待办事项：来自个人消息，未读且未处理的 -->
        <el-card class="panel msg-panel" shadow="never">
          <template #header>
            <div class="panel-head">
              <span class="panel-title">待办事项</span>
              <span v-if="pendingMsgCount > 0" class="unread-pill">
                未处理 {{ pendingMsgCount }} 条
              </span>
              <el-button link type="primary" @click="router.push('/message/list')">
                全部消息
              </el-button>
            </div>
          </template>

          <ul v-loading="msgLoading" class="msg-list">
            <li v-for="m in messages" :key="m.id" class="msg-item" @click="openMessage(m)">
              <div class="msg-line">
                <el-tag size="small" :type="msgTagType(m.msgType)" effect="plain">
                  {{ m.msgTypeText }}
                </el-tag>
                <span class="msg-title">{{ m.title }}</span>
                <el-tag v-if="m.urgent" size="small" type="danger" effect="dark">紧急</el-tag>
              </div>
              <div class="msg-meta">
                <span>{{ m.senderName || '系统' }} · {{ fromNow(m.createTime) }}</span>
                <span class="msg-go">{{ m.bizUrl ? '去处理 ›' : '查看 ›' }}</span>
              </div>
            </li>
          </ul>

          <el-empty
            v-if="!msgLoading && messages.length === 0"
            description="没有待处理的消息"
            :image-size="64"
          />
        </el-card>

        <!-- 常用入口：把每天要点好几次的页面挪到首屏 -->
        <el-card class="panel quick-panel" shadow="never">
          <template #header>
            <div class="panel-head">
              <span class="panel-title">常用入口</span>
            </div>
          </template>
          <div class="quick-grid">
            <button
              v-for="link in quickLinks"
              :key="link.path"
              class="quick-item"
              type="button"
              @click="router.push(link.path)"
            >
              <el-icon :size="18"><component :is="link.icon" /></el-icon>
              <span>{{ link.title }}</span>
            </button>
          </div>
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Bell,
  Box,
  CircleCheck,
  Delete,
  Document,
  Goods,
  Grid,
  List,
  Plus,
  Van,
  Warning
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import {
  createTodoApi,
  deleteTodoApi,
  getTodayTodosApi,
  updateTodoStatusApi,
  type TodayTodo,
  type TodoItem,
  type TodoPriority,
  type TodoStatus
} from '@/api/todo'
import {
  getMessageSummaryApi,
  pageMessagesApi,
  type MessageItem,
  type MessageSummary,
  type MessageType
} from '@/api/message'

const router = useRouter()
const userStore = useUserStore()

/* ---------------- 今日计划 ---------------- */

const today = ref<TodayTodo | null>(null)
const loading = ref(false)
const adding = ref(false)
const newTitle = ref('')
const newPriority = ref<TodoPriority>('N')

async function loadToday() {
  loading.value = true
  try {
    today.value = await getTodayTodosApi()
  } catch {
    // 请求拦截器已统一提示，这里保留上一次的数据即可
  } finally {
    loading.value = false
  }
}

async function addTodo() {
  const title = newTitle.value.trim()
  if (!title) {
    ElMessage.warning('先写一句要做的事')
    return
  }
  adding.value = true
  try {
    await createTodoApi({ title, priority: newPriority.value })
    newTitle.value = ''
    newPriority.value = 'N'
    await loadToday()
    ElMessage.success('已加入今日计划')
  } catch {
    // 提示由拦截器处理
  } finally {
    adding.value = false
  }
}

/** 勾选即改状态：已完成 → 待办；待办 → 已完成 */
async function toggleTodo(item: TodoItem) {
  const next: TodoStatus = item.done ? 'P' : 'D'
  try {
    await updateTodoStatusApi(item.id, next)
    await loadToday()
    if (next === 'D') ElMessage.success(`已完成：${item.title}`)
  } catch {
    // 提示由拦截器处理
  }
}

async function removeTodo(item: TodoItem) {
  try {
    await ElMessageBox.confirm(`确定删除「${item.title}」吗？`, '删除待办', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return // 取消
  }
  try {
    await deleteTodoApi(item.id)
    await loadToday()
    ElMessage.success('已删除')
  } catch {
    // 提示由拦截器处理
  }
}

/* ---------------- 待办事项（个人消息） ---------------- */

const messages = ref<MessageItem[]>([])
const summary = ref<MessageSummary | null>(null)
const msgLoading = ref(false)

async function loadMessages() {
  msgLoading.value = true
  try {
    // 取最近 20 条再筛「未处理」：只看未读会漏掉「点开看过但还没办」的消息，
    // 而这类恰恰是待办事项里最需要盯着的。
    const [page, s] = await Promise.all([
      pageMessagesApi({ page: 1, size: 20 }),
      getMessageSummaryApi()
    ])
    messages.value = page.records.filter((m) => !m.handled).slice(0, 5)
    summary.value = s
  } catch {
    // 提示由拦截器处理
  } finally {
    msgLoading.value = false
  }
}

/** 有跳转地址就直接去业务页，纯通知就进消息列表 */
function openMessage(m: MessageItem) {
  router.push(m.bizUrl || '/message/list')
}

/* ---------------- 展示用计算 ---------------- */

const roleLabel = computed(() => userStore.userInfo?.roleLabel || '')

/** 待办事项 = 还没处理的消息（未读 + 已读未处理），不是简单的未读数 */
const pendingMsgCount = computed(() => {
  const s = summary.value
  return s ? Math.max(0, s.total - s.handled) : 0
})

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 11) return '早上好'
  if (hour < 13) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const weekdayOf = (d: Date) =>
  ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六'][d.getDay()]

const dateText = computed(() => {
  const d = today.value?.date ? new Date(`${today.value.date}T00:00:00`) : new Date()
  const weekday = today.value?.weekdayText || weekdayOf(d)
  return `${d.getFullYear()} 年 ${d.getMonth() + 1} 月 ${d.getDate()} 日 · ${weekday}`
})

const pendingHint = computed(() => {
  const pending = today.value?.pending ?? 0
  const overdue = today.value?.overdue ?? 0
  if (pending === 0 && (today.value?.total ?? 0) > 0) {
    return '今天的计划已经全部完成，可以准备明天的事了'
  }
  if (pending === 0) {
    return '今天还没有安排，加一条计划开始吧'
  }
  if (overdue > 0) {
    return `今天还有 ${pending} 项待办，另有 ${overdue} 项逾期未完成`
  }
  return `今天还有 ${pending} 项待办等你处理`
})

const footerText = computed(() => {
  const done = today.value?.done ?? 0
  const total = today.value?.total ?? 0
  return `${done} / ${total} 已完成 · 完成率 ${today.value?.rate ?? 0}%`
})

const statCards = computed(() => [
  { label: '今日待办', value: today.value?.pending ?? 0, unit: '项', icon: List, tone: 'blue' },
  { label: '今日已完成', value: today.value?.done ?? 0, unit: '项', icon: CircleCheck, tone: 'green' },
  { label: '逾期未完成', value: today.value?.overdue ?? 0, unit: '项', icon: Warning, tone: 'orange' },
  { label: '未读消息', value: summary.value?.unread ?? 0, unit: '条', icon: Bell, tone: 'purple' }
])

/** 优先级：高＝红、普通＝蓝、低＝灰 */
function priorityType(priority: TodoPriority): 'danger' | 'primary' | 'info' {
  if (priority === 'H') return 'danger'
  if (priority === 'L') return 'info'
  return 'primary'
}

function msgTagType(type: MessageType): 'primary' | 'warning' | 'success' | 'info' {
  switch (type) {
    case 'APPROVAL':
      return 'warning'
    case 'ORDER':
      return 'primary'
    case 'INVENTORY':
      return 'success'
    default:
      return 'info'
  }
}

/** 「3 小时前」这种相对时间，比绝对时间更适合待办列表 */
function fromNow(value?: string) {
  if (!value) return ''
  const time = new Date(value).getTime()
  if (Number.isNaN(time)) return ''
  const minutes = Math.floor((Date.now() - time) / 60000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  const days = Math.floor(hours / 24)
  if (days < 30) return `${days} 天前`
  return value.slice(0, 10)
}

/* ---------------- 常用入口 ---------------- */

const quickLinks = [
  { title: '手办列表', path: '/stock/figure', icon: Goods },
  { title: '入库记录', path: '/stock/inbound', icon: Box },
  { title: '货架管理', path: '/stock/shelves', icon: Grid },
  { title: '所有订单', path: '/order/list', icon: Document },
  { title: '物流查询', path: '/logistics/query', icon: Van },
  { title: '个人消息', path: '/message/list', icon: Bell }
]

onMounted(async () => {
  await Promise.all([loadToday(), loadMessages()])
})
</script>

<style scoped>
.workbench {
  padding: 20px;
  background: #f5f7fa;
  min-height: calc(100vh - 60px);
  box-sizing: border-box;
}

/* ---------- 问候卡 ---------- */
.hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 22px 26px;
  margin-bottom: 16px;
  border-radius: 12px;
  background: linear-gradient(135deg, #eef4ff 0%, #f8fbff 55%, #eefaf3 100%);
  border: 1px solid #e6eefc;
}

.hero-date {
  margin: 0 0 8px;
  font-size: 13px;
  color: #7c8ba1;
  letter-spacing: 0.3px;
}

.hero-greeting {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: #1f2d3d;
}

.hero-tags {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
  flex-wrap: wrap;
}

.role-tag {
  background: #3b6ea5;
  border-color: #3b6ea5;
}

.hero-hint {
  font-size: 13px;
  color: #5d6b82;
}

.hero-progress {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  padding-right: 8px;
}

.progress-value {
  font-size: 17px;
  font-weight: 700;
  color: #1f2d3d;
  line-height: 1.2;
}

.progress-label {
  margin-top: 2px;
  font-size: 11px;
  color: #909399;
}

/* ---------- 数字卡 ---------- */
.stat-row {
  margin-bottom: 16px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 18px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  transition: box-shadow 0.2s, transform 0.2s;
}

.stat-card:hover {
  box-shadow: 0 6px 18px rgba(31, 45, 61, 0.08);
  transform: translateY(-1px);
}

.stat-icon {
  width: 42px;
  height: 42px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
}

.stat-icon.blue {
  color: #409eff;
  background: #ecf5ff;
}

.stat-icon.green {
  color: #67c23a;
  background: #f0f9eb;
}

.stat-icon.orange {
  color: #e6a23c;
  background: #fdf6ec;
}

.stat-icon.purple {
  color: #7a5af8;
  background: #f3f0ff;
}

.stat-label {
  font-size: 13px;
  color: #909399;
}

.stat-value {
  margin-top: 4px;
  font-size: 22px;
  font-weight: 700;
  color: #1f2d3d;
}

.unit {
  margin-left: 3px;
  font-size: 12px;
  font-weight: 400;
  color: #909399;
}

/* ---------- 主体两栏 ---------- */
.main-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.panel {
  border-radius: 10px;
  border-color: #ebeef5;
}

.panel-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.panel-title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2d3d;
}

.panel-sub {
  font-size: 12px;
  color: #909399;
}

/* 全部消息按钮靠右 */
.msg-panel .panel-head :deep(.el-button) {
  margin-left: auto;
}

.unread-pill {
  padding: 1px 8px;
  font-size: 12px;
  color: #f56c6c;
  background: #fef0f0;
  border-radius: 10px;
}

/* ---------- 快速添加 ---------- */
.quick-add {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.priority-select {
  width: 92px;
  flex: none;
}

/* ---------- 待办列表 ---------- */
.todo-list {
  margin: 0;
  padding: 0;
  list-style: none;
  min-height: 60px;
}

.todo-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 10px;
  border-radius: 8px;
  border-bottom: 1px solid #f5f7fa;
  transition: background 0.2s;
}

.todo-item:last-child {
  border-bottom: none;
}

.todo-item:hover {
  background: #fafcff;
}

.todo-item.is-done .todo-title {
  color: #b0b3b8;
  text-decoration: line-through;
}

.todo-main {
  flex: 1;
  min-width: 0;
}

.todo-title {
  font-size: 14px;
  color: #303133;
  line-height: 1.5;
  word-break: break-word;
}

.todo-desc {
  margin-top: 3px;
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
  word-break: break-word;
}

.todo-del {
  opacity: 0;
  transition: opacity 0.2s;
}

.todo-item:hover .todo-del {
  opacity: 1;
}

.plan-footer {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-top: 14px;
  margin-top: 6px;
  border-top: 1px dashed #ebeef5;
}

.plan-footer :deep(.el-progress) {
  flex: 1;
}

.plan-footer-text {
  flex: none;
  font-size: 12px;
  color: #909399;
}

/* ---------- 侧栏 ---------- */
.side {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.msg-list {
  margin: 0;
  padding: 0;
  list-style: none;
  min-height: 40px;
}

.msg-item {
  padding: 10px 8px;
  border-radius: 8px;
  cursor: pointer;
  border-bottom: 1px solid #f5f7fa;
  transition: background 0.2s;
}

.msg-item:last-child {
  border-bottom: none;
}

.msg-item:hover {
  background: #f5f9ff;
}

.msg-line {
  display: flex;
  align-items: center;
  gap: 8px;
}

.msg-title {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  color: #303133;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.msg-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 5px;
  padding-left: 2px;
  font-size: 12px;
  color: #909399;
}

.msg-go {
  color: #409eff;
}

/* ---------- 常用入口 ---------- */
.quick-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.quick-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 12px 6px;
  font-size: 12px;
  color: #5d6b82;
  background: #f7f9fc;
  border: 1px solid #eef1f6;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.quick-item:hover {
  color: #409eff;
  background: #ecf5ff;
  border-color: #d6e8ff;
}

/* ---------- 窄屏 ---------- */
@media (max-width: 1100px) {
  .main-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 768px) {
  .workbench {
    padding: 12px;
  }

  .hero {
    padding: 18px;
    align-items: flex-start;
  }

  .hero-greeting {
    font-size: 18px;
  }

  .hero-progress {
    padding-right: 0;
  }

  .quick-add {
    flex-wrap: wrap;
  }

  .quick-add :deep(.el-input) {
    flex: 1 1 100%;
  }

  .stat-value {
    font-size: 19px;
  }
}
</style>
