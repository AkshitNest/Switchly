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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class FlagControllerIntegrationTest extends ScopedSessionSupport {

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
    private String projectId;

    @BeforeEach
    void setUp() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = userRepository.save(new User(
                userId,
                "Flag Developer",
                "flagdev." + userId + "@example.com",
                "hash",
                true,
                true
        ));
        this.authToken = workspaceToken(user);

        // Create an organization
        MvcResult orgResult = mockMvc.perform(post("/api/v1/orgs")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Acme Corp\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String orgJson = orgResult.getResponse().getContentAsString();
        String orgId = extractJsonField(orgJson, "id");
        authToken = workspaceToken(user, orgId);

        // Create a project under the organization (auto-provisions development, staging, production)
        MvcResult projectResult = mockMvc.perform(post("/api/v1/orgs/" + orgId + "/projects")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Web App\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String projectJson = projectResult.getResponse().getContentAsString();
        this.projectId = extractJsonField(projectJson, "id");
    }

    @Test
    void createFlag_withDescription_returnsCreatedWithDescriptionAndTimestamps() throws Exception {
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/flags")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "key": "new-checkout-flow",
                                    "name": "New Checkout Flow",
                                    "description": "Enables the redesigned 3-step checkout process"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key", is("new-checkout-flow")))
                .andExpect(jsonPath("$.name", is("New Checkout Flow")))
                .andExpect(jsonPath("$.description", is("Enables the redesigned 3-step checkout process")))
                .andExpect(jsonPath("$.valueType", is("BOOLEAN")))
                .andExpect(jsonPath("$.defaultValue", is(false)))
                .andExpect(jsonPath("$.createdAt", notNullValue()))
                .andExpect(jsonPath("$.updatedAt", notNullValue()));
    }

    @Test
    void createFlag_typedValues_stringAndNumber() throws Exception {
        // String flag
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/flags")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "key": "hero-title",
                                    "name": "Hero Title",
                                    "valueType": "STRING",
                                    "defaultValue": "Welcome to Switchly"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key", is("hero-title")))
                .andExpect(jsonPath("$.valueType", is("STRING")))
                .andExpect(jsonPath("$.defaultValue", is("Welcome to Switchly")));

        // Number flag
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/flags")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "key": "max-retries",
                                    "name": "Max Retries",
                                    "valueType": "NUMBER",
                                    "defaultValue": 5
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key", is("max-retries")))
                .andExpect(jsonPath("$.valueType", is("NUMBER")))
                .andExpect(jsonPath("$.defaultValue", is(5)));
    }

    @Test
    void createFlag_incompatibleDefaultValue_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/flags")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "key": "bad-type",
                                    "name": "Bad Type",
                                    "valueType": "BOOLEAN",
                                    "defaultValue": "not-a-boolean"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("INVALID_ARGUMENT")));
    }

    @Test
    void deleteFlag_existingFlag_returns204NoContent_andSubsequentGetReturns404() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/projects/" + projectId + "/flags")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "key": "temp-banner",
                                    "name": "Temporary Banner",
                                    "description": "To be removed after campaign"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String flagId = extractJsonField(createResult.getResponse().getContentAsString(), "id");

        mockMvc.perform(get("/api/v1/flags/" + flagId)
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description", is("To be removed after campaign")));

        // Delete the flag
        mockMvc.perform(delete("/api/v1/flags/" + flagId)
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isNoContent());

        // Subsequent get should return 404
        mockMvc.perform(get("/api/v1/flags/" + flagId)
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("NOT_FOUND")));
    }

    @Test
    void legacyEndpoint_returns400DeprecatedEndpoint() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/projects/" + projectId + "/flags")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\": \"legacy-test\", \"name\": \"Legacy Test\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String flagId = extractJsonField(createResult.getResponse().getContentAsString(), "id");

        mockMvc.perform(put("/api/v1/flags/" + flagId + "/state")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("DEPRECATED_ENDPOINT")))
                .andExpect(jsonPath("$.error.message", containsString("Global flag state mutation is deprecated")));
    }

    @Test
    void environmentScopedFlagLifecycle_isolationAndTypedValues() throws Exception {
        // 1. Get project environments (development, staging, production)
        MvcResult envResult = mockMvc.perform(get("/api/v1/projects/" + projectId + "/environments")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andReturn();

        String envJson = envResult.getResponse().getContentAsString();
        String devEnvId = extractFirstEnvIdByKey(envJson, "development");
        String stagingEnvId = extractFirstEnvIdByKey(envJson, "staging");

        // 2. Create logical flag
        MvcResult flagResult = mockMvc.perform(post("/api/v1/projects/" + projectId + "/flags")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\": \"env-flag\", \"name\": \"Env Flag\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String flagId = extractJsonField(flagResult.getResponse().getContentAsString(), "id");

        // 3. Verify auto-provisioned configs in development and staging
        mockMvc.perform(get("/api/v1/environments/" + devEnvId + "/flags")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.flagId=='" + flagId + "')].enabled").value(false))
                .andExpect(jsonPath("$[?(@.flagId=='" + flagId + "')].value").value(false));

        mockMvc.perform(get("/api/v1/environments/" + stagingEnvId + "/flags/" + flagId)
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled", is(false)))
                .andExpect(jsonPath("$.value", is(false)));

        // 4. Toggle Development to enabled=true
        mockMvc.perform(put("/api/v1/environments/" + devEnvId + "/flags/" + flagId + "/state")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled", is(true)));

        // 5. Verify Staging remains enabled=false (Environment isolation!)
        mockMvc.perform(get("/api/v1/environments/" + stagingEnvId + "/flags/" + flagId)
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled", is(false)));

        // 6. Update Value in Development
        mockMvc.perform(put("/api/v1/environments/" + devEnvId + "/flags/" + flagId + "/value")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value", is(true)));

        // 7. Attempt incompatible value update -> 400
        mockMvc.perform(put("/api/v1/environments/" + devEnvId + "/flags/" + flagId + "/value")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\": 12345}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("INVALID_ARGUMENT")));
    }

    @Test
    void crossTenantFlagAccess_returns403Forbidden() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/projects/" + projectId + "/flags")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\": \"private-feature\", \"name\": \"Private Feature\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String flagId = extractJsonField(createResult.getResponse().getContentAsString(), "id");

        // Attacker user
        UUID attackerId = UUID.randomUUID();
        User attacker = userRepository.save(new User(
                attackerId, "Attacker", "attacker." + attackerId + "@example.com", "hash", true, true
        ));
        String attackerToken = workspaceToken(attacker);

        // Attacker attempts to read flag -> 403 Forbidden
        mockMvc.perform(get("/api/v1/flags/" + flagId)
                        .header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));

        // Attacker attempts to delete flag -> 403 Forbidden
        mockMvc.perform(delete("/api/v1/flags/" + flagId)
                        .header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));
    }

    private static String extractJsonField(String json, String field) {
        Pattern pattern = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalArgumentException("Field '" + field + "' not found in JSON: " + json);
    }

    private static String extractFirstEnvIdByKey(String json, String key) {
        Pattern pattern = Pattern.compile("\\{\"id\":\"([^\"]+)\"[^}]*\"key\":\"" + key + "\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalArgumentException("Environment key '" + key + "' not found in JSON: " + json);
    }
}
