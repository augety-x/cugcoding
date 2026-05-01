<template>
  <div class="home-page animate-in">
    <section class="home-layout">
      <div class="article-zone">
        <div class="section-title">
          <h2>精选文章</h2>
          <RouterLink to="/publish" class="btn primary">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
            发布文章
          </RouterLink>
        </div>

        <!-- Loading skeleton -->
        <div v-if="loading" class="article-grid">
          <div v-for="n in 3" :key="n" class="skeleton skeleton-card"></div>
        </div>

        <!-- Post list -->
        <div v-else-if="posts.length" class="article-grid">
          <RouterLink
            v-for="(post, idx) in posts"
            :key="post.id"
            :to="`/posts/${post.id}`"
            class="article-card panel compact-card card-link"
            :style="{ animationDelay: `${idx * 50}ms` }"
          >
            <h3>{{ post.title }}</h3>
            <p class="meta">作者 {{ post.authorName }} · {{ formatDate(post.createdAt) }} · {{ post.viewCount || 0 }} 阅读 · {{ post.likeCount || 0 }} 赞</p>
            <div class="markdown-body article-excerpt" v-html="renderMarkdown(excerpt(post.content), post.title)"></div>
          </RouterLink>
        </div>

        <!-- Empty state -->
        <div v-else class="empty-state panel">
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>
          <h3>还没有文章</h3>
          <p>成为第一个发布文章的人吧！点击右上角"发布文章"开始创作。</p>
        </div>
      </div>

      <aside class="column-zone">
        <div class="section-title">
          <h2>精选专栏</h2>
        </div>
        <RouterLink
          v-for="column in columns"
          :key="column.id"
          class="panel column-card"
          :to="`/columns/${column.id}`"
        >
          <h3>{{ column.name }}</h3>
          <p>{{ column.description }}</p>
        </RouterLink>
      </aside>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import axios from 'axios'
import { columns } from '../data/columns'
import { renderMarkdown } from '../utils/markdown'

const posts = ref([])
const loading = ref(true)

function excerpt(content) {
  return content.length > 180 ? `${content.slice(0, 180)}...` : content
}

function formatDate(dateStr) {
  if (!dateStr) return ''
  try {
    const d = new Date(dateStr)
    const now = new Date()
    const diff = now - d
    if (diff < 3600000) return `${Math.floor(diff / 60000)} 分钟前`
    if (diff < 86400000) return `${Math.floor(diff / 3600000)} 小时前`
    if (diff < 604800000) return `${Math.floor(diff / 86400000)} 天前`
    return d.toLocaleDateString('zh-CN')
  } catch { return dateStr }
}

async function loadPosts() {
  try {
    loading.value = true
    const { data } = await axios.get('/api/posts')
    posts.value = data
  } catch (e) {
    window.__toast?.('加载文章失败', 'error')
  } finally {
    loading.value = false
  }
}

onMounted(loadPosts)
</script>
