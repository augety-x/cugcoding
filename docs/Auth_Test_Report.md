# CUGCoding 校园论坛 —— 用户认证系统测试报告

> 测试报告编号：CUG-TR-2026-001  
> 测试日期：2026-04-30  
> 测试环境：Spring Boot 2.7.18 + JUnit 5 + H2 内存数据库 + MySQL 8.0 Docker + MockMvc + curl  
> 被测模块：用户认证系统（注册/登录/JWT/BCrypt/拦截器/黑名单/Session管理/登录审计）

---

## 1. 测试目的

验证 CUGCoding 校园论坛认证系统的功能正确性、安全性和健壮性，覆盖：

- 用户注册与 BCrypt 密码加密存储
- JWT 令牌签发、校验与防篡改
- Cookie 无状态认证 + Interceptor 统一鉴权
- 登出与 Token 黑名单失效
- **Session 元数据管理（多设备跟踪、IP/UA 记录、活跃时间刷新）**
- **登录审计日志（成功/失败记录、失败原因追踪）**
- **跨设备 Session 终止（踢人下线）**

---

## 2. 测试环境

| 项目 | 配置 |
|------|------|
| JDK | 1.8.0_472 |
| Spring Boot | 2.7.18 |
| 测试框架 | JUnit 5 (Jupiter) + MockMvc |
| 单元测试 DB | H2 内存数据库（MODE=MySQL） |
| 集成测试 DB | MySQL 8.0 (Docker) |
| JWT 库 | io.jsonwebtoken:jjwt-api:0.11.5 |
| 密码加密 | spring-security-crypto BCrypt |
| HTTP 测试工具 | curl 7.x |

---

## 3. 测试用例设计

### 3.1 注册模块（4 项）

| 编号 | 测试用例 | 预期结果 |
|------|---------|---------|
| TC-REG-01 | 正常注册 | 200，返回 {id, username}，无 password；设 HttpOnly Cookie；记录 REGISTER 审计 |
| TC-REG-02 | 重复用户名 | 400，"用户名已存在" |
| TC-REG-03 | 空用户名 | 400（@NotBlank 校验） |
| TC-REG-04 | 空密码 | 400（@NotBlank 校验） |

### 3.2 登录模块（3 项）

| TC-LOG-01 | 正确密码登录 | 200，返 {id, username}；设 Cookie；记 LOGIN SUCCESS |
| TC-LOG-02 | 错误密码 | 400，"密码错误"；记 LOGIN FAIL + failReason |
| TC-LOG-03 | 不存在用户 | 400，"用户不存在" |

### 3.3 JWT 令牌模块（4 项）

| TC-JWT-01 | 生成合法 JWT | parse 可得 userId/username/jti |
| TC-JWT-02 | 篡改 JWT | parse 返回 null |
| TC-JWT-03 | 空字符串 Token | parse 返回 null |
| TC-JWT-04 | null Token | parse 返回 null |

### 3.4 认证拦截器（3 项）

| TC-INT-01 | 无 Cookie 访问 /api/me | 200，无 id/username |
| TC-INT-02 | 有效 Cookie 访问 /api/me | 200，返回 {id, username}，刷新 last_seen |
| TC-INT-03 | 无 Cookie 发帖 | 400，"请先登录" |

### 3.5 登出与黑名单（2 项）

| TC-OUT-01 | 正常登出 | 200，Cookie maxAge=0；记 LOGOUT 审计；Session 标记 is_active=false |
| TC-OUT-02 | 登出后 Token 失效 | /api/me 不再返回用户信息 |

### 3.6 BCrypt 加密（2 项）

| TC-BCR-01 | 密码存储为 BCrypt | 正确密码可登录，错误被拒 |
| TC-BCR-02 | BCrypt 盐值随机 | 同密码两次加密结果不同，但均验证通过 |

### 3.7 Session 管理（6 项）

| 编号 | 测试用例 | 预期结果 |
|------|---------|---------|
| TC-SES-01 | 注册后产生 Session 记录 | 1 条活跃 session，含 IP、UA、deviceInfo、loginTime |
| TC-SES-02 | 另一设备登录产生第 2 条 Session | 2 条活跃 session，UA 不同 |
| TC-SES-03 | 未登录访问 /api/sessions | 400，"请先登录" |
| TC-SES-04 | Session last_seen 刷新 | 请求后 last_seen_time 更新 |
| TC-SES-05 | 终止指定 Session | 200；被踢 session 的 Cookie 失效 |
| TC-SES-06 | 不能终止他人 Session | 400，"会话不存在或不属于当前用户" |

### 3.8 登录审计（2 项）

| TC-AUD-01 | 审计记录登录成功/失败 | REGISTER→LOGIN→LOGIN_FAIL → 均正确记录 |
| TC-AUD-02 | 登录失败记录原因 | failReason = "密码错误" |

