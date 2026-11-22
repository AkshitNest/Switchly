package live.switchly.api.repository;

import live.switchly.api.entity.FlagEnvironmentConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaFlagEnvironmentConfigRepository extends JpaRepository<FlagEnvironmentConfigEntity, UUID> {

    Optional<FlagEnvironmentConfigEntity> findByFlagIdAndEnvironmentId(UUID flagId, UUID environmentId);

    List<FlagEnvironmentConfigEntity> findByEnvironmentId(UUID environmentId);

    List<FlagEnvironmentConfigEntity> findByFlagId(UUID flagId);

    List<FlagEnvironmentConfigEntity> findByProjectId(UUID projectId);

    boolean existsByFlagIdAndEnvironmentId(UUID flagId, UUID environmentId);

    void deleteByFlagId(UUID flagId);
}

