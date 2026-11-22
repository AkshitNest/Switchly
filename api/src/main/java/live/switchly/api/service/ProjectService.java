package live.switchly.api.service;

import live.switchly.api.exception.NotFoundException;
import live.switchly.api.model.Environment;
import live.switchly.api.model.Organization;
import live.switchly.api.model.Project;
import live.switchly.api.model.Role;
import live.switchly.api.repository.EnvironmentRepository;
import live.switchly.api.repository.ProjectRepository;
import live.switchly.api.security.TenantAuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ProjectService {

    private final OrganizationService organizationService;
    private final ProjectRepository projectRepository;
    private final EnvironmentRepository environmentRepository;
    private final TenantAuthorizationService tenantAuthorizationService;

    public ProjectService(
            OrganizationService organizationService,
            ProjectRepository projectRepository,
            EnvironmentRepository environmentRepository,
            TenantAuthorizationService tenantAuthorizationService
    ) {
        this.organizationService = organizationService;
        this.projectRepository = projectRepository;
        this.environmentRepository = environmentRepository;
        this.tenantAuthorizationService = tenantAuthorizationService;
    }

    // ── Public User-Facing Secured Operations ──────────────────────────────────

    @Transactional
    public Project create(UUID organizationId, String name, UUID requestingUserId) {
        return create(organizationId, name, null, requestingUserId);
    }

    @Transactional
    public Project create(UUID organizationId, String name, String key, UUID requestingUserId) {
        tenantAuthorizationService.validateOrganizationAccess(organizationId, requestingUserId, Role.ADMIN);
        return provision(organizationId, name, key);
    }

    public Project getById(UUID id, UUID requestingUserId) {
        tenantAuthorizationService.validateProjectAccess(id, requestingUserId, Role.EMPLOYEE);
        return getById(id);
    }

    public List<Project> getAllForOrganization(UUID organizationId, UUID requestingUserId) {
        var member = tenantAuthorizationService.validateOrganizationAccess(organizationId, requestingUserId, Role.EMPLOYEE);
        if (member.getRole() == Role.EMPLOYEE) {
            return member.getProjectId() == null ? List.of() : projectRepository.findById(member.getProjectId()).stream().toList();
        }
        return getAllForOrganization(organizationId);
    }

    // ── Internal / Package-Private Trusted Operations ─────────────────────────

    @Transactional
    Project create(UUID organizationId, String name) {
        return provision(organizationId, name, null);
    }

    private Project provision(UUID organizationId, String name, String key) {
        Organization organization = organizationService.getById(organizationId);
        Project project = new Project(UUID.randomUUID(), organization.getId(), name, key, null, null);
        Project savedProject = projectRepository.save(project);
        createDefaultEnvironments(savedProject.getId());
        return savedProject;
    }

    private void createDefaultEnvironments(UUID projectId) {
        environmentRepository.save(new Environment(UUID.randomUUID(), projectId, "development", "Development", "Development environment", true));
        environmentRepository.save(new Environment(UUID.randomUUID(), projectId, "staging", "Staging", "Staging environment", true));
        environmentRepository.save(new Environment(UUID.randomUUID(), projectId, "production", "Production", "Production environment", true));
    }

    Project getById(UUID id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Project " + id + " not found"));
    }

    List<Project> getAllForOrganization(UUID organizationId) {
        organizationService.getById(organizationId);
        return projectRepository.findByOrganizationId(organizationId);
    }
}

