package live.switchly.api.repository;

import live.switchly.api.model.Organization;
import live.switchly.api.model.OrganizationMembership;
import live.switchly.api.model.RefreshToken;
import live.switchly.api.model.Role;
import live.switchly.api.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@ActiveProfiles("default")
class AuthPersistencePostgresTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private OrganizationMembershipRepository membershipRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    @DisplayName("User lifecycle: save, find by email case-insensitively, verify database timestamps")
    void userLifecycleAndTimestamps() {
        UUID userId = UUID.randomUUID();
        User user = new User(
                userId,
                "Alice Smith",
                "Alice@Example.Com",
                "$2a$12$eXampleHashedPasswordString",
                true,
                false
        );

        User saved = userRepository.save(user);

        assertThat(saved.getId()).isEqualTo(userId);
        assertThat(saved.getEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getFullName()).isEqualTo("Alice Smith");
        assertThat(saved.isEnabled()).isTrue();
        assertThat(saved.isEmailVerified()).isFalse();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        // Find by ID
        Optional<User> foundById = userRepository.findById(userId);
        assertThat(foundById).isPresent();
        assertThat(foundById.get().getFullName()).isEqualTo("Alice Smith");

        // Find by email case-insensitively
        Optional<User> foundByUpper = userRepository.findByEmail("ALICE@EXAMPLE.COM");
        assertThat(foundByUpper).isPresent();
        assertThat(foundByUpper.get().getId()).isEqualTo(userId);

        Optional<User> foundByLower = userRepository.findByEmail("alice@example.com");
        assertThat(foundByLower).isPresent();

        assertThat(userRepository.existsByEmail("ALICE@EXAMPLE.COM")).isTrue();
        assertThat(userRepository.existsByEmail("nonexistent@example.com")).isFalse();
    }

    @Test
    @DisplayName("User duplicate email rejection by database unique index")
    void duplicateEmail_throwsDataIntegrityViolationException() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        userRepository.save(new User(id1, "Bob", "bob@example.com", "hash1", true, false));

        User duplicateUser = new User(id2, "Bob Junior", "BOB@example.com", "hash2", true, false);
        assertThatThrownBy(() -> userRepository.save(duplicateUser))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("OrganizationMembership lifecycle: save, role count, find by org/user, last-owner query support")
    void membershipLifecycleAndRoleQueries() {
        // Create user and org
        UUID userId = UUID.randomUUID();
        User user = userRepository.save(new User(userId, "Carol Owner", "carol@example.com", "hash", true, true));

        UUID orgId = UUID.randomUUID();
        Organization org = organizationRepository.save(new Organization(orgId, "Acme Corp"));

        // Add Carol as OWNER
        UUID membershipId = UUID.randomUUID();
        OrganizationMembership membership = new OrganizationMembership(membershipId, org.getId(), user.getId(), Role.ADMIN);
        OrganizationMembership saved = membershipRepository.save(membership);

        assertThat(saved.getId()).isEqualTo(membershipId);
        assertThat(saved.getOrganizationId()).isEqualTo(org.getId());
        assertThat(saved.getUserId()).isEqualTo(user.getId());
        assertThat(saved.getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        // Query by Org ID
        List<OrganizationMembership> orgMembers = membershipRepository.findByOrganizationId(org.getId());
        assertThat(orgMembers).hasSize(1);
        assertThat(orgMembers.getFirst().getRole()).isEqualTo(Role.ADMIN);

        // Query by User ID
        List<OrganizationMembership> userMemberships = membershipRepository.findByUserId(user.getId());
        assertThat(userMemberships).hasSize(1);

        // Query by Org and User ID
        Optional<OrganizationMembership> found = membershipRepository.findByOrganizationIdAndUserId(org.getId(), user.getId());
        assertThat(found).isPresent();

        // Count OWNERs
        long ownerCount = membershipRepository.countByOrganizationIdAndRole(org.getId(), Role.ADMIN);
        assertThat(ownerCount).isEqualTo(1);

        long adminCount = membershipRepository.countByOrganizationIdAndRole(org.getId(), Role.EMPLOYEE);
        assertThat(adminCount).isEqualTo(0);

        // Update role
        OrganizationMembership updatedRole = new OrganizationMembership(membershipId, org.getId(), user.getId(), Role.EMPLOYEE);
        OrganizationMembership savedRole = membershipRepository.save(updatedRole);
        assertThat(savedRole.getRole()).isEqualTo(Role.EMPLOYEE);
        assertThat(membershipRepository.countByOrganizationIdAndRole(org.getId(), Role.EMPLOYEE)).isEqualTo(1);
        assertThat(membershipRepository.countByOrganizationIdAndRole(org.getId(), Role.ADMIN)).isEqualTo(0);

        // Duplicate membership in same org should fail database unique constraint
        OrganizationMembership duplicate = new OrganizationMembership(UUID.randomUUID(), org.getId(), user.getId(), Role.EMPLOYEE);
        assertThatThrownBy(() -> membershipRepository.save(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("RefreshToken lifecycle: save, find by hash, revoke, and delete by user ID")
    void refreshTokenLifecycle() {
        UUID userId = UUID.randomUUID();
        User user = userRepository.save(new User(userId, "Dave Dev", "dave@example.com", "hash", true, true));

        UUID tokenId = UUID.randomUUID();
        String tokenHash = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";
        Instant expiresAt = Instant.now().plus(7, ChronoUnit.DAYS);

        RefreshToken token = new RefreshToken(tokenId, user.getId(), tokenHash, expiresAt, false);
        RefreshToken saved = refreshTokenRepository.save(token);

        assertThat(saved.getId()).isEqualTo(tokenId);
        assertThat(saved.getUserId()).isEqualTo(user.getId());
        assertThat(saved.getTokenHash()).isEqualTo(tokenHash);
        assertThat(saved.isRevoked()).isFalse();
        assertThat(saved.getCreatedAt()).isNotNull();

        // Find by hash
        Optional<RefreshToken> foundByHash = refreshTokenRepository.findByTokenHash(tokenHash);
        assertThat(foundByHash).isPresent();
        assertThat(foundByHash.get().getId()).isEqualTo(tokenId);

        // Revoke token
        refreshTokenRepository.revoke(tokenId);
        Optional<RefreshToken> revokedToken = refreshTokenRepository.findByTokenHash(tokenHash);
        assertThat(revokedToken).isPresent();
        assertThat(revokedToken.get().isRevoked()).isTrue();

        // Delete by user ID
        refreshTokenRepository.deleteByUserId(user.getId());
        Optional<RefreshToken> deletedToken = refreshTokenRepository.findByTokenHash(tokenHash);
        assertThat(deletedToken).isEmpty();
    }
}

