package live.switchly.api.repository;

import tools.jackson.databind.json.JsonMapper;
import live.switchly.api.entity.EnvironmentEntity;
import live.switchly.api.entity.FlagEntity;
import live.switchly.api.entity.FlagEnvironmentConfigEntity;
import live.switchly.api.entity.OrganizationEntity;
import live.switchly.api.entity.ProjectEntity;
import live.switchly.api.model.FlagValueType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
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
class JpaRepositoryPostgresTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private JpaOrganizationRepository organizationRepository;

    @Autowired
    private JpaProjectRepository projectRepository;

    @Autowired
    private JpaFlagRepository flagRepository;

    @Autowired
    private JpaEnvironmentRepository environmentRepository;

    @Autowired
    private JpaFlagEnvironmentConfigRepository flagEnvironmentConfigRepository;

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    @DisplayName("Verify OrganizationEntity persistence and DB-managed timestamps")
    void testOrganizationPersistence() {
        UUID orgId = UUID.randomUUID();
        OrganizationEntity org = new OrganizationEntity(orgId, "Acme Corp");

        OrganizationEntity saved = organizationRepository.saveAndFlush(org);

        assertThat(saved.getId()).isEqualTo(orgId);
        assertThat(saved.getName()).isEqualTo("Acme Corp");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        Optional<OrganizationEntity> found = organizationRepository.findById(orgId);
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Acme Corp");
    }

    @Test
    @DisplayName("Verify ProjectEntity persistence with ManyToOne Organization relationship")
    void testProjectPersistence() {
        OrganizationEntity org = organizationRepository.saveAndFlush(new OrganizationEntity(UUID.randomUUID(), "Stark Industries"));

        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity(projectId, org, "Web App");
        ProjectEntity savedProject = projectRepository.saveAndFlush(project);

        assertThat(savedProject.getId()).isEqualTo(projectId);
        assertThat(savedProject.getOrganization().getId()).isEqualTo(org.getId());
        assertThat(savedProject.getCreatedAt()).isNotNull();
        assertThat(savedProject.getUpdatedAt()).isNotNull();

        List<ProjectEntity> projects = projectRepository.findByOrganizationId(org.getId());
        assertThat(projects).hasSize(1);
        assertThat(projects.get(0).getName()).isEqualTo("Web App");
    }

    @Test
    @DisplayName("Verify FlagEntity persistence, relationships, typed values, and duplicate key constraint")
    void testFlagPersistenceAndDuplicateKeyConstraint() {
        OrganizationEntity org = organizationRepository.saveAndFlush(new OrganizationEntity(UUID.randomUUID(), "Wayne Enterprises"));
        ProjectEntity project = projectRepository.saveAndFlush(new ProjectEntity(UUID.randomUUID(), org, "Mobile App"));

        UUID flagId = UUID.randomUUID();
        FlagEntity flag = new FlagEntity(flagId, project, "dark-mode", "Dark Mode", "Enables dark theme",
                FlagValueType.BOOLEAN, "true");
        FlagEntity savedFlag = flagRepository.saveAndFlush(flag);

        assertThat(savedFlag.getId()).isEqualTo(flagId);
        assertThat(savedFlag.getKey()).isEqualTo("dark-mode");
        assertThat(savedFlag.getValueType()).isEqualTo(FlagValueType.BOOLEAN);
        assertThat(savedFlag.getDefaultValue()).isEqualTo("true");
        assertThat(savedFlag.getCreatedAt()).isNotNull();
        assertThat(savedFlag.getUpdatedAt()).isNotNull();

        // Verify existsByProjectIdAndKey
        assertThat(flagRepository.existsByProjectIdAndKey(project.getId(), "dark-mode")).isTrue();
        assertThat(flagRepository.existsByProjectIdAndKey(project.getId(), "non-existent")).isFalse();

        // Verify findByProjectId
        List<FlagEntity> flags = flagRepository.findByProjectId(project.getId());
        assertThat(flags).hasSize(1);
        assertThat(flags.get(0).getKey()).isEqualTo("dark-mode");

        // Verify unique constraint: duplicate key in same project must throw DataIntegrityViolationException
        FlagEntity duplicate = new FlagEntity(UUID.randomUUID(), project, "dark-mode", "Duplicate Dark Mode", null,
                FlagValueType.BOOLEAN, "false");
        assertThatThrownBy(() -> flagRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Verify EnvironmentEntity persistence, relationships, and duplicate key constraint")
    void testEnvironmentPersistenceAndDuplicateKeyConstraint() {
        OrganizationEntity org = organizationRepository.saveAndFlush(new OrganizationEntity(UUID.randomUUID(), "Oscorp"));
        ProjectEntity project = projectRepository.saveAndFlush(new ProjectEntity(UUID.randomUUID(), org, "Cloud Infra"));

        UUID envId = UUID.randomUUID();
        EnvironmentEntity env = new EnvironmentEntity(envId, project, "staging-custom", "Staging Custom", "Custom staging env", false);
        EnvironmentEntity savedEnv = environmentRepository.saveAndFlush(env);

        assertThat(savedEnv.getId()).isEqualTo(envId);
        assertThat(savedEnv.getKey()).isEqualTo("staging-custom");
        assertThat(savedEnv.getName()).isEqualTo("Staging Custom");
        assertThat(savedEnv.getDescription()).isEqualTo("Custom staging env");
        assertThat(savedEnv.isDefault()).isFalse();
        assertThat(savedEnv.getCreatedAt()).isNotNull();
        assertThat(savedEnv.getUpdatedAt()).isNotNull();

        // Verify existsByProjectIdAndKey
        assertThat(environmentRepository.existsByProjectIdAndKey(project.getId(), "staging-custom")).isTrue();
        assertThat(environmentRepository.existsByProjectIdAndKey(project.getId(), "non-existent")).isFalse();

        // Verify findByProjectIdAndKey
        Optional<EnvironmentEntity> found = environmentRepository.findByProjectIdAndKey(project.getId(), "staging-custom");
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Staging Custom");

        // Verify findByProjectId
        List<EnvironmentEntity> envs = environmentRepository.findByProjectId(project.getId());
        assertThat(envs).extracting(EnvironmentEntity::getKey).contains("staging-custom");

        // Verify unique constraint: duplicate key in same project must throw DataIntegrityViolationException
        EnvironmentEntity duplicate = new EnvironmentEntity(UUID.randomUUID(), project, "staging-custom", "Duplicate Key", null, false);
        assertThatThrownBy(() -> environmentRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Verify FlagEnvironmentConfigEntity JSONB mapping across BOOLEAN, STRING, NUMBER and JSON types")
    void testFlagEnvironmentConfigJsonbMappingAcrossTypes() throws Exception {
        OrganizationEntity org = organizationRepository.saveAndFlush(new OrganizationEntity(UUID.randomUUID(), "LexCorp"));
        ProjectEntity project = projectRepository.saveAndFlush(new ProjectEntity(UUID.randomUUID(), org, "Portal"));
        EnvironmentEntity env = environmentRepository.saveAndFlush(new EnvironmentEntity(UUID.randomUUID(), project, "prod", "Prod", null, true));

        // 1. Boolean config
        FlagEntity boolFlag = flagRepository.saveAndFlush(new FlagEntity(UUID.randomUUID(), project, "b-flag", "B", null, FlagValueType.BOOLEAN, "false"));
        FlagEnvironmentConfigEntity boolConfig = flagEnvironmentConfigRepository.saveAndFlush(
                new FlagEnvironmentConfigEntity(UUID.randomUUID(), boolFlag, env, project, true, "true")
        );
        assertThat(flagEnvironmentConfigRepository.findById(boolConfig.getId()).orElseThrow().getValue()).isEqualTo("true");

        // 2. String config
        FlagEntity strFlag = flagRepository.saveAndFlush(new FlagEntity(UUID.randomUUID(), project, "s-flag", "S", null, FlagValueType.STRING, "\"init\""));
        FlagEnvironmentConfigEntity strConfig = flagEnvironmentConfigRepository.saveAndFlush(
                new FlagEnvironmentConfigEntity(UUID.randomUUID(), strFlag, env, project, false, "\"custom-string\"")
        );
        assertThat(mapper.readTree(flagEnvironmentConfigRepository.findById(strConfig.getId()).orElseThrow().getValue()).asText()).isEqualTo("custom-string");

        // 3. Number config
        FlagEntity numFlag = flagRepository.saveAndFlush(new FlagEntity(UUID.randomUUID(), project, "n-flag", "N", null, FlagValueType.NUMBER, "0"));
        FlagEnvironmentConfigEntity numConfig = flagEnvironmentConfigRepository.saveAndFlush(
                new FlagEnvironmentConfigEntity(UUID.randomUUID(), numFlag, env, project, true, "1024")
        );
        assertThat(mapper.readTree(flagEnvironmentConfigRepository.findById(numConfig.getId()).orElseThrow().getValue()).asInt()).isEqualTo(1024);

        // 4. JSON config
        FlagEntity jsonFlag = flagRepository.saveAndFlush(new FlagEntity(UUID.randomUUID(), project, "j-flag", "J", null, FlagValueType.JSON, "{}"));
        FlagEnvironmentConfigEntity jsonConfig = flagEnvironmentConfigRepository.saveAndFlush(
                new FlagEnvironmentConfigEntity(UUID.randomUUID(), jsonFlag, env, project, true, "{\"limits\": [10, 20], \"active\": true}")
        );
        assertThat(mapper.readTree(flagEnvironmentConfigRepository.findById(jsonConfig.getId()).orElseThrow().getValue()).get("limits").get(1).asInt()).isEqualTo(20);
    }
}
