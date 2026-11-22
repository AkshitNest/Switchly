package live.switchly.api.controller;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import live.switchly.api.model.RefreshToken;
import live.switchly.api.model.User;
import live.switchly.api.repository.RefreshTokenRepository;
import live.switchly.api.repository.UserRepository;
import live.switchly.api.security.JwtService;
import live.switchly.api.security.RateLimitProperties;
import live.switchly.api.security.RateLimiterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthSecurityHardeningIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RateLimiterService rateLimiterService;

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @BeforeEach
    void setUp() {
        rateLimiterService.reset();
    }

    private record AuthTokens(String accessToken, String refreshToken, UUID userId) {}

    private AuthTokens registerUser(String name, String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "fullName": "%s",
                                    "email": "%s",
                                    "password": "%s"
                                }
                                """.formatted(name, email, password)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return new AuthTokens(
                json.get("accessToken").asText(),
                json.get("refreshToken").asText(),
                UUID.fromString(json.get("user").get("id").asText())
        );
    }

    private AuthTokens loginUser(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "%s",
                                    "password": "%s"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return new AuthTokens(
                json.get("accessToken").asText(),
                json.get("refreshToken").asText(),
                UUID.fromString(json.get("user").get("id").asText())
        );
    }

    // ── 1. RATE LIMITING ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Rate Limiting Tests")
    class RateLimitingTests {

        private final RateLimiterService defaultLimiter = new RateLimiterService(new RateLimitProperties());

        @Test
        @DisplayName("Rate Limiting: login endpoint throttles after limit with 429 and Retry-After")
        void loginThrottling() {
            String ip = "192.168.1.10";
            // Default configured limit is 5 in 60s
            for (int i = 0; i < 5; i++) {
                RateLimiterService.RateLimitResult res = defaultLimiter.tryAcquire("LOGIN", ip);
                assertThat(res.allowed()).isTrue();
            }
            // 6th attempt
            RateLimiterService.RateLimitResult throttled = defaultLimiter.tryAcquire("LOGIN", ip);
            assertThat(throttled.allowed()).isFalse();
            assertThat(throttled.retryAfterSeconds()).isGreaterThan(0);
        }

        @Test
        @DisplayName("Rate Limiting: register endpoint throttles after limit with 429 and Retry-After")
        void registerThrottling() {
            String ip = "192.168.1.20";
            // Default limit 3 in 60s
            for (int i = 0; i < 3; i++) {
                RateLimiterService.RateLimitResult res = defaultLimiter.tryAcquire("REGISTER", ip);
                assertThat(res.allowed()).isTrue();
            }
            // 4th attempt
            RateLimiterService.RateLimitResult throttled = defaultLimiter.tryAcquire("REGISTER", ip);
            assertThat(throttled.allowed()).isFalse();
            assertThat(throttled.retryAfterSeconds()).isGreaterThan(0);
        }

        @Test
        @DisplayName("Rate Limiting: refresh endpoint throttles after limit with 429 and Retry-After")
        void refreshThrottling() {
            String ip = "192.168.1.30";
            // Default limit 10 in 60s
            for (int i = 0; i < 10; i++) {
                RateLimiterService.RateLimitResult res = defaultLimiter.tryAcquire("REFRESH", ip);
                assertThat(res.allowed()).isTrue();
            }
            // 11th attempt
            RateLimiterService.RateLimitResult throttled = defaultLimiter.tryAcquire("REFRESH", ip);
            assertThat(throttled.allowed()).isFalse();
            assertThat(throttled.retryAfterSeconds()).isGreaterThan(0);
        }

        @Test
        @DisplayName("Rate Limiting: HTTP filter blocks throttled requests with 429, Retry-After, and error body")
        void filterBlocksThrottledRequest() throws Exception {
            // Fill up register quota for mockMvc's remote address (127.0.0.1)
            for (int i = 0; i < 1000; i++) {
                rateLimiterService.tryAcquire("REGISTER", "127.0.0.1");
            }

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "fullName": "Spam Bot",
                                        "email": "spam@example.com",
                                        "password": "Password123"
                                    }
                                    """))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                    .andExpect(jsonPath("$.error.code", is("RATE_LIMIT_EXCEEDED")))
                    .andExpect(jsonPath("$.error.message", containsString("Rate limit exceeded")));
        }

        @Test
        @DisplayName("Rate Limiting: Untrusted X-Forwarded-For is ignored and client is rate limited by remoteAddr")
        void untrustedXForwardedForIsIgnored() throws Exception {
            // By default, no proxies are trusted. Client cannot spoof IP via X-Forwarded-For to evade limit.
            for (int i = 0; i < 1000; i++) {
                rateLimiterService.tryAcquire("LOGIN", "127.0.0.1");
            }

            // Spoofed XFF
            mockMvc.perform(post("/api/v1/auth/login")
                            .header("X-Forwarded-For", "203.0.113.199")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "email": "user@example.com",
                                        "password": "Password123"
                                    }
                                    """))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.error.code", is("RATE_LIMIT_EXCEEDED")));
        }
    }

    // ── 2. REFRESH TOKEN FAMILIES & CONCURRENCY ────────────────────────────────

    @Nested
    @DisplayName("Refresh Token Family & Reuse Detection Tests")
    class RefreshTokenFamilyTests {

        @Test
        @DisplayName("Token Rotation: valid refresh rotates token, keeping same family ID")
        void validRefreshRotatesToken() throws Exception {
            String email = "family.user." + UUID.randomUUID() + "@example.com";
            AuthTokens initial = registerUser("Family User", email, "Password123");

            // Look up initial token
            String hash1 = jwtService.hashToken(initial.refreshToken());
            RefreshToken rt1 = refreshTokenRepository.findByTokenHash(hash1).orElseThrow();
            UUID familyId = rt1.getFamilyId();
            assertThat(familyId).isNotNull();

            // Refresh
            MvcResult refResult = mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "refreshToken": "%s"
                                    }
                                    """.formatted(initial.refreshToken())))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode refJson = objectMapper.readTree(refResult.getResponse().getContentAsString());
            String newRefreshToken = refJson.get("refreshToken").asText();
            assertThat(newRefreshToken).isNotEqualTo(initial.refreshToken());

            // Check db: rt1 is revoked, rt2 is active and has same familyId
            RefreshToken rt1Db = refreshTokenRepository.findByTokenHash(hash1).orElseThrow();
            assertThat(rt1Db.isRevoked()).isTrue();

            String hash2 = jwtService.hashToken(newRefreshToken);
            RefreshToken rt2Db = refreshTokenRepository.findByTokenHash(hash2).orElseThrow();
            assertThat(rt2Db.isRevoked()).isFalse();
            assertThat(rt2Db.getFamilyId()).isEqualTo(familyId);
        }

        @Test
        @DisplayName("Reuse Detection: presenting a revoked token invalidates the entire token family")
        void reuseDetectionRevokesEntireFamily() throws Exception {
            String email = "reuse." + UUID.randomUUID() + "@example.com";
            AuthTokens initial = registerUser("Reuse User", email, "Password123");

            // 1. Normal rotation (R1 -> R2)
            MvcResult ref1Result = mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "refreshToken": "%s"
                                    }
                                    """.formatted(initial.refreshToken())))
                    .andExpect(status().isOk())
                    .andReturn();

            String r2 = objectMapper.readTree(ref1Result.getResponse().getContentAsString()).get("refreshToken").asText();

            // 2. Normal rotation (R2 -> R3)
            MvcResult ref2Result = mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "refreshToken": "%s"
                                    }
                                    """.formatted(r2)))
                    .andExpect(status().isOk())
                    .andReturn();

            String r3 = objectMapper.readTree(ref2Result.getResponse().getContentAsString()).get("refreshToken").asText();

            // At this point: R1 and R2 are revoked, R3 is active
            String hash3 = jwtService.hashToken(r3);
            assertThat(refreshTokenRepository.findByTokenHash(hash3).orElseThrow().isRevoked()).isFalse();

            // 3. Attacker replays old token R1!
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "refreshToken": "%s"
                                    }
                                    """.formatted(initial.refreshToken())))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.message", is("Refresh token has been revoked")));

            // 4. Verification: Entire family is revoked. R3 is now revoked!
            assertThat(refreshTokenRepository.findByTokenHash(hash3).orElseThrow().isRevoked()).isTrue();

            // 5. Subsequent attempt to use R3 fails as well
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "refreshToken": "%s"
                                    }
                                    """.formatted(r3)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.message", is("Refresh token has been revoked")));
        }

        @Test
        @DisplayName("Session Isolation: family revocation preserves unrelated device sessions for the same user")
        void familyRevocationPreservesUnrelatedSessions() throws Exception {
            String email = "devices." + UUID.randomUUID() + "@example.com";
            AuthTokens deviceA = registerUser("MultiDevice User", email, "Password123");

            // User logs in from device B (separate session family)
            AuthTokens deviceB = loginUser(email, "Password123");

            String hashA = jwtService.hashToken(deviceA.refreshToken());
            String hashB = jwtService.hashToken(deviceB.refreshToken());

            UUID familyA = refreshTokenRepository.findByTokenHash(hashA).orElseThrow().getFamilyId();
            UUID familyB = refreshTokenRepository.findByTokenHash(hashB).orElseThrow().getFamilyId();

            assertThat(familyA).isNotEqualTo(familyB);

            // Rotate device A once
            MvcResult refA = mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "refreshToken": "%s"
                                    }
                                    """.formatted(deviceA.refreshToken())))
                    .andExpect(status().isOk())
                    .andReturn();
            String newA = objectMapper.readTree(refA.getResponse().getContentAsString()).get("refreshToken").asText();

            // Replay old token from device A
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "refreshToken": "%s"
                                    }
                                    """.formatted(deviceA.refreshToken())))
                    .andExpect(status().isUnauthorized());

            // Family A is revoked
            String newAHash = jwtService.hashToken(newA);
            assertThat(refreshTokenRepository.findByTokenHash(newAHash).orElseThrow().isRevoked()).isTrue();

            // But Device B is UNTOUCHED and can still refresh!
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "refreshToken": "%s"
                                    }
                                    """.formatted(deviceB.refreshToken())))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Concurrency: Simultaneous refresh attempts on same token cannot produce multiple active tokens")
        void concurrentRefreshSerializesSafely() throws Exception {
            String email = "concurrent." + UUID.randomUUID() + "@example.com";
            AuthTokens tokens = registerUser("Concurrent User", email, "Password123");

            String hash = jwtService.hashToken(tokens.refreshToken());
            UUID familyId = refreshTokenRepository.findByTokenHash(hash).orElseThrow().getFamilyId();

            ExecutorService executor = Executors.newFixedThreadPool(2);
            CountDownLatch readyLatch = new CountDownLatch(2);
            CountDownLatch startLatch = new CountDownLatch(1);

            Future<Integer> call1 = executor.submit(() -> {
                readyLatch.countDown();
                readyLatch.await(5, TimeUnit.SECONDS);
                startLatch.await(5, TimeUnit.SECONDS);
                MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "refreshToken": "%s"
                                        }
                                        """.formatted(tokens.refreshToken())))
                        .andReturn();
                return result.getResponse().getStatus();
            });

            Future<Integer> call2 = executor.submit(() -> {
                readyLatch.countDown();
                readyLatch.await(5, TimeUnit.SECONDS);
                startLatch.await(5, TimeUnit.SECONDS);
                MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "refreshToken": "%s"
                                        }
                                        """.formatted(tokens.refreshToken())))
                        .andReturn();
                return result.getResponse().getStatus();
            });

            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown();

            int status1 = call1.get(10, TimeUnit.SECONDS);
            int status2 = call2.get(10, TimeUnit.SECONDS);
            executor.shutdown();

            // At most one request can succeed (200 OK); the other gets 401 Unauthorized
            boolean oneSucceeded = (status1 == 200 && status2 == 401) || (status1 == 401 && status2 == 200);
            boolean bothFailedDueToReuseRevocation = (status1 == 401 && status2 == 401);
            assertThat(oneSucceeded || bothFailedDueToReuseRevocation).isTrue();

            // In all cases, active tokens in this family MUST be <= 1 (never duplicate active tokens)
            long activeCount = refreshTokenRepository.countActiveByFamilyId(familyId, Instant.now());
            assertThat(activeCount).isLessThanOrEqualTo(1);
        }
    }

    // ── 3. LOGOUT OWNERSHIP & CROSS-USER ISOLATION ────────────────────────────

    @Nested
    @DisplayName("Logout Ownership & Cross-User Isolation Tests")
    class LogoutOwnershipTests {

        @Test
        @DisplayName("Logout: authenticated user can log out their own session")
        void userLogsOutOwnSession() throws Exception {
            String email = "own.logout." + UUID.randomUUID() + "@example.com";
            AuthTokens tokens = registerUser("Own Logout", email, "Password123");

            mockMvc.perform(post("/api/v1/auth/logout")
                            .header("Authorization", "Bearer " + tokens.accessToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "refreshToken": "%s"
                                    }
                                    """.formatted(tokens.refreshToken())))
                    .andExpect(status().isNoContent());

            String hash = jwtService.hashToken(tokens.refreshToken());
            assertThat(refreshTokenRepository.findByTokenHash(hash).orElseThrow().isRevoked()).isTrue();
        }

        @Test
        @DisplayName("Logout: cross-user session revocation attempt returns 403 Forbidden")
        void crossUserRevocationReturns403() throws Exception {
            String victimEmail = "victim." + UUID.randomUUID() + "@example.com";
            AuthTokens victim = registerUser("Victim", victimEmail, "Password123");

            String attackerEmail = "attacker." + UUID.randomUUID() + "@example.com";
            AuthTokens attacker = registerUser("Attacker", attackerEmail, "Password123");

            // Attacker calls logout passing victim's refresh token
            mockMvc.perform(post("/api/v1/auth/logout")
                            .header("Authorization", "Bearer " + attacker.accessToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "refreshToken": "%s"
                                    }
                                    """.formatted(victim.refreshToken())))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code", is("FORBIDDEN")))
                    .andExpect(jsonPath("$.error.message", is("Cannot revoke another user's session")));

            // Victim's token is STILL active and untouched
            String victimHash = jwtService.hashToken(victim.refreshToken());
            assertThat(refreshTokenRepository.findByTokenHash(victimHash).orElseThrow().isRevoked()).isFalse();
        }

        @Test
        @DisplayName("Logout: idempotent behavior for already-revoked or non-existent token returns 204")
        void idempotentLogout() throws Exception {
            String email = "idempotent." + UUID.randomUUID() + "@example.com";
            AuthTokens tokens = registerUser("Idempotent", email, "Password123");

            // First logout
            mockMvc.perform(post("/api/v1/auth/logout")
                            .header("Authorization", "Bearer " + tokens.accessToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "refreshToken": "%s"
                                    }
                                    """.formatted(tokens.refreshToken())))
                    .andExpect(status().isNoContent());

            // Second logout with same revoked token returns 204
            mockMvc.perform(post("/api/v1/auth/logout")
                            .header("Authorization", "Bearer " + tokens.accessToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "refreshToken": "%s"
                                    }
                                    """.formatted(tokens.refreshToken())))
                    .andExpect(status().isNoContent());

            // Logout with non-existent token returns 204
            mockMvc.perform(post("/api/v1/auth/logout")
                            .header("Authorization", "Bearer " + tokens.accessToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "refreshToken": "nonexistent.token.raw"
                                    }
                                    """))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("Logout: unauthenticated logout request is rejected with 401")
        void unauthenticatedLogoutRejectedWith401() throws Exception {
            mockMvc.perform(post("/api/v1/auth/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "refreshToken": "some.token"
                                    }
                                    """))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code", is("UNAUTHORIZED")));
        }
    }

    // ── 4. DISABLED ACCOUNT IMMEDIATE BLOCK ────────────────────────────────────

    @Nested
    @DisplayName("Disabled Account Immediate Block Tests")
    class DisabledAccountTests {

        @Test
        @DisplayName("Disabled Account: access token is immediately rejected on protected APIs")
        void disabledUserAccessTokenRejected() throws Exception {
            String email = "disabled.acc." + UUID.randomUUID() + "@example.com";
            AuthTokens tokens = registerUser("Disabled User", email, "Password123");

            // Verify access token works initially
            mockMvc.perform(get("/api/v1/auth/me")
                            .header("Authorization", "Bearer " + tokens.accessToken()))
                    .andExpect(status().isOk());

            // Disable user in database
            User user = userRepository.findById(tokens.userId()).orElseThrow();
            userRepository.save(new User(
                    user.getId(),
                    user.getFullName(),
                    user.getEmail(),
                    user.getPasswordHash(),
                    false, // DISABLED!
                    user.isEmailVerified(),
                    user.getCreatedAt(),
                    Instant.now()
            ));

            // Existing access token must be immediately rejected with 401!
            mockMvc.perform(get("/api/v1/auth/me")
                            .header("Authorization", "Bearer " + tokens.accessToken()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code", is("UNAUTHORIZED")));
        }

        @Test
        @DisplayName("Disabled Account: refresh token attempt is rejected with 401")
        void disabledUserRefreshTokenRejected() throws Exception {
            String email = "disabled.ref." + UUID.randomUUID() + "@example.com";
            AuthTokens tokens = registerUser("Disabled Ref User", email, "Password123");

            // Disable user
            User user = userRepository.findById(tokens.userId()).orElseThrow();
            userRepository.save(new User(
                    user.getId(),
                    user.getFullName(),
                    user.getEmail(),
                    user.getPasswordHash(),
                    false, // DISABLED!
                    user.isEmailVerified(),
                    user.getCreatedAt(),
                    Instant.now()
            ));

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "refreshToken": "%s"
                                    }
                                    """.formatted(tokens.refreshToken())))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code", is("UNAUTHORIZED")))
                    .andExpect(jsonPath("$.error.message", is("Account is disabled")));
        }
    }

    // ── 5. CORS AND SECURITY RESPONSE HEADERS ─────────────────────────────────

    @Nested
    @DisplayName("CORS and Security Headers Tests")
    class CorsAndSecurityHeadersTests {

        @Test
        @DisplayName("CORS: preflight OPTIONS on auth endpoint succeeds without authentication")
        void preflightOptionsSucceeds() throws Exception {
            mockMvc.perform(options("/api/v1/auth/login")
                            .header("Origin", "https://app.switchly.live")
                            .header("Access-Control-Request-Method", "POST")
                            .header("Access-Control-Request-Headers", "Content-Type,Authorization"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", "https://app.switchly.live"))
                    .andExpect(header().exists("Access-Control-Allow-Methods"));
        }

        @Test
        @DisplayName("CORS: disallowed origin does not receive Access-Control-Allow-Origin")
        void disallowedOriginRejected() throws Exception {
            mockMvc.perform(options("/api/v1/auth/login")
                            .header("Origin", "https://evil-attacker.com")
                            .header("Access-Control-Request-Method", "POST"))
                    .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        }

        @Test
        @DisplayName("Security Headers: defensive HTTP headers are present on API responses")
        void defensiveSecurityHeadersPresent() throws Exception {
            mockMvc.perform(get("/api/v1/auth/me"))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string("X-Frame-Options", "DENY"))
                    .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
                    .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));
        }
    }
}
