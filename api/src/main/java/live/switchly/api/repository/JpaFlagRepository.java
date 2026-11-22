package live.switchly.api.repository;

import live.switchly.api.entity.FlagEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaFlagRepository extends JpaRepository<FlagEntity, UUID> {

    @EntityGraph(attributePaths = {"project", "project.organization"})
    Optional<FlagEntity> findById(UUID id);

    @EntityGraph(attributePaths = {"project", "project.organization"})
    List<FlagEntity> findByProjectId(UUID projectId);

    boolean existsByProjectIdAndKey(UUID projectId, String key);
}

