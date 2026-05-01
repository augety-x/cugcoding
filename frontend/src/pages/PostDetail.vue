<template>
  <section v-if="post" class="detail-page animate-in">
    <article class="card detail-hero">
      <p class="eyebrow">文章详情</p>
      <h1>{{ post.title }}</h1>
      <p class="meta">
        <span>作者 {{ post.authorName }}</span>
        <span class="meta-sep">·</span>
        <span>{{ post.createdAt }}</span>
        <span class="meta-sep">·</span>
        <span>{{ post.viewCount || 0 }} 次阅读</span>
        <span class="meta-sep">·</span>
        <button class="like-btn" :class="{ liked: post.liked }" @click="toggleLike">
          {{ post.liked ? '❤️' : '🤍' }} {{ post.likeCount || 0 }}
        </button>
      </p>
      <div class="markdown-body" v-html="renderedContent"></div>
    </article>

    <section class="card comment-card">
      <div class="section-title">
        <h2>评论区</h2>
        <span class="muted">{{ post.comments?.length || 0 }} 条评论</span>
      </div>

      <!-- Comment list -->
      <div v-if="post.comments?.length">
        <div v-for="comment in post.comments" :key="comment.id" class="comment-item">
          <div class="comment-header">
            <div class="comment-avatar">{{ comment.username?.charAt(0)?.toUpperCase() }}</div>
            <strong>{{ comment.username }}</strong>
          </div>
          <div class="markdown-body" v-html="renderMarkdown(comment.content)"></div>
        </div>
      </div>
      <div v-else class="empty-state" style="padding: 24px 0;">
        <p>暂无评论，来写下第一条评论吧</p>
      </div>

      <!-- Comment form -->
      <form class="comment-form" @submit.prevent="submitComment">
        <div class="form-group">
          <label class="form-label" for="comment">发表评论</label>
          <textarea
            id="comment"
            v-model="commentForm.content"
            rows="4"
            placeholder="写下你的评论，支持 Markdown 语法..."
          ></textarea>
        </div>
        <div class="comment-preview" v-if="commentForm.content">
          <h4>预览</h4>
          <div class="markdown-body" v-html="renderMarkdown(commentForm.content)"></div>
        </div>
        <button
          class="btn primary"
          :disabled="!commentForm.content.trim() || submitting"
        >
          <span v-if="submitting" class="spinner"></span>
          {{ submitting ? '发表中...' : '发表评论' }}
        </button>
      </form>
    </section>
  </section>

  <!-- Loading state -->
  <div v-else-if="loading" class="detail-page">
    <div class="skeleton" style="height: 200px;"></div>
    <div class="skeleton" style="height: 300px;"></div>
  </div>

  <!-- Error state -->
  <div v-else class="empty-state">
    <h3>文章不存在</h3>
    <p>该文章可能已被删除或不存在。</p>
    <RouterLink to="/" class="btn" style="margin-top:16px">返回首页</RouterLink>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import axios from 'axios'
import { useRoute, RouterLink } from 'vue-router'
import { renderMarkdown } from '../utils/markdown'

const route = useRoute()
const post = ref(null)
const loading = ref(true)
const submitting = ref(false)
const commentForm = reactive({ content: '' })

const renderedContent = computed(() => (post.value ? renderMarkdown(post.value.content || '', post.value.title) : ''))

async function loadPost() {
  try {
    loading.value = true
    const { data } = await axios.get(`/api/posts/${route.params.id}`)
    post.value = data
  } catch (e) {
    post.value = null
    window.__toast?.('加载文章失败', 'error')
  } finally {
    loading.value = false
  }
}

async function toggleLike() {
  try {
    const { data } = await axios.post(`/api/posts/${route.params.id}/like`)
    post.value.liked = data.liked
    post.value.likeCount += data.liked ? 1 : -1
  } catch (e) {
    window.__toast?.(e?.response?.data?.message || '操作失败', 'error')
  }
}

async function submitComment() {
  if (!commentForm.content.trim()) return
  try {
    submitting.value = true
    await axios.post(`/api/posts/${route.params.id}/comments`, { content: commentForm.content })
    commentForm.content = ''
    window.__toast?.('评论发表成功', 'success')
    await loadPost()
  } catch (e) {
    window.__toast?.(e?.response?.data?.message || '评论失败', 'error')
  } finally {
    submitting.value = false
  }
}

onMounted(loadPost)
</script>

<style scoped>
.like-btn {
  background: none; border: none; cursor: pointer; font-size: 14px;
  padding: 2px 8px; border-radius: 16px; transition: transform 0.15s;
}
.like-btn:hover { transform: scale(1.15); }
.like-btn.liked { background: #fff0f0; }

.comment-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}
.comment-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: linear-gradient(135deg, #6366f1, #a78bfa);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.7rem;
  color: #fff;
  font-weight: 700;
  flex-shrink: 0;
}
.meta-sep { margin: 0 6px; opacity: 0.4; }
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
