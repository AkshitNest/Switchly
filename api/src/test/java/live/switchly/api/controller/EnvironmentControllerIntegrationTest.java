package live.switchly.api.controller;

import live.switchly.api.model.OrganizationMembership;
import live.switchly.api.model.Role;
import live.switchly.api.model.User;
import live.switchly.api.repository.OrganizationMembershipRepository;
import live.switchly.api.repository.UserRepository;
import live.switchly.api.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class EnvironmentControllerIntegrationTest extends ScopedSessionSupport {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationMembershipRepository membershipRepository;

    @Autowired
    private JwtService jwtService;

    private User ownerUser;
    private String ownerToken;
    private String orgId;
    private String projectId;

    @BeforeEach
    void setUp() throws Exception {
        UUID userId = UUID.randomUUID();
        this.ownerUser = userRepository.save(new User(
                userId,
                "Env Owner",
                "envowner." + userId + "@example.com",
                "hash",
                true,
                true
        ));
        this.ownerToken = workspaceToken(ownerUser);

        // Create organization (caller becomes OWNER)
        MvcResult orgResult = mockMvc.perform(post("/api/v1/orgs")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Env Org\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        this.orgId = extractJsonField(orgResult.getResponse().getContentAsString(), "id");
        ownerToken = workspaceToken(ownerUser, orgId);

        // Create project
        MvcResult projectResult = mockMvc.perform(post("/api/v1/orgs/" + orgId + "/projects")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Core Service\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        this.projectId = extractJsonField(projectResult.getResponse().getContentAsString(), "id");
    }

    @Test
    @DisplayName("Project creation automatically creates development, staging, production default environments")
    void defaultEnvironmentsCreatedWithProject() throws Exception {
        mockMvc.perform(get("/api/v1/projects/" + projectId + "/environments")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].key", containsInAnyOrder("development", "staging", "production")))
                .andExpect(jsonPath("$[*].isDefault", containsInAnyOrder(true, true, true)));
    }

    @Test
    @DisplayName("Unauthenticated requests return 401")
    void unauthenticatedAccess_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/projects/" + projectId + "/environments"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/projects/" + projectId + "/environments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\": \"qa\", \"name\": \"QA\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("OWNER can create custom environment and read by ID")
    void createAndGetCustomEnvironment() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/projects/" + projectId + "/environments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\": \"qa-test\", \"name\": \"QA Testing\", \"description\": \"QA testbed\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key", is("qa-test")))
                .andExpect(jsonPath("$.name", is("QA Testing")))
                .andExpect(jsonPath("$.description", is("QA testbed")))
                .andExpect(jsonPath("$.isDefault", is(false)))
                .andExpect(jsonPath("$.createdAt", notNullValue()))
                .andReturn();

        String envId = extractJsonField(createResult.getResponse().getContentAsString(), "id");

        mockMvc.perform(get("/api/v1/environments/" + envId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(envId)))
                .andExpect(jsonPath("$.key", is("qa-test")))
                .andExpect(jsonPath("$.name", is("QA Testing")));
    }

    @Test
    @DisplayName("Key format validation rejects uppercase and invalid characters")
    void keyValidation() throws Exception {
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/environments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\": \"INVALID_KEY\", \"name\": \"Invalid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_FAILED")));

        mockMvc.perform(post("/api/v1/projects/" + projectId + "/environments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\": \"space key\", \"name\": \"Invalid Space\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_FAILED")));
    }

    @Test
    @DisplayName("Duplicate environment key in same project returns 409 Conflict")
    void duplicateKeyInSameProject_returns409() throws Exception {
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/environments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\": \"development\", \"name\": \"Another Dev\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("CONFLICT")));
    }

    @Test
    @DisplayName("Different projects can share the same environment key")
    void differentProjectsShareEnvironmentKey() throws Exception {
        // Create second project in same org
        MvcResult p2Result = mockMvc.perform(post("/api/v1/orgs/" + orgId + "/projects")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Secondary Service\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String project2Id = extractJsonField(p2Result.getResponse().getContentAsString(), "id");

        // Both projects already have 'development'
        mockMvc.perform(get("/api/v1/projects/" + project2Id + "/environments")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].key", containsInAnyOrder("development", "staging", "production")));

        // Create custom 'qa' in project 1
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/environments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\": \"qa\", \"name\": \"QA 1\"}"))
                .andExpect(status().isCreated());

        // Create custom 'qa' in project 2 (should succeed)
        mockMvc.perform(post("/api/v1/projects/" + project2Id + "/environments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\": \"qa\", \"name\": \"QA 2\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key", is("qa")))
                .andExpect(jsonPath("$.name", is("QA 2")));
    }

    @Test
    @DisplayName("OWNER can update custom environment name and description")
    void updateEnvironment() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/projects/" + projectId + "/environments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\": \"demo\", \"name\": \"Demo Old\", \"description\": \"Old desc\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String envId = extractJsonField(createResult.getResponse().getContentAsString(), "id");

        mockMvc.perform(put("/api/v1/environments/" + envId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Demo New\", \"description\": \"Updated description\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Demo New")))
                .andExpect(jsonPath("$.description", is("Updated description")));
    }

    @Test
    @DisplayName("OWNER can delete custom environment")
    void deleteCustomEnvironment() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/projects/" + projectId + "/environments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\": \"temp\", \"name\": \"Temp Env\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String envId = extractJsonField(createResult.getResponse().getContentAsString(), "id");

        mockMvc.perform(delete("/api/v1/environments/" + envId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/environments/" + envId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Default environments cannot be deleted and return 409 Conflict")
    void deleteDefaultEnvironment_returns409() throws Exception {
        MvcResult listResult = mockMvc.perform(get("/api/v1/projects/" + projectId + "/environments")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();

        String body = listResult.getResponse().getContentAsString();
        // find id of the development environment
        Pattern p = Pattern.compile("\\{\"id\":\"([^\"]+)\"[^}]*\"key\":\"development\"");
        Matcher m = p.matcher(body);
        assertThat(m.find()).isTrue();
        String devEnvId = m.group(1);

        mockMvc.perform(delete("/api/v1/environments/" + devEnvId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("CONFLICT")))
                .andExpect(jsonPath("$.error.message", is("Default environments cannot be deleted")));
    }

    @Test
    @DisplayName("RBAC matrix: DEVELOPER cannot create, update, or delete environments, but can read")
    void developerRbacPermissions() throws Exception {
        // Create user with DEVELOPER role
        UUID devUserId = UUID.randomUUID();
        User devUser = userRepository.save(new User(
                devUserId,
                "Dev User",
                "dev." + devUserId + "@example.com",
                "hash",
                true,
                true
        ));
        membershipRepository.save(new OrganizationMembership(UUID.randomUUID(), UUID.fromString(orgId), devUserId, Role.EMPLOYEE, null, null, UUID.fromString(projectId)));
        String devToken = workspaceToken(devUser);

        // Read project environments -> OK
        mockMvc.perform(get("/api/v1/projects/" + projectId + "/environments")
                        .header("Authorization", "Bearer " + devToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));

        // Create environment -> 403 Forbidden
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/environments")
                        .header("Authorization", "Bearer " + devToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\": \"dev-custom\", \"name\": \"Dev Custom\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));

        // Create custom env as OWNER first
        MvcResult createResult = mockMvc.perform(post("/api/v1/projects/" + projectId + "/environments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\": \"test-rbac\", \"name\": \"Test RBAC\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String envId = extractJsonField(createResult.getResponse().getContentAsString(), "id");

        // DEVELOPER can read the environment by ID
        mockMvc.perform(get("/api/v1/environments/" + envId)
                        .header("Authorization", "Bearer " + devToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key", is("test-rbac")));

        // DEVELOPER cannot update environment -> 403
        mockMvc.perform(put("/api/v1/environments/" + envId)
                        .header("Authorization", "Bearer " + devToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Dev Mutated\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));

        // DEVELOPER cannot delete environment -> 403
        mockMvc.perform(delete("/api/v1/environments/" + envId)
                        .header("Authorization", "Bearer " + devToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));
    }

    @Test
    @DisplayName("Cross-tenant isolation: User in another org cannot access or mutate environments")
    void crossTenantIsolation() throws Exception {
        // Create Org B with different user
        UUID attackerId = UUID.randomUUID();
        User attacker = userRepository.save(new User(
                attackerId,
                "Attacker",
                "attacker." + attackerId + "@example.com",
                "hash",
                true,
                true
        ));
        String attackerToken = workspaceToken(attacker);

        MvcResult orgBResult = mockMvc.perform(post("/api/v1/orgs")
                        .header("Authorization", "Bearer " + attackerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Attacker Org\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        // Attacker attempts to list environments of Org A's project -> 403
        mockMvc.perform(get("/api/v1/projects/" + projectId + "/environments")
                        .header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));

        // Attacker attempts to create environment in Org A's project -> 403
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/environments")
                        .header("Authorization", "Bearer " + attackerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\": \"exploit\", \"name\": \"Exploit\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));

        // Create environment in Org A
        MvcResult customEnvResult = mockMvc.perform(post("/api/v1/projects/" + projectId + "/environments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\": \"secret-env\", \"name\": \"Secret Env\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String secretEnvId = extractJsonField(customEnvResult.getResponse().getContentAsString(), "id");

        // Attacker attempts to read environment by ID -> 403
        mockMvc.perform(get("/api/v1/environments/" + secretEnvId)
                        .header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));

        // Attacker attempts to update environment -> 403
        mockMvc.perform(put("/api/v1/environments/" + secretEnvId)
                        .header("Authorization", "Bearer " + attackerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Hacked\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));

        // Attacker attempts to delete environment -> 403
        mockMvc.perform(delete("/api/v1/environments/" + secretEnvId)
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
}

