<template>
  <section class="column-page animate-in">
    <div class="card" style="margin-bottom: 24px;">
      <div class="section-title">
        <div>
          <h2>{{ column?.name || '专栏文章' }}</h2>
          <p class="meta" style="margin-top:4px;">{{ column?.description }}</p>
        </div>
        <RouterLink class="btn ghost" to="/">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="15 18 9 12 15 6"/></svg>
          返回首页
        </RouterLink>
      </div>
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
        <p class="meta">作者 {{ post.authorName }} · {{ post.createdAt }}</p>
        <div class="markdown-body article-excerpt" v-html="renderMarkdown(excerpt(post.content), post.title)"></div>
      </RouterLink>
    </div>

    <!-- Empty state -->
    <div v-else class="empty-state card">
      <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
      <h3>暂无文章</h3>
      <p>该专栏下还没有文章。</p>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import axios from 'axios'
import { columns } from '../data/columns'
import { renderMarkdown } from '../utils/markdown'

const route = useRoute()
const posts = ref([])
const loading = ref(true)
const column = computed(() => columns.find(c => c.id === route.params.id))

function excerpt(content) {
  return content.length > 180 ? `${content.slice(0, 180)}...` : content
}

async function loadPosts() {
  try {
    loading.value = true
    const { data } = await axios.get('/api/posts')
    posts.value = data
  } catch (e) {
    window.__toast?.('加载专栏文章失败', 'error')
  } finally {
    loading.value = false
  }
}

onMounted(loadPosts)
</script>
