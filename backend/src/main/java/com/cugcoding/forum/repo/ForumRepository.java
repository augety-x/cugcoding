package com.cugcoding.forum.repo;

import com.cugcoding.forum.model.*;
import com.cugcoding.forum.rag.KnowledgeChunk;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.ArrayList;

@Repository
public class ForumRepository {
    private final JdbcTemplate jdbcTemplate;

    public ForumRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<User> userRowMapper = (rs, i) -> {
        User u = new User();
        u.setId(rs.getLong("id"));
        u.setUsername(rs.getString("username"));
        u.setPassword(rs.getString("password"));
        u.setRole(rs.getString("role"));
        return u;
    };

    private final RowMapper<Post> postRowMapper = (rs, i) -> {
        Post p = new Post();
        p.setId(rs.getLong("id"));
        p.setUserId(rs.getLong("user_id"));
        p.setTitle(rs.getString("title"));
        p.setContent(rs.getString("content"));
        p.setViewCount(getLongOrZero(rs, "view_count"));
        p.setLikeCount(getLongOrZero(rs, "like_count"));
        p.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return p;
    };

    private long getLongOrZero(java.sql.ResultSet rs, String column) {
        try { long v = rs.getLong(column); return rs.wasNull() ? 0 : v; }
        catch (java.sql.SQLException e) { return 0; }
    }

    // ======================== Schema Init ========================

