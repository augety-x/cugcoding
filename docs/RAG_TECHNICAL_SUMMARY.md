# 派聪明 RAG 系统 —— 技术总结

> 来源：技术派专栏 column/10/11-18（共 8 篇）  
> 爬取日期：2026-05-02

---

## 一、系统概述

派聪明是一个**基于 RAG（Retrieval-Augmented Generation）的私有知识库智能对话平台**。用户上传文档构建专属知识空间，通过自然语言与知识库交互提问，AI 基于检索到的文档内容精准回答。

**核心能力**：不是纯粹和大模型聊天，而是 **"先检索相关知识片段，再基于这些片段生成回答"**，回答附来源引用。

---

## 二、技术栈

| 层次 | 技术 | 用途 |
|------|------|------|
| 后端框架 | Spring Boot 3.x, Java 17+ | 主框架 |
| 数据库 | MySQL 8.0 | 元数据存储 |
| 缓存 | Redis | 会话存储、权限缓存、分片状态 |
| 搜索引擎 | Elasticsearch 8.10（IK 分词器） | 全文检索 + 向量检索（dense_vector） |
| 向量检索引擎 | FAISS（第二阶段规划） | 大规模向量相似度计算 |
| 对象存储 | MinIO | 文档文件存储（分片上传） |
| 消息队列 | Kafka | 异步任务（文档解析、向量化） |
| 文档解析 | Apache Tika | PDF/Word/Excel 文本提取 |
| LLM API | DeepSeek API（WebClient 调用） | 内容生成 |
| Embedding API | 豆包 API | 文本向量化 |
| 实时通信 | Spring WebSocket（STOMP） | 流式对话 |
| 前端 | Vue 3 + TypeScript + Vite + Naive UI + Pinia | SPA |
| 安全 | Spring Security + JWT + RBAC | 认证授权 |
| 部署 | Docker + Docker Compose + Nginx | 容器化 |

---

## 三、系统架构（三层）

```
┌──────────────────────────────────────────────────┐
│                    用户层                         │
│   普通用户：知识查询、文档上传、智能对话             │
│   管理员：系统配置、用户管理、数据监控               │
└──────────────────────────────────────────────────┘
                        ↓
┌──────────────────────────────────────────────────┐
│                   逻辑层（4模块）                   │
│                                                   │
│  ① 用户管理       ② 文档上传与处理                 │
│  ③ 知识库检索     ④ 聊天助手                       │
└──────────────────────────────────────────────────┘
                        ↓
┌──────────────────────────────────────────────────┐
│                   数据层                           │
│  MySQL + Redis + MinIO + Elasticsearch + Kafka    │
└──────────────────────────────────────────────────┘
```

---

## 四、核心流程

### 4.1 文档向量化流程

```
用户上传文档 (PDF/Word/Excel)
  → MD5 去重检查
    → 分片上传至 MinIO (Redis BitMap 记录进度，支持断点续传)
      → 所有分片上传完成 → 合并文件
        → Kafka 发送解析任务
          → Apache Tika 提取文本
            → 文本分块 (chunk)
              → Kafka 发送向量化任务
                → 豆包 Embedding API 生成向量
                  → 存入 Elasticsearch (文本 + 向量)
                    → 更新 MySQL 处理状态
```

### 4.2 知识检索流程（混合检索）

```
用户输入查询文本
  → 豆包 API 向量化查询文本
    → Elasticsearch 混合检索：
      ① 全文检索 (IK 分词，text_content 字段)
      ② 向量检索 (dense_vector + 脚本打分)
      ③ 权限过滤 (org_tags 过滤)
        → 按权重融合排序
          → 取 topK 条结果
            → 返回 {text_content, score, file_name, chunk_id}
```

**权限过滤规则**：
1. 用户可访问自己上传的文档
2. 公开文档 (is_public=true) 所有人可访问
3. 用户 org_tags 匹配的文档可访问（支持层级继承）

### 4.3 聊天助手流程（RAG 核心）

```
用户通过 WebSocket 发送问题
  → ChatWebSocketHandler 接收
    → 获取/创建 conversationId
      → 调用 searchService.searchWithPermission(userMessage, userId, topK=5)
        → 从 ES 检索最相关 5 条知识片段
          → 从 Redis 获取对话历史 (最近 20 条)
            → 构建 Prompt:
              ┌──────────────────────────────┐
              │ 系统指令 (你是谁、回答规则)      │
              │ + 参考信息 (检索到的文档片段)    │
              │ + 对话历史 (上下文)             │
              │ + 用户问题                     │
              └──────────────────────────────┘
              → DeepSeek API 流式调用
                → 通过 WebSocket 逐 chunk 推送给前端（打字机效果）
                  → 保存完整对话到 Redis (TTL 7天)
```

**Prompt 模板示例**：
```
你是派聪明，一个基于本地知识库的智能助手。

当回答问题时，请遵循以下规则：
1. 优先基于提供的参考信息回答
2. 如果参考信息不足，清楚地表明
3. 回答要简洁、准确、客观
4. 引用来源时使用[文档X]格式

参考信息：
{{context}}

对话历史：
{{history}}

用户问题：{{query}}

请用中文回答。
```

