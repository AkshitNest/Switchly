package live.switchly.api.security;

import live.switchly.api.exception.ConflictException;
import live.switchly.api.exception.ForbiddenException;
import live.switchly.api.exception.NotFoundException;
import live.switchly.api.exception.UnauthorizedException;
import live.switchly.api.model.Environment;
import live.switchly.api.model.Flag;
import live.switchly.api.model.FlagEnvironmentConfig;
import live.switchly.api.model.OrganizationMembership;
import live.switchly.api.model.Project;
import live.switchly.api.model.Role;
import live.switchly.api.repository.EnvironmentRepository;
import live.switchly.api.repository.FlagEnvironmentConfigRepository;
import live.switchly.api.repository.FlagRepository;
import live.switchly.api.repository.OrganizationMembershipRepository;
import live.switchly.api.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TenantAuthorizationService {

    private final OrganizationMembershipRepository membershipRepository;
    private final ProjectRepository projectRepository;
    private final FlagRepository flagRepository;
    private final EnvironmentRepository environmentRepository;
    private final FlagEnvironmentConfigRepository flagEnvironmentConfigRepository;

    public TenantAuthorizationService(
            OrganizationMembershipRepository membershipRepository,
            ProjectRepository projectRepository,
            FlagRepository flagRepository,
            EnvironmentRepository environmentRepository,
            FlagEnvironmentConfigRepository flagEnvironmentConfigRepository
    ) {
        this.membershipRepository = membershipRepository;
        this.projectRepository = projectRepository;
        this.flagRepository = flagRepository;
        this.environmentRepository = environmentRepository;
        this.flagEnvironmentConfigRepository = flagEnvironmentConfigRepository;
    }

    public OrganizationMembership validateOrganizationAccess(UUID orgId, UUID userId) {
        return validateOrganizationAccess(orgId, userId, Role.EMPLOYEE);
    }

    public OrganizationMembership validateOrganizationAccess(UUID orgId, UUID userId, Role minimumRole) {
        if (userId == null) {
            throw new UnauthorizedException("Full authentication is required to access this resource");
        }
        UUID activeOrg = UserPrincipal.activeOrganization();
        if (activeOrg != null && !activeOrg.equals(orgId)) throw new ForbiddenException("Organization access denied");
        OrganizationMembership membership = membershipRepository.findByOrganizationIdAndUserId(orgId, userId)
                .orElseThrow(() -> new ForbiddenException("User does not have access to organization " + orgId));
        if (!membership.getRole().isAtLeast(minimumRole)) {
            throw new ForbiddenException("Insufficient permissions. Required role: " + minimumRole);
        }
        return membership;
    }

    public Project validateProjectAccess(UUID projectId, UUID userId) {
        return validateProjectAccess(projectId, userId, Role.EMPLOYEE);
    }

    public Project validateProjectAccess(UUID projectId, UUID userId, Role minimumRole) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project " + projectId + " not found"));
        var membership = validateOrganizationAccess(project.getOrganizationId(), userId, minimumRole);
        if (membership.getRole() == Role.EMPLOYEE && !projectId.equals(membership.getProjectId())) {
            throw new ForbiddenException("Project access denied");
        }
        return project;
    }

    public Flag validateFlagAccess(UUID flagId, UUID userId) {
        return validateFlagAccess(flagId, userId, Role.EMPLOYEE);
    }

    public Flag validateFlagAccess(UUID flagId, UUID userId, Role minimumRole) {
        Flag flag = flagRepository.findById(flagId)
                .orElseThrow(() -> new NotFoundException("Flag " + flagId + " not found"));
        validateProjectAccess(flag.getProjectId(), userId, minimumRole);
        return flag;
    }

    public Environment validateEnvironmentAccess(UUID environmentId, UUID userId) {
        return validateEnvironmentAccess(environmentId, userId, Role.EMPLOYEE);
    }

    public Environment validateEnvironmentAccess(UUID environmentId, UUID userId, Role minimumRole) {
        Environment environment = environmentRepository.findById(environmentId)
                .orElseThrow(() -> new NotFoundException("Environment " + environmentId + " not found"));
        validateProjectAccess(environment.getProjectId(), userId, minimumRole);
        return environment;
    }

    public FlagEnvironmentConfig validateFlagEnvironmentAccess(UUID environmentId, UUID flagId, UUID userId, Role minimumRole) {
        Environment environment = validateEnvironmentAccess(environmentId, userId, minimumRole);
        Flag flag = validateFlagAccess(flagId, userId, minimumRole);

        if (!environment.getProjectId().equals(flag.getProjectId())) {
            throw new ConflictException("Flag and environment must belong to the same project");
        }

        return flagEnvironmentConfigRepository.findByFlagIdAndEnvironmentId(flagId, environmentId)
                .orElseThrow(() -> new NotFoundException("Configuration for flag " + flagId + " in environment " + environmentId + " not found"));
    }
}


