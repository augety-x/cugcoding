package com.cugcoding.forum.auth;

public class AuthContext {
    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    public static void set(LoginUser user) { HOLDER.set(user); }
    public static LoginUser get() { return HOLDER.get(); }
    public static void clear() { HOLDER.remove(); }

    public static Long requireLogin() {
        LoginUser user = get();
        if (user == null) throw new IllegalArgumentException("请先登录");
        return user.getUserId();
    }

    public static void requireAdmin() {
        LoginUser user = get();
        if (user == null) throw new IllegalArgumentException("请先登录");
        if (!user.isAdmin()) throw new IllegalArgumentException("需要管理员权限");
    }

    public static class LoginUser {
        private final Long userId;
        private final String username;
        private final String role;

        public LoginUser(Long userId, String username, String role) {
            this.userId = userId;
            this.username = username;
            this.role = role;
        }

        public Long getUserId() { return userId; }
        public String getUsername() { return username; }
        public String getRole() { return role; }
        public boolean isAdmin() { return "ADMIN".equals(role); }
    }
}