### 3.9 权限控制（2 项）

| TC-PRO-01 | 无 Cookie 发帖 | 400 |
| TC-PRO-02 | 无 Cookie 评论 | 400 |

### 3.10 公开接口（1 项）

| TC-PUB-01 | 无认证查看 /api/posts | 200 |

### 3.11 完整流程集成测试（1 项）

| TC-INTG-01 | 端到端流程 10 步 | 注册→me→发帖→查看文章→评论→查看文章→登出→失效→重登→恢复 |

---

## 4. 测试结果

### 4.1 单元测试（JUnit + MockMvc + H2）

```
Tests run: 33, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

| 测试类 | 用例数 | 通过 | 覆盖模块 |
|--------|--------|------|---------|
| AuthSystemTests | 30 | 30 | 注册/登录/JWT/拦截器/黑名单/BCrypt/Session/Audit/权限/集成 |
| ApiValidationTests | 1 | 1 | 参数校验 |
| ForumServiceTests | 1 | 1 | Service 加载 |
| ForumApplicationTests | 1 | 1 | Spring 上下文 |
| **合计** | **33** | **33** | |

### 4.2 真实 HTTP 测试（curl + MySQL Docker）

11 项端到端测试全部通过：

| 测试场景 | 结果 |
|---------|------|
| 注册 → 设 Cookie + 审计记录 | PASS |
| 查看活跃 Session（IP/UA/设备） | PASS |
| 查看登录审计历史 | PASS |
| 另一设备登录 → 2 条活跃 Session | PASS |
| 错误密码登录 → 审计记录 "密码错误" | PASS |
| 终止指定设备 Session（踢人） | PASS |
| 被踢设备 Cookie 立即失效 | PASS |
| 保留设备 Cookie 仍有效 | PASS |
| 登出 → Cookie 清除 + Session 停用 | PASS |
| 登出后 Token 无法再用 | PASS |
| 重新登录 → 新 Token 可用 | PASS |

### 4.3 数据库验证

MySQL 中 `user_sessions` 和 `login_audit` 表数据与操作完全对应：

```
user_sessions:
  id=1  sessiontest  is_active=1  (活跃)
  id=2  sessiontest  is_active=0  (被踢下线)

login_audit:
  REGISTER       SUCCESS
  LOGIN          SUCCESS
  LOGIN          FAIL     fail_reason=密码错误
  SESSION_TERMINATED  SUCCESS
```

---

## 5. 关键技术点验证

### 5.1 密码安全

- BCrypt 哈希存储，响应不含 password 字段
- 同密码两次 BCrypt 结果不同（随机盐），均能验证通过

### 5.2 令牌安全

- JWT HMAC-SHA512 签名，≥256-bit 密钥
- 篡改后 parse 返回 null
- 7 天有效期 + 黑名单主动失效

### 5.3 Session 管理

```
登录/注册 → JwtUtil.generate()
         → ForumRepository.createSession(userId, username, jti, ip, ua, expiresAt)
         → user_sessions 表记录：IP、User-Agent、设备类型、登录时间
         ↓
后续请求 → LoginInterceptor → JwtUtil.parse() → 查黑名单
         → ForumRepository.updateLastSeen(jti)   ← 刷新活跃时间
         ↓
登出/踢人 → ForumRepository.deactivateSession(jti)
         → ForumRepository.blacklistToken(jti, expiresAt)
         → Cookie maxAge=0 清除
```

### 5.4 登录审计

每次认证事件（注册/登录/登出/踢人/登录失败）均写入 `login_audit` 表，包含：
- userId、username、action、ipAddress、userAgent、result、failReason、createdAt

---

## 6. 结论

本次测试对 CUGCoding 校园论坛认证系统进行了**单元测试（33 项）** 和**真实 HTTP 集成测试（11 项）**，覆盖了注册、登录、JWT 令牌、BCrypt 加密、Cookie 管理、拦截器鉴权、Token 黑名单、**Session 元数据管理、多设备跟踪、登录审计、跨设备踢人下线**等功能。

**全部 33 项单元测试通过 + 11 项集成测试通过，通过率 100%。**

系统实现的安全特性：

1. **BCrypt 密码哈希** —— 防明文泄露，带随机盐
2. **JWT + Cookie 认证** —— 无状态、可扩展、HttpOnly
3. **Token 黑名单机制** —— 弥补 JWT 不可主动撤销的缺点
4. **Session 元数据表（user_sessions）** —— 记录 IP/UA/设备类型/登录时间/最后活跃时间
5. **登录审计表（login_audit）** —— 追踪所有认证事件，含失败原因
6. **多设备管理** —— 查看所有活跃会话、踢出指定设备
7. **统一拦截器鉴权** —— AuthContext ThreadLocal 模式，控制器代码简洁

系统满足毕业设计的功能需求和安全要求，可进入下一阶段功能开发。
