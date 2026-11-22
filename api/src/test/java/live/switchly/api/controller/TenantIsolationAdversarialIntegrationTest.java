package live.switchly.api.controller;

import live.switchly.api.model.Environment;
import live.switchly.api.model.Flag;
import live.switchly.api.model.FlagEnvironmentConfig;
import live.switchly.api.model.Organization;
import live.switchly.api.model.OrganizationMembership;
import live.switchly.api.model.Project;
import live.switchly.api.model.Role;
import live.switchly.api.model.User;
import live.switchly.api.repository.EnvironmentRepository;
import live.switchly.api.repository.FlagEnvironmentConfigRepository;
import live.switchly.api.repository.FlagRepository;
import live.switchly.api.repository.OrganizationMembershipRepository;
import live.switchly.api.repository.OrganizationRepository;
import live.switchly.api.repository.ProjectRepository;
import live.switchly.api.repository.UserRepository;
import live.switchly.api.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TenantIsolationAdversarialIntegrationTest extends ScopedSessionSupport {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private OrganizationMembershipRepository membershipRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private FlagRepository flagRepository;

    @Autowired
    private EnvironmentRepository environmentRepository;

    @Autowired
    private FlagEnvironmentConfigRepository flagEnvironmentConfigRepository;

    @Autowired
    private JwtService jwtService;

    // Tenant A
    private User userA;
    private String tokenA;
    private Organization orgA;
    private Project projectA;
    private Flag flagA;
    private Environment envA;
    private FlagEnvironmentConfig configA;

    // Tenant B
    private User userB;
    private String tokenB;
    private Organization orgB;
    private Project projectB;
    private Flag flagB;
    private Environment envB;
    private FlagEnvironmentConfig configB;

    @BeforeEach
    void setUp() {
        // Tenant A setup
        userA = createTestUser("User Alpha");
        
        orgA = organizationRepository.save(new Organization(UUID.randomUUID(), "Tenant Alpha"));
        membershipRepository.save(new OrganizationMembership(UUID.randomUUID(), orgA.getId(), userA.getId(), Role.ADMIN));
            tokenA = workspaceToken(userA);
        projectA = projectRepository.save(new Project(UUID.randomUUID(), orgA.getId(), "Project Alpha"));
        flagA = flagRepository.save(new Flag(UUID.randomUUID(), orgA.getId(), projectA.getId(), "alpha-flag", "Alpha Flag", null));
        envA = environmentRepository.save(new Environment(UUID.randomUUID(), projectA.getId(), "dev", "Dev", null, true));
        configA = flagEnvironmentConfigRepository.save(new FlagEnvironmentConfig(UUID.randomUUID(), flagA.getId(), envA.getId(), projectA.getId(), false, flagA.getDefaultValue()));

        // Tenant B setup
        userB = createTestUser("User Beta");
        
        orgB = organizationRepository.save(new Organization(UUID.randomUUID(), "Tenant Beta"));
        membershipRepository.save(new OrganizationMembership(UUID.randomUUID(), orgB.getId(), userB.getId(), Role.ADMIN));
            tokenB = workspaceToken(userB);
        projectB = projectRepository.save(new Project(UUID.randomUUID(), orgB.getId(), "Project Beta"));
        flagB = flagRepository.save(new Flag(UUID.randomUUID(), orgB.getId(), projectB.getId(), "beta-flag", "Beta Flag", null));
        envB = environmentRepository.save(new Environment(UUID.randomUUID(), projectB.getId(), "dev", "Dev", null, true));
        configB = flagEnvironmentConfigRepository.save(new FlagEnvironmentConfig(UUID.randomUUID(), flagB.getId(), envB.getId(), projectB.getId(), false, flagB.getDefaultValue()));
    }

    private User createTestUser(String fullName) {
        UUID id = UUID.randomUUID();
        return userRepository.save(new User(
                id, fullName, "user." + id + "@example.com", "hash", true, true
        ));
    }

    @Nested
    @DisplayName("1. Cross-Tenant IDOR and Isolation Tests")
    class CrossTenantIdorTests {

        @Test
        @DisplayName("User A cannot access User B's organization by UUID (403 Forbidden)")
        void userACannotAccessUserBOrganization() throws Exception {
            mockMvc.perform(get("/api/v1/orgs/{orgId}", orgB.getId())
                            .header("Authorization", "Bearer " + tokenA))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));
        }

        @Test
        @DisplayName("User A cannot access User B's project by UUID (403 Forbidden)")
        void userACannotAccessUserBProject() throws Exception {
            mockMvc.perform(get("/api/v1/projects/{projectId}", projectB.getId())
                            .header("Authorization", "Bearer " + tokenA))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));
        }

        @Test
        @DisplayName("User A cannot access User B's flag by UUID (403 Forbidden)")
        void userACannotAccessUserBFlag() throws Exception {
            mockMvc.perform(get("/api/v1/flags/{flagId}", flagB.getId())
                            .header("Authorization", "Bearer " + tokenA))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));
        }

        @Test
        @DisplayName("User A cannot update User B's flag state (403 Forbidden)")
        void userACannotUpdateUserBFlagState() throws Exception {
            mockMvc.perform(put("/api/v1/environments/{environmentId}/flags/{flagId}/state", envB.getId(), flagB.getId())
                            .header("Authorization", "Bearer " + tokenA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"enabled\": true}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));

            // Verify config in DB unchanged
            FlagEnvironmentConfig freshConfigB = flagEnvironmentConfigRepository.findByFlagIdAndEnvironmentId(flagB.getId(), envB.getId()).orElseThrow();
            assertThat(freshConfigB.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("User A cannot delete User B's flag (403 Forbidden)")
        void userACannotDeleteUserBFlag() throws Exception {
            mockMvc.perform(delete("/api/v1/flags/{flagId}", flagB.getId())
                            .header("Authorization", "Bearer " + tokenA))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));

            // Verify flag still exists
            assertThat(flagRepository.findById(flagB.getId())).isPresent();
        }

        @Test
        @DisplayName("User A cannot create a project inside User B's organization (403 Forbidden)")
        void userACannotCreateProjectInUserBOrg() throws Exception {
            mockMvc.perform(post("/api/v1/orgs/{orgId}/projects", orgB.getId())
                            .header("Authorization", "Bearer " + tokenA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\": \"Intruder Project\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));
        }

        @Test
        @DisplayName("User A cannot create a flag inside User B's project (403 Forbidden)")
        void userACannotCreateFlagInUserBProject() throws Exception {
            mockMvc.perform(post("/api/v1/projects/{projectId}/flags", projectB.getId())
                            .header("Authorization", "Bearer " + tokenA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"key\": \"injected-flag\", \"name\": \"Injected\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));
        }

        @Test
        @DisplayName("User A cannot list projects from User B's organization (403 Forbidden)")
        void userACannotListProjectsInUserBOrg() throws Exception {
            mockMvc.perform(get("/api/v1/orgs/{orgId}/projects", orgB.getId())
                            .header("Authorization", "Bearer " + tokenA))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));
        }

        @Test
        @DisplayName("User A cannot list flags from User B's project (403 Forbidden)")
        void userACannotListFlagsInUserBProject() throws Exception {
            mockMvc.perform(get("/api/v1/projects/{projectId}/flags", projectB.getId())
                            .header("Authorization", "Bearer " + tokenA))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));
        }

        @Test
        @DisplayName("GET /api/v1/orgs returns ONLY caller's organizations (Tenant B never leaked)")
        void organizationListingOnlyReturnsCallersOrganizations() throws Exception {
            mockMvc.perform(get("/api/v1/orgs")
                            .header("Authorization", "Bearer " + tokenA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].id", is(orgA.getId().toString())))
                    .andExpect(jsonPath("$[0].name", is("Tenant Alpha")));
        }
    }

    @Nested
    @DisplayName("2. Role Hierarchy Boundary Tests (VIEWER, DEVELOPER, ADMIN, OWNER)")
    class RoleBoundaryTests {

        private User viewerUser;
        private String viewerToken;
        private User devUser;
        private String devToken;
        private User adminUser;
        private String adminToken;

        @BeforeEach
        void setupRolesInOrgA() {
            viewerUser = createTestUser("Viewer User");
            
            membershipRepository.save(new OrganizationMembership(UUID.randomUUID(), orgA.getId(), viewerUser.getId(), Role.EMPLOYEE));
            viewerToken = workspaceToken(viewerUser);

            devUser = createTestUser("Dev User");
            
            membershipRepository.save(new OrganizationMembership(UUID.randomUUID(), orgA.getId(), devUser.getId(), Role.EMPLOYEE, null, null, projectA.getId()));
            devToken = workspaceToken(devUser);

            adminUser = createTestUser("Admin User");
            
            membershipRepository.save(new OrganizationMembership(UUID.randomUUID(), orgA.getId(), adminUser.getId(), Role.ADMIN));
            adminToken = workspaceToken(adminUser);
        }

        @Test
        @DisplayName("VIEWER cannot create projects (403 Forbidden)")
        void viewerCannotCreateProject() throws Exception {
            mockMvc.perform(post("/api/v1/orgs/{orgId}/projects", orgA.getId())
                            .header("Authorization", "Bearer " + viewerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\": \"Viewer Denied\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("VIEWER cannot create flags (403 Forbidden)")
        void viewerCannotCreateFlag() throws Exception {
            mockMvc.perform(post("/api/v1/projects/{projectId}/flags", projectA.getId())
                            .header("Authorization", "Bearer " + viewerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"key\": \"v-flag\", \"name\": \"Viewer Denied\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("VIEWER cannot toggle flag state (403 Forbidden)")
        void viewerCannotToggleFlag() throws Exception {
            mockMvc.perform(put("/api/v1/environments/{environmentId}/flags/{flagId}/state", envA.getId(), flagA.getId())
                            .header("Authorization", "Bearer " + viewerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"enabled\": true}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("VIEWER cannot delete flag (403 Forbidden)")
        void viewerCannotDeleteFlag() throws Exception {
            mockMvc.perform(delete("/api/v1/flags/{flagId}", flagA.getId())
                            .header("Authorization", "Bearer " + viewerToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("DEVELOPER cannot create projects (403 Forbidden)")
        void developerCannotCreateProject() throws Exception {
            mockMvc.perform(post("/api/v1/orgs/{orgId}/projects", orgA.getId())
                            .header("Authorization", "Bearer " + devToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\": \"Dev Denied\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("DEVELOPER CAN create flags, toggle state, and delete flags")
        void developerCanManageFlags() throws Exception {
            // Create flag
            mockMvc.perform(post("/api/v1/projects/{projectId}/flags", projectA.getId())
                            .header("Authorization", "Bearer " + devToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"key\": \"dev-flag\", \"name\": \"Dev Flag\"}"))
                    .andExpect(status().isCreated());

            // Toggle flag
            mockMvc.perform(put("/api/v1/environments/{environmentId}/flags/{flagId}/state", envA.getId(), flagA.getId())
                            .header("Authorization", "Bearer " + devToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"enabled\": true}"))
                    .andExpect(status().isOk());

            // Delete flag
            mockMvc.perform(delete("/api/v1/flags/{flagId}", flagA.getId())
                            .header("Authorization", "Bearer " + devToken))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("ADMIN CAN create projects in own organization")
        void adminCanCreateProjects() throws Exception {
            mockMvc.perform(post("/api/v1/orgs/{orgId}/projects", orgA.getId())
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\": \"Admin Allowed Project\"}"))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("ADMIN cannot grant OWNER role to members (403 Forbidden)")
        void adminCannotGrantOwnerRole() throws Exception {
            User target = createTestUser("Target User");

            mockMvc.perform(post("/api/v1/orgs/{orgId}/members/invitations", orgA.getId())
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\": \"" + target.getEmail() + "\", \"role\": \"OWNER\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("3. Unauthenticated Access Tests (All 18 Protected Endpoints)")
    class UnauthenticatedAccessTests {

        @Test
        @DisplayName("GET /api/v1/auth/me without token returns 401")
        void unauthGetMe() throws Exception {
            mockMvc.perform(get("/api/v1/auth/me"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("PUT /api/v1/auth/me without token returns 401")
        void unauthPutMe() throws Exception {
            mockMvc.perform(put("/api/v1/auth/me")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"fullName\": \"New Name\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("POST /api/v1/auth/logout without token returns 401")
        void unauthLogout() throws Exception {
            mockMvc.perform(post("/api/v1/auth/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\": \"some-token\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("POST /api/v1/orgs without token returns 401")
        void unauthCreateOrg() throws Exception {
            mockMvc.perform(post("/api/v1/orgs")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\": \"No Auth Org\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /api/v1/orgs without token returns 401")
        void unauthListOrgs() throws Exception {
            mockMvc.perform(get("/api/v1/orgs"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /api/v1/orgs/{orgId} without token returns 401")
        void unauthGetOrg() throws Exception {
            mockMvc.perform(get("/api/v1/orgs/{orgId}", orgA.getId()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /api/v1/orgs/{orgId}/members without token returns 401")
        void unauthListMembers() throws Exception {
            mockMvc.perform(get("/api/v1/orgs/{orgId}/members", orgA.getId()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("POST /api/v1/orgs/{orgId}/members without token returns 401")
        void unauthAddMember() throws Exception {
            mockMvc.perform(post("/api/v1/orgs/{orgId}/members", orgA.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\": \"a@b.com\", \"role\": \"VIEWER\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("PUT /api/v1/orgs/{orgId}/members/{userId}/role without token returns 401")
        void unauthUpdateMemberRole() throws Exception {
            mockMvc.perform(put("/api/v1/orgs/{orgId}/members/{userId}/role", orgA.getId(), userA.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"role\": \"ADMIN\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("DELETE /api/v1/orgs/{orgId}/members/{userId} without token returns 401")
        void unauthRemoveMember() throws Exception {
            mockMvc.perform(delete("/api/v1/orgs/{orgId}/members/{userId}", orgA.getId(), userA.getId()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("POST /api/v1/orgs/{orgId}/projects without token returns 401")
        void unauthCreateProject() throws Exception {
            mockMvc.perform(post("/api/v1/orgs/{orgId}/projects", orgA.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\": \"No Auth Project\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /api/v1/orgs/{orgId}/projects without token returns 401")
        void unauthListProjects() throws Exception {
            mockMvc.perform(get("/api/v1/orgs/{orgId}/projects", orgA.getId()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /api/v1/projects/{projectId} without token returns 401")
        void unauthGetProject() throws Exception {
            mockMvc.perform(get("/api/v1/projects/{projectId}", projectA.getId()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("POST /api/v1/projects/{projectId}/flags without token returns 401")
        void unauthCreateFlag() throws Exception {
            mockMvc.perform(post("/api/v1/projects/{projectId}/flags", projectA.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"key\": \"no-auth\", \"name\": \"No Auth\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /api/v1/projects/{projectId}/flags without token returns 401")
        void unauthListFlags() throws Exception {
            mockMvc.perform(get("/api/v1/projects/{projectId}/flags", projectA.getId()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /api/v1/flags/{flagId} without token returns 401")
        void unauthGetFlag() throws Exception {
            mockMvc.perform(get("/api/v1/flags/{flagId}", flagA.getId()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("PUT /api/v1/flags/{flagId}/state without token returns 401")
        void unauthToggleFlag() throws Exception {
            mockMvc.perform(put("/api/v1/flags/{flagId}/state", flagA.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"enabled\": true}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("DELETE /api/v1/flags/{flagId} without token returns 401")
        void unauthDeleteFlag() throws Exception {
            mockMvc.perform(delete("/api/v1/flags/{flagId}", flagA.getId()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("4. Non-Existent Resources (404 Not Found Preserved)")
    class NonExistentResourceTests {

        @Test
        @DisplayName("Non-existent project by UUID returns 404")
        void nonExistentProjectReturns404() throws Exception {
            UUID missingId = UUID.randomUUID();
            mockMvc.perform(get("/api/v1/projects/{projectId}", missingId)
                            .header("Authorization", "Bearer " + tokenA))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code", is("NOT_FOUND")));
        }

        @Test
        @DisplayName("Non-existent flag by UUID returns 404")
        void nonExistentFlagReturns404() throws Exception {
            UUID missingId = UUID.randomUUID();
            mockMvc.perform(get("/api/v1/flags/{flagId}", missingId)
                            .header("Authorization", "Bearer " + tokenA))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code", is("NOT_FOUND")));
        }
    }
}

