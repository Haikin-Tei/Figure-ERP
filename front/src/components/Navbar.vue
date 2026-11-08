<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowDown,
  Bell,
  Refresh,
  SwitchButton,
  User,
  UserFilled
} from '@element-plus/icons-vue'
import { getHealth, getDbHealth, type HealthInfo, type DbHealthInfo } from '@/api/system'
import { useUserStore } from '@/stores/user'
import { useMessageStore } from '@/stores/message'
import MessageCenter from './MessageCenter.vue'

const router = useRouter()
const userStore = useUserStore()
const messageStore = useMessageStore()

/* ---------------- 服务状态 ---------------- */
const health = ref<HealthInfo | null>(null)
const db = ref<DbHealthInfo | null>(null)
const checking = ref(false)

async function checkStatus() {
  checking.value = true
  try {
    health.value = await getHealth()
    db.value = await getDbHealth()
  } catch {
    // 请求层已经弹过错误提示，这里只需要把状态置为异常
    health.value = null
    db.value = null
  } finally {
    checking.value = false
  }
}

onMounted(() => {
  void checkStatus()
  // 登录后开始轮询未读数，右上角菜单的红点靠它刷新
  if (userStore.isLoggedIn) {
    messageStore.startPolling()
  }
})

onBeforeUnmount(() => messageStore.stopPolling())

/* ---------------- 个人消息 ---------------- */
const messageVisible = ref(false)

/* ---------------- 当前用户 ---------------- */
const profileVisible = ref(false)
const defaultAvatar =
  'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'

const avatarUrl = computed(() => userStore.userInfo?.avatar || defaultAvatar)

/** 后端返回的是 ISO 格式（2026-11-01T13:09:39），这里转成好读的样子 */
function formatTime(value?: string) {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 19)
}

async function handleCommand(command: string) {
  if (command === 'message') {
    messageVisible.value = true
    void messageStore.refresh()
    return
  }
  if (command === 'profile') {
    profileVisible.value = true
    return
  }
  if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
        type: 'warning',
        confirmButtonText: '退出',
        cancelButtonText: '取消'
      })
    } catch {
      return // 用户点了取消
    }
    await userStore.logout()
    messageStore.reset()
    ElMessage.success('已退出登录')
    router.replace('/login')
  }
}
</script>

