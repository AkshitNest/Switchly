package live.switchly.api.service;

import live.switchly.api.exception.NotFoundException;
import live.switchly.api.model.Organization;
import live.switchly.api.model.Project;
import live.switchly.api.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private OrganizationService organizationService;

    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private ProjectService projectService;

    @Test
    void create_savesProjectForOrganization() {
        UUID orgId = UUID.randomUUID();
        when(organizationService.getById(orgId)).thenReturn(new Organization(orgId, "Acme"));
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        Project project = projectService.create(orgId, "Web App");

        assertThat(project.getName()).isEqualTo("Web App");
        assertThat(project.getOrganizationId()).isEqualTo(orgId);
        verify(projectRepository).save(any(Project.class));
    }

    @Test
    void create_missingOrg_throwsNotFound() {
        UUID orgId = UUID.randomUUID();
        when(organizationService.getById(orgId)).thenThrow(new NotFoundException("Organization " + orgId + " not found"));

        assertThatThrownBy(() -> projectService.create(orgId, "Web App"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getById_missing_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(projectRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.getById(id))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Project " + id + " not found");
    }

    @Test
    void getAllForOrganization_returnsProjects() {
        UUID orgId = UUID.randomUUID();
        List<Project> projects = List.of(new Project(UUID.randomUUID(), orgId, "Web"));
        when(organizationService.getById(orgId)).thenReturn(new Organization(orgId, "Acme"));
        when(projectRepository.findByOrganizationId(orgId)).thenReturn(projects);

        assertThat(projectService.getAllForOrganization(orgId)).isEqualTo(projects);
    }
}
