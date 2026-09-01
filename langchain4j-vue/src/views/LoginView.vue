<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../stores/user'
import { login, register } from '../api/auth'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const mode = ref('login')
const loading = ref(false)
const formRef = ref()

const form = reactive({
  username: '',
  password: '',
  nickname: '',
})

/** 用户名/密码基础校验，注册时补充昵称校验 */
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' },
  ],
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
}

/** 提交登录或注册，成功后统一跳转回原目标页 */
const handleSubmit = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    if (mode.value === 'register') {
      await register(form)
      ElMessage.success('注册成功，请登录')
      mode.value = 'login'
    } else {
      const data = await userStore.login(form)
      ElMessage.success(`欢迎回来，${data.nickname || data.username}`)
      router.push(String(route.query.redirect || '/kb'))
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-shape shape-a"></div>
    <div class="auth-shape shape-b"></div>
    <div class="auth-card">
      <div class="auth-brand">
        <div class="auth-logo"><el-icon><Collection /></el-icon></div>
        <div>
          <h1 class="auth-title">企业 AI 知识问答库</h1>
          <p class="auth-subtitle">{{ mode === 'login' ? '登录后开始组织与检索企业知识' : '创建企业工作账号' }}</p>
        </div>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" size="large" @submit.prevent="handleSubmit">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" autocomplete="username">
            <template #prefix><el-icon><User /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-form-item v-if="mode === 'register'" prop="nickname">
          <el-input v-model="form.nickname" placeholder="昵称" autocomplete="nickname">
            <template #prefix><el-icon><Postcard /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="密码" autocomplete="current-password">
            <template #prefix><el-icon><Lock /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-button class="auth-button" type="primary" size="large" :loading="loading" native-type="submit">
          {{ mode === 'login' ? '登录' : '注册' }}
        </el-button>
      </el-form>

      <div class="auth-switch">
        {{ mode === 'login' ? '还没有账号？' : '已有账号？' }}
        <el-button link type="primary" @click="mode = mode === 'login' ? 'register' : 'login'">
          {{ mode === 'login' ? '立即注册' : '返回登录' }}
        </el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  position: relative;
  display: grid;
  place-items: center;
  min-height: 100dvh;
  padding: 24px 16px;
  overflow-y: auto;
  background:
    radial-gradient(circle at 12% 10%, rgba(37, 99, 235, 0.34), transparent 32%),
    radial-gradient(circle at 88% 90%, rgba(8, 145, 178, 0.28), transparent 28%),
    linear-gradient(135deg, #0b1220 0%, #172554 55%, #0f3d56 100%);
}

.auth-page::before {
  position: absolute;
  inset: 0;
  background-image: linear-gradient(rgba(148, 163, 184, 0.06) 1px, transparent 1px),
    linear-gradient(90deg, rgba(148, 163, 184, 0.06) 1px, transparent 1px);
  background-size: 42px 42px;
  content: '';
  mask-image: linear-gradient(to bottom, black, transparent 78%);
  pointer-events: none;
}

.auth-shape {
  position: absolute;
  border-radius: 50%;
  filter: blur(22px);
  opacity: 0.24;
  pointer-events: none;
}

.shape-a {
  width: 420px;
  height: 420px;
  top: -150px;
  left: -120px;
  background: #2563eb;
}

.shape-b {
  width: 360px;
  height: 360px;
  right: -120px;
  bottom: -160px;
  background: #0891b2;
}

.auth-card {
  position: relative;
  width: min(440px, 100%);
  padding: 40px;
  border: 1px solid rgba(255, 255, 255, 0.72);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.97);
  box-shadow: 0 28px 80px rgba(2, 8, 23, 0.36);
  color: var(--ink-900);
}

.auth-brand {
  display: flex;
  gap: 14px;
  align-items: center;
  margin-bottom: 30px;
}

.auth-logo {
  display: grid;
  width: 48px;
  height: 48px;
  flex: 0 0 48px;
  place-items: center;
  border-radius: 14px;
  color: #fff;
  background: linear-gradient(145deg, #1d4ed8, #0891b2);
  box-shadow: 0 8px 20px rgba(29, 78, 216, 0.25);
}

.auth-title {
  margin: 0;
  color: #0f172a;
  font-size: 22px;
  font-weight: 750;
  letter-spacing: -0.03em;
}

.auth-subtitle {
  margin: 5px 0 0;
  color: #64748b;
  font-size: 13px;
}

.auth-button {
  width: 100%;
  min-height: 44px;
  margin-top: 8px;
  border: 0;
  border-radius: 10px;
  color: #fff;
  background: linear-gradient(135deg, #1d4ed8, #2563eb);
  box-shadow: 0 8px 18px rgba(37, 99, 235, 0.24);
  font-weight: 700;
  transition: transform 160ms ease-out, box-shadow 160ms ease-out;
}

.auth-button:hover {
  color: #fff;
  background: linear-gradient(135deg, #1e40af, #1d4ed8);
  box-shadow: 0 10px 22px rgba(37, 99, 235, 0.34);
  transform: translateY(-1px);
}

.auth-switch {
  display: flex;
  min-height: 44px;
  gap: 4px;
  align-items: center;
  justify-content: center;
  margin-top: 20px;
  color: #64748b;
  font-size: 14px;
  text-align: center;
}

.auth-switch :deep(.el-button) {
  min-height: 40px;
  padding: 8px 12px;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  color: #1d4ed8;
  background: #eff6ff;
  font-weight: 650;
}

.auth-switch :deep(.el-button:hover),
.auth-switch :deep(.el-button:focus-visible) {
  border-color: #2563eb;
  color: #1e40af;
  background: #dbeafe;
}

.auth-card :deep(.el-form-item) {
  margin-bottom: 18px;
}

.auth-card :deep(.el-input__wrapper) {
  min-height: 44px;
  background: #f8fafc;
  box-shadow: 0 0 0 1px #e2e8f0 inset;
  transition: box-shadow 160ms ease-out, transform 160ms ease-out;
}

.auth-card :deep(.el-input__wrapper.is-focus) {
  transform: translateY(-1px);
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.45) inset;
}

@media (max-width: 480px) {
  .auth-card {
    padding: 28px 22px;
    border-radius: 20px;
  }

  .auth-title {
    font-size: 20px;
  }
}
</style>
