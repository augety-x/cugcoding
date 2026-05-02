<template>
  <div class="rag-page animate-in">
    <!-- Left panel: Knowledge sources -->
    <div class="rag-sidebar">
      <h3>知识库</h3>

      <!-- Article selection -->
      <div class="source-section">
        <h4>论坛文章</h4>
        <div v-if="articles.length" class="article-list">
          <label v-for="a in articles" :key="a.id" class="article-check">
            <input type="checkbox" :value="a.id" v-model="selectedArticles" />
            <span>{{ a.title }}</span>
          </label>
        </div>
        <p v-else class="muted">加载中...</p>
      </div>

      <!-- File upload -->
      <div class="source-section">
        <h4>上传文件</h4>
        <div class="upload-zone" @click="triggerFile" @dragover.prevent @drop.prevent="handleDrop">
          <p>点击或拖拽上传<br/>PDF / DOCX / TXT / MD</p>
        </div>
        <input ref="fileInput" type="file" accept=".pdf,.docx,.doc,.txt,.md" @change="handleFile" style="display:none" />
        <div v-if="uploadedFiles.length" class="file-list">
          <div v-for="f in uploadedFiles" :key="f" class="file-tag">📄 {{ f }}</div>
        </div>
        <button class="btn btn-sm" @click="rebuildIndex" :disabled="rebuilding" style="margin-top:8px;width:100%">
          {{ rebuilding ? '重建中...' : '重建知识库索引' }}
        </button>
      </div>
    </div>

    <!-- Right panel: Chat -->
    <div class="rag-chat">
      <div class="chat-messages" ref="msgContainer">
        <div v-if="messages.length === 0" class="chat-empty">
          <h2>CUGCoding AI 助手</h2>
          <p>选择左侧文章或上传文件，然后输入问题开始对话</p>
        </div>
        <div v-for="(m, i) in messages" :key="i" :class="['msg', m.role]">
          <div class="msg-avatar">{{ m.role === 'user' ? 'U' : 'AI' }}</div>
          <div class="msg-content markdown-body" v-html="renderMd(m.content)"></div>
        </div>
        <div v-if="streaming" class="msg assistant">
          <div class="msg-avatar">AI</div>
          <div class="msg-content markdown-body" v-html="renderMd(streamText)"></div>
        </div>
      </div>

      <form class="chat-input" @submit.prevent="sendMessage">
        <input v-model="input" placeholder="输入问题，AI 会基于选中的知识来回答..." :disabled="streaming" />
        <button type="submit" :disabled="!input.trim() || streaming">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M2 21l21-9L2 3v7l15 2-15 2v7z"/></svg>
        </button>
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import axios from 'axios'
import { renderMarkdown } from '../utils/markdown'

const articles = ref([])
const selectedArticles = ref([])
const uploadedFiles = ref([])
const messages = ref([])
const input = ref('')
const streaming = ref(false)
const streamText = ref('')
const rebuild = ref(false)
const fileInput = ref(null)
const msgContainer = ref(null)
let ws = null

function triggerFile() { fileInput.value?.click() }
function handleFile(e) { uploadFile(e.target.files[0]); e.target.value = '' }
function handleDrop(e) { const f = e.dataTransfer.files[0]; if (f) uploadFile(f) }

async function uploadFile(file) {
  if (!file) return
  const fd = new FormData(); fd.append('file', file)
  try {
    const { data } = await axios.post('/api/rag/upload', fd)
    uploadedFiles.value.push(data.filename)
    window.__toast?.(data.message, 'success')
  } catch (e) { window.__toast?.(e?.response?.data?.message || '上传失败', 'error') }
}

async function rebuildIndex() {
  rebuild.value = true
  try {
    const { data } = await axios.post('/api/rag/admin/rebuild')
    window.__toast?.(data.message, 'success')
  } catch (e) { window.__toast?.('重建失败', 'error') }
  finally { rebuild.value = false }
}

