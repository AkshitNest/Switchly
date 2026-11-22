package live.switchly.api.repository;

import live.switchly.api.model.OrganizationMembership;
import live.switchly.api.model.Role;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationMembershipRepository {
    OrganizationMembership save(OrganizationMembership membership);
    Optional<OrganizationMembership> findById(UUID id);
    List<OrganizationMembership> findByOrganizationId(UUID organizationId);
    List<OrganizationMembership> findByUserId(UUID userId);
    Optional<OrganizationMembership> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);
    long countByOrganizationIdAndRole(UUID organizationId, Role role);
    boolean existsByOrganizationIdAndUserId(UUID organizationId, UUID userId);
    void deleteByOrganizationIdAndUserId(UUID organizationId, UUID userId);
}

