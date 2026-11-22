package live.switchly.api.service;

import live.switchly.api.exception.NotFoundException;
import live.switchly.api.model.Organization;
import live.switchly.api.model.OrganizationMembership;
import live.switchly.api.model.Role;
import live.switchly.api.repository.OrganizationMembershipRepository;
import live.switchly.api.repository.OrganizationRepository;
import live.switchly.api.security.TenantAuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final TenantAuthorizationService tenantAuthorizationService;

    public OrganizationService(
            OrganizationRepository organizationRepository,
            OrganizationMembershipRepository membershipRepository,
            TenantAuthorizationService tenantAuthorizationService
    ) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.tenantAuthorizationService = tenantAuthorizationService;
    }

    // ── Public User-Facing Secured Operations ──────────────────────────────────

    @Transactional
    public Organization create(String name, UUID ownerUserId) {
        Organization organization = new Organization(UUID.randomUUID(), name);
        Organization saved = organizationRepository.save(organization);

        if (ownerUserId != null) {
            OrganizationMembership membership = new OrganizationMembership(
                    UUID.randomUUID(),
                    saved.getId(),
                    ownerUserId,
                    Role.ADMIN
            );
            membershipRepository.save(membership);
        }

        return saved;
    }

    public void requireAdmin(UUID organization, UUID user) {
        tenantAuthorizationService.validateOrganizationAccess(organization, user, Role.ADMIN);
    }

    public Organization getById(UUID id, UUID requestingUserId) {
        tenantAuthorizationService.validateOrganizationAccess(id, requestingUserId, Role.EMPLOYEE);
        return getById(id);
    }

    public List<Organization> getAllForUser(UUID userId) {
        UUID active = live.switchly.api.security.UserPrincipal.activeOrganization();
        if (active != null) return List.of(getById(active, userId));
        List<OrganizationMembership> memberships = membershipRepository.findByUserId(userId);
        return memberships.stream()
                .map(m -> organizationRepository.findById(m.getOrganizationId()).orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    // ── Internal / Package-Private Trusted Operations ─────────────────────────

    @Transactional
    Organization create(String name) {
        return create(name, null);
    }

    Organization getById(UUID id) {
        return organizationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Organization " + id + " not found"));
    }

    List<Organization> getAll() {
        return organizationRepository.findAll();
    }
}

