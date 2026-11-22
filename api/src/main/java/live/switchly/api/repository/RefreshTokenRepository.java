package live.switchly.api.repository;

import live.switchly.api.model.RefreshToken;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {
    RefreshToken save(RefreshToken token);
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    Optional<RefreshToken> findByTokenHashForUpdate(String tokenHash);
    void revoke(UUID id);
    void revokeAllByFamilyId(UUID familyId);
    long countActiveByFamilyId(UUID familyId, Instant now);
    List<RefreshToken> findByFamilyId(UUID familyId);
    void deleteByUserId(UUID userId);
}
