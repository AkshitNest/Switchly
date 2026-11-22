package live.switchly.api.repository;

import live.switchly.api.entity.OrganizationMembershipEntity;
import live.switchly.api.model.Role;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaOrganizationMembershipRepository extends JpaRepository<OrganizationMembershipEntity, UUID> {

    @EntityGraph(attributePaths = {"organization", "user"})
    List<OrganizationMembershipEntity> findByOrganizationId(UUID organizationId);

    @EntityGraph(attributePaths = {"organization", "user"})
    List<OrganizationMembershipEntity> findByUserId(UUID userId);

    @EntityGraph(attributePaths = {"organization", "user"})
    Optional<OrganizationMembershipEntity> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);

    long countByOrganizationIdAndRole(UUID organizationId, Role role);

    boolean existsByOrganizationIdAndUserId(UUID organizationId, UUID userId);

    void deleteByOrganizationIdAndUserId(UUID organizationId, UUID userId);
}