    public void initSchema() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS users ("
                + "id BIGINT PRIMARY KEY AUTO_INCREMENT, "
                + "username VARCHAR(50) NOT NULL UNIQUE, "
                + "password VARCHAR(200) NOT NULL, "
                + "role VARCHAR(20) NOT NULL DEFAULT 'USER', "
                + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        // Migrate existing tables
        try {
            jdbcTemplate.execute("ALTER TABLE users MODIFY COLUMN password VARCHAR(200) NOT NULL");
        } catch (Exception ignored) { }
        try {
            jdbcTemplate.execute("ALTER TABLE users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER'");
        } catch (Exception ignored) { }
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS posts ("
                + "id BIGINT PRIMARY KEY AUTO_INCREMENT, "
                + "user_id BIGINT NOT NULL, "
                + "title VARCHAR(200) NOT NULL, "
                + "content TEXT NOT NULL, "
                + "view_count BIGINT DEFAULT 0, "
                + "like_count BIGINT DEFAULT 0, "
                + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        // Migrate existing posts table
        try {
            jdbcTemplate.execute("ALTER TABLE posts ADD COLUMN view_count BIGINT DEFAULT 0");
        } catch (Exception ignored) { }
        try {
            jdbcTemplate.execute("ALTER TABLE posts ADD COLUMN like_count BIGINT DEFAULT 0");
        } catch (Exception ignored) { }
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS post_likes ("
                + "id BIGINT PRIMARY KEY AUTO_INCREMENT, "
                + "post_id BIGINT NOT NULL, "
                + "user_id BIGINT NOT NULL, "
                + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                + "UNIQUE KEY uk_post_user (post_id, user_id))");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS site_visits ("
                + "id BIGINT PRIMARY KEY AUTO_INCREMENT, "
                + "path VARCHAR(200), "
                + "ip_address VARCHAR(45), "
                + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS knowledge_chunks ("
                + "id BIGINT PRIMARY KEY AUTO_INCREMENT, "
                + "source_type VARCHAR(20) NOT NULL, "
                + "source_id BIGINT DEFAULT 0, "
                + "source_name VARCHAR(255), "
                + "chunk_index INT DEFAULT 0, "
                + "content TEXT NOT NULL, "
                + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                + "INDEX idx_source (source_type, source_id))");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS comments ("
                + "id BIGINT PRIMARY KEY AUTO_INCREMENT, "
                + "post_id BIGINT NOT NULL, "
                + "user_id BIGINT NOT NULL, "
                + "content TEXT NOT NULL, "
                + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS token_blacklist ("
                + "jti VARCHAR(64) PRIMARY KEY, "
                + "expires_at TIMESTAMP NOT NULL)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS user_sessions ("
                + "id BIGINT PRIMARY KEY AUTO_INCREMENT, "
                + "user_id BIGINT NOT NULL, "
                + "username VARCHAR(50) NOT NULL, "
                + "token_jti VARCHAR(64) NOT NULL, "
                + "ip_address VARCHAR(45), "
                + "user_agent VARCHAR(500), "
                + "device_info VARCHAR(200), "
                + "login_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                + "last_seen_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                + "expires_at TIMESTAMP NOT NULL, "
                + "is_active BOOLEAN DEFAULT TRUE)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS login_audit ("
                + "id BIGINT PRIMARY KEY AUTO_INCREMENT, "
                + "user_id BIGINT, "
                + "username VARCHAR(50), "
                + "action VARCHAR(20) NOT NULL, "
                + "ip_address VARCHAR(45), "
                + "user_agent VARCHAR(500), "
                + "result VARCHAR(10) NOT NULL, "
                + "fail_reason VARCHAR(100), "
                + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
    }

    // ======================== User ========================

    public User createUser(String username, String passwordHash) {
        jdbcTemplate.update("INSERT INTO users(username,password) VALUES(?,?)", username, passwordHash);
        return findUserByUsername(username).orElseThrow(IllegalStateException::new);
    }

    /** Create a user with explicit role. */
    public User createUser(String username, String passwordHash, String role) {
        jdbcTemplate.update("INSERT INTO users(username,password,role) VALUES(?,?,?)",
                username, passwordHash, role);
        return findUserByUsername(username).orElseThrow(IllegalStateException::new);
    }

    public Optional<User> findUserByUsername(String username) {
        List<User> list = jdbcTemplate.query(
                "SELECT id,username,password,role FROM users WHERE username=?", userRowMapper, username);
        return list.stream().findFirst();
    }

    public void updateUserRole(Long userId, String role) {
        jdbcTemplate.update("UPDATE users SET role=? WHERE id=?", role, userId);
    }

    public Optional<User> findUserById(Long id) {
        List<User> list = jdbcTemplate.query(
                "SELECT id,username,password,role FROM users WHERE id=?", userRowMapper, id);
        return list.stream().findFirst();
    }

    public long countUsers() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Long.class);
        return count != null ? count : 0;
    }

    // ======================== Post ========================

    public List<Post> findPosts() {
        return jdbcTemplate.query(
                "SELECT id,user_id,title,content,view_count,like_count,created_at FROM posts ORDER BY id DESC", postRowMapper);
    }

    public Optional<Post> findPostById(Long id) {
        List<Post> list = jdbcTemplate.query(
                "SELECT id,user_id,title,content,view_count,like_count,created_at FROM posts WHERE id=?", postRowMapper, id);
        return list.stream().findFirst();
    }

    public long createPost(Long userId, String title, String content) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO posts(user_id,title,content) VALUES(?,?,?)",
                    new String[]{"id"});
            ps.setLong(1, userId);
            ps.setString(2, title);
            ps.setString(3, content);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : 0L;
    }

