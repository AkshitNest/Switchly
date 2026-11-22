package live.switchly.api.service;

import live.switchly.api.dto.OrganizationMemberResponse;
import live.switchly.api.exception.*;
import live.switchly.api.model.*;
import live.switchly.api.repository.*;
import live.switchly.api.security.JwtService;
import live.switchly.api.security.TenantAuthorizationService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class OrganizationMembershipService {
    private final OrganizationMembershipRepository members;
    private final OrganizationRepository organizations;
    private final TenantAuthorizationService access;
    private final UserService users;
    private final JwtService tokens;
    private final JdbcClient db;
    private final PasswordEncoder passwords;

    public OrganizationMembershipService(OrganizationMembershipRepository members, OrganizationRepository organizations,
            TenantAuthorizationService access, UserService users, JwtService tokens, JdbcClient db, PasswordEncoder passwords) {
        this.members = members;
        this.organizations = organizations;
        this.access = access;
        this.users = users;
        this.tokens = tokens;
        this.db = db;
        this.passwords = passwords;
    }

    public List<OrganizationMemberResponse> listMembers(UUID org, UUID user) {
        var self = access.validateOrganizationAccess(org, user);
        var visible = self.getRole() == Role.ADMIN ? members.findByOrganizationId(org) : List.of(self);
        return visible.stream().map(m -> OrganizationMemberResponse.from(m, users.getById(m.getUserId()))).toList();
    }

    private void lockAdmin(UUID org, UUID actor) {
        UUID active = live.switchly.api.security.UserPrincipal.activeOrganization();
        if (active != null && !org.equals(active)) throw new ForbiddenException("Organization access denied");
        lockOrganization(org);
        access.validateOrganizationAccess(org, actor, Role.ADMIN);
    }

    private void lockOrganization(UUID org) {
        organizations.findByIdForUpdate(org).orElseThrow(() -> new NotFoundException("Organization unavailable"));
    }

    private void validateAssignment(UUID org, UUID actor, Role role, UUID project) {
        if ((role == Role.EMPLOYEE) != (project != null)) {
            throw new IllegalArgumentException("Assign exactly one project to an employee. Admins have organization-wide access.");
        }
        if (project != null && !org.equals(access.validateProjectAccess(project, actor, Role.ADMIN).getOrganizationId())) {
            throw new ForbiddenException("Project access denied");
        }
    }

    @Transactional
    public String invite(UUID org, UUID actor, String email, Role role, UUID project) {
        lockAdmin(org, actor);
        validateAssignment(org, actor, role, project);
        String address = UserService.normalizeEmail(email);
        if (db.sql("""
                SELECT EXISTS(SELECT 1 FROM organization_memberships m JOIN users u ON u.id=m.user_id
                WHERE m.organization_id=:org AND lower(u.email)=:email)
                """).param("org", org).param("email", address).query(Boolean.class).single()) {
            throw new ConflictException("This person already belongs to your organization");
        }
        String token = tokens.generateRefreshTokenRaw();
        db.sql("""
                INSERT INTO organization_invitations(token_hash,organization_id,email,role,project_id,expires_at)
                VALUES (:hash,:org,:email,:role,:project,now()+interval '48 hours')
                ON CONFLICT (organization_id,email) DO UPDATE SET token_hash=excluded.token_hash,
                role=excluded.role, project_id=excluded.project_id, expires_at=excluded.expires_at
                """).param("hash", tokens.hashToken(token)).param("org", org).param("email", address)
                .param("role", role.name()).param("project", project, java.sql.Types.OTHER).update();
        return token;
    }

    public record Pending(String email, String role, UUID projectId, java.time.OffsetDateTime expiresAt) {}

    public List<Pending> pending(UUID org, UUID actor) {
        access.validateOrganizationAccess(org, actor, Role.ADMIN);
        return db.sql("""
                SELECT email,role,project_id,expires_at FROM organization_invitations
                WHERE organization_id=:org AND expires_at>now() ORDER BY email
                """).param("org",org).query(Pending.class).list();
    }

    @Transactional
    public void cancel(UUID org, UUID actor, String email) {
        lockAdmin(org, actor);
        db.sql("DELETE FROM organization_invitations WHERE organization_id=:org AND email=:email")
                .param("org",org).param("email",UserService.normalizeEmail(email)).update();
    }

    private record Invitation(UUID organizationId, String email, String role, UUID projectId) {}

    @Transactional
    public UUID accept(String token, String email, String name, String password) {
        String hash = tokens.hashToken(token);
        var query = db.sql("""
                SELECT organization_id,email,role,project_id FROM organization_invitations
                WHERE token_hash=:hash AND expires_at>now() AND email=:email
                """).param("hash", hash).param("email", UserService.normalizeEmail(email));
        var invite = query.query(Invitation.class).optional().orElseThrow(OrganizationMembershipService::invalidInvite);
        lockOrganization(invite.organizationId());
        if (db.sql("DELETE FROM organization_invitations WHERE token_hash=:hash AND expires_at>now()")
                .param("hash", hash).update() != 1) throw invalidInvite();
        var existing = users.findByEmail(invite.email());
        if (existing.isPresent() && (!existing.get().isEnabled() || !passwords.matches(password, existing.get().getPasswordHash()))) {
            throw new UnauthorizedException("Use your existing Switchly password to join this organization");
        }
        var user = existing.orElseGet(() -> users.create(name, invite.email(), password));
        members.save(new OrganizationMembership(UUID.randomUUID(), invite.organizationId(), user.getId(),
                Role.valueOf(invite.role()), null, null, invite.projectId()));
        return invite.organizationId();
    }

    private static UnauthorizedException invalidInvite() { return new UnauthorizedException("Invitation is invalid or has expired"); }

    private OrganizationMembership target(UUID org, UUID user) {
        return members.findByOrganizationIdAndUserId(org, user).orElseThrow(() -> new NotFoundException("Member unavailable"));
    }

    private void protectLastAdmin(UUID org, OrganizationMembership member) {
        if (member.getRole() == Role.ADMIN && members.countByOrganizationIdAndRole(org, Role.ADMIN) <= 1) {
            throw new ConflictException("An organization must retain at least one admin");
        }
    }

    @Transactional
    public OrganizationMemberResponse updateRole(UUID org, UUID actor, UUID user, Role role, UUID project) {
        lockAdmin(org, actor);
        validateAssignment(org, actor, role, project);
        var current = target(org, user);
        if (role != Role.ADMIN) protectLastAdmin(org, current);
        var saved = members.save(new OrganizationMembership(current.getId(), org, user, role, null, null, project));
        return OrganizationMemberResponse.from(saved, users.getById(user));
    }

    @Transactional
    public void removeMember(UUID org, UUID actor, UUID user) {
        lockAdmin(org, actor);
        protectLastAdmin(org, target(org, user));
        members.deleteByOrganizationIdAndUserId(org, user);
    }
}
