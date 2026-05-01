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
      <form v-if="showSearch" class="global-search" @submit.prevent="doSearch">
        <input v-model="searchQuery" placeholder="搜索文章、教程、技术话题..." />
        <button type="submit">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
        </button>
      </form>
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
import { useRouter, useRoute } from 'vue-router'
import axios from 'axios'
import { sessionState } from './stores/session'

const router = useRouter()
const route = useRoute()
const user = computed(() => sessionState.user)
const toasts = ref([])
const searchQuery = ref('')
let toastId = 0

const showSearch = computed(() => !['/login', '/publish', '/admin'].includes(route.path))

function doSearch() {
  const q = searchQuery.value.trim()
  if (q) router.push({ path: '/search', query: { q } })
}

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

/* Global search */
.global-search {
  display: flex;
  align-items: center;
  max-width: 1200px;
  margin: 0 auto 20px;
  background: #fff;
  border: 1px solid #e0e0e0;
  border-radius: 10px;
  padding: 4px 4px 4px 16px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
  transition: border-color 0.2s, box-shadow 0.2s;
}
.global-search:focus-within {
  border-color: #333;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
}
.global-search input {
  flex: 1;
  border: none;
  outline: none;
  font-size: 14px;
  padding: 10px 8px;
  background: transparent;
  color: #333;
}
.global-search input::placeholder { color: #bbb; }
.global-search button {
  padding: 8px 12px;
  background: none;
  border: none;
  color: #999;
  cursor: pointer;
  display: flex;
  align-items: center;
}
.global-search button:hover { color: #333; }

/* Admin link */
.admin-link { color: #ffa502 !important; font-weight: 600; }
.admin-link:hover { color: #ff6348 !important; }

/* Toast transition */
.toast-enter-active { transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1); }
.toast-leave-active { transition: all 0.2s ease-in; }
.toast-enter-from { opacity: 0; transform: translateX(24px); }
.toast-leave-to { opacity: 0; transform: translateX(24px); }
</style>
