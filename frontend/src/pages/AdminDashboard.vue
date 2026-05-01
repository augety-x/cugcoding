<template>
  <div class="admin-dashboard">
    <h1>管理后台</h1>

    <!-- Stats Cards -->
    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-icon users">U</div>
        <div class="stat-body">
          <div class="stat-value">{{ stats.userCount }}</div>
          <div class="stat-label">注册用户</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon posts">P</div>
        <div class="stat-body">
          <div class="stat-value">{{ stats.postCount }}</div>
          <div class="stat-label">文章总数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon comments">C</div>
        <div class="stat-body">
          <div class="stat-value">{{ stats.commentCount }}</div>
          <div class="stat-label">评论总数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon visits">V</div>
        <div class="stat-body">
          <div class="stat-value">{{ stats.totalVisits }}</div>
          <div class="stat-label">网站总访问量</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon sessions">S</div>
        <div class="stat-body">
          <div class="stat-value">{{ stats.activeSessionCount }}</div>
          <div class="stat-label">当前在线</div>
        </div>
      </div>
      <div class="stat-card warn">
        <div class="stat-icon failed">F</div>
        <div class="stat-body">
          <div class="stat-value">{{ stats.failedLoginCount }}</div>
          <div class="stat-label">近7天失败登录</div>
        </div>
      </div>
    </div>

    <!-- Charts Row -->
    <div class="charts-row">
      <div class="chart-box">
        <h3>文章阅读量排行 Top 10</h3>
        <canvas ref="viewsChart"></canvas>
      </div>
      <div class="chart-box">
        <h3>文章点赞量排行 Top 10</h3>
        <canvas ref="likesChart"></canvas>
      </div>
    </div>

    <!-- Active Sessions -->
    <div class="section">
      <h3>
        {{ allUsers ? '所有活跃会话' : '我的活跃会话' }}
        <button v-if="userRole === 'ADMIN'" class="btn-sm" @click="toggleView">
          {{ allUsers ? '只看我的' : '查看所有' }}
        </button>
      </h3>
      <table v-if="sessions.length" class="data-table">
        <thead>
          <tr>
            <th>用户</th>
            <th>设备</th>
            <th>IP 地址</th>
            <th>登录时间</th>
            <th>最后活跃</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="s in sessions" :key="s.id">
            <td>{{ s.username }}</td>
            <td>{{ s.deviceInfo }}</td>
            <td><code>{{ s.ipAddress }}</code></td>
            <td>{{ fmt(s.loginTime) }}</td>
            <td>{{ fmt(s.lastSeenTime) }}</td>
            <td>
              <button class="btn-danger-sm" @click="kickSession(s.tokenJti)">
                踢出
              </button>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-else class="empty">暂无活跃会话</p>
    </div>

    <!-- Audit Log -->
    <div class="section">
      <h3>登录审计日志</h3>
      <table v-if="audits.length" class="data-table">
        <thead>
          <tr>
            <th>用户</th>
            <th>事件</th>
            <th>结果</th>
            <th>原因</th>
            <th>IP</th>
            <th>时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="a in audits" :key="a.id"
            :class="{ 'row-fail': a.result === 'FAIL' }">
            <td>{{ a.username || '-' }}</td>
            <td>
              <span class="tag" :class="'tag-' + a.action">{{ actionLabel(a.action) }}</span>
            </td>
            <td>
              <span class="tag" :class="a.result === 'SUCCESS' ? 'tag-success' : 'tag-fail'">
                {{ a.result === 'SUCCESS' ? '成功' : '失败' }}
              </span>
            </td>
            <td>{{ a.failReason || '-' }}</td>
            <td><code>{{ a.ipAddress }}</code></td>
            <td>{{ fmt(a.createdAt) }}</td>
          </tr>
        </tbody>
      </table>
      <p v-else class="empty">暂无审计记录</p>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, watch } from 'vue'
import axios from 'axios'
import { Chart, registerables } from 'chart.js'
Chart.register(...registerables)

const userRole = ref('')
const stats = ref({
  userCount: 0, postCount: 0, commentCount: 0, totalVisits: 0,
  activeSessionCount: 0, failedLoginCount: 0,
  topPostsByViews: [], topPostsByLikes: [], sessionsByDevice: []
})
const sessions = ref([])
const audits = ref([])
const allUsers = ref(false)
const viewsChart = ref(null)
const likesChart = ref(null)
let viewsChartInst = null
let likesChartInst = null

const actionLabel = (a) => ({
  REGISTER: '注册', LOGIN: '登录', LOGOUT: '登出',
  SESSION_TERMINATED: '会话终止'
}[a] || a)

const fmt = (t) => t ? t.replace('T', ' ').substring(0, 19) : '-'

async function loadUser() {
  try {
    const { data } = await axios.get('/api/me')
    userRole.value = data.role || ''
  } catch (_) { }
}

async function loadStats() {
  try {
    const { data } = await axios.get('/api/admin/stats')
    stats.value = data
    await nextTick()
    renderViewsChart()
    renderLikesChart()
  } catch (_) { }
}

async function loadSessions() {
  try {
    const url = allUsers.value ? '/api/admin/sessions' : '/api/sessions'
    const { data } = await axios.get(url)
    sessions.value = data
  } catch (_) { sessions.value = [] }
}

async function loadAudits() {
  try {
    const url = allUsers.value ? '/api/admin/audits' : '/api/audits'
    const { data } = await axios.get(url)
    audits.value = data
  } catch (_) { audits.value = [] }
}

async function kickSession(tokenJti) {
  if (!confirm('确认踢出该会话？')) return
  try {
    const url = allUsers.value
      ? `/api/admin/sessions/${tokenJti}/terminate`
      : `/api/sessions/${tokenJti}/terminate`
    await axios.post(url)
    await loadSessions()
    await loadStats()
  } catch (_) { }
}