<template>
  <div class="navbar">
    <!-- 服务状态：前端 → 后端 → 数据库 链路指示灯 -->
    <el-popover placement="bottom-start" :width="300" trigger="hover">
      <template #reference>
        <div class="status-bar" :class="{ 'is-bad': !health || !db?.connected }">
          <span class="dot" />
          <span class="status-text">
            {{ !health ? '后端未连接' : !db?.connected ? '数据库异常' : '服务正常' }}
          </span>
          <el-icon class="refresh-icon" :class="{ spinning: checking }" @click.stop="checkStatus">
            <Refresh />
          </el-icon>
        </div>
      </template>

      <div class="status-panel">
        <div class="panel-title">运行环境</div>
        <div class="panel-row"><span>后端</span><b>{{ health ? health.status : '未连接' }}</b></div>
        <div v-if="health" class="panel-row"><span>版本</span><b>{{ health.version }}</b></div>
        <div v-if="health" class="panel-row"><span>JVM</span><b>{{ health.javaVersion }}</b></div>
        <template v-if="db">
          <el-divider style="margin: 8px 0" />
          <div class="panel-title">数据库</div>
          <div class="panel-row">
            <span>状态</span>
            <b :style="{ color: db.connected ? '#67c23a' : '#f56c6c' }">
              {{ db.connected ? '已连接' : '连接失败' }}
            </b>
          </div>
          <template v-if="db.connected">
            <div class="panel-row"><span>数据库</span><b>{{ db.database }}</b></div>
            <div class="panel-row"><span>版本</span><b>{{ db.product }} {{ db.productVersion }}</b></div>
            <div class="panel-row"><span>表数量</span><b>{{ db.tableCount }}</b></div>
            <div class="panel-row"><span>耗时</span><b>{{ db.latencyMs }} ms</b></div>
          </template>
          <div v-else class="panel-error">{{ db.error }}</div>
        </template>
      </div>
    </el-popover>

    <!-- 右侧：登录后显示员工姓名，未登录显示登录按钮 -->
    <div class="right-menu">
      <el-dropdown
        v-if="userStore.isLoggedIn"
        trigger="click"
        popper-class="user-dropdown"
        @command="handleCommand"
      >
        <div class="user-trigger">
          <span class="avatar-wrap">
            <el-avatar :size="34" :src="avatarUrl" />
            <span v-if="messageStore.unread > 0" class="avatar-dot" />
          </span>
          <span class="username">{{ userStore.displayName || '未命名员工' }}</span>
          <el-icon class="arrow"><ArrowDown /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="message" :icon="Bell">
              <span class="menu-entry">
                <span>个人消息</span>
                <span v-if="messageStore.unread > 0" class="unread-pill">
                  {{ messageStore.unread > 99 ? '99+' : messageStore.unread }}
                </span>
              </span>
            </el-dropdown-item>
            <el-dropdown-item command="profile" :icon="UserFilled">个人信息</el-dropdown-item>
            <el-dropdown-item command="logout" :icon="SwitchButton" divided>退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

      <el-button v-else type="primary" plain :icon="User" @click="router.push('/login')">
        登录
      </el-button>
    </div>

    <!-- 个人信息抽屉 -->
    <el-drawer v-model="profileVisible" title="个人信息" size="380px">
      <div class="profile-head">
        <el-avatar :size="64" :src="avatarUrl" />
        <div class="profile-name">
          <div class="name">{{ userStore.userInfo?.realName || '—' }}</div>
          <div class="account">@{{ userStore.userInfo?.username }}</div>
        </div>
      </div>

      <el-descriptions :column="1" border size="small" class="profile-desc">
        <el-descriptions-item label="登录账号">
          {{ userStore.userInfo?.username || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="员工姓名">
          {{ userStore.userInfo?.realName || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="员工介绍">
          <span class="pre-wrap">{{ userStore.userInfo?.intro || '—' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="备注">
          <span class="pre-wrap">{{ userStore.userInfo?.remark || '—' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="最后登录">
          {{ formatTime(userStore.userInfo?.lastLoginTime) }}
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">
          {{ formatTime(userStore.userInfo?.createTime) }}
        </el-descriptions-item>
      </el-descriptions>
    </el-drawer>

    <!-- 个人消息抽屉：用户菜单 → 个人消息 -->
    <MessageCenter v-model="messageVisible" />
  </div>
</template>

<style scoped>
.navbar {
  height: 60px;
  overflow: hidden;
  position: relative;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 20px;
}

/* --- 服务状态指示 --- */
.status-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 12px;
  background: #f0f9eb;
  cursor: pointer;
  user-select: none;
  font-size: 13px;
  color: #67c23a;
  transition: background 0.2s;
}

.status-bar.is-bad {
  background: #fef0f0;
  color: #f56c6c;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: currentColor;
}

.refresh-icon {
  font-size: 13px;
  opacity: 0.6;
}

.refresh-icon:hover {
  opacity: 1;
}

.spinning {
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

/* --- 状态详情面板 --- */
.status-panel {
  font-size: 13px;
  color: #303133;
}

.panel-title {
  font-weight: 600;
  margin-bottom: 6px;
  color: #606266;
}

.panel-row {
  display: flex;
  justify-content: space-between;
  padding: 2px 0;
  color: #909399;
}

.panel-row b {
  color: #303133;
  font-weight: 500;
}

.panel-error {
  margin-top: 6px;
  color: #f56c6c;
  word-break: break-all;
  line-height: 1.5;
}

/* --- 右侧用户 --- */
.right-menu {
  display: flex;
  align-items: center;
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 8px;
  outline: none;
  transition: background 0.2s;
}

.user-trigger:hover {
  background: #f5f7fa;
}

.username {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

/* 有未读消息时头像右上角亮红点，不用点开菜单也知道有新消息 */
.avatar-wrap {
  position: relative;
  display: inline-flex;
}

.avatar-dot {
  position: absolute;
  top: 0;
  right: 0;
  width: 8px;
  height: 8px;
  background: #f56c6c;
  border: 1.5px solid #fff;
  border-radius: 50%;
}

.arrow {
  font-size: 12px;
  color: #909399;
}

/* --- 下拉里的「个人消息」 --- */
.menu-entry {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  min-width: 92px;
}

.unread-pill {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  font-size: 11px;
  font-weight: 600;
  line-height: 18px;
  text-align: center;
  color: #fff;
  background: #f56c6c;
  border-radius: 9px;
}

/* 下拉菜单是 teleport 到 body 的，scoped 选择器够不到，需要用 :global */
:global(.user-dropdown .el-dropdown-menu__item) {
  min-width: 158px;
  padding-right: 14px;
}

/* --- 个人信息抽屉 --- */
.profile-head {
  display: flex;
  align-items: center;
  gap: 14px;
  padding-bottom: 18px;
  margin-bottom: 18px;
  border-bottom: 1px solid #ebeef5;
}

.profile-name .name {
  font-size: 17px;
  font-weight: 600;
  color: #303133;
}

.profile-name .account {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}

.profile-desc :deep(.el-descriptions__label) {
  width: 92px;
  color: #909399;
}

.pre-wrap {
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
