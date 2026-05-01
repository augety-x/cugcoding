package com.cugcoding.forum.web;

import com.cugcoding.forum.auth.AuthContext;
import com.cugcoding.forum.model.LoginAudit;
import com.cugcoding.forum.model.PostDetail;
import com.cugcoding.forum.model.UserSession;
import com.cugcoding.forum.service.ForumService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Validated
public class ApiController {

    private static final String COOKIE_NAME = "f-session";
    private static final int COOKIE_MAX_AGE = 7 * 24 * 60 * 60; // 7 days, matches JWT expiry
    private static final String COOKIE_PATH = "/";

    private final ForumService service;

    public ApiController(ForumService service) {
        this.service = service;
    }

    // ======================== Auth ========================

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody @Valid AuthRequest body,
                                        HttpServletRequest request,
                                        HttpServletResponse response) {
        ForumService.LoginResult result = service.register(
                body.getUsername(), body.getPassword(),
                getClientIp(request), request.getHeader("User-Agent"));
        setTokenCookie(response, result.getToken());
        return buildLoginResponse(result);
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody @Valid AuthRequest body,
                                     HttpServletRequest request,
                                     HttpServletResponse response) {
        ForumService.LoginResult result = service.login(
                body.getUsername(), body.getPassword(),
                getClientIp(request), request.getHeader("User-Agent"));
        setTokenCookie(response, result.getToken());
        return buildLoginResponse(result);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request,
                                       HttpServletResponse response) {
        String token = extractToken(request);
        if (token != null) {
            service.logout(token);
        }
        clearTokenCookie(response);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        AuthContext.LoginUser user = AuthContext.get();
        Map<String, Object> result = new LinkedHashMap<>();
        if (user != null) {
            result.put("id", user.getUserId());
            result.put("username", user.getUsername());
            result.put("role", user.getRole());
        }
        return result;
    }

    // ======================== User Session & Audit ========================

    /** Get current user's active sessions. */
    @GetMapping("/sessions")
    public List<UserSession> mySessions() {
        Long userId = AuthContext.requireLogin();
        return service.getActiveSessions(userId);
    }

    /** User terminates own session. */
    @PostMapping("/sessions/{tokenJti}/terminate")
    public ResponseEntity<Void> terminateSession(@PathVariable String tokenJti) {
        Long userId = AuthContext.requireLogin();
        service.terminateSession(userId, tokenJti);
        return ResponseEntity.ok().build();
    }

    /** Get current user's audit history. */
    @GetMapping("/audits")
    public List<LoginAudit> myAudits(@RequestParam(defaultValue = "20") int limit) {
        Long userId = AuthContext.requireLogin();
        return service.getRecentAudits(userId, limit);
    }

    // ======================== Admin Endpoints ========================

    /** Dashboard statistics (admin only). */
    @GetMapping("/admin/stats")
    public Map<String, Object> dashboardStats() {
        AuthContext.requireAdmin();
        return service.getDashboardStats();
    }

    /** Admin: list all active sessions across all users. */
    @GetMapping("/admin/sessions")
    public List<UserSession> allSessions() {
        AuthContext.requireAdmin();
        return service.getAllActiveSessions();
    }

    /** Admin: terminate any session. */
    @PostMapping("/admin/sessions/{tokenJti}/terminate")
    public ResponseEntity<Void> adminTerminateSession(@PathVariable String tokenJti) {
        AuthContext.requireAdmin();
        service.adminTerminateSession(tokenJti);
        return ResponseEntity.ok().build();
    }

    /** Admin: all login audit records. */
    @GetMapping("/admin/audits")
    public List<LoginAudit> allAudits(@RequestParam(defaultValue = "50") int limit) {
        AuthContext.requireAdmin();
        return service.getAllRecentAudits(limit);
    }

    // ======================== Search ========================

    @GetMapping("/search")
    public ForumService.SearchResult search(
            @RequestParam String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        AuthContext.LoginUser user = AuthContext.get();
        return service.searchPosts(q.trim(), Math.max(1, page), Math.min(50, size),
                user != null ? user.getUserId() : null);
    }

    /** Admin: rebuild ES index from MySQL. */
    @PostMapping("/admin/search/rebuild")
    public ResponseEntity<Void> rebuildIndex() {
        AuthContext.requireAdmin();
        service.rebuildIndex();
        return ResponseEntity.ok().build();
    }

    // ======================== Posts ========================

    @GetMapping("/posts")
    public List<PostDetail> posts() {
        List<PostDetail> posts = service.listPosts();
        AuthContext.LoginUser user = AuthContext.get();
        if (user != null && !posts.isEmpty()) {
            service.fillLikedStatus(posts, user.getUserId());
        }
        return posts;
    }

    @GetMapping("/posts/{id}")
    public PostDetail post(@PathVariable @Min(1) Long id,
                           javax.servlet.http.HttpServletRequest request) {
        // Record view
        service.incrementView(id);
        service.recordVisit("/api/posts/" + id, getClientIp(request));
        // Include like status for logged-in users
        AuthContext.LoginUser user = AuthContext.get();
        return service.getPost(id, user != null ? user.getUserId() : null);
    }

    @PostMapping("/posts")
    public Map<String, Long> createPost(@RequestBody @Valid PostRequest body) {
        Long userId = AuthContext.requireLogin();
        Map<String, Long> response = new HashMap<>();
        response.put("id", service.createPost(userId, body.getTitle(), body.getContent()));
        return response;
    }

    // ======================== Comments ========================

    @PostMapping("/posts/{id}/like")
    public Map<String, Object> toggleLike(@PathVariable @Min(1) Long id) {
        Long userId = AuthContext.requireLogin();
        boolean liked = service.toggleLike(id, userId);
        Map<String, Object> result = new HashMap<>();
        result.put("liked", liked);
        return result;
    }

    @PostMapping("/visits")
    public ResponseEntity<Void> recordPageVisit(@RequestBody Map<String, String> body,
                                                 HttpServletRequest request) {
        String path = body.getOrDefault("path", "/");
        service.recordVisit(path, getClientIp(request));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/posts/{id}/comments")
    public ResponseEntity<Void> comment(@PathVariable @Min(1) Long id,
                                        @RequestBody @Valid CommentRequest body) {
        Long userId = AuthContext.requireLogin();
        service.addComment(id, userId, body.getContent());
        return ResponseEntity.ok().build();
    }

    // ======================== Internal ========================

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        return request.getRemoteAddr();
    }

    private void setTokenCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie(COOKIE_NAME, token);
        cookie.setHttpOnly(true);
        cookie.setPath(COOKIE_PATH);
        cookie.setMaxAge(COOKIE_MAX_AGE);
        response.addCookie(cookie);
    }

    private void clearTokenCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(COOKIE_NAME, "");
        cookie.setHttpOnly(true);
        cookie.setPath(COOKIE_PATH);
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    private String extractToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private Map<String, Object> buildLoginResponse(ForumService.LoginResult result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", result.getId());
        body.put("username", result.getUsername());
        return body;
    }

    // ======================== Request DTOs ========================

    public static class AuthRequest {
        @NotBlank
        private String username;
        @NotBlank
        private String password;
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class PostRequest {
        @NotBlank
        private String title;
        @NotBlank
        private String content;
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }

    public static class CommentRequest {
        @NotBlank
        private String content;
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }
}
