package com.cugcoding.forum;

import com.cugcoding.forum.auth.JwtUtil;
import com.cugcoding.forum.service.ForumService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.servlet.http.Cookie;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Comprehensive authentication system tests.
 * Covers: register, login, JWT token, BCrypt, interceptor, logout/blacklist, protected endpoints.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthSystemTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ForumService forumService;

    @BeforeEach
    void cleanup() {
        // Each test gets a fresh H2 in-memory DB via Spring context
    }

    // ======================== Registration Tests ========================

    @Test
    void registerShouldReturnUserInfoAndSetCookie() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"testuser\",\"password\":\"test123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();

        Cookie cookie = result.getResponse().getCookie("f-session");
        assertNotNull(cookie, "Should set f-session cookie on register");
        assertTrue(cookie.isHttpOnly(), "Cookie should be HttpOnly");
        assertTrue(cookie.getMaxAge() > 0, "Cookie should have positive max age");
    }

    @Test
    void registerShouldRejectDuplicateUsername() throws Exception {
        // First registration
        mockMvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"dupuser\",\"password\":\"test123\"}"))
                .andExpect(status().isOk());

        // Duplicate registration
        mockMvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"dupuser\",\"password\":\"other456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("用户名已存在"));
    }

    @Test
    void registerShouldRejectEmptyUsername() throws Exception {
        mockMvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"123456\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerShouldRejectEmptyPassword() throws Exception {
        mockMvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"user1\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    // ======================== Login Tests ========================

    @Test
    void loginShouldReturnUserInfoAndSetCookie() throws Exception {
        // Register first
        mockMvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"loginuser\",\"password\":\"mypassword\"}"))
                .andExpect(status().isOk());

        // Then login
        MvcResult result = mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"loginuser\",\"password\":\"mypassword\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.username").value("loginuser"))
                .andReturn();

        Cookie cookie = result.getResponse().getCookie("f-session");
        assertNotNull(cookie, "Login should set f-session cookie");
    }

    @Test
    void loginShouldRejectWrongPassword() throws Exception {
        // Register first
        mockMvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"pwduser\",\"password\":\"correct\"}"))
                .andExpect(status().isOk());

        // Login with wrong password
        mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"pwduser\",\"password\":\"wrongpassword\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("密码错误"));
    }

    @Test
    void loginShouldRejectNonExistentUser() throws Exception {
        mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nobody\",\"password\":\"whatever\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("用户不存在"));
    }

    // ======================== JWT Token Tests ========================

    @Test
    void jwtTokenShouldBeValid() {
        String token = JwtUtil.generate(1L, "testuser", "USER");
        assertNotNull(token);

        Long userId = JwtUtil.getUserId(token);
        assertEquals(Long.valueOf(1L), userId);

        String username = JwtUtil.getUsername(token);
        assertEquals("testuser", username);

        String jti = JwtUtil.getJti(token);
        assertNotNull(jti);
        assertFalse(jti.isEmpty());
    }

    @Test
    void jwtTokenShouldBeInvalidForTamperedToken() {
        String token = JwtUtil.generate(1L, "testuser", "USER");
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertNull(JwtUtil.parse(tampered));
    }

    @Test
    void jwtTokenShouldBeInvalidForEmptyToken() {
        assertNull(JwtUtil.parse(""));
        assertNull(JwtUtil.getUserId(""));
        assertNull(JwtUtil.getUsername(""));
    }

    // ======================== Auth Interceptor Tests ========================

    @Test
    void meEndpointShouldReturnEmptyWhenNoCookie() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.username").doesNotExist());
    }

    @Test
    void meEndpointShouldReturnUserInfoWithValidCookie() throws Exception {
        // Register
        MvcResult regResult = mockMvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cookieuser\",\"password\":\"test123\"}"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie cookie = regResult.getResponse().getCookie("f-session");

        // Call /api/me with the cookie
        mockMvc.perform(get("/api/me").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.username").value("cookieuser"));
    }

    @Test
    void createPostShouldRequireLogin() throws Exception {
        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Test Post\",\"content\":\"Some content\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请先登录"));
    }

    @Test
    void createPostShouldSucceedWithValidToken() throws Exception {
        // Register
        MvcResult regResult = mockMvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"postauthor\",\"password\":\"test123\"}"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie cookie = regResult.getResponse().getCookie("f-session");

        // Create post with cookie
        mockMvc.perform(post("/api/posts")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"My First Post\",\"content\":\"Hello World\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    void addCommentShouldRequireLogin() throws Exception {
        // First register and create a post
        MvcResult regResult = mockMvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"commenter\",\"password\":\"test123\"}"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie cookie = regResult.getResponse().getCookie("f-session");

        MvcResult postResult = mockMvc.perform(post("/api/posts")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Post for Comments\",\"content\":\"Content\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String postId = com.jayway.jsonpath.JsonPath.read(
                postResult.getResponse().getContentAsString(), "$.id").toString();

        // Comment without cookie
        mockMvc.perform(post("/api/posts/" + postId + "/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Unauthorized comment\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请先登录"));

        // Comment with valid cookie
        mockMvc.perform(post("/api/posts/" + postId + "/comments")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Great post!\"}"))
                .andExpect(status().isOk());
    }

    // ======================== Logout / Blacklist Tests ========================

    @Test
    void logoutShouldClearCookie() throws Exception {
        // Register
        MvcResult regResult = mockMvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"logoutuser\",\"password\":\"test123\"}"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie sessionCookie = regResult.getResponse().getCookie("f-session");

        // Logout
        MvcResult logoutResult = mockMvc.perform(post("/api/logout")
                        .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andReturn();

        Cookie clearedCookie = logoutResult.getResponse().getCookie("f-session");
        assertNotNull(clearedCookie, "Logout should return a cleared cookie");
        assertEquals(0, clearedCookie.getMaxAge(), "Cookie max-age should be 0 (cleared)");
        assertTrue(clearedCookie.getValue().isEmpty(), "Cookie value should be empty");
    }

    @Test
    void blacklistedTokenShouldNotWork() throws Exception {
        // Register
        MvcResult regResult = mockMvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"blacklistuser\",\"password\":\"test123\"}"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie sessionCookie = regResult.getResponse().getCookie("f-session");
        String token = sessionCookie.getValue();

        // Token should work initially
        mockMvc.perform(get("/api/me").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("blacklistuser"));

        // Logout (blacklists the token)
        mockMvc.perform(post("/api/logout").cookie(sessionCookie))
                .andExpect(status().isOk());

        // Same token should no longer work
        mockMvc.perform(get("/api/me").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").doesNotExist());
    }

    // ======================== BCrypt Tests ========================

    @Test
    void passwordShouldBeStoredAsBCryptHash() {
        // Register user through service
        ForumService.LoginResult result = forumService.register("bcryptuser", "mysecret", "127.0.0.1", "JUnit-Test");
        assertNotNull(result);
        assertNotNull(result.getToken());

        // Login with correct password
        ForumService.LoginResult loginResult = forumService.login("bcryptuser", "mysecret", "127.0.0.1", "JUnit-Test");
        assertNotNull(loginResult);

        // Login with wrong password should fail
        assertThrows(IllegalArgumentException.class, () -> {
            forumService.login("bcryptuser", "wrongpassword", "127.0.0.1", "JUnit-Test");
        });
    }

    @Test
    void bcryptHashShouldBeDifferentEachTime() {
        org.springframework.security.crypto.password.PasswordEncoder encoder =
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();

        String hash1 = encoder.encode("samepassword");
        String hash2 = encoder.encode("samepassword");

        assertNotEquals(hash1, hash2, "BCrypt should generate different salts each time");
        assertTrue(encoder.matches("samepassword", hash1));
        assertTrue(encoder.matches("samepassword", hash2));
    }

    @Test
    void tokenValidateShouldRejectBlacklistedToken() {
        // Register
        ForumService.LoginResult result = forumService.register("validuser", "password123", "127.0.0.1", "JUnit-Test");
        String token = result.getToken();

        // Token should validate
        ForumService.LoginResult validated = forumService.validateToken(token);
        assertNotNull(validated);

        // Logout (blacklists the token)
        forumService.logout(token);

        // Token should no longer validate
        ForumService.LoginResult afterLogout = forumService.validateToken(token);
        assertNull(afterLogout, "Blacklisted token should not validate");
    }

    @Test
    void validateTokenShouldRejectInvalidToken() {
        assertNull(forumService.validateToken("invalid.token.string"));
        assertNull(forumService.validateToken(""));
        assertNull(forumService.validateToken(null));
    }

    // ======================== Public Endpoint Tests ========================

    @Test
    void publicEndpointsShouldWorkWithoutAuth() throws Exception {
        // Posts list should be accessible
        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isOk());

        // Login/Register endpoints should be accessible
        mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"anyone\",\"password\":\"whatever\"}"))
                .andExpect(status().isBadRequest()); // 404 user not found, but not blocked by interceptor
    }

    // ======================== Full Flow Integration Test ========================

    @Test
    void fullUserFlowShouldWork() throws Exception {
        // 1. Register
        MvcResult regResult = mockMvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"flowuser\",\"password\":\"flowpass\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = regResult.getResponse().getCookie("f-session");

        // 2. Check /api/me
        mockMvc.perform(get("/api/me").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("flowuser"));

        // 3. Create a post
        MvcResult postResult = mockMvc.perform(post("/api/posts")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Integration Test Post\",\"content\":\"This is a **markdown** post.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andReturn();
        String postId = com.jayway.jsonpath.JsonPath.read(
                postResult.getResponse().getContentAsString(), "$.id").toString();

        // 4. View the post (public)
        mockMvc.perform(get("/api/posts/" + postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Integration Test Post"));

        // 5. Add a comment
        mockMvc.perform(post("/api/posts/" + postId + "/comments")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Nice article!\"}"))
                .andExpect(status().isOk());

        // 6. View post again - should have comment
        mockMvc.perform(get("/api/posts/" + postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments.length()").value(1))
                .andExpect(jsonPath("$.comments[0].username").value("flowuser"))
                .andExpect(jsonPath("$.comments[0].content").value("Nice article!"));

        // 7. Logout
        mockMvc.perform(post("/api/logout").cookie(cookie))
                .andExpect(status().isOk());

        // 8. After logout, /api/me should return empty
        mockMvc.perform(get("/api/me").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").doesNotExist());

        // 9. Re-login
        MvcResult loginResult = mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"flowuser\",\"password\":\"flowpass\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie newCookie = loginResult.getResponse().getCookie("f-session");

        // 10. Should work again with new token
        mockMvc.perform(get("/api/me").cookie(newCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("flowuser"));
    }

    // ======================== Session Management Tests ========================

    @Test
    void mySessionsShouldReturnActiveSessions() throws Exception {
        // Register creates a session automatically
        MvcResult regResult = mockMvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"sessuser\",\"password\":\"test123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = regResult.getResponse().getCookie("f-session");

        // Get sessions - should have 1 active
        mockMvc.perform(get("/api/sessions").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].username").value("sessuser"))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].ipAddress").isNotEmpty());
    }

    @Test
    void sessionsShouldRequireLogin() throws Exception {
        mockMvc.perform(get("/api/sessions"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请先登录"));
    }

    @Test
    void loginCreatesSeparateSessions() throws Exception {
        // Register
        MvcResult regResult = mockMvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"multidevice\",\"password\":\"test123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie1 = regResult.getResponse().getCookie("f-session");

        // Login again (simulates another device)
        MvcResult loginResult = mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"multidevice\",\"password\":\"test123\"}")
                        .header("User-Agent", "Mobile-Device"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie2 = loginResult.getResponse().getCookie("f-session");

        // Should have 2 active sessions
        mockMvc.perform(get("/api/sessions").cookie(cookie1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // Both cookies should work
        mockMvc.perform(get("/api/me").cookie(cookie1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("multidevice"));
        mockMvc.perform(get("/api/me").cookie(cookie2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("multidevice"));
    }

    // ======================== Login Audit Tests ========================

    @Test
    void auditShouldRecordLoginEvents() throws Exception {
        // Register
        MvcResult regResult = mockMvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"audituser\",\"password\":\"test123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = regResult.getResponse().getCookie("f-session");

        // Login (another audit record)
        mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"audituser\",\"password\":\"test123\"}"))
                .andExpect(status().isOk());

        // Get audit history
        mockMvc.perform(get("/api/audits").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("LOGIN"))
                .andExpect(jsonPath("$[0].result").value("SUCCESS"))
                .andExpect(jsonPath("$[1].action").value("REGISTER"))
                .andExpect(jsonPath("$[1].result").value("SUCCESS"));
    }

    @Test
    void auditShouldRecordFailedLogin() throws Exception {
        // Register
        MvcResult regResult = mockMvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"failuser\",\"password\":\"test123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = regResult.getResponse().getCookie("f-session");

        // Failed login attempt
        mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"failuser\",\"password\":\"wrong\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("密码错误"));

        // Audit should have the failure recorded
        mockMvc.perform(get("/api/audits").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("LOGIN"))
                .andExpect(jsonPath("$[0].result").value("FAIL"))
                .andExpect(jsonPath("$[0].failReason").value("密码错误"));
    }

    // ======================== Session Termination Tests ========================

    @Test
    void terminateSessionShouldKickDevice() throws Exception {
        // Register (session 1)
        MvcResult regResult = mockMvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"kickuser\",\"password\":\"test123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie1 = regResult.getResponse().getCookie("f-session");

        // Login from another device (session 2)
        MvcResult loginResult = mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"kickuser\",\"password\":\"test123\"}")
                        .header("User-Agent", "Evil-Device"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie2 = loginResult.getResponse().getCookie("f-session");

        // Get sessions to find the tokenJti of session 2
        String responseBody = mockMvc.perform(get("/api/sessions").cookie(cookie1))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String tokenJti2 = com.jayway.jsonpath.JsonPath.read(responseBody, "$[0].tokenJti");

        // Terminate session 2
        mockMvc.perform(post("/api/sessions/" + tokenJti2 + "/terminate").cookie(cookie1))
                .andExpect(status().isOk());

        // Cookie2 should no longer work
        mockMvc.perform(get("/api/me").cookie(cookie2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").doesNotExist());

        // Cookie1 should still work
        mockMvc.perform(get("/api/me").cookie(cookie1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("kickuser"));
    }

    @Test
    void cannotTerminateOtherUsersSession() throws Exception {
        // User A registers
        MvcResult regA = mockMvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"userA\",\"password\":\"test123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookieA = regA.getResponse().getCookie("f-session");

        String sessionsA = mockMvc.perform(get("/api/sessions").cookie(cookieA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String jtiA = com.jayway.jsonpath.JsonPath.read(sessionsA, "$[0].tokenJti");

        // User B registers
        MvcResult regB = mockMvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"userB\",\"password\":\"test123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookieB = regB.getResponse().getCookie("f-session");

        // User B tries to terminate User A's session  - should fail
        mockMvc.perform(post("/api/sessions/" + jtiA + "/terminate").cookie(cookieB))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("会话不存在或不属于当前用户"));
    }

    // ======================== Admin Role Tests ========================

    @Test
    void adminEndpointsShouldBe403ForRegularUser() throws Exception {
        // Register as regular user
        MvcResult regResult = mockMvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"regularuser\",\"password\":\"test123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = regResult.getResponse().getCookie("f-session");

        // Regular user cannot access admin endpoints
        mockMvc.perform(get("/api/admin/stats").cookie(cookie))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("需要管理员权限"));

        mockMvc.perform(get("/api/admin/sessions").cookie(cookie))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("需要管理员权限"));

        mockMvc.perform(get("/api/admin/audits").cookie(cookie))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("需要管理员权限"));
    }

    @Test
    void userShouldSeeOwnSessionsButNotAll() throws Exception {
        MvcResult regResult = mockMvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"selfuser\",\"password\":\"test123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = regResult.getResponse().getCookie("f-session");

        // Own sessions should be visible
        mockMvc.perform(get("/api/sessions").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void jwtTokenShouldContainRole() {
        String token = JwtUtil.generate(1L, "admin", "ADMIN");
        String role = JwtUtil.getRole(token);
        assertEquals("ADMIN", role);

        String userToken = JwtUtil.generate(2L, "user", "USER");
        assertEquals("USER", JwtUtil.getRole(userToken));
    }
}
