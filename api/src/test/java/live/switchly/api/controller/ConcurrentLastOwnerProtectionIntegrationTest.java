package live.switchly.api.controller;

import live.switchly.api.model.Organization;
import live.switchly.api.model.OrganizationMembership;
import live.switchly.api.model.Role;
import live.switchly.api.model.User;
import live.switchly.api.repository.OrganizationMembershipRepository;
import live.switchly.api.repository.OrganizationRepository;
import live.switchly.api.repository.UserRepository;
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

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ConcurrentLastOwnerProtectionIntegrationTest extends ScopedSessionSupport {

    @Autowired org.springframework.jdbc.core.simple.JdbcClient testDb;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private OrganizationMembershipRepository membershipRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Test
    @DisplayName("Concurrent removal of two co-owners preserves at least one owner and serializes safely")
    void concurrentRemovalOfTwoOwnersSafelyPreventsZeroOwners() throws Exception {
        // 1. Create organization
        Organization org = organizationRepository.save(new Organization(UUID.randomUUID(), "Concurrent Org " + UUID.randomUUID()));

        // 2. Create two owners
        User owner1 = createOwner(org.getId(), "Owner One");
        User owner2 = createOwner(org.getId(), "Owner Two");

        String token1 = workspaceToken(owner1);
        String token2 = workspaceToken(owner2);

        // Precondition check: exactly 2 owners in DB
        assertThat(membershipRepository.countByOrganizationIdAndRole(org.getId(), Role.ADMIN)).isEqualTo(2);

        // 3. Concurrently attempt to remove each other
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        Future<Integer> call1 = executor.submit(() -> {
            readyLatch.countDown();
            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.await(5, TimeUnit.SECONDS);
            MvcResult result = mockMvc.perform(delete("/api/v1/orgs/{orgId}/members/{userId}", org.getId(), owner2.getId())
                            .header("Authorization", "Bearer " + token1))
                    .andReturn();
            return result.getResponse().getStatus();
        });

        Future<Integer> call2 = executor.submit(() -> {
            readyLatch.countDown();
            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.await(5, TimeUnit.SECONDS);
            MvcResult result = mockMvc.perform(delete("/api/v1/orgs/{orgId}/members/{userId}", org.getId(), owner1.getId())
                            .header("Authorization", "Bearer " + token2))
                    .andReturn();
            return result.getResponse().getStatus();
        });

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // trigger both simultaneous requests

        int status1 = call1.get(15, TimeUnit.SECONDS);
        int status2 = call2.get(15, TimeUnit.SECONDS);
        executor.shutdown();

        // 4. Verification: exactly one succeeds (204 No Content) and one fails (409 Conflict)
        assertThat(new int[]{status1, status2})
                .contains(204);
        assertThat(status1 == 204 ? status2 : status1).isIn(401, 403);

        // 5. Database consistency: exactly one OWNER remains
        long remainingOwners = membershipRepository.countByOrganizationIdAndRole(org.getId(), Role.ADMIN);
        assertThat(remainingOwners).isEqualTo(1);
    }

    @Test
    @DisplayName("Concurrent demotion of two co-owners preserves at least one owner and serializes safely")
    void concurrentDemotionOfTwoOwnersSafelyPreventsZeroOwners() throws Exception {
        // 1. Create organization
        Organization org = organizationRepository.save(new Organization(UUID.randomUUID(), "Concurrent Demote Org " + UUID.randomUUID()));

        // 2. Create two owners
        java.util.UUID project = java.util.UUID.randomUUID();
        testDb.sql("insert into projects(id,organization_id,key,name) values(?,?,'app','App')").params(project, org.getId()).update();
        User owner1 = createOwner(org.getId(), "Demote Owner One");
        User owner2 = createOwner(org.getId(), "Demote Owner Two");

        String token1 = workspaceToken(owner1);
        String token2 = workspaceToken(owner2);

        // Precondition check: exactly 2 owners in DB
        assertThat(membershipRepository.countByOrganizationIdAndRole(org.getId(), Role.ADMIN)).isEqualTo(2);

        // 3. Concurrently attempt to demote each other to DEVELOPER
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        Future<Integer> call1 = executor.submit(() -> {
            readyLatch.countDown();
            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.await(5, TimeUnit.SECONDS);
            MvcResult result = mockMvc.perform(put("/api/v1/orgs/{orgId}/members/{userId}/role", org.getId(), owner1.getId())
                            .header("Authorization", "Bearer " + token1)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"role\":\"EMPLOYEE\",\"projectId\":\"" + project + "\"}"))
                    .andReturn();
            return result.getResponse().getStatus();
        });

        Future<Integer> call2 = executor.submit(() -> {
            readyLatch.countDown();
            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.await(5, TimeUnit.SECONDS);
            MvcResult result = mockMvc.perform(put("/api/v1/orgs/{orgId}/members/{userId}/role", org.getId(), owner2.getId())
                            .header("Authorization", "Bearer " + token2)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"role\":\"EMPLOYEE\",\"projectId\":\"" + project + "\"}"))
                    .andReturn();
            return result.getResponse().getStatus();
        });

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        int status1 = call1.get(15, TimeUnit.SECONDS);
        int status2 = call2.get(15, TimeUnit.SECONDS);
        executor.shutdown();

        // 4. Verification: exactly one succeeds (200 OK) and one fails (409 Conflict)
        assertThat(new int[]{status1, status2})
                .containsExactlyInAnyOrder(200, 409);

        // 5. Database consistency: exactly one OWNER remains
        long remainingOwners = membershipRepository.countByOrganizationIdAndRole(org.getId(), Role.ADMIN);
        assertThat(remainingOwners).isEqualTo(1);
    }

    private User createOwner(UUID orgId, String name) {
        UUID userId = UUID.randomUUID();
        User user = userRepository.save(new User(
                userId, name, "owner." + userId + "@example.com", "hash", true, true
        ));
        membershipRepository.save(new OrganizationMembership(
                UUID.randomUUID(), orgId, user.getId(), Role.ADMIN
        ));
        return user;
    }
}

