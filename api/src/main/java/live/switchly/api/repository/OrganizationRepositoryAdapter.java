package live.switchly.api.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import live.switchly.api.entity.OrganizationEntity;
import live.switchly.api.model.Organization;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile("!in-memory")
@Transactional
public class OrganizationRepositoryAdapter implements OrganizationRepository {

    private final JpaOrganizationRepository jpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public OrganizationRepositoryAdapter(JpaOrganizationRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Organization save(Organization organization) {
        Optional<OrganizationEntity> existing = jpaRepository.findById(organization.getId());
        OrganizationEntity entity;
        if (existing.isPresent()) {
            entity = existing.get();
            entity.setName(organization.getName());
        } else {
            entity = new OrganizationEntity(organization.getId(), organization.getName());
        }
        OrganizationEntity saved = jpaRepository.saveAndFlush(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Organization> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Organization> findByIdForUpdate(UUID id) {
        return jpaRepository.findByIdForUpdate(id).map(this::toDomain);
    }

    @Override
    public List<Organization> findAll() {
        return jpaRepository.findAll().stream()
                .map(this::toDomain)
                .toList();
    }

    private Organization toDomain(OrganizationEntity entity) {
        return new Organization(entity.getId(), entity.getName(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
