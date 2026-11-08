<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { homePathFor } from '@/utils/role'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  username: 'admin',
  password: ''
})

const rules: FormRules<typeof form> = {
  username: [{ required: true, message: '请输入登录账号', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 64, message: '密码长度需在 6-64 个字符之间', trigger: 'blur' }
  ]
}

async function handleSubmit() {
  if (!formRef.value) return

  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await userStore.login({ username: form.username.trim(), password: form.password })
    ElMessage.success('登录成功')

    // 登录前想去的页面（由守卫带过来），没有就按角色回首页：
    // 总经理/销售主管 → 数据概览，其他人 → 工作台
    const redirect =
      typeof route.query.redirect === 'string'
        ? route.query.redirect
        : homePathFor(userStore.userInfo)
    await router.replace(redirect)
  } catch {
    // 失败提示由请求拦截器统一弹出（如「账号或密码不正确」）
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <!-- 左侧品牌区 -->
      <aside class="brand">
        <div class="brand-logo">F</div>
        <h1>手办管家</h1>
        <p class="brand-sub">Figure ERP</p>
        <ul class="brand-points">
          <li>手办档案与库存一体化管理</li>
          <li>仓库 · 货架 · 库位可视化存放</li>
          <li>入库出库全程留痕</li>
        </ul>
      </aside>

      <!-- 右侧表单区 -->
      <main class="form-panel">
        <h2>员工登录</h2>
        <p class="form-sub">请使用你的员工账号登录系统</p>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          size="large"
          label-position="top"
          @submit.prevent="handleSubmit"
        >
          <el-form-item prop="username">
            <el-input
              v-model="form.username"
              placeholder="登录账号"
              :prefix-icon="User"
              clearable
              autocomplete="username"
            />
          </el-form-item>

          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="登录密码"
              :prefix-icon="Lock"
              show-password
              autocomplete="current-password"
              @keyup.enter="handleSubmit"
            />
          </el-form-item>

          <el-button
            type="primary"
            class="submit-btn"
            :loading="loading"
            native-type="submit"
            @click="handleSubmit"
          >
            登 录
          </el-button>
        </el-form>

        <p class="tip">初始账号 admin / admin123，登录后请及时修改密码</p>
      </main>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1f2a44 0%, #2f4a6d 50%, #3b6ea5 100%);
}

.login-card {
  width: 860px;
  max-width: calc(100vw - 32px);
  min-height: 460px;
  display: flex;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.35);
  background: #fff;
}

/* 左侧品牌 */
.brand {
  width: 44%;
  padding: 48px 36px;
  color: #fff;
  background: linear-gradient(160deg, #2b4a75 0%, #1c3355 100%);
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.brand-logo {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.16);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  font-weight: 700;
  margin-bottom: 20px;
}

.brand h1 {
  margin: 0;
  font-size: 26px;
  letter-spacing: 2px;
}

.brand-sub {
  margin: 6px 0 28px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.6);
  letter-spacing: 1px;
}

.brand-points {
  margin: 0;
  padding: 0;
  list-style: none;
  font-size: 13px;
  line-height: 2.1;
  color: rgba(255, 255, 255, 0.82);
}

.brand-points li::before {
  content: '·';
  margin-right: 8px;
  color: #7fb3ff;
}

/* 右侧表单 */
.form-panel {
  flex: 1;
  padding: 56px 48px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.form-panel h2 {
  margin: 0;
  font-size: 22px;
  color: #1f2d3d;
}

.form-sub {
  margin: 8px 0 28px;
  font-size: 13px;
  color: #909399;
}

.submit-btn {
  width: 100%;
  letter-spacing: 4px;
  margin-top: 4px;
}

.tip {
  margin-top: 20px;
  font-size: 12px;
  color: #b0b3b8;
  text-align: center;
}

@media (max-width: 720px) {
  .brand {
    display: none;
  }
  .login-card {
    min-height: auto;
  }
}
</style>
