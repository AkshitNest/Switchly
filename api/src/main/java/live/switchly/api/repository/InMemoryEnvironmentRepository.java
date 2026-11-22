package live.switchly.api.repository;

import live.switchly.api.model.Environment;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@Profile("in-memory")
public class InMemoryEnvironmentRepository implements EnvironmentRepository {

    private final Map<UUID, Environment> store = new ConcurrentHashMap<>();

    @Override
    public Environment save(Environment environment) {
        store.put(environment.getId(), environment);
        return environment;
    }

    @Override
    public Optional<Environment> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Environment> findByProjectId(UUID projectId) {
        return store.values().stream()
                .filter(env -> env.getProjectId().equals(projectId))
                .toList();
    }

    @Override
    public Optional<Environment> findByProjectIdAndKey(UUID projectId, String key) {
        return store.values().stream()
                .filter(env -> env.getProjectId().equals(projectId) && env.getKey().equals(key))
                .findFirst();
    }

    @Override
    public boolean existsByProjectIdAndKey(UUID projectId, String key) {
        return store.values().stream()
                .anyMatch(env -> env.getProjectId().equals(projectId) && env.getKey().equals(key));
    }

    @Override
    public void deleteById(UUID id) {
        store.remove(id);
    }
}

