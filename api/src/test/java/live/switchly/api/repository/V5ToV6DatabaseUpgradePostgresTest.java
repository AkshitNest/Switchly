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
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class V5ToV6DatabaseUpgradePostgresTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Test
    @DisplayName("Verify upgrade from V5 to V6: preserves flags, backfills configs across environments, and enforces composite FKs")
    void upgradeFromV5ToV6_preservesData_backfillsConfigs_andEnforcesCrossProjectIntegrity() throws Exception {
        DataSource dataSource = createDataSource();

        // 1. Migrate only up to V5
        Flyway flywayV5 = Flyway.configure()
                .dataSource(dataSource)
                .target("5")
                .locations("classpath:db/migration")
                .load();

        flywayV5.migrate();

        // 2. Insert representative V5 data: 2 projects, each with environments and flags
        UUID orgId = UUID.randomUUID();
        UUID project1Id = UUID.randomUUID();
        UUID project2Id = UUID.randomUUID();

        UUID env1Dev = UUID.randomUUID();
        UUID env1Prod = UUID.randomUUID();
        UUID env2Dev = UUID.randomUUID();

        UUID flag1Id = UUID.randomUUID(); // enabled = true in V5
        UUID flag2Id = UUID.randomUUID(); // enabled = false in V5

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO organizations (id, name) VALUES (?, ?)")) {
                ps.setObject(1, orgId);
                ps.setString(2, "V5 Corp");
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO projects (id, organization_id, name) VALUES (?, ?, ?)")) {
                ps.setObject(1, project1Id);
                ps.setObject(2, orgId);
                ps.setString(3, "Project 1");
                ps.executeUpdate();

                ps.setObject(1, project2Id);
                ps.setObject(2, orgId);
                ps.setString(3, "Project 2");
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO environments (id, project_id, key, name, is_default) VALUES (?, ?, ?, ?, ?)")) {
                ps.setObject(1, env1Dev);
                ps.setObject(2, project1Id);
                ps.setString(3, "development");
                ps.setString(4, "Development");
                ps.setBoolean(5, true);
                ps.executeUpdate();

                ps.setObject(1, env1Prod);
                ps.setObject(2, project1Id);
                ps.setString(3, "production");
                ps.setString(4, "Production");
                ps.setBoolean(5, true);
                ps.executeUpdate();

                ps.setObject(1, env2Dev);
                ps.setObject(2, project2Id);
                ps.setString(3, "development");
                ps.setString(4, "Development");
                ps.setBoolean(5, true);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO flags (id, project_id, key, name, description, enabled) VALUES (?, ?, ?, ?, ?, ?)")) {
                ps.setObject(1, flag1Id);
                ps.setObject(2, project1Id);
                ps.setString(3, "beta-feature");
                ps.setString(4, "Beta Feature");
                ps.setString(5, "Flag with enabled=true");
                ps.setBoolean(6, true);
                ps.executeUpdate();

                ps.setObject(1, flag2Id);
                ps.setObject(2, project1Id);
                ps.setString(3, "dark-mode");
                ps.setString(4, "Dark Mode");
                ps.setString(5, "Flag with enabled=false");
                ps.setBoolean(6, false);
                ps.executeUpdate();
            }

            conn.commit();
        }

        // 3. Migrate from V5 to V6
        Flyway flywayV6 = Flyway.configure()
                .dataSource(dataSource)
                .target("6")
                .locations("classpath:db/migration")
                .load();

        flywayV6.migrate();

        // 4. Verify schema is at V6
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT version, description FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("version")).isEqualTo("6");
            assertThat(rs.getString("description")).isEqualTo("flag environment configs");
        }

        // 5. Verify backfill of flag_environment_configs
        try (Connection conn = dataSource.getConnection()) {
            // Flag 1 was enabled=true -> configs in dev and prod must both have enabled=true, value='true'
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT environment_id, enabled, value FROM flag_environment_configs WHERE flag_id = ? ORDER BY environment_id")) {
                ps.setObject(1, flag1Id);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getBoolean("enabled")).isTrue();
                    assertThat(rs.getString("value")).isEqualTo("true");

                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getBoolean("enabled")).isTrue();
                    assertThat(rs.getString("value")).isEqualTo("true");

                    assertThat(rs.next()).isFalse();
                }
            }

            // Flag 2 was enabled=false -> configs in dev and prod must have enabled=false, value='false'
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT environment_id, enabled, value FROM flag_environment_configs WHERE flag_id = ?")) {
                ps.setObject(1, flag2Id);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getBoolean("enabled")).isFalse();
                    assertThat(rs.getString("value")).isEqualTo("false");

                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getBoolean("enabled")).isFalse();
                    assertThat(rs.getString("value")).isEqualTo("false");

                    assertThat(rs.next()).isFalse();
                }
            }

            // 6. Verify cross-project integrity at DB level:
            // Attempting to link flag1 (Project 1) with env2Dev (Project 2) must be REJECTED by composite FK!
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO flag_environment_configs (flag_id, environment_id, project_id, enabled, value) VALUES (?, ?, ?, ?, ?::jsonb)")) {
                ps.setObject(1, flag1Id);
                ps.setObject(2, env2Dev);
                ps.setObject(3, project1Id); // mismatched project for env2Dev!
                ps.setBoolean(4, false);
                ps.setString(5, "false");

                assertThatThrownBy(ps::executeUpdate)
                        .isInstanceOf(SQLException.class);
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

