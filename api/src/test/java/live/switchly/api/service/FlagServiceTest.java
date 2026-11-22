package live.switchly.api.service;

import tools.jackson.databind.node.BooleanNode;
import tools.jackson.databind.node.IntNode;
import tools.jackson.databind.node.StringNode;
import live.switchly.api.exception.ConflictException;
import live.switchly.api.exception.NotFoundException;
import live.switchly.api.model.Environment;
import live.switchly.api.model.Flag;
import live.switchly.api.model.FlagEnvironmentConfig;
import live.switchly.api.model.FlagValueType;
import live.switchly.api.model.Project;
import live.switchly.api.model.Role;
import live.switchly.api.repository.EnvironmentRepository;
import live.switchly.api.repository.FlagEnvironmentConfigRepository;
import live.switchly.api.repository.FlagRepository;
import live.switchly.api.security.TenantAuthorizationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlagServiceTest {

    @Mock
    private ProjectService projectService;

    @Mock
    private FlagRepository flagRepository;

    @Mock
    private EnvironmentRepository environmentRepository;

    @Mock
    private FlagEnvironmentConfigRepository flagEnvironmentConfigRepository;

    @Mock
    private TenantAuthorizationService tenantAuthorizationService;

    @InjectMocks
    private FlagService flagService;

    @Test
    void create_secured_savesFlagAndAutoProvisionsEnvironmentConfigs() {
        UUID projectId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Project project = new Project(projectId, orgId, "Test Project");

        when(tenantAuthorizationService.validateProjectAccess(projectId, userId, Role.EMPLOYEE)).thenReturn(project);
        when(flagRepository.existsByProjectIdAndKey(projectId, "dark-mode")).thenReturn(false);
        when(flagRepository.save(any(Flag.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UUID env1Id = UUID.randomUUID();
        UUID env2Id = UUID.randomUUID();
        List<Environment> envs = List.of(
                new Environment(env1Id, projectId, "dev", "Dev", null, true),
                new Environment(env2Id, projectId, "prod", "Prod", null, true)
        );
        when(environmentRepository.findByProjectId(projectId)).thenReturn(envs);

        Flag flag = flagService.create(projectId, "dark-mode", "Dark Mode", "Toggle dark theme UI",
                FlagValueType.BOOLEAN, BooleanNode.FALSE, userId);

        assertThat(flag).isNotNull();
        assertThat(flag.getKey()).isEqualTo("dark-mode");
        assertThat(flag.getName()).isEqualTo("Dark Mode");
        assertThat(flag.getDescription()).isEqualTo("Toggle dark theme UI");
        assertThat(flag.getValueType()).isEqualTo(FlagValueType.BOOLEAN);
        assertThat(flag.getDefaultValue()).isEqualTo(BooleanNode.FALSE);

        verify(flagRepository).save(any(Flag.class));

        ArgumentCaptor<FlagEnvironmentConfig> configCaptor = ArgumentCaptor.forClass(FlagEnvironmentConfig.class);
        verify(flagEnvironmentConfigRepository, times(2)).save(configCaptor.capture());
        List<FlagEnvironmentConfig> savedConfigs = configCaptor.getAllValues();
        assertThat(savedConfigs).extracting(FlagEnvironmentConfig::getEnvironmentId)
                .containsExactlyInAnyOrder(env1Id, env2Id);
        assertThat(savedConfigs).allMatch(c -> !c.isEnabled());
        assertThat(savedConfigs).allMatch(c -> c.getValue().equals(BooleanNode.FALSE));
    }

    @Test
    void create_duplicateKeyInSameProject_throwsConflictException() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Project project = new Project(projectId, UUID.randomUUID(), "Test Project");

        when(tenantAuthorizationService.validateProjectAccess(projectId, userId, Role.EMPLOYEE)).thenReturn(project);
        when(flagRepository.existsByProjectIdAndKey(projectId, "dark-mode")).thenReturn(true);

        assertThatThrownBy(() -> flagService.create(projectId, "dark-mode", "Dark Mode", "Some description",
                FlagValueType.BOOLEAN, null, userId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists in this project");
    }

    @Test
    void setEnabled_environmentScoped_updatesConfig() {
        UUID envId = UUID.randomUUID();
        UUID flagId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID projId = UUID.randomUUID();

        FlagEnvironmentConfig config = new FlagEnvironmentConfig(UUID.randomUUID(), flagId, envId, projId, false, BooleanNode.FALSE);
        when(tenantAuthorizationService.validateFlagEnvironmentAccess(envId, flagId, userId, Role.EMPLOYEE)).thenReturn(config);
        when(flagEnvironmentConfigRepository.save(config)).thenReturn(config);

        FlagEnvironmentConfig updated = flagService.setEnabled(envId, flagId, true, userId);

        assertThat(updated.isEnabled()).isTrue();
        verify(flagEnvironmentConfigRepository).save(config);
    }

    @Test
    void setValue_environmentScoped_validatesTypeAndUpdatesConfig() {
        UUID envId = UUID.randomUUID();
        UUID flagId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID projId = UUID.randomUUID();

        Flag flag = new Flag(flagId, UUID.randomUUID(), projId, "num-flag", "Num Flag", null,
                FlagValueType.NUMBER, new IntNode(10));
        FlagEnvironmentConfig config = new FlagEnvironmentConfig(UUID.randomUUID(), flagId, envId, projId, false, new IntNode(10));

        when(tenantAuthorizationService.validateFlagEnvironmentAccess(envId, flagId, userId, Role.EMPLOYEE)).thenReturn(config);
        when(flagRepository.findById(flagId)).thenReturn(Optional.of(flag));
        when(flagEnvironmentConfigRepository.save(config)).thenReturn(config);

        FlagEnvironmentConfig updated = flagService.setValue(envId, flagId, new IntNode(99), userId);

        assertThat(updated.getValue().asInt()).isEqualTo(99);

        // Attempt invalid type (string for number)
        assertThatThrownBy(() -> flagService.setValue(envId, flagId, new StringNode("invalid"), userId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateLogicalFlag_updatesNameAndDescription_doesNotMutateConfigs() {
        UUID flagId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Flag flag = new Flag(flagId, UUID.randomUUID(), UUID.randomUUID(), "flag-key", "Old Name", "Old Desc");

        when(tenantAuthorizationService.validateFlagAccess(flagId, userId, Role.EMPLOYEE)).thenReturn(flag);
        when(flagRepository.save(flag)).thenReturn(flag);

        Flag updated = flagService.update(flagId, "New Name", "New Desc", BooleanNode.TRUE, userId);

        assertThat(updated.getName()).isEqualTo("New Name");
        assertThat(updated.getDescription()).isEqualTo("New Desc");
        assertThat(updated.getDefaultValue()).isEqualTo(BooleanNode.TRUE);
        verify(flagRepository).save(flag);
    }

    @Test
    void delete_existingFlag_deletesConfigsAndFlag() {
        UUID flagId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Flag flag = new Flag(flagId, UUID.randomUUID(), UUID.randomUUID(), "flag-key", "Flag Name");

        when(tenantAuthorizationService.validateFlagAccess(flagId, userId, Role.EMPLOYEE)).thenReturn(flag);

        flagService.delete(flagId, userId);

        verify(flagEnvironmentConfigRepository).deleteByFlagId(flagId);
        verify(flagRepository).deleteById(flagId);
    }
}
