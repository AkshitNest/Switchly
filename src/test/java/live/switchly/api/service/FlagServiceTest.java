package live.switchly.api.service;

import live.switchly.api.exception.ConflictException;
import live.switchly.api.exception.NotFoundException;
import live.switchly.api.model.Flag;
import live.switchly.api.model.Project;
import live.switchly.api.repository.FlagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlagServiceTest {

    @Mock
    private ProjectService projectService;

    @Mock
    private FlagRepository flagRepository;

    @InjectMocks
    private FlagService flagService;

    @Test
    void create_withDescription_savesFlagWithDescription() {
        UUID projectId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        Project project = new Project(projectId, orgId, "Test Project");

        when(projectService.getById(projectId)).thenReturn(project);
        when(flagRepository.existsByProjectIdAndKey(projectId, "dark-mode")).thenReturn(false);
        when(flagRepository.save(any(Flag.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Flag flag = flagService.create(projectId, "dark-mode", "Dark Mode", "Toggle dark theme UI");

        assertThat(flag).isNotNull();
        assertThat(flag.getKey()).isEqualTo("dark-mode");
        assertThat(flag.getName()).isEqualTo("Dark Mode");
        assertThat(flag.getDescription()).isEqualTo("Toggle dark theme UI");
        assertThat(flag.isEnabled()).isFalse();
        assertThat(flag.getProjectId()).isEqualTo(projectId);
        assertThat(flag.getOrganizationId()).isEqualTo(orgId);

        ArgumentCaptor<Flag> captor = ArgumentCaptor.forClass(Flag.class);
        verify(flagRepository).save(captor.capture());
        assertThat(captor.getValue().getDescription()).isEqualTo("Toggle dark theme UI");
    }

    @Test
    void create_withoutDescription_savesFlagWithNullDescription() {
        UUID projectId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        Project project = new Project(projectId, orgId, "Test Project");

        when(projectService.getById(projectId)).thenReturn(project);
        when(flagRepository.existsByProjectIdAndKey(projectId, "beta-feature")).thenReturn(false);
        when(flagRepository.save(any(Flag.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Flag flag = flagService.create(projectId, "beta-feature", "Beta Feature");

        assertThat(flag).isNotNull();
        assertThat(flag.getKey()).isEqualTo("beta-feature");
        assertThat(flag.getName()).isEqualTo("Beta Feature");
        assertThat(flag.getDescription()).isNull();
        assertThat(flag.isEnabled()).isFalse();
    }

    @Test
    void create_duplicateKeyInSameProject_throwsConflictException() {
        UUID projectId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        Project project = new Project(projectId, orgId, "Test Project");

        when(projectService.getById(projectId)).thenReturn(project);
        when(flagRepository.existsByProjectIdAndKey(projectId, "dark-mode")).thenReturn(true);

        assertThatThrownBy(() -> flagService.create(projectId, "dark-mode", "Dark Mode", "Some description"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists in this project");
    }

    @Test
    void delete_existingFlag_deletesSuccessfully() {
        UUID flagId = UUID.randomUUID();
        Flag flag = new Flag(flagId, UUID.randomUUID(), UUID.randomUUID(), "flag-key", "Flag Name", "Desc", false);

        when(flagRepository.findById(flagId)).thenReturn(Optional.of(flag));

        flagService.delete(flagId);

        verify(flagRepository).deleteById(flagId);
    }

    @Test
    void delete_nonExistentFlag_throwsNotFoundException() {
        UUID flagId = UUID.randomUUID();

        when(flagRepository.findById(flagId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> flagService.delete(flagId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Flag " + flagId + " not found");
    }

    @Test
    void getById_existing_returnsFlag() {
        UUID flagId = UUID.randomUUID();
        Flag flag = new Flag(flagId, UUID.randomUUID(), UUID.randomUUID(), "k", "N", false);
        when(flagRepository.findById(flagId)).thenReturn(Optional.of(flag));

        assertThat(flagService.getById(flagId)).isEqualTo(flag);
    }

    @Test
    void getAllForProject_returnsFlags() {
        UUID projectId = UUID.randomUUID();
        Project project = new Project(projectId, UUID.randomUUID(), "P");
        Flag flag = new Flag(UUID.randomUUID(), project.getOrganizationId(), projectId, "k", "N", false);
        when(projectService.getById(projectId)).thenReturn(project);
        when(flagRepository.findByProjectId(projectId)).thenReturn(java.util.List.of(flag));

        assertThat(flagService.getAllForProject(projectId)).containsExactly(flag);
    }

    @Test
    void setEnabled_updatesAndSaves() {
        UUID flagId = UUID.randomUUID();
        Flag flag = new Flag(flagId, UUID.randomUUID(), UUID.randomUUID(), "k", "N", false);
        when(flagRepository.findById(flagId)).thenReturn(Optional.of(flag));
        when(flagRepository.save(flag)).thenReturn(flag);

        Flag updated = flagService.setEnabled(flagId, true);

        assertThat(updated.isEnabled()).isTrue();
        verify(flagRepository).save(flag);
    }
}
