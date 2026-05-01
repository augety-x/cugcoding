# CUGCoding 校园论坛 —— 发布文章模块技术报告

> 日期：2026-05-01  
> 版本：v1.0

---

## 1. 模块概述

发布文章模块是 CUGCoding 校园论坛的**核心内容生产入口**。用户通过该模块撰写 Markdown 文章，编辑器支持**图片粘贴/拖拽自动上传至华为云 OBS**，并支持**导入本地 .md 文件**（自动处理内嵌 base64 图片上传）。提交后文章持久化至 MySQL，首页即可展示。

---

## 2. 文件清单

### 2.1 前端

| 文件 | 职责 |
|------|------|
| `frontend/src/pages/Publish.vue` | 发布页面：标题输入 + bytemd 编辑器 + MD 导入 + 图片上传 + 提交 |
| `frontend/package.json` | 依赖：`@bytemd/vue-next`, `@bytemd/plugin-gfm`, `@bytemd/plugin-highlight` |

### 2.2 后端

| 文件 | 职责 |
|------|------|
| `backend/.../web/ApiController.java` | `POST /api/posts` — 文章创建接口 |
| `backend/.../web/ImageController.java` | `POST /api/image/upload` — 图片上传 OBS |
| `backend/.../web/ImageController.java` | `POST /api/image/import-markdown` — MD 导入（服务端降级） |
| `backend/.../service/ForumService.java` | `createPost()` — 业务逻辑 |
| `backend/.../repo/ForumRepository.java` | `createPost()` — JDBC Template 写入 |
| `backend/.../oss/ObsUploader.java` | 华为云 OBS 客户端封装 |
| `backend/.../oss/ObsConfig.java` | OBS 配置类（`@ConfigurationProperties`） |
| `backend/src/main/resources/application.properties` | OBS endpoint/AK/SK/bucket 配置 |

---

## 3. 架构与数据流

### 3.1 撰写发布流程

```
用户输入标题
    ↓
bytemd 编辑器 (mode="split" 左编辑/右预览)
    │
    │  粘贴/拖拽图片 → uploadImages(files)
    │    → FormData → POST /api/image/upload (需登录)
    │      → ObsUploader.upload(bytes, type)
    │        → MD5 计算文件名 → ObsClient.putObject()
    │          → 返回 https://cugcoding.obs.cn-east-3.myhuaweicloud.com/forum/{md5}.png
    │            → 插入 ![alt](url)
    ↓
点击"发布文章"
    → POST /api/posts {title, content} (需登录)
      → ForumService.createPost(userId, title, content)
        → ForumRepository JDBC Template INSERT
          → 跳转首页
```

### 3.2 MD 文件导入流程

```
点击"导入 MD 文件" → <input type="file"> 选择 .md
    ↓
FileReader.readAsText() 浏览器本地读取（不经过后端）
    ↓
提取标题：正则匹配第一个 # heading
    ↓
扫描所有 ![alt](url) 图片引用，分类处理：
    │
    ├─ data:image/...;base64,...
    │   → atob() 解码 → Uint8Array → new File()
    │     → uploadSingleImage() → POST /api/image/upload
    │       → OBS URL → 替换原始 data URI
    │
    ├─ http(s)://...
    │   → 保留原样（浏览器直接加载）
    │
    └─ C:\... / ./... / 纯文件名
        → 替换为占位引用块：
          > ⚠️ 本地图片无法自动导入，请将图片拖拽到此处替换
          > 原始路径: `C:\Users\...\xxx.png`
    ↓
form.content = processedContent
form.title = extractedTitle
    ↓
Toast: "N 张已上传, M 张需手动替换"
```

---

## 4. 组件选型

### 4.1 bytemd vs md-editor-v3

| 对比项 | md-editor-v3（旧） | bytemd（新） |
|--------|-------------------|-------------|
| 分屏预览 | 需手动实现 | `mode="split"` 原生支持 |
| 图片上传 | 需自行封装 | `uploadImages` 回调原生支持 |
| 插件系统 | 有限 | GFM 表格/任务列表、代码高亮、数学公式等 |
| 生态 | 仅 Vue | React/Vue/Svelte 统一底层 |
| 字节跳动开源 | 否 | 是 |

### 4.2 华为云 OBS SDK

- 依赖：`com.huaweicloud:esdk-obs-java-bundle:3.22.3`
- 配置：`obs.endpoint/ak/sk/bucket/host/prefix` 通过 `application.properties` 注入
- AK/SK 使用 `${OBS_AK:}` / `${OBS_SK:}` 环境变量占位，不写入 Git

---

## 5. 安全约束

| 约束 | 实现 |
|------|------|
| 图片上传需登录 | `AuthContext.requireLogin()` |
| 文章发布需登录 | `AuthContext.requireLogin()` |
| 图片 ≤5MB | 前端 `file.size` 预检 + 后端 `MAX_SIZE` 校验 |
| 格式白名单 | PNG / JPG / GIF / WebP |
| 图片去重 | MD5 作为文件名，相同内容只存一份 |
| AK/SK 不提交 | `application.properties` 使用 `${OBS_AK:}` 占位 |

---

## 6. 文件结构一览

```
frontend/src/pages/Publish.vue         ← 发布文章页面（~250 行）
  ├─ 模板：标题 input + bytemd <Editor> + 发布按钮
  ├─ 脚本：triggerImport / handleMdImport / uploadImages / publish
  └─ 样式：bytemd 600px 高度、分屏布局、导入按钮

backend/.../web/ApiController.java     ← POST /api/posts
backend/.../web/ImageController.java   ← POST /api/image/upload
                                        ← POST /api/image/import-markdown
backend/.../oss/ObsUploader.java       ← OBS putObject 封装
backend/.../oss/ObsConfig.java         ← OBS 配置属性
```

---

## 7. 测试验证

| 测试场景 | 方法 | 结果 |
|---------|------|------|
| 登录后发布文章 | `POST /api/posts` + Cookie | 200, 返回 `{id: N}` |
| 未登录发布 | 无 Cookie | 400 "请先登录" |
| 上传 PNG 图片 | `POST /api/image/upload` + `-F image=@test.png` | 200, 返回 OBS URL |
| 未登录上传图片 | 无 Cookie | 400 "请先登录" |
| OBS 公开访问 | `curl OBS_URL` | HTTP 200 |
| 导入含 base64 的 .md | 前端 import → 自动上传 | base64 替换为 OBS URL |
| 导入含本地路径的 .md | 前端 import → 占位替换 | 显示 ⚠️ 提示块 |
| 单元测试 | `mvn test` | 36/36 pass |
