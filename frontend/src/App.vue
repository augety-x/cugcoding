<template>
  <div class="shell">
    <header class="topbar">
      <div class="topbar-brand">
        <div class="topbar-logo">C</div>
        <div>
          <h1 class="topbar-title">CUGCoding Forum</h1>
          <div class="topbar-subtitle">地大校园论坛</div>
        </div>
      </div>
      <nav class="topnav">
        <RouterLink to="/" class="nav-link">首页</RouterLink>
        <RouterLink v-if="user && user.role === 'ADMIN'" to="/admin" class="nav-link admin-link">管理后台</RouterLink>
        <RouterLink to="/publish" class="nav-link primary-action">发布文章</RouterLink>
        <template v-if="!user">
          <RouterLink to="/login" class="nav-link">登录</RouterLink>
        </template>
        <div v-else class="nav-user">
          <div class="user-chip">
            <div class="user-avatar">{{ user.username.charAt(0).toUpperCase() }}</div>
            <span>{{ user.username }}</span>
          </div>
          <button class="btn btn-sm ghost" @click="logout">退出</button>
        </div>
      </nav>
    </header>
    <main class="container">
      <RouterView v-slot="{ Component }">
        <Transition name="page" mode="out-in">
          <component :is="Component" />
        </Transition>
      </RouterView>
    </main>

    <!-- Toast Container -->
    <div class="toast-container">
      <TransitionGroup name="toast">
        <div v-for="toast in toasts" :key="toast.id" :class="['toast', toast.type]">
          <span v-if="toast.type === 'success'">&#10003;</span>
          <span v-else-if="toast.type === 'error'">&#10007;</span>
          <span v-else>&#9432;</span>
          {{ toast.message }}
        </div>
      </TransitionGroup>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { sessionState } from './stores/session'

const router = useRouter()
const user = computed(() => sessionState.user)
const toasts = ref([])
let toastId = 0

// Expose toast globally
window.__toast = (message, type = 'info') => {
  const id = ++toastId
  toasts.value.push({ id, message, type })
  setTimeout(() => {
    toasts.value = toasts.value.filter(t => t.id !== id)
  }, 3500)
}

async function syncSession() {
  try {
    const { data } = await axios.get('/api/me', { withCredentials: true })
    sessionState.user = data.id ? { id: data.id, username: data.username, role: data.role } : null
  } catch (e) {
    sessionState.user = null
  }
}

async function logout() {
  try {
    await axios.post('/api/logout', {}, { withCredentials: true })
    sessionState.user = null
    window.__toast?.('已退出登录', 'info')
    await router.push('/')
  } catch (e) {
    window.__toast?.('退出失败', 'error')
  }
}

onMounted(syncSession)
</script>

<style>
/* Page transition */
.page-enter-active,
.page-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.page-enter-from { opacity: 0; transform: translateY(6px); }
.page-leave-to { opacity: 0; transform: translateY(-6px); }

/* Admin link */
.admin-link { color: #ffa502 !important; font-weight: 600; }
.admin-link:hover { color: #ff6348 !important; }

/* Toast transition */
.toast-enter-active { transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1); }
.toast-leave-active { transition: all 0.2s ease-in; }
.toast-enter-from { opacity: 0; transform: translateX(24px); }
.toast-leave-to { opacity: 0; transform: translateX(24px); }
</style>
