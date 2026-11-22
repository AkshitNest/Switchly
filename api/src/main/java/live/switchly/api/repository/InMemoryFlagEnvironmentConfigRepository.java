package live.switchly.api.repository;

import live.switchly.api.model.FlagEnvironmentConfig;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@Profile("in-memory")
public class InMemoryFlagEnvironmentConfigRepository implements FlagEnvironmentConfigRepository {

    private final Map<UUID, FlagEnvironmentConfig> store = new ConcurrentHashMap<>();

    @Override
    public FlagEnvironmentConfig save(FlagEnvironmentConfig config) {
        store.put(config.getId(), config);
        return config;
    }

    @Override
    public Optional<FlagEnvironmentConfig> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<FlagEnvironmentConfig> findByFlagIdAndEnvironmentId(UUID flagId, UUID environmentId) {
        return store.values().stream()
                .filter(c -> c.getFlagId().equals(flagId) && c.getEnvironmentId().equals(environmentId))
                .findFirst();
    }

    @Override
    public List<FlagEnvironmentConfig> findByEnvironmentId(UUID environmentId) {
        return store.values().stream()
                .filter(c -> c.getEnvironmentId().equals(environmentId))
                .toList();
    }

    @Override
    public List<FlagEnvironmentConfig> findByFlagId(UUID flagId) {
        return store.values().stream()
                .filter(c -> c.getFlagId().equals(flagId))
                .toList();
    }

    @Override
    public List<FlagEnvironmentConfig> findByProjectId(UUID projectId) {
        return store.values().stream()
                .filter(c -> c.getProjectId().equals(projectId))
                .toList();
    }

    @Override
    public boolean existsByFlagIdAndEnvironmentId(UUID flagId, UUID environmentId) {
        return store.values().stream()
                .anyMatch(c -> c.getFlagId().equals(flagId) && c.getEnvironmentId().equals(environmentId));
    }

    @Override
    public void deleteById(UUID id) {
        store.remove(id);
    }

    @Override
    public void deleteByFlagId(UUID flagId) {
        store.values().removeIf(c -> c.getFlagId().equals(flagId));
    }
}

