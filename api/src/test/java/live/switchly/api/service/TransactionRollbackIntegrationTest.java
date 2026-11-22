package live.switchly.api.service;

import tools.jackson.databind.node.BooleanNode;
import live.switchly.api.model.Environment;
import live.switchly.api.model.Flag;
import live.switchly.api.model.FlagEnvironmentConfig;
import live.switchly.api.model.FlagValueType;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class TransactionRollbackIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private ProjectService projectService;

    @Autowired
    private EnvironmentService environmentService;

    @Autowired
    private FlagService flagService;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private OrganizationMembershipRepository membershipRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private EnvironmentRepository environmentRepository;

    @Autowired
    private FlagRepository flagRepository;

    @Autowired
    private FlagEnvironmentConfigRepository flagEnvironmentConfigRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User adminUser;
    private Organization testOrg;

    @BeforeEach
    void setUp() {
        UUID userId = UUID.randomUUID();
        this.adminUser = userRepository.save(new User(userId, "Admin Tester", "adm." + userId + "@example.com", "hash", true, true));
        this.testOrg = organizationRepository.save(new Organization(UUID.randomUUID(), "Rollback Test Org"));
        membershipRepository.save(new OrganizationMembership(UUID.randomUUID(), testOrg.getId(), adminUser.getId(), Role.ADMIN));
    }

    @Test
    @DisplayName("Failed default environment creation rolls back project creation (zero rows remain)")
    void failedDefaultEnvironmentCreation_rollsBackProject() {
        // Install PostgreSQL trigger that induces failure when project name starts with 'TriggerFailProject'
        jdbcTemplate.execute("""
            CREATE OR REPLACE FUNCTION fail_default_env() RETURNS TRIGGER AS $$
            BEGIN
                IF EXISTS (SELECT 1 FROM projects p WHERE p.id = NEW.project_id AND p.name LIKE 'TriggerFailProject%') THEN
                    RAISE EXCEPTION 'Induced PostgreSQL failure on default environment creation';
                END IF;
                RETURN NEW;
            END;
            $$ LANGUAGE plpgsql;

            DROP TRIGGER IF EXISTS trg_fail_default_env ON environments;
            CREATE TRIGGER trg_fail_default_env
            BEFORE INSERT ON environments
            FOR EACH ROW EXECUTE FUNCTION fail_default_env();
        """);

        try {
            assertThatThrownBy(() -> projectService.create(testOrg.getId(), "TriggerFailProject-1", adminUser.getId()))
                    .getRootCause()
                    .hasMessageContaining("Induced PostgreSQL failure on default environment creation");

            // Verify project was rolled back completely
            List<Project> projects = projectRepository.findByOrganizationId(testOrg.getId());
            assertThat(projects).noneMatch(p -> p.getName().startsWith("TriggerFailProject"));

            // Verify no orphan environments exist
            List<UUID> envIds = jdbcTemplate.query(
                    "SELECT id FROM environments WHERE name = 'Development'",
                    (rs, rowNum) -> (UUID) rs.getObject("id")
            );
            for (UUID envId : envIds) {
                Environment env = environmentRepository.findById(envId).orElseThrow();
                assertThat(projectRepository.findById(env.getProjectId())).isPresent();
            }
        } finally {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS trg_fail_default_env ON environments;");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS fail_default_env();");
        }
    }

    @Test
    @DisplayName("Failed environment configuration provisioning rolls back logical flag creation (zero rows remain)")
    void failedConfigProvisioning_rollsBackFlagCreation() {
        // Create normal project first
        Project project = projectService.create(testOrg.getId(), "Flag Rollback Proj", adminUser.getId());

        // Install PostgreSQL trigger that fails on flag config insertion for key starting with 'fail-flag'
        jdbcTemplate.execute("""
            CREATE OR REPLACE FUNCTION fail_flag_config() RETURNS TRIGGER AS $$
            BEGIN
                IF EXISTS (SELECT 1 FROM flags f WHERE f.id = NEW.flag_id AND f.key LIKE 'fail-flag%') THEN
                    RAISE EXCEPTION 'Induced PostgreSQL failure on flag environment config creation';
                END IF;
                RETURN NEW;
            END;
            $$ LANGUAGE plpgsql;

            DROP TRIGGER IF EXISTS trg_fail_flag_config ON flag_environment_configs;
            CREATE TRIGGER trg_fail_flag_config
            BEFORE INSERT ON flag_environment_configs
            FOR EACH ROW EXECUTE FUNCTION fail_flag_config();
        """);

        try {
            assertThatThrownBy(() -> flagService.create(project.getId(), "fail-flag-1", "Fail Flag", "Desc",
                    FlagValueType.BOOLEAN, BooleanNode.FALSE, adminUser.getId()))
                    .getRootCause()
                    .hasMessageContaining("Induced PostgreSQL failure on flag environment config creation");

            // Verify flag was rolled back completely
            assertThat(flagRepository.existsByProjectIdAndKey(project.getId(), "fail-flag-1")).isFalse();

            // Verify no orphan configs exist
            List<FlagEnvironmentConfig> configs = flagEnvironmentConfigRepository.findByProjectId(project.getId());
            assertThat(configs).isEmpty();
        } finally {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS trg_fail_flag_config ON flag_environment_configs;");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS fail_flag_config();");
        }
    }

    @Test
    @DisplayName("Failed flag configuration provisioning rolls back custom environment creation (zero rows remain)")
    void failedConfigProvisioning_rollsBackEnvironmentCreation() {
        // Create project and flag
        Project project = projectService.create(testOrg.getId(), "Env Rollback Proj", adminUser.getId());
        Flag flag = flagService.create(project.getId(), "stable-flag", "Stable", null, FlagValueType.BOOLEAN, BooleanNode.FALSE, adminUser.getId());

        // Install PostgreSQL trigger that fails when creating config for environment key 'fail-env'
        jdbcTemplate.execute("""
            CREATE OR REPLACE FUNCTION fail_env_config() RETURNS TRIGGER AS $$
            BEGIN
                IF EXISTS (SELECT 1 FROM environments e WHERE e.id = NEW.environment_id AND e.key LIKE 'fail-env%') THEN
                    RAISE EXCEPTION 'Induced PostgreSQL failure on environment flag config creation';
                END IF;
                RETURN NEW;
            END;
            $$ LANGUAGE plpgsql;

            DROP TRIGGER IF EXISTS trg_fail_env_config ON flag_environment_configs;
            CREATE TRIGGER trg_fail_env_config
            BEFORE INSERT ON flag_environment_configs
            FOR EACH ROW EXECUTE FUNCTION fail_env_config();
        """);

        try {
            assertThatThrownBy(() -> environmentService.create(project.getId(), "fail-env", "Fail Env", "Desc", adminUser.getId()))
                    .getRootCause()
                    .hasMessageContaining("Induced PostgreSQL failure on environment flag config creation");

            // Verify environment was rolled back completely
            assertThat(environmentRepository.existsByProjectIdAndKey(project.getId(), "fail-env")).isFalse();
        } finally {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS trg_fail_env_config ON flag_environment_configs;");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS fail_env_config();");
        }
    }
}