function sendMessage() {
  const q = input.value.trim()
  if (!q || streaming.value) return
  messages.value.push({ role: 'user', content: q })
  input.value = ''
  streaming.value = true
  streamText.value = ''
  scrollDown()

  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  const wsUrl = `${protocol}//localhost:8080/ws/rag/chat`
  ws = new WebSocket(wsUrl)
  ws.onopen = () => {
    ws.send(JSON.stringify({ query: q, articleIds: selectedArticles.value }))
  }
  ws.onmessage = (e) => {
    try {
      const data = JSON.parse(e.data)
      if (data.chunk) { streamText.value += data.chunk; scrollDown() }
      if (data.type === 'completion') {
        messages.value.push({ role: 'assistant', content: streamText.value })
        streamText.value = ''
        streaming.value = false
        ws.close()
      }
      if (data.error) {
        messages.value.push({ role: 'assistant', content: '⚠️ ' + data.error })
        streaming.value = false
        ws.close()
      }
    } catch (_) { }
  }
  ws.onerror = () => {
    messages.value.push({ role: 'assistant', content: '⚠️ 连接失败，请确保后端已启动' })
    streaming.value = false
  }
}

function scrollDown() {
  nextTick(() => {
    const el = msgContainer.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

async function loadSources() {
  try {
    const { data } = await axios.get('/api/rag/sources')
    articles.value = data.articles || []
  } catch (_) { }
}

function renderMd(text) {
  try { return renderMarkdown(text || '') } catch { return text }
}

onMounted(loadSources)
</script>

<style scoped>
.rag-page { display: flex; height: calc(100vh - 60px); max-width: 1400px; margin: 0 auto; }
.rag-sidebar { width: 300px; background: #f8f9fa; border-right: 1px solid #eee; padding: 16px; overflow-y: auto; flex-shrink: 0; }
.rag-sidebar h3 { font-size: 16px; margin-bottom: 12px; color: #111; }
.rag-sidebar h4 { font-size: 13px; color: #666; margin: 12px 0 6px; }

.source-section { margin-bottom: 16px; }
.article-list { max-height: 250px; overflow-y: auto; }
.article-check { display: flex; align-items: flex-start; gap: 6px; padding: 4px 0; font-size: 13px; cursor: pointer; }
.article-check input { margin-top: 3px; flex-shrink: 0; }
.article-check span { line-height: 1.4; }

.upload-zone { border: 2px dashed #ddd; border-radius: 8px; padding: 16px; text-align: center; cursor: pointer; transition: border-color 0.2s; font-size: 13px; color: #888; }
.upload-zone:hover { border-color: #667eea; color: #667eea; }
.file-list { margin-top: 8px; }
.file-tag { font-size: 12px; padding: 2px 8px; background: #e8f0fe; border-radius: 4px; margin: 2px 0; color: #1a73e8; }

.rag-chat { flex: 1; display: flex; flex-direction: column; background: #fff; }
.chat-messages { flex: 1; overflow-y: auto; padding: 20px; }
.chat-empty { text-align: center; padding: 80px 20px; color: #999; }
.chat-empty h2 { font-size: 24px; color: #333; margin-bottom: 8px; }

.msg { display: flex; gap: 10px; margin-bottom: 16px; }
.msg.user { flex-direction: row-reverse; }
.msg.user .msg-content { background: #667eea; color: #fff; border-radius: 12px 12px 4px 12px; }
.msg.assistant .msg-content { background: #f0f2f5; color: #222; border-radius: 12px 12px 12px 4px; }
.msg-avatar { width: 32px; height: 32px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 13px; font-weight: 700; flex-shrink: 0; }
.msg.user .msg-avatar { background: #667eea; color: #fff; }
.msg.assistant .msg-avatar { background: #e0e0e0; color: #555; }
.msg-content { max-width: 75%; padding: 10px 14px; font-size: 14px; line-height: 1.6; }
.msg-content :deep(p) { margin: 0 0 4px; }
.msg-content :deep(pre) { background: rgba(0,0,0,0.05); padding: 8px; border-radius: 6px; overflow-x: auto; font-size: 12px; }
.msg.user .msg-content :deep(pre) { background: rgba(255,255,255,0.15); }

.chat-input { display: flex; gap: 8px; padding: 12px 20px; border-top: 1px solid #eee; }
.chat-input input { flex: 1; padding: 10px 16px; border: 1px solid #ddd; border-radius: 24px; font-size: 14px; outline: none; }
.chat-input input:focus { border-color: #667eea; }
.chat-input button { width: 42px; height: 42px; border: none; border-radius: 50%; background: #667eea; color: #fff; cursor: pointer; display: flex; align-items: center; justify-content: center; }
.chat-input button:disabled { background: #ccc; cursor: default; }
.muted { color: #999; font-size: 13px; }
</style>
