package live.switchly.api.repository;

import live.switchly.api.entity.OrganizationEntity;
import live.switchly.api.entity.OrganizationMembershipEntity;
import live.switchly.api.entity.UserEntity;
import live.switchly.api.exception.NotFoundException;
import live.switchly.api.model.OrganizationMembership;
import live.switchly.api.model.Role;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@Profile("!in-memory")
@Transactional
public class OrganizationMembershipRepositoryAdapter implements OrganizationMembershipRepository {

    private final JpaOrganizationMembershipRepository jpaMembershipRepo;
    private final JpaOrganizationRepository jpaOrgRepo;
    private final JpaUserRepository jpaUserRepo;

    public OrganizationMembershipRepositoryAdapter(
            JpaOrganizationMembershipRepository jpaMembershipRepo,
            JpaOrganizationRepository jpaOrgRepo,
            JpaUserRepository jpaUserRepo) {
        this.jpaMembershipRepo = jpaMembershipRepo;
        this.jpaOrgRepo = jpaOrgRepo;
        this.jpaUserRepo = jpaUserRepo;
    }

    @Override
    public OrganizationMembership save(OrganizationMembership membership) {
        OrganizationMembershipEntity entity = jpaMembershipRepo.findById(membership.getId())
                .orElseGet(() -> {
                    OrganizationEntity org = jpaOrgRepo.findById(membership.getOrganizationId())
                            .orElseThrow(() -> new NotFoundException("Organization not found: " + membership.getOrganizationId()));
                    UserEntity user = jpaUserRepo.findById(membership.getUserId())
                            .orElseThrow(() -> new NotFoundException("User not found: " + membership.getUserId()));
                    return new OrganizationMembershipEntity(membership.getId(), org, user, membership.getRole());
                });

        entity.setProjectId(membership.getProjectId());
        entity.setRole(membership.getRole());
        OrganizationMembershipEntity saved = jpaMembershipRepo.saveAndFlush(entity);
        return toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrganizationMembership> findById(UUID id) {
        return jpaMembershipRepo.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrganizationMembership> findByOrganizationId(UUID organizationId) {
        return jpaMembershipRepo.findByOrganizationId(organizationId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrganizationMembership> findByUserId(UUID userId) {
        return jpaMembershipRepo.findByUserId(userId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrganizationMembership> findByOrganizationIdAndUserId(UUID organizationId, UUID userId) {
        return jpaMembershipRepo.findByOrganizationIdAndUserId(organizationId, userId).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public long countByOrganizationIdAndRole(UUID organizationId, Role role) {
        return jpaMembershipRepo.countByOrganizationIdAndRole(organizationId, role);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByOrganizationIdAndUserId(UUID organizationId, UUID userId) {
        return jpaMembershipRepo.existsByOrganizationIdAndUserId(organizationId, userId);
    }

    @Override
    public void deleteByOrganizationIdAndUserId(UUID organizationId, UUID userId) {
        jpaMembershipRepo.deleteByOrganizationIdAndUserId(organizationId, userId);
    }

    private OrganizationMembership toDomain(OrganizationMembershipEntity entity) {
        return new OrganizationMembership(
                entity.getId(),
                entity.getOrganization().getId(),
                entity.getUser().getId(),
                entity.getRole(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getProjectId()
        );
    }
}

