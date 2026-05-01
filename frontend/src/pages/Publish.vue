<template>
  <section class="card publish-page animate-in">
    <div class="section-title">
      <h2>发布文章</h2>
      <div style="display:flex;gap:8px;align-items:center">
        <button type="button" class="btn btn-sm import-btn" @click="triggerImport">
          导入 MD 文件
        </button>
        <input ref="fileInput" type="file" accept=".md,.markdown,.txt" @change="handleMdImport" style="position:absolute;left:-9999px;width:1px;height:1px;overflow:hidden" />
        <RouterLink class="btn ghost" to="/">取消</RouterLink>
      </div>
    </div>

    <div class="publish-layout-full">
      <form class="panel editor-panel" @submit.prevent="publish">
        <div class="form-group">
          <label class="form-label" for="post-title">文章标题</label>
          <input
            id="post-title"
            v-model="form.title"
            placeholder="输入一个吸引人的标题..."
          />
        </div>
        <div class="form-group">
          <label class="form-label">文章内容（支持 Markdown，可直接粘贴图片或拖拽图片上传）</label>
          <Editor
            :value="form.content"
            :plugins="plugins"
            :upload-images="uploadImages"
            mode="split"
            placeholder="写下文章内容...&#10;&#10;提示：可以直接粘贴剪贴板中的图片，或拖拽图片到编辑区，也可以导入 .md 文件。"
            @change="handleChange"
          />
        </div>
        <div class="row gap">
          <button class="btn primary btn-lg" :disabled="publishing || !form.title.trim()">
            <span v-if="publishing" class="spinner"></span>
            {{ publishing ? '发布中...' : '发布文章' }}
          </button>
        </div>
      </form>
    </div>
  </section>
</template>

<script setup>
import { reactive, ref } from 'vue'
import axios from 'axios'
import { useRouter, RouterLink } from 'vue-router'
import { Editor } from '@bytemd/vue-next'
import gfm from '@bytemd/plugin-gfm'
import highlight from '@bytemd/plugin-highlight'
import 'bytemd/dist/index.css'
import 'highlight.js/styles/github.css'

const router = useRouter()
const form = reactive({ title: '', content: '' })
const publishing = ref(false)
const fileInput = ref(null)

function triggerImport() {
  fileInput.value?.click()
}

const plugins = [gfm(), highlight()]

function handleChange(v) {
  form.content = v
}

/**
 * Upload images to Huawei OBS.
 * bytemd passes File[] directly (NOT wrapped in {file: ...} objects).
 * Called on: paste image from clipboard, drag-and-drop, toolbar upload.
 */
async function uploadImages(files) {
  const results = []
  for (const file of files) {
    if (file.size > 5 * 1024 * 1024) {
      window.__toast?.('图片大小不能超过 5MB', 'error')
      continue
    }
    const formData = new FormData()
    formData.append('image', file)
    try {
      const { data } = await axios.post('/api/image/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      })
      if (data.url) {
        results.push({ url: data.url, alt: file.name || 'image' })
      }
    } catch (e) {
      window.__toast?.(e?.response?.data?.message || '图片上传失败', 'error')
    }
  }
  return results
}

/** Import a .md file: upload to backend, which extracts images, uploads to OBS, returns processed markdown. */
async function handleMdImport(e) {
  const file = e.target.files?.[0]
  if (!file) return
  window.__toast?.('正在解析 Markdown 文件并上传图片...', 'info')
  try {
    const formData = new FormData()
    formData.append('file', file)
    const { data } = await axios.post('/api/image/import-markdown', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    // Use filename as default title (strip extension)
    if (!form.title.trim() && data.title) {
      form.title = data.title
    }
    form.content = data.content || ''
    window.__toast?.('Markdown 文件导入成功', 'success')
  } catch (e) {
    window.__toast?.(e?.response?.data?.message || '导入失败', 'error')
  } finally {
    e.target.value = '' // reset file input
  }
}

async function publish() {
  if (!form.title.trim()) {
    window.__toast?.('请输入文章标题', 'error')
    return
  }
  try {
    publishing.value = true
    await axios.post('/api/posts', { title: form.title, content: form.content })
    window.__toast?.('文章发布成功', 'success')
    form.title = ''
    form.content = ''
    setTimeout(() => router.push('/'), 400)
  } catch (e) {
    window.__toast?.(e?.response?.data?.message || '发布失败，请重试', 'error')
  } finally {
    publishing.value = false
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

.publish-layout-full { max-width: 100%; }
.publish-layout-full .editor-panel { padding: 20px; }

:deep(.bytemd) {
  height: 600px;
  border: 1px solid #ddd;
  border-radius: 8px;
  flex: 1;
}
:deep(.bytemd-toolbar) { border-bottom: 1px solid #eee; }
:deep(.bytemd-toolbar-icon:hover) { background: #f0f2f5; }
:deep(.bytemd-editor) { flex: 1; }
:deep(.bytemd-preview) { flex: 1; border-left: 1px solid #eee; }

.import-btn {
  cursor: pointer;
  border: 1px solid #667eea;
  color: #667eea;
}
.import-btn:hover { background: #667eea; color: #fff; }
</style>
