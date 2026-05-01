package com.cugcoding.forum.service;

import com.cugcoding.forum.auth.JwtUtil;
import com.cugcoding.forum.model.*;
import com.cugcoding.forum.repo.ForumRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ForumService {

    private final ForumRepository repository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public ForumService(ForumRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void init() {
        repository.initSchema();
        repository.cleanExpiredSessions();
        if (!repository.findUserByUsername("admin").isPresent()) {
            repository.createUser("admin", passwordEncoder.encode("123456"), "ADMIN");
        } else {
            // Ensure existing admin has ADMIN role
            User admin = repository.findUserByUsername("admin").orElse(null);
            if (admin != null && !admin.isAdmin()) {
                repository.updateUserRole(admin.getId(), "ADMIN");
            }
        }
        seedPosts();
    }

    // ======================== Auth ========================

    public LoginResult register(String username, String password,
                                 String ipAddress, String userAgent) {
        validateCredentials(username, password);
        repository.findUserByUsername(username).ifPresent(u -> {
            throw new IllegalArgumentException("用户名已存在");
        });
        String hash = passwordEncoder.encode(password);
        User user = repository.createUser(username, hash);
        String token = JwtUtil.generate(user.getId(), user.getUsername(), user.getRole());

        // Record session & audit
        recordSession(user, token, ipAddress, userAgent);
        repository.recordAudit(user.getId(), user.getUsername(), "REGISTER",
                ipAddress, userAgent, "SUCCESS", null);

        return new LoginResult(user.getId(), user.getUsername(), token, user.getRole());
    }

    public LoginResult login(String username, String password,
                              String ipAddress, String userAgent) {
        validateCredentials(username, password);
        User user = repository.findUserByUsername(username)
                .orElseThrow(() -> {
                    // Record failed login for non-existent user
                    repository.recordAudit(null, username, "LOGIN",
                            ipAddress, userAgent, "FAIL", "用户不存在");
                    return new IllegalArgumentException("用户不存在");
                });
        if (!passwordEncoder.matches(password, user.getPassword())) {
            repository.recordAudit(user.getId(), user.getUsername(), "LOGIN",
                    ipAddress, userAgent, "FAIL", "密码错误");
            throw new IllegalArgumentException("密码错误");
        }
        String token = JwtUtil.generate(user.getId(), user.getUsername(), user.getRole());

        // Record session & audit
        recordSession(user, token, ipAddress, userAgent);
        repository.recordAudit(user.getId(), user.getUsername(), "LOGIN",
                ipAddress, userAgent, "SUCCESS", null);

        return new LoginResult(user.getId(), user.getUsername(), token, user.getRole());
    }

    public void logout(String token) {
        String jti = JwtUtil.getJti(token);
        Date expiresAt = JwtUtil.getExpiration(token);
        if (jti != null && expiresAt != null) {
            repository.blacklistToken(jti, expiresAt);
            repository.deactivateSession(jti);
        }
        // Record logout audit
        Long userId = JwtUtil.getUserId(token);
        String username = JwtUtil.getUsername(token);
        if (userId != null) {
            repository.recordAudit(userId, username, "LOGOUT",
                    null, null, "SUCCESS", null);
        }
    }

    /** Validate JWT token, check blacklist, and refresh session last_seen. */
    public LoginResult validateToken(String token) {
        Long userId = JwtUtil.getUserId(token);
        String username = JwtUtil.getUsername(token);
        String role = JwtUtil.getRole(token);
        String jti = JwtUtil.getJti(token);
        if (userId == null || username == null || jti == null) return null;
        if (repository.isTokenBlacklisted(jti)) return null;
        // Refresh last seen time
        repository.updateLastSeen(jti);
        return new LoginResult(userId, username, token, role);
    }

    /** Periodically clean expired tokens and sessions. */
    public void cleanExpired() {
        repository.cleanExpiredBlacklist();
        repository.cleanExpiredSessions();
    }

    // ======================== Session Management ========================

    /** List all active sessions for a user. */
    public List<UserSession> getActiveSessions(Long userId) {
        return repository.findActiveSessionsByUserId(userId);
    }

    /** Get the number of active sessions for a user. */
    public int getActiveSessionCount(Long userId) {
        return repository.countActiveSessions(userId);
    }

    /** Force-terminate a specific session by its JWT jti. */
    public void terminateSession(Long userId, String tokenJti) {
        // Verify the session belongs to this user
        List<UserSession> sessions = repository.findActiveSessionsByUserId(userId);
        boolean found = sessions.stream().anyMatch(s -> s.getTokenJti().equals(tokenJti));
        if (!found) {
            throw new IllegalArgumentException("会话不存在或不属于当前用户");
        }
        repository.deactivateSession(tokenJti);
        // Also blacklist the token so it can't be reused
        repository.blacklistToken(tokenJti, new Date(System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L));
        repository.recordAudit(userId, null, "SESSION_TERMINATED",
                null, null, "SUCCESS", "管理员主动终止");
    }

    // ======================== Audit ========================

    /** Get recent login audit records for a user. */
    public List<LoginAudit> getRecentAudits(Long userId, int limit) {
        return repository.findRecentAudits(userId, limit);
    }

    /** Admin: get all recent audit records. */
    public List<LoginAudit> getAllRecentAudits(int limit) {
        return repository.findAllRecentAudits(limit);
    }

    // ======================== Admin Statistics ========================

    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("userCount", repository.countUsers());
        stats.put("postCount", repository.countPosts());
        stats.put("commentCount", repository.countComments());
        stats.put("totalVisits", repository.countSiteVisits());
        stats.put("activeSessionCount", repository.countActiveSessionsTotal());
        stats.put("failedLoginCount", repository.countFailedLogins(7));
        stats.put("topPostsByViews", repository.topPostsByViews(10));
        stats.put("topPostsByLikes", repository.topPostsByLikes(10));
        stats.put("sessionsByDevice", repository.sessionStatsByDevice());
        return stats;
    }

    /** Admin: list all active sessions across all users. */
    public List<UserSession> getAllActiveSessions() {
        return repository.findAllActiveSessions();
    }

    /** Admin: terminate any session. */
    public void adminTerminateSession(String tokenJti) {
        repository.deactivateSession(tokenJti);
        repository.blacklistToken(tokenJti,
                new Date(System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L));
        repository.recordAudit(null, null, "SESSION_TERMINATED",
                null, null, "SUCCESS", "管理员终止会话");
    }

    // ======================== Posts ========================

    public List<PostDetail> listPosts() {
        List<PostDetail> result = new ArrayList<>();
        for (Post post : repository.findPosts()) {
            result.add(toDetail(post));
        }
        return result;
    }

    public PostDetail getPost(Long id) {
        return getPost(id, null);
    }

    /** Get post detail with optional current user for like status. */
    public PostDetail getPost(Long id, Long currentUserId) {
        Post post = repository.findPostById(id)
                .orElseThrow(() -> new IllegalArgumentException("帖子不存在"));
        PostDetail detail = toDetail(post);
        if (currentUserId != null) {
            detail.setLiked(repository.isLikedBy(id, currentUserId));
        }
        return detail;
    }

    public long createPost(Long userId, String title, String content) {
        return repository.createPost(userId, title, content);
    }

    // ======================== Views & Likes ========================

    public void incrementView(Long postId) {
        repository.incrementViewCount(postId);
    }

    /** Toggle like. Returns true if now liked, false if unliked. */
    public boolean toggleLike(Long postId, Long userId) {
        return repository.toggleLike(postId, userId);
    }

    /** Fill liked status for a list of posts. */
    public void fillLikedStatus(List<PostDetail> posts, Long userId) {
        if (userId == null || posts.isEmpty()) return;
        List<Long> ids = new ArrayList<>();
        for (PostDetail p : posts) ids.add(p.getId());
        java.util.Set<Long> likedIds = repository.getLikedPostIds(userId, ids);
        for (PostDetail p : posts) p.setLiked(likedIds.contains(p.getId()));
    }

    /** Record a site visit. */
    public void recordVisit(String path, String ipAddress) {
        repository.recordVisit(path, ipAddress);
    }

    // ======================== Comments ========================

    public void addComment(Long postId, Long userId, String content) {
        if (!repository.existsPost(postId)) {
            throw new IllegalArgumentException("帖子不存在");
        }
        repository.createComment(postId, userId, content);
    }

    // ======================== Internal ========================

    private void recordSession(User user, String token, String ipAddress, String userAgent) {
        String jti = JwtUtil.getJti(token);
        Date expiresAt = JwtUtil.getExpiration(token);
        if (jti != null && expiresAt != null) {
            repository.createSession(user.getId(), user.getUsername(),
                    jti, ipAddress, userAgent, expiresAt);
        }
    }

    private void seedPosts() {
        if (!repository.findPosts().isEmpty()) return;
        long adminId = repository.findUserByUsername("admin")
                .orElseThrow(IllegalStateException::new).getId();
        repository.createPost(adminId, "欢迎来到 CUGCoding 校园论坛",
                "CUGCoding 论坛是一个校园学习交流平台，支持 Markdown 写作和代码分享。\n\n"
                + "## 快速开始\n\n"
                + "1. 点击「发布文章」开始创作\n"
                + "2. 使用 Markdown 语法排版内容\n"
                + "3. 在文章下方参与评论讨论\n\n"
                + "> 技术让校园连接更紧密。");
    }

    private void validateCredentials(String username, String password) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }
    }

    private PostDetail toDetail(Post post) {
        PostDetail detail = new PostDetail();
        detail.setId(post.getId());
        detail.setUserId(post.getUserId());
        detail.setTitle(post.getTitle());
        detail.setContent(post.getContent());
        detail.setViewCount(post.getViewCount());
        detail.setLikeCount(post.getLikeCount());
        detail.setCreatedAt(post.getCreatedAt());
        detail.setAuthorName(
                repository.findUserById(post.getUserId()).map(User::getUsername).orElse("unknown"));
        detail.setComments(repository.findCommentsByPostId(post.getId()));
        return detail;
    }

    // ======================== DTO ========================

    public static class LoginResult {
        private final Long id;
        private final String username;
        private final String token;
        private final String role;

        public LoginResult(Long id, String username, String token, String role) {
            this.id = id;
            this.username = username;
            this.token = token;
            this.role = role;
        }

        public Long getId() { return id; }
        public String getUsername() { return username; }
        public String getToken() { return token; }
        public String getRole() { return role; }
    }
}
