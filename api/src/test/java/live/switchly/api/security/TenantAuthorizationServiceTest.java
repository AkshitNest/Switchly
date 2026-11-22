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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantAuthorizationServiceTest {

    @Mock
    private OrganizationMembershipRepository membershipRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private FlagRepository flagRepository;

    @Mock
    private EnvironmentRepository environmentRepository;

    @Mock
    private FlagEnvironmentConfigRepository flagEnvironmentConfigRepository;

    @InjectMocks
    private TenantAuthorizationService authService;

    @Test
    void validateEnvironmentAccess_success() {
        UUID envId = UUID.randomUUID();
        UUID projId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Environment env = new Environment(envId, projId, "dev", "Dev", null, true);
        Project project = new Project(projId, orgId, "Proj");
        OrganizationMembership membership = new OrganizationMembership(UUID.randomUUID(), orgId, userId, Role.EMPLOYEE, null, null, projId);

        when(environmentRepository.findById(envId)).thenReturn(Optional.of(env));
        when(projectRepository.findById(projId)).thenReturn(Optional.of(project));
        when(membershipRepository.findByOrganizationIdAndUserId(orgId, userId)).thenReturn(Optional.of(membership));

        Environment result = authService.validateEnvironmentAccess(envId, userId, Role.EMPLOYEE);
        assertThat(result).isEqualTo(env);
    }

    @Test
    void validateEnvironmentAccess_nullUserId_throwsUnauthorized() {
        UUID envId = UUID.randomUUID();
        UUID projId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();

        Environment env = new Environment(envId, projId, "dev", "Dev", null, true);
        Project project = new Project(projId, orgId, "Proj");

        when(environmentRepository.findById(envId)).thenReturn(Optional.of(env));
        when(projectRepository.findById(projId)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> authService.validateEnvironmentAccess(envId, null, Role.EMPLOYEE))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void validateEnvironmentAccess_environmentNotFound_throwsNotFound() {
        UUID envId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(environmentRepository.findById(envId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.validateEnvironmentAccess(envId, userId, Role.EMPLOYEE))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Environment " + envId + " not found");
    }

    @Test
    void validateEnvironmentAccess_insufficientRole_throwsForbidden() {
        UUID envId = UUID.randomUUID();
        UUID projId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Environment env = new Environment(envId, projId, "dev", "Dev", null, true);
        Project project = new Project(projId, orgId, "Proj");
        OrganizationMembership membership = new OrganizationMembership(UUID.randomUUID(), orgId, userId, Role.EMPLOYEE);

        when(environmentRepository.findById(envId)).thenReturn(Optional.of(env));
        when(projectRepository.findById(projId)).thenReturn(Optional.of(project));
        when(membershipRepository.findByOrganizationIdAndUserId(orgId, userId)).thenReturn(Optional.of(membership));

        assertThatThrownBy(() -> authService.validateEnvironmentAccess(envId, userId, Role.ADMIN))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Insufficient permissions");
    }

    @Test
    void validateFlagEnvironmentAccess_success() {
        UUID envId = UUID.randomUUID();
        UUID flagId = UUID.randomUUID();
        UUID projId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Environment env = new Environment(envId, projId, "dev", "Dev", null, true);
        Flag flag = new Flag(flagId, orgId, projId, "dark-mode", "Dark Mode");
        OrganizationMembership membership = new OrganizationMembership(UUID.randomUUID(), orgId, userId, Role.EMPLOYEE, null, null, projId);
        FlagEnvironmentConfig config = new FlagEnvironmentConfig(UUID.randomUUID(), flagId, envId, projId, false, flag.getDefaultValue());

        when(environmentRepository.findById(envId)).thenReturn(Optional.of(env));
        when(flagRepository.findById(flagId)).thenReturn(Optional.of(flag));
        when(projectRepository.findById(projId)).thenReturn(Optional.of(new Project(projId, orgId, "P")));
        when(membershipRepository.findByOrganizationIdAndUserId(orgId, userId)).thenReturn(Optional.of(membership));
        when(flagEnvironmentConfigRepository.findByFlagIdAndEnvironmentId(flagId, envId)).thenReturn(Optional.of(config));

        FlagEnvironmentConfig result = authService.validateFlagEnvironmentAccess(envId, flagId, userId, Role.EMPLOYEE);
        assertThat(result).isEqualTo(config);
    }

    @Test
    void validateFlagEnvironmentAccess_crossProjectMismatch_throwsConflict() {
        UUID envId = UUID.randomUUID();
        UUID flagId = UUID.randomUUID();
        UUID projId1 = UUID.randomUUID();
        UUID projId2 = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Environment env = new Environment(envId, projId1, "dev", "Dev", null, true);
        Flag flag = new Flag(flagId, orgId, projId2, "dark-mode", "Dark Mode");
        OrganizationMembership membership = new OrganizationMembership(UUID.randomUUID(), orgId, userId, Role.ADMIN);

        when(environmentRepository.findById(envId)).thenReturn(Optional.of(env));
        when(flagRepository.findById(flagId)).thenReturn(Optional.of(flag));
        when(projectRepository.findById(projId1)).thenReturn(Optional.of(new Project(projId1, orgId, "P1")));
        when(projectRepository.findById(projId2)).thenReturn(Optional.of(new Project(projId2, orgId, "P2")));
        when(membershipRepository.findByOrganizationIdAndUserId(orgId, userId)).thenReturn(Optional.of(membership));

        assertThatThrownBy(() -> authService.validateFlagEnvironmentAccess(envId, flagId, userId, Role.EMPLOYEE))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Flag and environment must belong to the same project");
    }
}

