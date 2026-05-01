package com.cugcoding.forum.auth;

import com.cugcoding.forum.service.ForumService;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Intercepts requests to extract and validate JWT from the "f-session" cookie.
 * Sets AuthContext for the current thread; clears it after request completion.
 * Public endpoints (login, register, home) pass through without authentication.
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {

    private static final String COOKIE_NAME = "f-session";

    private final ForumService forumService;

    public LoginInterceptor(ForumService forumService) {
        this.forumService = forumService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        String token = extractToken(request);
        if (token != null) {
            ForumService.LoginResult result = forumService.validateToken(token);
            if (result != null) {
                AuthContext.set(new AuthContext.LoginUser(result.getId(), result.getUsername(), result.getRole()));
            }
        }
        return true; // always allow the request; controllers decide if auth is required
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        AuthContext.clear();
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
}
