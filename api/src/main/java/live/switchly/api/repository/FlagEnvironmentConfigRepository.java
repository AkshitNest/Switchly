package live.switchly.api.repository;

import live.switchly.api.model.FlagEnvironmentConfig;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FlagEnvironmentConfigRepository {

    FlagEnvironmentConfig save(FlagEnvironmentConfig config);

    Optional<FlagEnvironmentConfig> findById(UUID id);

    Optional<FlagEnvironmentConfig> findByFlagIdAndEnvironmentId(UUID flagId, UUID environmentId);

    List<FlagEnvironmentConfig> findByEnvironmentId(UUID environmentId);

    List<FlagEnvironmentConfig> findByFlagId(UUID flagId);

    List<FlagEnvironmentConfig> findByProjectId(UUID projectId);

    boolean existsByFlagIdAndEnvironmentId(UUID flagId, UUID environmentId);

    void deleteById(UUID id);

    void deleteByFlagId(UUID flagId);
}

