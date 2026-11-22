package live.switchly.api.repository;

import live.switchly.api.entity.EnvironmentEntity;
import live.switchly.api.entity.FlagEntity;
import live.switchly.api.entity.FlagEnvironmentConfigEntity;
import live.switchly.api.entity.ProjectEntity;
import live.switchly.api.exception.ConflictException;
import live.switchly.api.exception.NotFoundException;
import live.switchly.api.model.FlagEnvironmentConfig;
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
public class FlagEnvironmentConfigRepositoryAdapter implements FlagEnvironmentConfigRepository {

    private static final JsonMapper MAPPER = FlagValueValidator.getMapper();

    private final JpaFlagEnvironmentConfigRepository jpaConfigRepository;
    private final JpaFlagRepository jpaFlagRepository;
    private final JpaEnvironmentRepository jpaEnvironmentRepository;
    private final JpaProjectRepository jpaProjectRepository;

    public FlagEnvironmentConfigRepositoryAdapter(
            JpaFlagEnvironmentConfigRepository jpaConfigRepository,
            JpaFlagRepository jpaFlagRepository,
            JpaEnvironmentRepository jpaEnvironmentRepository,
            JpaProjectRepository jpaProjectRepository
    ) {
        this.jpaConfigRepository = jpaConfigRepository;
        this.jpaFlagRepository = jpaFlagRepository;
        this.jpaEnvironmentRepository = jpaEnvironmentRepository;
        this.jpaProjectRepository = jpaProjectRepository;
    }

    @Override
    public FlagEnvironmentConfig save(FlagEnvironmentConfig config) {
        try {
            Optional<FlagEnvironmentConfigEntity> existing = jpaConfigRepository.findById(config.getId());
            FlagEnvironmentConfigEntity entity;
            if (existing.isPresent()) {
                entity = existing.get();
                entity.setEnabled(config.isEnabled());
                entity.setValue(toJson(config.getValue()));
            } else {
                FlagEntity flag = jpaFlagRepository.findById(config.getFlagId())
                        .orElseThrow(() -> new NotFoundException("Flag " + config.getFlagId() + " not found"));
                EnvironmentEntity env = jpaEnvironmentRepository.findById(config.getEnvironmentId())
                        .orElseThrow(() -> new NotFoundException("Environment " + config.getEnvironmentId() + " not found"));
                ProjectEntity project = jpaProjectRepository.findById(config.getProjectId())
                        .orElseThrow(() -> new NotFoundException("Project " + config.getProjectId() + " not found"));

                entity = new FlagEnvironmentConfigEntity(
                        config.getId(),
                        flag,
                        env,
                        project,
                        config.isEnabled(),
                        toJson(config.getValue())
                );
            }
            FlagEnvironmentConfigEntity saved = jpaConfigRepository.saveAndFlush(entity);
            return toDomain(saved);
        } catch (DataIntegrityViolationException ex) {
            String message = ex.getMessage();
            if (message != null && (message.contains("uq_flag_env_configs_flag_env") || message.contains("duplicate key"))) {
                throw new ConflictException("A configuration for this flag and environment already exists");
            }
            if (message != null && (message.contains("fk_flag_env_configs_flag") || message.contains("fk_flag_env_configs_env"))) {
                throw new ConflictException("Flag and environment must belong to the same project");
            }
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FlagEnvironmentConfig> findById(UUID id) {
        return jpaConfigRepository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FlagEnvironmentConfig> findByFlagIdAndEnvironmentId(UUID flagId, UUID environmentId) {
        return jpaConfigRepository.findByFlagIdAndEnvironmentId(flagId, environmentId).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FlagEnvironmentConfig> findByEnvironmentId(UUID environmentId) {
        return jpaConfigRepository.findByEnvironmentId(environmentId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FlagEnvironmentConfig> findByFlagId(UUID flagId) {
        return jpaConfigRepository.findByFlagId(flagId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FlagEnvironmentConfig> findByProjectId(UUID projectId) {
        return jpaConfigRepository.findByProjectId(projectId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByFlagIdAndEnvironmentId(UUID flagId, UUID environmentId) {
        return jpaConfigRepository.existsByFlagIdAndEnvironmentId(flagId, environmentId);
    }

    @Override
    public void deleteById(UUID id) {
        jpaConfigRepository.findById(id).ifPresent(jpaConfigRepository::delete);
    }

    @Override
    public void deleteByFlagId(UUID flagId) {
        jpaConfigRepository.deleteByFlagId(flagId);
    }

    private FlagEnvironmentConfig toDomain(FlagEnvironmentConfigEntity entity) {
        return new FlagEnvironmentConfig(
                entity.getId(),
                entity.getFlag().getId(),
                entity.getEnvironment().getId(),
                entity.getProject().getId(),
                entity.isEnabled(),
                fromJson(entity.getValue()),
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
