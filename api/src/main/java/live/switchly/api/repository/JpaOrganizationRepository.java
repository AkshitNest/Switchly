package live.switchly.api.repository;

import jakarta.persistence.LockModeType;
import live.switchly.api.entity.OrganizationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface JpaOrganizationRepository extends JpaRepository<OrganizationEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OrganizationEntity o WHERE o.id = :id")
    Optional<OrganizationEntity> findByIdForUpdate(@Param("id") UUID id);
}


