package live.switchly.api.controller;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import live.switchly.api.model.User;
import live.switchly.api.repository.UserRepository;
import live.switchly.api.security.JwtProperties;
import live.switchly.api.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private MockMvc mockMvc;

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Test
    @DisplayName("Registration: creates user, creates initial organization, returns access and refresh tokens")
    void register_success() throws Exception {
        String email = "alice." + UUID.randomUUID() + "@example.com";
        String requestJson = """
                {
                    "fullName": "Alice Architect",
                    "email": "%s",
                    "password": "Password123!",
                    "organizationName": "Alice Corp"
                }
                """.formatted(email);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.expiresIn", is(900)))
                .andExpect(jsonPath("$.user.fullName", is("Alice Architect")))
                .andExpect(jsonPath("$.user.email", is(email.toLowerCase())))
                .andExpect(jsonPath("$.user.enabled", is(true)))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        String accessToken = response.get("accessToken").asText();

        // Verify the access token works on /api/v1/auth/me
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is(email.toLowerCase())));

        // Verify initial organization was created for this user
        mockMvc.perform(get("/api/v1/orgs")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name", is("Alice Corp")));
    }

    @Test
    @DisplayName("Registration: fails with 409 Conflict when email already registered")
    void register_duplicateEmail_returns409Conflict() throws Exception {
        String email = "duplicate." + UUID.randomUUID() + "@example.com";
        String requestJson = """
                {
                    "fullName": "First User",
                    "email": "%s",
                    "password": "Password123!"
                }
                """.formatted(email);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated());

        // Duplicate registration
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("CONFLICT")))
                .andExpect(jsonPath("$.error.message", containsString("already registered")));
    }

    @Test
    @DisplayName("Registration: email is trimmed and normalized to lowercase")
    void register_emailNormalization() throws Exception {
        String base = "Normalized." + UUID.randomUUID();
        String messyEmail = "  " + base + "@EXAMPLE.COM  ";
        String requestJson = """
                {
                    "fullName": "Norm User",
                    "email": "%s",
                    "password": "Password123!"
                }
                """.formatted(messyEmail);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.email", is(base.toLowerCase() + "@example.com")));

        // Login with mixed case should succeed
        String loginJson = """
                {
                    "email": "%s",
                    "password": "Password123!"
                }
                """.formatted("  " + base.toUpperCase() + "@example.com ");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()));
    }

    @Test
    @DisplayName("Registration: validation failures return 400 Bad Request")
    void register_invalidPayload_returns400() throws Exception {
        String invalidJson = """
                {
                    "fullName": "",
                    "email": "not-an-email",
                    "password": "short"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_FAILED")));
    }

    @Test
    @DisplayName("Login: incorrect password returns 401 Unauthorized without account enumeration")
    void login_incorrectPassword_returns401() throws Exception {
        String email = "login.test." + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "fullName": "Test User",
                                    "email": "%s",
                                    "password": "CorrectPassword123"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "%s",
                                    "password": "WrongPassword999"
                                }
                                """.formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("UNAUTHORIZED")))
                .andExpect(jsonPath("$.error.message", is("Invalid email or password")));
    }

    @Test
    @DisplayName("Login: non-existent email returns generic 401 Unauthorized")
    void login_nonExistentUser_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "nonexistent@example.com",
                                    "password": "AnyPassword123"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("UNAUTHORIZED")))
                .andExpect(jsonPath("$.error.message", is("Invalid email or password")));
    }

    @Test
    @DisplayName("Login: disabled user is rejected with 401")
    void login_disabledUser_rejectedWith401() throws Exception {
        String email = "disabled." + UUID.randomUUID() + "@example.com";
        MvcResult reg = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "fullName": "Disabled User",
                                    "email": "%s",
                                    "password": "Password123!"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(reg.getResponse().getContentAsString());
        UUID userId = UUID.fromString(json.get("user").get("id").asText());

        // Disable user in database
        User user = userRepository.findById(userId).orElseThrow();
        User disabledUser = new User(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPasswordHash(),
                false, // disabled
                user.isEmailVerified(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
        userRepository.save(disabledUser);

        // Attempt login
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "%s",
                                    "password": "Password123!"
                                }
                                """.formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("UNAUTHORIZED")))
                .andExpect(jsonPath("$.error.message", is("Account is disabled")));
    }

    @Test
    @DisplayName("Profile update: PUT /api/v1/auth/me updates user full name")
    void updateProfile_success() throws Exception {
        String email = "update." + UUID.randomUUID() + "@example.com";
        MvcResult reg = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "fullName": "Old Name",
                                    "email": "%s",
                                    "password": "Password123!"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();

        String token = objectMapper.readTree(reg.getResponse().getContentAsString()).get("accessToken").asText();

        mockMvc.perform(put("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "fullName": "New Renamed Name"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName", is("New Renamed Name")));
    }

    @Test
    @DisplayName("Refresh token rotation: rotates token and revokes predecessor")
    void refreshToken_rotationAndRevocation() throws Exception {
        String email = "refresh." + UUID.randomUUID() + "@example.com";
        MvcResult reg = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "fullName": "Refresh Tester",
                                    "email": "%s",
                                    "password": "Password123!"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode initialTokens = objectMapper.readTree(reg.getResponse().getContentAsString());
        String initialRefreshToken = initialTokens.get("refreshToken").asText();

        // 1. Refresh using initial refresh token
        MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "refreshToken": "%s"
                                }
                                """.formatted(initialRefreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()))
                .andReturn();

        JsonNode newTokens = objectMapper.readTree(refreshed.getResponse().getContentAsString());
        String newRefreshToken = newTokens.get("refreshToken").asText();
        assertThat(newRefreshToken).isNotEqualTo(initialRefreshToken);

        // 2. Reuse of old refresh token must be rejected with 401
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "refreshToken": "%s"
                                }
                                """.formatted(initialRefreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("UNAUTHORIZED")))
                .andExpect(jsonPath("$.error.message", is("Refresh token has been revoked")));
    }

    @Test
    @DisplayName("Logout: revokes refresh token and subsequent refresh is rejected")
    void logout_revokesRefreshToken() throws Exception {
        String email = "logout." + UUID.randomUUID() + "@example.com";
        MvcResult reg = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "fullName": "Logout Tester",
                                    "email": "%s",
                                    "password": "Password123!"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode regJson = objectMapper.readTree(reg.getResponse().getContentAsString());
        String accessToken = regJson.get("accessToken").asText();
        String refreshToken = regJson.get("refreshToken").asText();

        // Logout
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "refreshToken": "%s"
                                }
                                """.formatted(refreshToken)))
                .andExpect(status().isNoContent());

        // Subsequent refresh must fail
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "refreshToken": "%s"
                                }
                                """.formatted(refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("UNAUTHORIZED")))
                .andExpect(jsonPath("$.error.message", is("Refresh token has been revoked")));
    }

    @Test
    @DisplayName("JWT Validation: tampered and expired tokens are rejected with 401")
    void jwtValidation_tamperedAndExpiredTokens_returns401() throws Exception {
        // 1. Completely invalid/tampered token
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer invalid.jwt.token.here"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("UNAUTHORIZED")));

        // 2. Token signed with wrong secret
        JwtProperties wrongKeyProps = new JwtProperties();
        wrongKeyProps.setSecret("999E635266556A586E3272357538782F413F4428472B4B6250645367566B5999");
        JwtService wrongJwtService = new JwtService(wrongKeyProps);
        User dummyUser = new User(UUID.randomUUID(), "Dummy", "dummy@example.com", "hash", true, true);
        String wrongSigToken = wrongJwtService.generateAccessToken(dummyUser);

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + wrongSigToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("UNAUTHORIZED")));

        // 3. Expired token
        String expiredToken = jwtService.generateAccessToken(dummyUser, -10, ChronoUnit.MINUTES);
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("UNAUTHORIZED")));
    }

    @Test
    @DisplayName("Security: unauthenticated access to protected endpoints is blocked with standardized 401")
    void unauthenticatedAccess_blockedWith401() throws Exception {
        UUID randomId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/orgs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("UNAUTHORIZED")));

        mockMvc.perform(post("/api/v1/orgs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Secret Corp\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("UNAUTHORIZED")));

        mockMvc.perform(get("/api/v1/projects/" + randomId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("UNAUTHORIZED")));

        mockMvc.perform(get("/api/v1/flags/" + randomId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("UNAUTHORIZED")));
    }
}
