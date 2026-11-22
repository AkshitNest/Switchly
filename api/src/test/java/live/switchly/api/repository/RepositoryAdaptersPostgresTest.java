package live.switchly.api.repository;

import tools.jackson.databind.node.BooleanNode;
import live.switchly.api.exception.ConflictException;
import live.switchly.api.model.Environment;
import live.switchly.api.model.Flag;
import live.switchly.api.model.FlagEnvironmentConfig;
import live.switchly.api.model.Organization;
import live.switchly.api.model.Project;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class RepositoryAdaptersPostgresTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private FlagRepository flagRepository;

    @Autowired
    private EnvironmentRepository environmentRepository;

    @Autowired
    private FlagEnvironmentConfigRepository flagEnvironmentConfigRepository;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("Verify default context selects Adapter beans and not InMemory repositories")
    void verifyDefaultBeanSelection() {
        assertThat(organizationRepository).isInstanceOf(OrganizationRepositoryAdapter.class);
        assertThat(projectRepository).isInstanceOf(ProjectRepositoryAdapter.class);
        assertThat(flagRepository).isInstanceOf(FlagRepositoryAdapter.class);
        assertThat(environmentRepository).isInstanceOf(EnvironmentRepositoryAdapter.class);
        assertThat(flagEnvironmentConfigRepository).isInstanceOf(FlagEnvironmentConfigRepositoryAdapter.class);

        // Ensure in-memory repositories are NOT registered in default profile
        assertThat(applicationContext.getBeansOfType(InMemoryOrganizationRepository.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(InMemoryProjectRepository.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(InMemoryFlagRepository.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(InMemoryEnvironmentRepository.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(InMemoryFlagEnvironmentConfigRepository.class)).isEmpty();
    }

    @Test
    @DisplayName("Full CRUD & relationship lifecycle through Repository Interfaces")
    void testFullCrudLifecycle() {
        // 1. Create Organization
        UUID orgId = UUID.randomUUID();
        Organization org = new Organization(orgId, "Acme Platform");
        Organization savedOrg = organizationRepository.save(org);

        assertThat(savedOrg.getId()).isEqualTo(orgId);
        assertThat(savedOrg.getName()).isEqualTo("Acme Platform");

        Optional<Organization> foundOrg = organizationRepository.findById(orgId);
        assertThat(foundOrg).isPresent();
        assertThat(foundOrg.get().getName()).isEqualTo("Acme Platform");

        // 2. Create Project under Organization
        UUID projectId = UUID.randomUUID();
        Project project = new Project(projectId, orgId, "Core Service");
        Project savedProject = projectRepository.save(project);

        assertThat(savedProject.getId()).isEqualTo(projectId);
        assertThat(savedProject.getOrganizationId()).isEqualTo(orgId);
        assertThat(savedProject.getName()).isEqualTo("Core Service");

        List<Project> orgProjects = projectRepository.findByOrganizationId(orgId);
        assertThat(orgProjects).extracting(Project::getName).contains("Core Service");

        // 3. Create Environment under Project
        UUID envId = UUID.randomUUID();
        Environment env = new Environment(envId, projectId, "qa-env", "QA Env", "QA description", false);
        Environment savedEnv = environmentRepository.save(env);
        assertThat(savedEnv.getId()).isEqualTo(envId);

        // 4. Create Flag under Project
        UUID flagId = UUID.randomUUID();
        Flag flag = new Flag(flagId, orgId, projectId, "beta-access", "Beta Access", "Early access flag");
        Flag savedFlag = flagRepository.save(flag);

        assertThat(savedFlag.getId()).isEqualTo(flagId);
        assertThat(savedFlag.getProjectId()).isEqualTo(projectId);
        assertThat(savedFlag.getOrganizationId()).isEqualTo(orgId);
        assertThat(savedFlag.getKey()).isEqualTo("beta-access");

        // 5. Create FlagEnvironmentConfig
        UUID configId = UUID.randomUUID();
        FlagEnvironmentConfig config = new FlagEnvironmentConfig(configId, flagId, envId, projectId, true, BooleanNode.TRUE);
        FlagEnvironmentConfig savedConfig = flagEnvironmentConfigRepository.save(config);

        assertThat(savedConfig.getId()).isEqualTo(configId);
        assertThat(savedConfig.isEnabled()).isTrue();
        assertThat(savedConfig.getValue()).isEqualTo(BooleanNode.TRUE);

        // Update FlagEnvironmentConfig
        savedConfig.setEnabled(false);
        FlagEnvironmentConfig updatedConfig = flagEnvironmentConfigRepository.save(savedConfig);
        assertThat(updatedConfig.isEnabled()).isFalse();

        // Query Configs
        List<FlagEnvironmentConfig> envConfigs = flagEnvironmentConfigRepository.findByEnvironmentId(envId);
        assertThat(envConfigs).hasSize(1);
        assertThat(envConfigs.get(0).getId()).isEqualTo(configId);

        // 6. Delete Flag cascades or deletes
        flagEnvironmentConfigRepository.deleteById(configId);
        assertThat(flagEnvironmentConfigRepository.findById(configId)).isEmpty();

        flagRepository.deleteById(flagId);
        assertThat(flagRepository.findById(flagId)).isEmpty();

        environmentRepository.deleteById(envId);
        assertThat(environmentRepository.findById(envId)).isEmpty();
    }

    @Test
    @DisplayName("Verify duplicate key constraint translation in FlagRepositoryAdapter")
    void testDuplicateKeyTranslation() {
        UUID orgId = UUID.randomUUID();
        organizationRepository.save(new Organization(orgId, "Dupe Test Org"));

        UUID projectId = UUID.randomUUID();
        projectRepository.save(new Project(projectId, orgId, "Dupe Test Project"));

        Flag flag1 = new Flag(UUID.randomUUID(), orgId, projectId, "unique-flag", "Flag 1");
        flagRepository.save(flag1);

        Flag flag2 = new Flag(UUID.randomUUID(), orgId, projectId, "unique-flag", "Flag 2 Duplicate");

        assertThatThrownBy(() -> flagRepository.save(flag2))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists in this project");

        Environment env1 = new Environment(UUID.randomUUID(), projectId, "unique-env", "Env 1", null, false);
        environmentRepository.save(env1);

        Environment env2 = new Environment(UUID.randomUUID(), projectId, "unique-env", "Env 2 Duplicate", null, false);
        assertThatThrownBy(() -> environmentRepository.save(env2))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists in this project");
    }

    @Test
    @DisplayName("Verify transaction rollback on multi-step failure")
    void testTransactionRollback() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        UUID orgId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        try {
            tx.execute(status -> {
                organizationRepository.save(new Organization(orgId, "Rollback Org"));
                projectRepository.save(new Project(projectId, orgId, "Rollback Project"));

                // Deliberately throw an exception to force rollback
                throw new RuntimeException("Simulated failure to verify rollback");
            });
        } catch (RuntimeException ignored) {
            // Expected
        }

        // Neither organization nor project should exist
        assertThat(organizationRepository.findById(orgId)).isEmpty();
        assertThat(projectRepository.findById(projectId)).isEmpty();
    }
}
