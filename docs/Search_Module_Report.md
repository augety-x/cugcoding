# CUGCoding 校园论坛 —— 全文搜索模块技术报告

> 版本：1.0 | 日期：2026-05-01 | 基于 Elasticsearch 7.17.25

---

## 1. 概述

搜索模块为 CUGCoding 论坛提供基于 Elasticsearch 的全文检索能力。用户在页面顶部的搜索框中输入关键词，系统在 ES 索引中执行 `multi_match` 查询，匹配文章的标题（title）和正文（content）字段，返回按相关性排序的结果列表，并对匹配的关键词进行高亮显示。

### 1.1 核心特性

- **中英文搜索**：使用 ES `standard` 分词器，中文逐字匹配、英文词级匹配
- **实时同步**：文章发布时自动索引到 ES；启动时全量重建索引
- **关键词高亮**：搜索结果中匹配关键词以黄色背景高亮
- **内容摘要**：搜索结果展示关键词附近的内容片段
- **分页支持**：后端分页，前端上一页/下一页导航
- **管理重建**：管理员可通过 API 手动触发全量索引重建

### 1.2 涉及文件

| 层 | 文件 | 职责 |
|----|------|------|
| ES 模型 | [PostDocument.java](../backend/src/main/java/com/cugcoding/forum/search/PostDocument.java) | ES 索引文档映射（@Document, @Field） |
| ES 服务 | [PostIndexService.java](../backend/src/main/java/com/cugcoding/forum/search/PostIndexService.java) | 索引 CRUD + multi_match 搜索 |
| ES 配置 | [es-settings.json](../backend/src/main/resources/es-settings.json) | 索引分片/副本/分析器配置 |
| 业务层 | [ForumService.java](../backend/src/main/java/com/cugcoding/forum/service/ForumService.java) | `searchPosts()`, `rebuildIndex()`, `toDocument()` |
| 控制器 | [ApiController.java](../backend/src/main/java/com/cugcoding/forum/web/ApiController.java) | `GET /api/search`, `POST /api/admin/search/rebuild` |
| 启动同步 | [StartupRunner.java](../backend/src/main/java/com/cugcoding/forum/config/StartupRunner.java) | 启动时调用 `rebuildIndex()` |
| 前端搜索框 | [App.vue](../frontend/src/App.vue) | 页面顶部居中搜索横幅（渐变色卡片样式） |
| 搜索结果页 | [SearchResults.vue](../frontend/src/pages/SearchResults.vue) | 结果列表 + 高亮 + 摘要 + 分页 |

---

## 2. 架构设计

### 2.1 部署架构

```
Docker Container: cugcoding-es
  Elasticsearch 7.17.25
  端口: 9200 (HTTP), 9300 (Transport)
  内存: 512MB
  索引: cugcoding_posts (1 shard, 0 replica)
```

### 2.2 搜索流程图

```
┌─────────────────────────────────────────────────┐
│  用户输入关键词 → 点击搜索 / 回车                    │
│       ↓                                          │
│  前端跳转 /search?q={keyword}                      │
│       ↓                                          │
│  SearchResults.vue mounted → GET /api/search       │
│       ↓                                          │
│  ApiController.search(q, page, size)               │
│       ↓                                          │
│  ForumService.searchPosts(keyword, page, size, uid)│
│       ↓                                          │
│  PostIndexService.search(keyword, page, size)      │
│       ↓                                          │
│  ES multi_match query:                            │
│  {                                                │
│    "multi_match": {                               │
│      "query": "keyword",                          │
│      "fields": ["title", "content"],              │
│      "type": "best_fields"                        │
│    }                                              │
│  }                                                │
│       ↓                                          │
│  ES 返回 SearchHits<PostDocument>                  │
│       ↓                                          │
│  按 ID 从 MySQL 查完整文章数据                       │
│       ↓                                          │
│  组装 PostDetail + liked 状态                      │
│       ↓                                          │
│  返回 { items: [...PostDetail], total, page, size }│
│       ↓                                          │
│  前端渲染结果列表 + 关键词高亮                        │
└─────────────────────────────────────────────────┘
```

