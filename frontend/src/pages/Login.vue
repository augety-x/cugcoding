<template>
  <section class="card auth-card animate-in">
    <div class="section-title">
      <h2>账号登录</h2>
      <span>登录或注册，即刻加入校园论坛</span>
    </div>

    <form class="auth-form" @submit.prevent>
      <div class="form-group">
        <label class="form-label" for="username">用户名</label>
        <input
          id="username"
          v-model="form.username"
          placeholder="请输入用户名"
          autocomplete="username"
        />
      </div>
      <div class="form-group">
        <label class="form-label" for="password">密码</label>
        <input
          id="password"
          v-model="form.password"
          type="password"
          placeholder="请输入密码"
          autocomplete="current-password"
        />
      </div>

      <div class="auth-actions">
        <button class="btn primary btn-lg" :disabled="loading" @click="mode='login'; submit()">
          <span v-if="loading && mode === 'login'" class="spinner"></span>
          {{ loading && mode === 'login' ? '登录中...' : '登录' }}
        </button>
        <button class="btn btn-lg" :disabled="loading" @click="mode='register'; submit()">
          <span v-if="loading && mode === 'register'" class="spinner"></span>
          {{ loading && mode === 'register' ? '注册中...' : '注册' }}
        </button>
      </div>
    </form>

    <div v-if="message" :class="['auth-message', messageType]">{{ message }}</div>
  </section>
</template>

<script setup>
import { reactive, ref } from 'vue'
import axios from 'axios'
import { useRouter } from 'vue-router'
import { sessionState } from '../stores/session'

const router = useRouter()
const form = reactive({ username: '', password: '' })
const message = ref('')
const messageType = ref('info')
const mode = ref('login')
const loading = ref(false)

async function submit() {
  if (!form.username.trim() || !form.password.trim()) {
    message.value = '请填写用户名和密码'
    messageType.value = 'error'
    return
  }
  try {
    loading.value = true
    message.value = ''
    const url = mode.value === 'login' ? '/api/login' : '/api/register'
    const { data } = await axios.post(url, form, { withCredentials: true })
    sessionState.user = data
    message.value = `${mode.value === 'login' ? '登录' : '注册'}成功，欢迎 ${data.username}`
    messageType.value = 'success'
    window.__toast?.(`${mode.value === 'login' ? '登录' : '注册'}成功`, 'success')
    setTimeout(() => router.push('/'), 400)
  } catch (e) {
    const errMsg = e?.response?.data?.message || '操作失败，请重试'
    message.value = errMsg
    messageType.value = 'error'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.spinner {
  display: inline-block;
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255,255,255,0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
</style>
