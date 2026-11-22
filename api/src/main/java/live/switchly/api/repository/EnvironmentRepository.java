package live.switchly.api.repository;

import live.switchly.api.model.Environment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnvironmentRepository {
    Environment save(Environment environment);
    Optional<Environment> findById(UUID id);
    List<Environment> findByProjectId(UUID projectId);
    Optional<Environment> findByProjectIdAndKey(UUID projectId, String key);
    boolean existsByProjectIdAndKey(UUID projectId, String key);
    void deleteById(UUID id);
}

