package live.switchly.api.repository;

import live.switchly.api.entity.OrganizationEntity;
import live.switchly.api.entity.ProjectEntity;
import live.switchly.api.exception.NotFoundException;
import live.switchly.api.exception.ConflictException;
import org.springframework.dao.DataIntegrityViolationException;
import live.switchly.api.model.Project;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile("!in-memory")
@Transactional
public class ProjectRepositoryAdapter implements ProjectRepository {

    private final JpaProjectRepository jpaProjectRepository;
    private final JpaOrganizationRepository jpaOrganizationRepository;

    public ProjectRepositoryAdapter(JpaProjectRepository jpaProjectRepository, JpaOrganizationRepository jpaOrganizationRepository) {
        this.jpaProjectRepository = jpaProjectRepository;
        this.jpaOrganizationRepository = jpaOrganizationRepository;
    }

    @Override
    public Project save(Project project) {
        Optional<ProjectEntity> existing = jpaProjectRepository.findById(project.getId());
        ProjectEntity entity;
        if (existing.isPresent()) {
            entity = existing.get();
            entity.setName(project.getName());
        } else {
            OrganizationEntity org = jpaOrganizationRepository.findById(project.getOrganizationId())
                    .orElseThrow(() -> new NotFoundException("Organization " + project.getOrganizationId() + " not found"));
            entity = new ProjectEntity(project.getId(), org, project.getName(), project.getKey());
        }
        try {
            return toDomain(jpaProjectRepository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException ex) {
            if (ex.getMostSpecificCause().getMessage().contains("uq_projects_org_key")) {
                throw new ConflictException("Project key already exists in this organization");
            }
            throw ex;
        }
    }

    @Override
    public Optional<Project> findById(UUID id) {
        return jpaProjectRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Project> findByOrganizationId(UUID organizationId) {
        return jpaProjectRepository.findByOrganizationId(organizationId).stream()
                .map(this::toDomain)
                .toList();
    }

    private Project toDomain(ProjectEntity entity) {
        return new Project(entity.getId(), entity.getOrganization().getId(), entity.getName(), entity.getKey(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