    public boolean existsPost(Long id) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM posts WHERE id=?", Integer.class, id);
        return count != null && count > 0;
    }

    // ======================== Comment ========================

    public List<CommentView> findCommentsByPostId(Long postId) {
        return jdbcTemplate.query(
                "SELECT c.id, u.username, c.content, c.created_at "
                        + "FROM comments c JOIN users u ON c.user_id=u.id "
                        + "WHERE c.post_id=? ORDER BY c.id ASC",
                (rs, i) -> {
                    CommentView cv = new CommentView();
                    cv.setId(rs.getLong("id"));
                    cv.setUsername(rs.getString("username"));
                    cv.setContent(rs.getString("content"));
                    cv.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                    return cv;
                }, postId);
    }

    public void createComment(Long postId, Long userId, String content) {
        jdbcTemplate.update("INSERT INTO comments(post_id,user_id,content) VALUES(?,?,?)",
                postId, userId, content);
    }

    // ======================== Token Blacklist ========================

    /** Add a JWT token ID to the blacklist (for logout). */
    public void blacklistToken(String jti, Date expiresAt) {
        jdbcTemplate.update("INSERT INTO token_blacklist(jti,expires_at) VALUES(?,?)",
                jti, new Timestamp(expiresAt.getTime()));
    }

    /** Check if a token ID has been blacklisted. */
    public boolean isTokenBlacklisted(String jti) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM token_blacklist WHERE jti=?", Integer.class, jti);
        return count != null && count > 0;
    }

    /** Remove expired tokens from the blacklist. Called periodically. */
    public void cleanExpiredBlacklist() {
        jdbcTemplate.update("DELETE FROM token_blacklist WHERE expires_at < NOW()");
    }

    // ======================== Session Management ========================

    /** Record a new active session on login/register. */
    public void createSession(Long userId, String username, String tokenJti,
                              String ipAddress, String userAgent,
                              Date expiresAt) {
        String device = extractDeviceInfo(userAgent);
        jdbcTemplate.update(
                "INSERT INTO user_sessions(user_id,username,token_jti,ip_address,user_agent,device_info,expires_at) "
                        + "VALUES(?,?,?,?,?,?,?)",
                userId, username, tokenJti, ipAddress, userAgent, device,
                new Timestamp(expiresAt.getTime()));
    }

    /** Update last_seen_time for an active session. */
    public void updateLastSeen(String tokenJti) {
        jdbcTemplate.update(
                "UPDATE user_sessions SET last_seen_time=CURRENT_TIMESTAMP WHERE token_jti=? AND is_active=TRUE",
                tokenJti);
    }

    /** Mark a session as inactive (on logout). */
    public void deactivateSession(String tokenJti) {
        jdbcTemplate.update(
                "UPDATE user_sessions SET is_active=FALSE WHERE token_jti=?",
                tokenJti);
    }

    /** Count active sessions for a user (for device limit checks). */
    public int countActiveSessions(Long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_sessions WHERE user_id=? AND is_active=TRUE",
                Integer.class, userId);
        return count != null ? count : 0;
    }

    /** List all active sessions for a user. */
    public List<UserSession> findActiveSessionsByUserId(Long userId) {
        return jdbcTemplate.query(
                "SELECT id,user_id,username,token_jti,ip_address,user_agent,device_info,"
                        + "login_time,last_seen_time,expires_at,is_active "
                        + "FROM user_sessions WHERE user_id=? AND is_active=TRUE ORDER BY login_time DESC",
                sessionRowMapper, userId);
    }

    /** Remove expired sessions. */
    public void cleanExpiredSessions() {
        jdbcTemplate.update("UPDATE user_sessions SET is_active=FALSE WHERE expires_at < NOW()");
    }

    private final RowMapper<UserSession> sessionRowMapper = (rs, i) -> {
        UserSession s = new UserSession();
        s.setId(rs.getLong("id"));
        s.setUserId(rs.getLong("user_id"));
        s.setUsername(rs.getString("username"));
        s.setTokenJti(rs.getString("token_jti"));
        s.setIpAddress(rs.getString("ip_address"));
        s.setUserAgent(rs.getString("user_agent"));
        s.setDeviceInfo(rs.getString("device_info"));
        s.setLoginTime(rs.getTimestamp("login_time").toLocalDateTime());
        s.setLastSeenTime(rs.getTimestamp("last_seen_time").toLocalDateTime());
        s.setExpiresAt(rs.getTimestamp("expires_at").toLocalDateTime());
        s.setActive(rs.getBoolean("is_active"));
        return s;
    };

    private String extractDeviceInfo(String userAgent) {
        if (userAgent == null) return "Unknown";
        String ua = userAgent.toLowerCase();
        if (ua.contains("windows")) return "Windows PC";
        if (ua.contains("mac os") || ua.contains("macintosh")) return "Mac";
        if (ua.contains("android")) return "Android";
        if (ua.contains("iphone") || ua.contains("ipad")) return "iOS";
        if (ua.contains("linux")) return "Linux";
        return "Other";
    }

    // ======================== Login Audit ========================

    /** Record a login audit event. */
    public void recordAudit(Long userId, String username, String action,
                            String ipAddress, String userAgent,
                            String result, String failReason) {
        jdbcTemplate.update(
                "INSERT INTO login_audit(user_id,username,action,ip_address,user_agent,result,fail_reason) "
                        + "VALUES(?,?,?,?,?,?,?)",
                userId, username, action, ipAddress, userAgent, result, failReason);
    }

    /** Get recent audit records for a user. */
    public List<LoginAudit> findRecentAudits(Long userId, int limit) {
        return jdbcTemplate.query(
                "SELECT id,user_id,username,action,ip_address,user_agent,result,fail_reason,created_at "
                        + "FROM login_audit WHERE user_id=? ORDER BY created_at DESC LIMIT ?",
                auditRowMapper, userId, limit);
    }

    /** Admin: get all recent audit records. */
    public List<LoginAudit> findAllRecentAudits(int limit) {
        return jdbcTemplate.query(
                "SELECT id,user_id,username,action,ip_address,user_agent,result,fail_reason,created_at "
                        + "FROM login_audit ORDER BY created_at DESC LIMIT ?",
                auditRowMapper, limit);
    }

    private final RowMapper<LoginAudit> auditRowMapper = (rs, i) -> {
        LoginAudit a = new LoginAudit();
        a.setId(rs.getLong("id"));
        a.setUserId(rs.getLong("user_id"));
        a.setUsername(rs.getString("username"));
        a.setAction(rs.getString("action"));
        a.setIpAddress(rs.getString("ip_address"));
        a.setUserAgent(rs.getString("user_agent"));
        a.setResult(rs.getString("result"));
        a.setFailReason(rs.getString("fail_reason"));
        a.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return a;
    };

    // ======================== Admin Statistics ========================

    public long countPosts() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM posts", Long.class);
        return count != null ? count : 0;
    }

    public long countComments() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM comments", Long.class);
        return count != null ? count : 0;
    }

    public long countActiveSessionsTotal() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_sessions WHERE is_active=TRUE", Long.class);
        return count != null ? count : 0;
    }

    /** Count failed logins in last N days. */
    public long countFailedLogins(int days) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM login_audit WHERE action='LOGIN' AND result='FAIL' "
                        + "AND created_at >= NOW() - INTERVAL " + days + " DAY",
                Long.class);
        return count != null ? count : 0;
    }

    /** Total site visits count. */
    public long countSiteVisits() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM site_visits", Long.class);
        return count != null ? count : 0;
    }

    /** Top posts by views. */
    public List<Map<String, Object>> topPostsByViews(int limit) {
        return jdbcTemplate.queryForList(
                "SELECT id, title, view_count, like_count FROM posts "
                        + "WHERE view_count > 0 ORDER BY view_count DESC LIMIT ?", limit);
    }

    /** Top posts by likes. */
    public List<Map<String, Object>> topPostsByLikes(int limit) {
        return jdbcTemplate.queryForList(
                "SELECT id, title, view_count, like_count FROM posts "
                        + "WHERE like_count > 0 ORDER BY like_count DESC LIMIT ?", limit);
    }

    /** Session count by device (for pie chart). */
    public List<Map<String, Object>> sessionStatsByDevice() {
        return jdbcTemplate.queryForList(
                "SELECT device_info AS device, COUNT(*) AS count "
                        + "FROM user_sessions WHERE is_active=TRUE GROUP BY device_info");
    }

    // ======================== Views & Likes ========================

    /** Increment view count for a post. */
    public void incrementViewCount(Long postId) {
        jdbcTemplate.update("UPDATE posts SET view_count = view_count + 1 WHERE id=?", postId);
    }

    /** Toggle like: returns true if liked, false if unliked. */
    public boolean toggleLike(Long postId, Long userId) {
        int deleted = jdbcTemplate.update(
                "DELETE FROM post_likes WHERE post_id=? AND user_id=?", postId, userId);
        if (deleted > 0) {
            jdbcTemplate.update("UPDATE posts SET like_count = like_count - 1 WHERE id=?", postId);
            return false; // unliked
        } else {
            jdbcTemplate.update("INSERT INTO post_likes(post_id,user_id) VALUES(?,?)", postId, userId);
            jdbcTemplate.update("UPDATE posts SET like_count = like_count + 1 WHERE id=?", postId);
            return true; // liked
        }
    }

    /** Check if a user has liked a post. */
    public boolean isLikedBy(Long postId, Long userId) {
        if (userId == null) return false;
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM post_likes WHERE post_id=? AND user_id=?", Integer.class, postId, userId);
        return count != null && count > 0;
    }

    /** Get which posts a user has liked from a list. */
    public java.util.Set<Long> getLikedPostIds(Long userId, List<Long> postIds) {
        if (userId == null || postIds.isEmpty()) return java.util.Collections.emptySet();
        List<Long> list = jdbcTemplate.queryForList(
                "SELECT post_id FROM post_likes WHERE user_id=? AND post_id IN ("
                        + postIds.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("0") + ")",
                Long.class, userId);
        return new java.util.HashSet<>(list);
    }

    /** Record a site visit. */
    public void recordVisit(String path, String ipAddress) {
        jdbcTemplate.update("INSERT INTO site_visits(path,ip_address) VALUES(?,?)", path, ipAddress);
    }

    // ======================== Admin Stats ========================

    // ======================== Knowledge Chunks (RAG) ========================

    public void clearKnowledgeChunks() {
        jdbcTemplate.update("DELETE FROM knowledge_chunks");
    }

    public void insertKnowledgeChunk(String sourceType, Long sourceId, String sourceName, int chunkIndex, String content) {
        String sql = "INSERT INTO knowledge_chunks(source_type,source_id,source_name,chunk_index,content) VALUES(?,?,?,?,?)";
        jdbcTemplate.update(sql, sourceType, sourceId, sourceName, chunkIndex, content);
    }

    public List<KnowledgeChunk> findAllKnowledgeChunks() {
        return jdbcTemplate.query(
                "SELECT id,source_type,source_id,source_name,chunk_index,content,created_at FROM knowledge_chunks ORDER BY id",
                chunkRowMapper);
    }

    public List<KnowledgeChunk> findChunksByArticleIds(List<Long> articleIds) {
        if (articleIds.isEmpty()) return new ArrayList<>();
        String placeholders = articleIds.stream().map(id -> "?").reduce((a, b) -> a + "," + b).orElse("");
        return jdbcTemplate.query(
                "SELECT id,source_type,source_id,source_name,chunk_index,content,created_at FROM knowledge_chunks "
                        + "WHERE source_type='ARTICLE' AND source_id IN (" + placeholders + ") ORDER BY id",
                chunkRowMapper, articleIds.toArray());
    }

    private final RowMapper<KnowledgeChunk> chunkRowMapper = (rs, i) -> {
        KnowledgeChunk c = new KnowledgeChunk();
        c.setId(rs.getLong("id"));
        c.setSourceType(rs.getString("source_type"));
        c.setSourceId(rs.getLong("source_id"));
        c.setSourceName(rs.getString("source_name"));
        c.setChunkIndex(rs.getInt("chunk_index"));
        c.setContent(rs.getString("content"));
        c.setCreatedAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null);
        return c;
    };

    // ======================== Admin Sessions ========================

    /** All active sessions (for admin session management). */
    public List<UserSession> findAllActiveSessions() {
        return jdbcTemplate.query(
                "SELECT id,user_id,username,token_jti,ip_address,user_agent,device_info,"
                        + "login_time,last_seen_time,expires_at,is_active "
                        + "FROM user_sessions WHERE is_active=TRUE ORDER BY last_seen_time DESC",
                sessionRowMapper);
    }
}
