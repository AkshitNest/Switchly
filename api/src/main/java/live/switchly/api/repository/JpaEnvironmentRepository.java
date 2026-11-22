package live.switchly.api.repository;

import live.switchly.api.entity.EnvironmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaEnvironmentRepository extends JpaRepository<EnvironmentEntity, UUID> {
    List<EnvironmentEntity> findByProjectId(UUID projectId);
    Optional<EnvironmentEntity> findByProjectIdAndKey(UUID projectId, String key);
    boolean existsByProjectIdAndKey(UUID projectId, String key);
}

