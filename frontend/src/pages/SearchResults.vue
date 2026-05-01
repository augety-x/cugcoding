<template>
  <div class="search-page animate-in">
    <div class="search-header">
      <h2 v-if="keyword">搜索：{{ keyword }}</h2>
      <p class="search-count" v-if="!loading">{{ total }} 条结果</p>
    </div>

    <!-- Loading -->
    <div v-if="loading" style="padding:32px;text-align:center;color:#999">搜索中...</div>

    <!-- Results -->
    <div v-else-if="items.length" class="article-grid">
      <RouterLink v-for="post in items" :key="post.id"
        :to="`/posts/${post.id}`" class="article-card panel compact-card card-link">
        <h3 v-html="highlight(post.title)"></h3>
        <p class="meta">
          作者 {{ post.authorName }} · {{ formatDate(post.createdAt) }}
          · {{ post.viewCount || 0 }} 阅读 · {{ post.likeCount || 0 }} 赞
        </p>
        <div class="article-excerpt" v-html="highlightExcerpt(post.content)"></div>
      </RouterLink>
    </div>

    <!-- Empty -->
    <div v-else class="empty-state panel">
      <h3>未找到相关文章</h3>
      <p>尝试使用其他关键词搜索，或返回首页浏览全部文章。</p>
      <RouterLink to="/" class="btn primary" style="margin-top:16px">返回首页</RouterLink>
    </div>

    <!-- Pagination -->
    <div v-if="total > size" class="pagination">
      <button :disabled="page <= 1" @click="goPage(page - 1)">上一页</button>
      <span>第 {{ page }} / {{ totalPages }} 页</span>
      <button :disabled="page >= totalPages" @click="goPage(page + 1)">下一页</button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter, RouterLink } from 'vue-router'
import axios from 'axios'

const route = useRoute()
const router = useRouter()

const keyword = ref('')
const items = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))

function doSearch() {
  const q = route.query.q
  if (!q) { items.value = []; total.value = 0; return }
  keyword.value = q
  page.value = parseInt(route.query.page) || 1
  loading.value = true
  axios.get('/api/search', { params: { q, page: page.value, size: size.value } })
    .then(({ data }) => {
      items.value = data.items || []
      total.value = data.total || 0
    })
    .catch(() => { items.value = []; total.value = 0 })
    .finally(() => { loading.value = false })
}

function goPage(p) {
  router.push({ path: '/search', query: { q: keyword.value, page: p } })
}

/** Highlight keyword matches in text. */
function highlight(text) {
  if (!keyword.value || !text) return text
  const escaped = keyword.value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  return text.replace(new RegExp(`(${escaped})`, 'gi'), '<mark>$1</mark>')
}

/** Show a short excerpt with keyword highlighted. */
function highlightExcerpt(content) {
  if (!content) return ''
  const idx = content.toLowerCase().indexOf(keyword.value.toLowerCase())
  const start = Math.max(0, idx - 40)
  const end = Math.min(content.length, idx + keyword.value.length + 60)
  let snippet = content.substring(start, end)
  if (start > 0) snippet = '...' + snippet
  if (end < content.length) snippet += '...'
  return highlight(snippet)
}

function formatDate(s) {
  if (!s) return ''
  try { return new Date(s).toLocaleDateString('zh-CN') } catch { return s }
}

watch(() => route.query.q, doSearch, { immediate: true })
watch(() => route.query.page, doSearch)
</script>

<style scoped>
.search-page { max-width: 800px; margin: 0 auto; padding: 24px; }
.search-header { margin-bottom: 20px; }
.search-header h2 { font-size: 24px; color: #111; }
.search-count { color: #666; font-size: 14px; margin-top: 4px; }

.article-card h3 { margin-bottom: 6px; font-size: 18px; }
.article-card h3 :deep(mark) { background: #fff3b0; color: #111; padding: 0 2px; border-radius: 2px; }
.article-excerpt { font-size: 14px; color: #555; line-height: 1.6; margin-top: 8px; }
.article-excerpt :deep(mark) { background: #fff3b0; color: #111; padding: 0 2px; border-radius: 2px; }

.pagination { display: flex; align-items: center; justify-content: center; gap: 16px; margin-top: 24px; padding: 16px; }
.pagination button { padding: 8px 20px; border: 1px solid #ddd; background: #fff; border-radius: 6px; cursor: pointer; font-size: 14px; }
.pagination button:hover:not(:disabled) { border-color: #667eea; color: #667eea; }
.pagination button:disabled { opacity: 0.4; cursor: default; }
.pagination span { font-size: 14px; color: #666; }
</style>
