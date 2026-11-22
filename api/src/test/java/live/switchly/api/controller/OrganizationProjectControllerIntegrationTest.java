package live.switchly.api.controller;

import live.switchly.api.model.User;
import live.switchly.api.repository.UserRepository;
import live.switchly.api.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class OrganizationProjectControllerIntegrationTest extends ScopedSessionSupport {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    private String authToken;
    private User testUser;

    @BeforeEach
    void setUp() {
        UUID userId = UUID.randomUUID();
        this.testUser = userRepository.save(new User(
                userId,
                "Org Project Tester",
                "orgtest." + userId + "@example.com",
                "hash",
                true,
                true
        ));
        this.authToken = workspaceToken(testUser);
    }

    @Test
    void organizationAndProjectLifecycle() throws Exception {
        mockMvc.perform(post("/api/v1/orgs")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_FAILED")));

        MvcResult orgResult = mockMvc.perform(post("/api/v1/orgs")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Acme\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Acme")))
                .andExpect(jsonPath("$.createdAt", notNullValue()))
                .andReturn();

        String orgJson = orgResult.getResponse().getContentAsString();
        String orgId = extractJsonField(orgJson, "id");
        authToken = workspaceToken(testUser, orgId);

        mockMvc.perform(get("/api/v1/orgs/" + orgId)
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(orgId)))
                .andExpect(jsonPath("$.name", is("Acme")));

        mockMvc.perform(get("/api/v1/orgs")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name", is("Acme")));

        MvcResult projectResult = mockMvc.perform(post("/api/v1/orgs/" + orgId + "/projects")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Web App\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizationId", is(orgId)))
                .andExpect(jsonPath("$.name", is("Web App")))
                .andExpect(jsonPath("$.createdAt", notNullValue()))
                .andReturn();

        String projectJson = projectResult.getResponse().getContentAsString();
        String projectId = extractJsonField(projectJson, "id");

        mockMvc.perform(get("/api/v1/projects/" + projectId)
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(projectId)))
                .andExpect(jsonPath("$.organizationId", is(orgId)))
                .andExpect(jsonPath("$.name", is("Web App")));

        mockMvc.perform(get("/api/v1/orgs/" + orgId + "/projects")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name", is("Web App")));
    }

    @Test
    void crossTenantAccess_returns403Forbidden() throws Exception {
        // User A creates Org A
        MvcResult orgResult = mockMvc.perform(post("/api/v1/orgs")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"User A Org\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String orgId = extractJsonField(orgResult.getResponse().getContentAsString(), "id");

        // User B (different user, not member of Org A)
        UUID userBId = UUID.randomUUID();
        User userB = userRepository.save(new User(
                userBId, "User B", "userb." + userBId + "@example.com", "hash", true, true
        ));
        String userBToken = workspaceToken(userB);

        // User B tries to view Org A -> 403 Forbidden
        mockMvc.perform(get("/api/v1/orgs/" + orgId)
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));

        // User B tries to create project under Org A -> 403 Forbidden
        mockMvc.perform(post("/api/v1/orgs/" + orgId + "/projects")
                        .header("Authorization", "Bearer " + userBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Hacked Project\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));
    }

    private static String extractJsonField(String json, String field) {
        String marker = "\"" + field + "\":\"";
        int start = json.indexOf(marker);
        if (start == -1) {
            throw new IllegalArgumentException("Field " + field + " not found in: " + json);
        }
        start += marker.length();
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }
}
