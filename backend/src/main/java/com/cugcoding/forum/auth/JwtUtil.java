package com.cugcoding.forum.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

public class JwtUtil {

    private static final String SECRET = "cugcoding-forum-jwt-secret-key-2024-graduation-project-min-256bits!!";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    private static final String ISSUER = "cugcoding-forum";
    private static final long EXPIRE_MS = 7 * 24 * 60 * 60 * 1000L; // 7 days

    /** Generate JWT token for a user. */
    public static String generate(Long userId, String username, String role) {
        Date now = new Date();
        return Jwts.builder()
                .setId(UUID.randomUUID().toString())
                .setIssuer(ISSUER)
                .setSubject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + EXPIRE_MS))
                .signWith(KEY)
                .compact();
    }

    /** Parse and validate a JWT token. Returns null if invalid/expired/empty. */
    public static Claims parse(String token) {
        if (token == null || token.isEmpty()) return null;
        try {
            return Jwts.parserBuilder()
                    .requireIssuer(ISSUER)
                    .setSigningKey(KEY)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    /** Extract userId from a valid token. Returns null if invalid. */
    public static Long getUserId(String token) {
        Claims claims = parse(token);
        if (claims == null) return null;
        return Long.valueOf(claims.getSubject());
    }

    /** Extract username from a valid token. Returns null if invalid. */
    public static String getUsername(String token) {
        Claims claims = parse(token);
        if (claims == null) return null;
        return claims.get("username", String.class);
    }

    /** Extract JWT ID (jti) from a valid token. Returns null if invalid. */
    public static String getJti(String token) {
        Claims claims = parse(token);
        if (claims == null) return null;
        return claims.getId();
    }

    /** Extract role from a valid token. Returns null if invalid. */
    public static String getRole(String token) {
        Claims claims = parse(token);
        if (claims == null) return null;
        return claims.get("role", String.class);
    }

    /** Extract expiration time from a valid token. Returns null if invalid. */
    public static Date getExpiration(String token) {
        Claims claims = parse(token);
        if (claims == null) return null;
        return claims.getExpiration();
    }
}
