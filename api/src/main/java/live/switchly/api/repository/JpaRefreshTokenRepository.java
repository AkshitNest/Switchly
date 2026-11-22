package live.switchly.api.repository;

import jakarta.persistence.LockModeType;
import live.switchly.api.entity.RefreshTokenEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaRefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    @EntityGraph(attributePaths = {"user"})
    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"user"})
    @Query("SELECT r FROM RefreshTokenEntity r WHERE r.tokenHash = :tokenHash")
    Optional<RefreshTokenEntity> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshTokenEntity r SET r.revoked = true WHERE r.familyId = :familyId")
    void revokeAllByFamilyId(@Param("familyId") UUID familyId);

    @Query("SELECT COUNT(r) FROM RefreshTokenEntity r WHERE r.familyId = :familyId AND r.revoked = false AND r.expiresAt > :now")
    long countActiveByFamilyId(@Param("familyId") UUID familyId, @Param("now") Instant now);

    List<RefreshTokenEntity> findByFamilyId(UUID familyId);

    void deleteByUserId(UUID userId);
}