### 2.3 索引同步流程

```
┌─ 全量同步（启动时）──────────────────┐
│  StartupRunner.run()                 │
│    → ForumService.init()             │
│    → ForumService.rebuildIndex()     │
│      → findPosts() 查所有文章         │
│      → toDocument() 转换 Post→PostDoc │
│      → bulkIndex() 批量写入 ES        │
└──────────────────────────────────────┘

┌─ 增量同步（发布时）──────────────────┐
│  ApiController.createPost()          │
│    → ForumService.createPost()       │
│      → repository.createPost() 写DB  │
│      → postIndexService.index() 写ES │
└──────────────────────────────────────┘
```

---

## 3. 核心实现

### 3.1 ES 索引文档模型

```java
@Document(indexName = "cugcoding_posts")
@Setting(settingPath = "es-settings.json")
public class PostDocument {
    @Id
    private Long id;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String title;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String content;

    @Field(type = FieldType.Keyword)
    private String authorName;

    @Field(type = FieldType.Long)
    private long viewCount;

    @Field(type = FieldType.Long)
    private long likeCount;
}
```

### 3.2 multi_match 查询

```java
public SearchHits<PostDocument> search(String keyword, int page, int size) {
    MultiMatchQueryBuilder mmq = QueryBuilders
        .multiMatchQuery(keyword, "title", "content")
        .type(MultiMatchQueryBuilder.Type.BEST_FIELDS);

    NativeSearchQuery query = new NativeSearchQueryBuilder()
        .withQuery(mmq)
        .withPageable(PageRequest.of(page, size))
        .build();

    return esTemplate.search(query, PostDocument.class);
}
```

**`best_fields` 策略**：ES 对 title 和 content 两个字段分别评分，取最高分作为文档得分。标题匹配的文章排名更靠前（标题字段更短，匹配权重更高）。

### 3.3 关键词高亮

前端使用正则替换实现客户端高亮：

```javascript
function highlight(text) {
  if (!keyword.value || !text) return text
  const escaped = keyword.value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  return text.replace(new RegExp(`(${escaped})`, 'gi'), '<mark>$1</mark>')
}
```

### 3.4 内容摘要

搜索结果中展示关键词附近的内容片段：

```javascript
function highlightExcerpt(content) {
  const idx = content.toLowerCase().indexOf(keyword.value.toLowerCase())
  const start = Math.max(0, idx - 40)
  const end = Math.min(content.length, idx + keyword.value.length + 60)
  let snippet = content.substring(start, end)
  if (start > 0) snippet = '...' + snippet
  if (end < content.length) snippet += '...'
  return highlight(snippet)
}
```

---

## 4. API 接口

### 4.1 搜索文章

```
GET /api/search?q={keyword}&page={page}&size={size}
```

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| q | String | 必填 | 搜索关键词 |
| page | Integer | 1 | 页码 |
| size | Integer | 20 | 每页条数（最大 50） |

**响应示例**：

```json
{
  "items": [
    {
      "id": 1,
      "title": "从 JDBC Template 到 MyBatis-Plus：校园论坛持久层选型思考",
      "content": "...",
      "authorName": "admin",
      "viewCount": 6,
      "likeCount": 0,
      "liked": null,
      "createdAt": "2026-05-01T13:05:58"
    }
  ],
  "total": 8,
  "page": 1,
  "size": 20
}
```

### 4.2 重建索引（管理员）

```
POST /api/admin/search/rebuild
```

需要 ADMIN 角色。将 MySQL 中所有文章重新写入 ES 索引。

---

## 5. 搜索 UI 设计

### 5.1 搜索横幅

导航栏下方展示紫色渐变搜索横幅，包含：
- 搜索图标
- 输入框（placeholder: "搜索文章、教程、技术话题..."）
- 搜索按钮（渐变紫色圆角）

**显示规则**：首页、搜索页、文章详情页、专栏页等公开页面显示搜索横幅；登录页、发布页、管理后台页隐藏。

