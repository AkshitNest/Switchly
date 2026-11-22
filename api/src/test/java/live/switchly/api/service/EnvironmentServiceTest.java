package live.switchly.api.service;

import live.switchly.api.exception.ConflictException;
import live.switchly.api.exception.NotFoundException;
import live.switchly.api.model.Environment;
import live.switchly.api.model.Flag;
import live.switchly.api.model.FlagEnvironmentConfig;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnvironmentServiceTest {

    @Mock
    private ProjectService projectService;

    @Mock
    private EnvironmentRepository environmentRepository;

    @Mock
    private FlagRepository flagRepository;

    @Mock
    private FlagEnvironmentConfigRepository flagEnvironmentConfigRepository;

    @Mock
    private TenantAuthorizationService tenantAuthorizationService;

    @InjectMocks
    private EnvironmentService environmentService;

    @Test
    void create_secured_savesEnvironmentWhenAdmin() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Project project = new Project(projectId, UUID.randomUUID(), "Test Project");

        when(tenantAuthorizationService.validateProjectAccess(projectId, userId, Role.ADMIN)).thenReturn(project);
        when(environmentRepository.existsByProjectIdAndKey(projectId, "qa")).thenReturn(false);
        when(environmentRepository.save(any(Environment.class))).thenAnswer(inv -> inv.getArgument(0));

        Flag flag1 = new Flag(UUID.randomUUID(), UUID.randomUUID(), projectId, "f1", "F 1");
        when(flagRepository.findByProjectId(projectId)).thenReturn(List.of(flag1));

        Environment created = environmentService.create(projectId, "qa", "QA", "QA testing environment", userId);

        assertThat(created).isNotNull();
        assertThat(created.getKey()).isEqualTo("qa");
        assertThat(created.getName()).isEqualTo("QA");
        assertThat(created.getDescription()).isEqualTo("QA testing environment");
        assertThat(created.isDefault()).isFalse();
        assertThat(created.getProjectId()).isEqualTo(projectId);

        ArgumentCaptor<Environment> captor = ArgumentCaptor.forClass(Environment.class);
        verify(environmentRepository).save(captor.capture());
        assertThat(captor.getValue().getKey()).isEqualTo("qa");
        assertThat(captor.getValue().isDefault()).isFalse();

        verify(flagEnvironmentConfigRepository).save(any(FlagEnvironmentConfig.class));
    }

    @Test
    void create_secured_duplicateKey_throwsConflictException() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Project project = new Project(projectId, UUID.randomUUID(), "Test Project");

        when(tenantAuthorizationService.validateProjectAccess(projectId, userId, Role.ADMIN)).thenReturn(project);
        when(environmentRepository.existsByProjectIdAndKey(projectId, "development")).thenReturn(true);

        assertThatThrownBy(() -> environmentService.create(projectId, "development", "Dev 2", "desc", userId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists in this project");

        verify(environmentRepository, never()).save(any());
    }

    @Test
    void getById_secured_returnsEnvironmentForViewer() {
        UUID envId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Environment env = new Environment(envId, UUID.randomUUID(), "dev", "Dev", "desc", true);

        when(tenantAuthorizationService.validateEnvironmentAccess(envId, userId, Role.EMPLOYEE)).thenReturn(env);
        when(environmentRepository.findById(envId)).thenReturn(Optional.of(env));

        Environment result = environmentService.getById(envId, userId);

        assertThat(result).isEqualTo(env);
    }

    @Test
    void getAllForProject_secured_returnsEnvironmentsForViewer() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Project project = new Project(projectId, UUID.randomUUID(), "Proj");
        List<Environment> envs = List.of(
                new Environment(UUID.randomUUID(), projectId, "development", "Dev", null, true),
                new Environment(UUID.randomUUID(), projectId, "staging", "Staging", null, true)
        );

        when(tenantAuthorizationService.validateProjectAccess(projectId, userId, Role.EMPLOYEE)).thenReturn(project);
        when(environmentRepository.findByProjectId(projectId)).thenReturn(envs);

        List<Environment> results = environmentService.getAllForProject(projectId, userId);

        assertThat(results).isEqualTo(envs);
    }

    @Test
    void update_secured_updatesNameAndDescriptionForAdmin() {
        UUID envId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Environment env = new Environment(envId, UUID.randomUUID(), "qa", "Old Name", "Old Desc", false);

        when(tenantAuthorizationService.validateEnvironmentAccess(envId, userId, Role.ADMIN)).thenReturn(env);
        when(environmentRepository.save(env)).thenReturn(env);

        Environment updated = environmentService.update(envId, "New Name", "New Desc", userId);

        assertThat(updated.getName()).isEqualTo("New Name");
        assertThat(updated.getDescription()).isEqualTo("New Desc");
        verify(environmentRepository).save(env);
    }

    @Test
    void delete_secured_deletesCustomEnvironmentForAdmin() {
        UUID envId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Environment env = new Environment(envId, UUID.randomUUID(), "custom-env", "Custom", "desc", false);

        when(tenantAuthorizationService.validateEnvironmentAccess(envId, userId, Role.ADMIN)).thenReturn(env);

        environmentService.delete(envId, userId);

        verify(environmentRepository).deleteById(envId);
    }

    @Test
    void delete_secured_defaultEnvironment_throwsConflictException() {
        UUID envId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Environment env = new Environment(envId, UUID.randomUUID(), "production", "Production", "desc", true);

        when(tenantAuthorizationService.validateEnvironmentAccess(envId, userId, Role.ADMIN)).thenReturn(env);

        assertThatThrownBy(() -> environmentService.delete(envId, userId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Default environments cannot be deleted");

        verify(environmentRepository, never()).deleteById(any());
    }

    @Test
    void delete_internal_defaultEnvironment_throwsConflictException() {
        UUID envId = UUID.randomUUID();
        Environment env = new Environment(envId, UUID.randomUUID(), "staging", "Staging", "desc", true);

        when(environmentRepository.findById(envId)).thenReturn(Optional.of(env));

        assertThatThrownBy(() -> environmentService.delete(envId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Default environments cannot be deleted");

        verify(environmentRepository, never()).deleteById(any());
    }

    @Test
    void getById_internal_missing_throwsNotFoundException() {
        UUID envId = UUID.randomUUID();
        when(environmentRepository.findById(envId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> environmentService.getById(envId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Environment " + envId + " not found");
    }
}

