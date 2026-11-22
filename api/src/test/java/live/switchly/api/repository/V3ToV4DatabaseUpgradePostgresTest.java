package live.switchly.api.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class V3ToV4DatabaseUpgradePostgresTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Test
    @DisplayName("Verify database migration upgrade from V3 to V4 preserves all data and creates new indexes")
    void upgradeFromV3ToV4_preservesData_andCreatesIndexes() throws Exception {
        DataSource dataSource = DataSourceBuilder.create()
                .url(postgres.getJdbcUrl())
                .username(postgres.getUsername())
                .password(postgres.getPassword())
                .driverClassName(postgres.getDriverClassName())
                .build();

        // 1. Migrate only up to V3
        Flyway flywayV3 = Flyway.configure()
                .dataSource(dataSource)
                .target("3")
                .locations("classpath:db/migration")
                .load();

        flywayV3.migrate();

        // Verify schema is at V3
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("version")).isEqualTo("3");
        }

        // 2. Insert representative production data into all 6 tables under V3
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID flagId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        String tokenHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO organizations (id, name) VALUES (?, ?)")) {
                ps.setObject(1, orgId);
                ps.setString(2, "Acme Corporation");
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO users (id, full_name, email, password_hash, enabled, email_verified) VALUES (?, ?, ?, ?, ?, ?)")) {
                ps.setObject(1, userId);
                ps.setString(2, "Alice Engineer");
                ps.setString(3, "alice@acme.com");
                ps.setString(4, "$2a$12$eACCf9xG56p...hash");
                ps.setBoolean(5, true);
                ps.setBoolean(6, true);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO organization_memberships (id, organization_id, user_id, role) VALUES (?, ?, ?, ?)")) {
                ps.setObject(1, membershipId);
                ps.setObject(2, orgId);
                ps.setObject(3, userId);
                ps.setString(4, "OWNER");
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO projects (id, organization_id, name) VALUES (?, ?, ?)")) {
                ps.setObject(1, projectId);
                ps.setObject(2, orgId);
                ps.setString(3, "Core Backend");
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO flags (id, project_id, key, name, description, enabled) VALUES (?, ?, ?, ?, ?, ?)")) {
                ps.setObject(1, flagId);
                ps.setObject(2, projectId);
                ps.setString(3, "checkout-v2");
                ps.setString(4, "New Checkout Flow");
                ps.setString(5, "Toggles checkout v2 experience");
                ps.setBoolean(6, true);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO refresh_tokens (id, user_id, family_id, token_hash, expires_at, revoked) VALUES (?, ?, ?, ?, now() + interval '7 days', ?)")) {
                ps.setObject(1, tokenId);
                ps.setObject(2, userId);
                ps.setObject(3, familyId);
                ps.setString(4, tokenHash);
                ps.setBoolean(5, false);
                ps.executeUpdate();
            }

            conn.commit();
        }

        // 3. Perform V4 upgrade migration on the same database
        Flyway flywayV4 = Flyway.configure()
                .dataSource(dataSource)
                .target("4")
                .locations("classpath:db/migration")
                .load();

        flywayV4.migrate();

        // 4. Verify schema is now at V4
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT version, description FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("version")).isEqualTo("4");
            assertThat(rs.getString("description")).isEqualTo("add missing indexes");
        }

        // 5. Verify all existing data is 100% intact
        try (Connection conn = dataSource.getConnection()) {
            // Organizations
            try (PreparedStatement ps = conn.prepareStatement("SELECT name FROM organizations WHERE id = ?")) {
                ps.setObject(1, orgId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("name")).isEqualTo("Acme Corporation");
                }
            }

            // Users
            try (PreparedStatement ps = conn.prepareStatement("SELECT full_name, email, enabled FROM users WHERE id = ?")) {
                ps.setObject(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("full_name")).isEqualTo("Alice Engineer");
                    assertThat(rs.getString("email")).isEqualTo("alice@acme.com");
                    assertThat(rs.getBoolean("enabled")).isTrue();
                }
            }

            // Memberships
            try (PreparedStatement ps = conn.prepareStatement("SELECT role FROM organization_memberships WHERE id = ?")) {
                ps.setObject(1, membershipId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("role")).isEqualTo("OWNER");
                }
            }

            // Projects
            try (PreparedStatement ps = conn.prepareStatement("SELECT name FROM projects WHERE id = ?")) {
                ps.setObject(1, projectId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("name")).isEqualTo("Core Backend");
                }
            }

            // Flags
            try (PreparedStatement ps = conn.prepareStatement("SELECT key, name, enabled FROM flags WHERE id = ?")) {
                ps.setObject(1, flagId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("key")).isEqualTo("checkout-v2");
                    assertThat(rs.getString("name")).isEqualTo("New Checkout Flow");
                    assertThat(rs.getBoolean("enabled")).isTrue();
                }
            }

            // Refresh tokens
            try (PreparedStatement ps = conn.prepareStatement("SELECT token_hash, family_id, revoked FROM refresh_tokens WHERE id = ?")) {
                ps.setObject(1, tokenId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("token_hash")).isEqualTo(tokenHash);
                    assertThat(rs.getObject("family_id", UUID.class)).isEqualTo(familyId);
                    assertThat(rs.getBoolean("revoked")).isFalse();
                }
            }
        }

        // 6. Verify the V4 indexes exist
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT indexname FROM pg_indexes WHERE schemaname = 'public' AND indexname IN ('idx_org_memberships_org_role', 'idx_refresh_tokens_family_active')")) {
            int count = 0;
            while (rs.next()) {
                count++;
            }
            assertThat(count).isEqualTo(2);
        }
    }
}

