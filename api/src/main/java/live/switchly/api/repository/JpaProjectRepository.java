package live.switchly.api.repository;

import live.switchly.api.entity.ProjectEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaProjectRepository extends JpaRepository<ProjectEntity, UUID> {

    @EntityGraph(attributePaths = {"organization"})
    Optional<ProjectEntity> findById(UUID id);

    @EntityGraph(attributePaths = {"organization"})
    List<ProjectEntity> findByOrganizationId(UUID organizationId);
}