### 5.2 搜索结果页

结果页包含：
- 搜索统计（"N 条结果"）
- 文章卡片列表（标题、作者、阅读量、点赞量、内容摘要）
- 关键词高亮（`<mark>` 黄色背景）
- 分页导航（上一页/下一页 + 页码）
- 无结果空状态（"未找到相关文章" + 返回首页链接）

---

## 6. 测试报告

### 6.1 API 测试

| 测试项 | 查询词 | 结果数 | 状态 |
|--------|--------|--------|------|
| 英文搜索 | "Spring" | 4 | PASS |
| 英文搜索 | "Vue" | 3 | PASS |
| 中文搜索 | "学习" | 2 | PASS |
| 中文搜索 | "论坛" | 8 | PASS |
| 无匹配 | "xyznotfound" | 0 | PASS |
| 分页 | "论坛" page=1 size=3 | 3 items, total=8 | PASS |

### 6.2 同步测试

| 测试项 | 操作 | 结果 |
|--------|------|------|
| 启动同步 | 启动后端 | ES 索引包含所有 MySQL posts 记录 |
| 增量同步 | 发布新文章 | 新文章可被搜索到 |
| 手动重建 | POST /api/admin/search/rebuild | HTTP 200，日志输出 indexed N posts |

### 6.3 前端测试

| 测试项 | 操作 | 结果 |
|--------|------|------|
| 搜索框展示 | 访问首页 | 紫色渐变搜索横幅显示在导航栏下方 |
| 搜索框隐藏 | 访问 /login | 搜索横幅不显示 |
| 搜索框隐藏 | 访问 /publish | 搜索横幅不显示 |
| 英文搜索 | 输入 "Spring" 点击搜索 | 跳转搜索结果页，4 条结果 |
| 中文搜索 | 输入 "论坛" 回车 | 跳转搜索结果页，8 条结果 |
| 关键词高亮 | 查看搜索结果 | 匹配词黄色高亮 |
| 无结果 | 搜索无匹配词 | 显示空状态提示 |
| 分页 | 翻页 | 正确加载下一页结果 |

### 6.4 单元测试

```
Tests run: 36, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

---

## 7. 运维指南

### 7.1 启动 ES

```bash
docker run -d --name cugcoding-es \
  -p 9200:9200 \
  -e "discovery.type=single-node" \
  -e "xpack.security.enabled=false" \
  -e "ES_JAVA_OPTS=-Xms512m -Xmx512m" \
  elasticsearch:7.17.25
```

### 7.2 验证 ES 状态

```bash
# 检查集群健康
curl http://localhost:9200/_cluster/health

# 查看索引
curl http://localhost:9200/_cat/indices?v

# 查看索引文档数
curl http://localhost:9200/cugcoding_posts/_count
```

### 7.3 重建索引

```bash
# 登录管理员
curl -X POST http://localhost:8080/api/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}' -c /tmp/cookie

# 触发重建
curl -X POST http://localhost:8080/api/admin/search/rebuild -b /tmp/cookie
```

### 7.4 问题排查

| 现象 | 可能原因 | 解决 |
|------|---------|------|
| 搜索返回空 | ES 容器未启动 | `docker ps` 检查 cugcoding-es |
| 新文章搜不到 | 增量索引未触发 | 手动 POST /api/admin/search/rebuild |
| 搜索结果不对 | 索引数据过期 | 重建索引 |

---

## 8. 后续优化方向

1. **IK 中文分词器**：替换 `standard` 分词器，实现中文词语级分词（"校园论坛" → "校园" + "论坛"），提升搜索精度
2. **ES 高亮**：使用 ES 内置的 `highlight` 功能替代前端正则，支持多字段、多片段高亮
3. **搜索建议**：输入时实时展示搜索建议（autocomplete）
4. **搜索权重**：title 字段权重 > content 字段权重，标题匹配文章排更前
5. **同义词**：配置同义词词典（如 "Java" ↔ "JAVA" ↔ "java"）
6. **搜索日志**：记录用户搜索历史，用于热门搜索推荐
