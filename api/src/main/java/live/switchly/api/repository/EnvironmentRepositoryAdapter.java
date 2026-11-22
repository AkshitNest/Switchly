package live.switchly.api.repository;

import live.switchly.api.entity.EnvironmentEntity;
import live.switchly.api.entity.ProjectEntity;
import live.switchly.api.exception.ConflictException;
import live.switchly.api.exception.NotFoundException;
import live.switchly.api.model.Environment;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile("!in-memory")
@Transactional
public class EnvironmentRepositoryAdapter implements EnvironmentRepository {

    private final JpaEnvironmentRepository jpaEnvironmentRepository;
    private final JpaProjectRepository jpaProjectRepository;

    public EnvironmentRepositoryAdapter(JpaEnvironmentRepository jpaEnvironmentRepository, JpaProjectRepository jpaProjectRepository) {
        this.jpaEnvironmentRepository = jpaEnvironmentRepository;
        this.jpaProjectRepository = jpaProjectRepository;
    }

    @Override
    public Environment save(Environment environment) {
        try {
            Optional<EnvironmentEntity> existing = jpaEnvironmentRepository.findById(environment.getId());
            EnvironmentEntity entity;
            if (existing.isPresent()) {
                entity = existing.get();
                entity.setName(environment.getName());
                entity.setDescription(environment.getDescription());
                entity.setDefault(environment.isDefault());
            } else {
                ProjectEntity project = jpaProjectRepository.findById(environment.getProjectId())
                        .orElseThrow(() -> new NotFoundException("Project " + environment.getProjectId() + " not found"));
                entity = new EnvironmentEntity(
                        environment.getId(),
                        project,
                        environment.getKey(),
                        environment.getName(),
                        environment.getDescription(),
                        environment.isDefault()
                );
            }
            EnvironmentEntity saved = jpaEnvironmentRepository.saveAndFlush(entity);
            return toDomain(saved);
        } catch (DataIntegrityViolationException ex) {
            String message = ex.getMessage();
            if (message != null && (message.contains("uq_environments_project_key") || message.contains("duplicate key"))) {
                throw new ConflictException("An environment with key '" + environment.getKey() + "' already exists in this project");
            }
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Environment> findById(UUID id) {
        return jpaEnvironmentRepository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Environment> findByProjectId(UUID projectId) {
        return jpaEnvironmentRepository.findByProjectId(projectId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Environment> findByProjectIdAndKey(UUID projectId, String key) {
        return jpaEnvironmentRepository.findByProjectIdAndKey(projectId, key).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByProjectIdAndKey(UUID projectId, String key) {
        return jpaEnvironmentRepository.existsByProjectIdAndKey(projectId, key);
    }

    @Override
    public void deleteById(UUID id) {
        jpaEnvironmentRepository.findById(id).ifPresent(jpaEnvironmentRepository::delete);
    }

    private Environment toDomain(EnvironmentEntity entity) {
        return new Environment(
                entity.getId(),
                entity.getProject().getId(),
                entity.getKey(),
                entity.getName(),
                entity.getDescription(),
                entity.isDefault(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}

