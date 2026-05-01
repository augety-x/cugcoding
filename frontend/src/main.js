import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import axios from 'axios'
import App from './App.vue'
import Home from './pages/Home.vue'
import Login from './pages/Login.vue'
import PostDetail from './pages/PostDetail.vue'
import Publish from './pages/Publish.vue'
import ColumnList from './pages/ColumnList.vue'
import AdminDashboard from './pages/AdminDashboard.vue'
import SearchResults from './pages/SearchResults.vue'
import './style.css'

axios.defaults.baseURL = 'http://localhost:8080'
axios.defaults.withCredentials = true

const routes = [
  { path: '/', component: Home },
  { path: '/login', component: Login },
  { path: '/publish', component: Publish },
  { path: '/admin', component: AdminDashboard },
  { path: '/search', component: SearchResults },
  { path: '/columns/:id', component: ColumnList, props: true },
  { path: '/posts/:id', component: PostDetail, props: true }
]

const router = createRouter({ history: createWebHistory(), routes })

// Global code copy handler
window.__copyCode = function (btn) {
  const block = btn.closest('.code-block')
  const code = block ? block.querySelector('pre').textContent : ''
  navigator.clipboard.writeText(code).then(() => {
    const span = btn.querySelector('span')
    const orig = span.textContent
    span.textContent = '已复制!'
    btn.style.borderColor = '#28c840'
    setTimeout(() => {
      span.textContent = orig
      btn.style.borderColor = ''
    }, 2000)
  }).catch(() => {
    // Fallback for older browsers
    const textarea = document.createElement('textarea')
    textarea.value = code
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
    const span = btn.querySelector('span')
    span.textContent = '已复制!'
    setTimeout(() => { span.textContent = '复制' }, 2000)
  })
}

createApp(App).use(router).mount('#app')
