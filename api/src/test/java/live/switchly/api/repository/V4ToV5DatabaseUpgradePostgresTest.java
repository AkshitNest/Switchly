package live.switchly.api.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
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
class V4ToV5DatabaseUpgradePostgresTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Test
    @DisplayName("Verify upgrade from V4 to V5: preserves existing data and backfills default environments")
    void upgradeFromV4ToV5_preservesData_andBackfillsEnvironments() throws Exception {
        DataSource dataSource = createDataSource();

        // 1. Migrate only up to V4
        Flyway flywayV4 = Flyway.configure()
                .dataSource(dataSource)
                .target("4")
                .locations("classpath:db/migration")
                .load();

        flywayV4.migrate();

        // Verify schema is at V4
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("version")).isEqualTo("4");
        }

        // 2. Insert representative production data into V4
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID flagId = UUID.randomUUID();

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO organizations (id, name) VALUES (?, ?)")) {
                ps.setObject(1, orgId);
                ps.setString(2, "Beta Corp");
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO users (id, full_name, email, password_hash, enabled, email_verified) VALUES (?, ?, ?, ?, ?, ?)")) {
                ps.setObject(1, userId);
                ps.setString(2, "Bob Engineer");
                ps.setString(3, "bob@betacorp.com");
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
                ps.setString(3, "V4 Legacy Project");
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO flags (id, project_id, key, name, description, enabled) VALUES (?, ?, ?, ?, ?, ?)")) {
                ps.setObject(1, flagId);
                ps.setObject(2, projectId);
                ps.setString(3, "legacy-flag");
                ps.setString(4, "Legacy Flag");
                ps.setString(5, "Flag created under V4");
                ps.setBoolean(6, true);
                ps.executeUpdate();
            }

            conn.commit();
        }

        // 3. Perform V5 upgrade migration on the same database
        Flyway flywayV5 = Flyway.configure()
                .dataSource(dataSource)
                .target("5")
                .locations("classpath:db/migration")
                .load();

        flywayV5.migrate();

        // 4. Verify schema is now at V5
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT version, description FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("version")).isEqualTo("5");
            assertThat(rs.getString("description")).isEqualTo("add environments");
        }

        // 5. Verify all existing data is 100% intact
        try (Connection conn = dataSource.getConnection()) {
            // Organizations
            try (PreparedStatement ps = conn.prepareStatement("SELECT name FROM organizations WHERE id = ?")) {
                ps.setObject(1, orgId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("name")).isEqualTo("Beta Corp");
                }
            }

            // Projects
            try (PreparedStatement ps = conn.prepareStatement("SELECT name FROM projects WHERE id = ?")) {
                ps.setObject(1, projectId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("name")).isEqualTo("V4 Legacy Project");
                }
            }

            // Flags
            try (PreparedStatement ps = conn.prepareStatement("SELECT key, enabled FROM flags WHERE id = ?")) {
                ps.setObject(1, flagId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("key")).isEqualTo("legacy-flag");
                    assertThat(rs.getBoolean("enabled")).isTrue();
                }
            }

            // 6. Verify default environments were backfilled for pre-existing project
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT key, name, is_default FROM environments WHERE project_id = ? ORDER BY key")) {
                ps.setObject(1, projectId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("key")).isEqualTo("development");
                    assertThat(rs.getBoolean("is_default")).isTrue();

                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("key")).isEqualTo("production");
                    assertThat(rs.getBoolean("is_default")).isTrue();

                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("key")).isEqualTo("staging");
                    assertThat(rs.getBoolean("is_default")).isTrue();

                    assertThat(rs.next()).isFalse();
                }
            }

            // 7. Verify unique constraint uq_environments_project_key exists in pg_constraint
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT conname FROM pg_constraint WHERE conname = 'uq_environments_project_key'")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                }
            }
        }
    }

    private static DataSource createDataSource() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(postgres.getJdbcUrl());
        ds.setUser(postgres.getUsername());
        ds.setPassword(postgres.getPassword());
        return ds;
    }
}