function toggleView() {
  allUsers.value = !allUsers.value
  loadSessions()
  loadAudits()
}

function renderViewsChart() {
  if (viewsChartInst) viewsChartInst.destroy()
  const posts = stats.value.topPostsByViews || []
  const ctx = viewsChart.value?.getContext('2d')
  if (!ctx) return
  viewsChartInst = new Chart(ctx, {
    type: 'bar',
    data: {
      labels: posts.map(p => p.title.length > 15 ? p.title.substring(0, 15) + '...' : p.title),
      datasets: [{
        label: '阅读量', data: posts.map(p => p.view_count),
        backgroundColor: 'rgba(102,126,234,0.7)', borderColor: '#667eea', borderWidth: 1
      }]
    },
    options: {
      responsive: true, indexAxis: 'y',
      plugins: { legend: { display: false } },
      scales: { x: { beginAtZero: true, ticks: { stepSize: 1 } } }
    }
  })
}

function renderLikesChart() {
  if (likesChartInst) likesChartInst.destroy()
  const posts = stats.value.topPostsByLikes || []
  const ctx = likesChart.value?.getContext('2d')
  if (!ctx) return
  likesChartInst = new Chart(ctx, {
    type: 'bar',
    data: {
      labels: posts.map(p => p.title.length > 15 ? p.title.substring(0, 15) + '...' : p.title),
      datasets: [{
        label: '点赞数', data: posts.map(p => p.like_count),
        backgroundColor: 'rgba(255,71,87,0.7)', borderColor: '#ff4757', borderWidth: 1
      }]
    },
    options: {
      responsive: true, indexAxis: 'y',
      plugins: { legend: { display: false } },
      scales: { x: { beginAtZero: true, ticks: { stepSize: 1 } } }
    }
  })
}

onMounted(async () => {
  await loadUser()
  if (userRole.value === 'ADMIN') {
    await loadStats()
    allUsers.value = true
  }
  await loadSessions()
  await loadAudits()
})
</script>

<style scoped>
.admin-dashboard { max-width: 1200px; margin: 0 auto; padding: 24px; background: #f0f2f5; min-height: 100vh; }
.admin-dashboard h1 { font-size: 28px; margin-bottom: 24px; color: #111; font-weight: 700; }
.admin-dashboard h3 { font-size: 18px; margin-bottom: 12px; color: #111; font-weight: 700; }

.stats-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 16px; margin-bottom: 24px; }
.stat-card { background: #fff; border-radius: 12px; padding: 20px; display: flex; align-items: center; gap: 16px; box-shadow: 0 2px 8px rgba(0,0,0,0.08); }
.stat-card.warn { border-left: 4px solid #ff4757; }
.stat-icon { width: 48px; height: 48px; border-radius: 12px; display: flex; align-items: center; justify-content: center; font-size: 20px; font-weight: 700; color: #fff; }
.stat-icon.users { background: linear-gradient(135deg, #667eea, #764ba2); }
.stat-icon.posts { background: linear-gradient(135deg, #28c840, #20b2aa); }
.stat-icon.comments { background: linear-gradient(135deg, #ffa502, #ff6348); }
.stat-icon.visits { background: linear-gradient(135deg, #f093fb, #f5576c); }
.stat-icon.sessions { background: linear-gradient(135deg, #1e90ff, #00bcd4); }
.stat-icon.failed { background: linear-gradient(135deg, #ff4757, #ff6b81); }
.stat-value { font-size: 28px; font-weight: 700; color: #111; }
.stat-label { font-size: 13px; color: #555; font-weight: 500; margin-top: 4px; }

.charts-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 24px; }
@media (max-width: 768px) { .charts-row { grid-template-columns: 1fr; } }
.chart-box { background: #fff; border-radius: 12px; padding: 20px; box-shadow: 0 2px 8px rgba(0,0,0,0.08); }

.section { background: #fff; border-radius: 12px; padding: 20px; margin-bottom: 24px; box-shadow: 0 2px 8px rgba(0,0,0,0.08); }

.data-table { width: 100%; border-collapse: collapse; }
.data-table th { text-align: left; padding: 10px 12px; border-bottom: 2px solid #ddd; font-size: 13px; color: #333; font-weight: 600; background: #fafafa; }
.data-table td { padding: 10px 12px; border-bottom: 1px solid #e8e8e8; font-size: 14px; color: #222; }
.data-table code { background: #eee; padding: 2px 6px; border-radius: 4px; font-size: 12px; color: #333; }
.row-fail { background: #fff0f0; }

.tag { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 12px; font-weight: 700; }
.tag-REGISTER { background: #d4edda; color: #155724; }
.tag-LOGIN { background: #d6eaf8; color: #0d47a1; }
.tag-LOGOUT { background: #ffeaa7; color: #7b5b00; }
.tag-SESSION_TERMINATED { background: #f8d7da; color: #721c24; }
.tag-success { background: #d4edda; color: #155724; }
.tag-fail { background: #f8d7da; color: #721c24; }

.btn-sm { margin-left: 12px; padding: 4px 12px; border: 1px solid #667eea; background: #fff; color: #667eea; border-radius: 6px; cursor: pointer; font-size: 13px; font-weight: 600; }
.btn-sm:hover { background: #667eea; color: #fff; }
.btn-danger-sm { padding: 4px 12px; border: 1px solid #d63031; background: #fff; color: #d63031; border-radius: 6px; cursor: pointer; font-size: 13px; font-weight: 600; }
.btn-danger-sm:hover { background: #d63031; color: #fff; }
.empty { color: #777; text-align: center; padding: 32px; font-size: 14px; }
</style>
