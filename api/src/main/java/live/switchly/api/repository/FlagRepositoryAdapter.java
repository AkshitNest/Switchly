package live.switchly.api.repository;

import live.switchly.api.entity.FlagEntity;
import live.switchly.api.entity.ProjectEntity;
import live.switchly.api.exception.ConflictException;
import live.switchly.api.exception.NotFoundException;
import live.switchly.api.model.Flag;
import live.switchly.api.model.FlagValueValidator;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile("!in-memory")
@Transactional
public class FlagRepositoryAdapter implements FlagRepository {

    private static final JsonMapper MAPPER = FlagValueValidator.getMapper();

    private final JpaFlagRepository jpaFlagRepository;
    private final JpaProjectRepository jpaProjectRepository;

    public FlagRepositoryAdapter(JpaFlagRepository jpaFlagRepository, JpaProjectRepository jpaProjectRepository) {
        this.jpaFlagRepository = jpaFlagRepository;
        this.jpaProjectRepository = jpaProjectRepository;
    }

    @Override
    public Flag save(Flag flag) {
        try {
            Optional<FlagEntity> existing = jpaFlagRepository.findById(flag.getId());
            FlagEntity entity;
            if (existing.isPresent()) {
                entity = existing.get();
                entity.setName(flag.getName());
                entity.setDescription(flag.getDescription());
                entity.setDefaultValue(toJson(flag.getDefaultValue()));
            } else {
                ProjectEntity project = jpaProjectRepository.findById(flag.getProjectId())
                        .orElseThrow(() -> new NotFoundException("Project " + flag.getProjectId() + " not found"));
                entity = new FlagEntity(
                        flag.getId(),
                        project,
                        flag.getKey(),
                        flag.getName(),
                        flag.getDescription(),
                        flag.getValueType(),
                        toJson(flag.getDefaultValue())
                );
            }
            FlagEntity saved = jpaFlagRepository.saveAndFlush(entity);
            return toDomain(saved);
        } catch (DataIntegrityViolationException ex) {
            String message = ex.getMessage();
            if (message != null && (message.contains("uq_flags_project_key") || message.contains("duplicate key"))) {
                throw new ConflictException("A flag with key '" + flag.getKey() + "' already exists in this project");
            }
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Flag> findById(UUID id) {
        return jpaFlagRepository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Flag> findByProjectId(UUID projectId) {
        return jpaFlagRepository.findByProjectId(projectId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByProjectIdAndKey(UUID projectId, String key) {
        return jpaFlagRepository.existsByProjectIdAndKey(projectId, key);
    }

    @Override
    public void deleteById(UUID id) {
        jpaFlagRepository.findById(id).ifPresent(jpaFlagRepository::delete);
    }

    private Flag toDomain(FlagEntity entity) {
        return new Flag(
                entity.getId(),
                entity.getProject().getOrganization().getId(),
                entity.getProject().getId(),
                entity.getKey(),
                entity.getName(),
                entity.getDescription(),
                entity.getValueType(),
                fromJson(entity.getDefaultValue()),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private String toJson(JsonNode node) {
        return node != null ? node.toString() : "false";
    }

    private JsonNode fromJson(String json) {
        if (json == null || json.isBlank()) {
            return tools.jackson.databind.node.BooleanNode.FALSE;
        }
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize JSON value from database: " + json, e);
        }
    }
}
