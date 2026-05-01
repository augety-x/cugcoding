package com.cugcoding.forum.model;

import java.time.LocalDateTime;

public class UserSession {
    private Long id;
    private Long userId;
    private String username;
    private String tokenJti;
    private String ipAddress;
    private String userAgent;
    private String deviceInfo;
    private LocalDateTime loginTime;
    private LocalDateTime lastSeenTime;
    private LocalDateTime expiresAt;
    private boolean active;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getTokenJti() { return tokenJti; }
    public void setTokenJti(String tokenJti) { this.tokenJti = tokenJti; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public String getDeviceInfo() { return deviceInfo; }
    public void setDeviceInfo(String deviceInfo) { this.deviceInfo = deviceInfo; }
    public LocalDateTime getLoginTime() { return loginTime; }
    public void setLoginTime(LocalDateTime loginTime) { this.loginTime = loginTime; }
    public LocalDateTime getLastSeenTime() { return lastSeenTime; }
    public void setLastSeenTime(LocalDateTime lastSeenTime) { this.lastSeenTime = lastSeenTime; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