**Redis 结构**：
- `user:{userId}:current_conversation` → 当前 conversationId
- `conversation:{conversationId}` → JSON 数组 [{role, content, timestamp}]
- `user:{userId}:conversations` → 历史 conversationId 列表
- `prompt_templates:{templateName}` → Prompt 模板
- TTL 统一 7 天

---

## 五、数据库设计

### 5 张核心表

| 表名 | 用途 | 核心字段 |
|------|------|---------|
| `users` | 用户 | id, username, password(BCrypt), role(USER/ADMIN), org_tags, primary_org |
| `organization_tags` | 组织标签 | tag_id(PK), name, parent_tag, created_by |
| `file_upload` | 文件元数据 | file_md5(PK), file_name, total_size, status, user_id, org_tag, is_public |
| `chunk_info` | 分片信息 | id, file_md5(FK), chunk_index, chunk_md5, storage_path |
| `document_vectors` | 向量元数据 | vector_id, file_md5(FK), chunk_id, text_content, model_version |

### Elasticsearch 索引

```json
{
  "mappings": {
    "properties": {
      "file_md5": { "type": "keyword" },
      "chunk_id": { "type": "integer" },
      "text_content": { "type": "text", "analyzer": "ik_max_word" },
      "vector": { "type": "dense_vector", "dims": 768 },
      "user_id": { "type": "keyword" },
      "org_tag": { "type": "keyword" },
      "is_public": { "type": "boolean" },
      "file_name": { "type": "keyword" }
    }
  }
}
```

---

## 六、API 接口汇总

### 用户模块
| 方法 | URL | 说明 |
|------|-----|------|
| POST | /api/v1/users/register | 注册 |
| POST | /api/v1/users/login | 登录（返回 JWT） |
| GET | /api/v1/users/me | 获取用户信息 |
| POST | /api/v1/admin/org-tags | 创建组织标签 |
| PUT | /api/v1/admin/users/{id}/org-tags | 分配组织标签 |

### 文件上传模块
| 方法 | URL | 说明 |
|------|-----|------|
| POST | /api/v1/upload/chunk | 分片上传 |
| GET | /api/v1/upload/status | 查询上传进度 |
| POST | /api/v1/upload/merge | 合并文件 |
| DELETE | /api/v1/documents/{md5} | 删除文档 |

### 知识检索模块
| 方法 | URL | 说明 |
|------|-----|------|
| POST | /api/search/hybrid | 混合检索（关键词+向量） |
| DELETE | /api/documents/{md5} | 删除文档索引 |

### 聊天模块
| 方法 | URL | 说明 |
|------|-----|------|
| WS | /ws/chat | WebSocket 对话 |
| GET | /api/v1/users/conversation | 获取对话历史 |
| GET | /api/chat/websocket-token | 获取停止令牌 |

---

## 七、关键设计要点

### 1. 分片上传 + 断点续传
- Redis BitMap 记录已上传分片
- MinIO 临时分片路径：`/temp/{fileMd5}/{chunkIndex}`
- 合并后存储：`/documents/{userId}/{fileName}`
- 支持并行分片上传，网络中断后从上次断点继续

### 2. 混合检索策略
- **第一阶段（当前）**：ES 同时做全文检索 + 向量检索，架构简单运维成本低
- **第二阶段（规划）**：ES 负责全文检索 + 元数据过滤，FAISS 专注向量相似度计算，双引擎协同，QPS 预计提升 5-10x

### 3. 权限控制（三级）
- **JWT 认证**：无状态身份验证
- **RBAC 角色**：USER/ADMIN 基础权限边界
- **组织标签**：细粒度数据访问控制，支持层级继承（父标签 → 所有子标签）

### 4. 异步处理
- Kafka 解耦文档解析和向量化任务
- 定时任务扫描异常状态，自动重试
- 不可恢复错误告警通知管理员

### 5. 流式响应
- WebSocket + STOMP 协议
- DeepSeek API 流式调用 (WebFlux)
- 前端逐 chunk 渲染（打字机效果）
- 支持停止生成（cmdToken 机制）

---

## 八、对 CUGCoding 的适用性分析

### 可以直接复用的设计思路：
1. **ES 混合检索**：已有 ES 7.17 容器，可直接用 dense_vector + multi_match
2. **Prompt 模板**：非常简单，就是字符串拼接 + 变量替换
3. **DeepSeek API 流式调用**：WebClient + WebFlux，代码量不大
4. **WebSocket 流式响应**：Spring WebSocket 标准实现

### 需要简化的部分：
1. **MinIO** → 直接用华为 OBS（已有）
2. **MySQL 元数据** → 已有 posts 表，不需要 file_upload 表
3. **Kafka** → 当前阶段不需要，RAG 检索可以直接同步执行
4. **组织标签权限** → 当前阶段不需要，CUGCoding 是公开校园论坛
5. **分片上传** → 当前阶段不需要，论坛文章不是大文件
6. **豆包 Embedding API** → 可用 DeepSeek Embedding API 或免费模型

### CUGCoding RAG 最小可行方案：
```
用户在文章列表勾选 N 篇文章
  → 输入问题
    → 后端读取文章内容 → Embedding API 向量化
      → ES 向量检索 → 返回 topK 相关片段
        → 构建 Prompt (系统指令 + 片段 + 问题)
          → DeepSeek API 流式生成
            → WebSocket 推送给前端
```
