package live.switchly.api.repository;

import live.switchly.api.entity.RefreshTokenEntity;
import live.switchly.api.entity.UserEntity;
import live.switchly.api.exception.NotFoundException;
import live.switchly.api.model.RefreshToken;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@Profile("!in-memory")
@Transactional
public class RefreshTokenRepositoryAdapter implements RefreshTokenRepository {

    private final JpaRefreshTokenRepository jpaRefreshTokenRepo;
    private final JpaUserRepository jpaUserRepo;

    public RefreshTokenRepositoryAdapter(JpaRefreshTokenRepository jpaRefreshTokenRepo, JpaUserRepository jpaUserRepo) {
        this.jpaRefreshTokenRepo = jpaRefreshTokenRepo;
        this.jpaUserRepo = jpaUserRepo;
    }

    @Override
    public RefreshToken save(RefreshToken token) {
        RefreshTokenEntity entity = jpaRefreshTokenRepo.findById(token.getId())
                .orElseGet(() -> {
                    UserEntity user = jpaUserRepo.findById(token.getUserId())
                            .orElseThrow(() -> new NotFoundException("User not found: " + token.getUserId()));
                    return new RefreshTokenEntity(
                            token.getId(),
                            user,
                            token.getFamilyId(),
                            token.getTokenHash(),
                            token.getExpiresAt(),
                            token.isRevoked()
                    );
                });

        entity.setMembershipId(token.getMembershipId());
        entity.setRevoked(token.isRevoked());
        entity.setFamilyId(token.getFamilyId());
        RefreshTokenEntity saved = jpaRefreshTokenRepo.saveAndFlush(entity);
        return toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return jpaRefreshTokenRepo.findByTokenHash(tokenHash).map(this::toDomain);
    }

    @Override
    public Optional<RefreshToken> findByTokenHashForUpdate(String tokenHash) {
        return jpaRefreshTokenRepo.findByTokenHashForUpdate(tokenHash).map(this::toDomain);
    }

    @Override
    public void revoke(UUID id) {
        jpaRefreshTokenRepo.findById(id).ifPresent(entity -> {
            entity.setRevoked(true);
            jpaRefreshTokenRepo.saveAndFlush(entity);
        });
    }

    @Override
    public void revokeAllByFamilyId(UUID familyId) {
        jpaRefreshTokenRepo.revokeAllByFamilyId(familyId);
    }

    @Override
    @Transactional(readOnly = true)
    public long countActiveByFamilyId(UUID familyId, Instant now) {
        return jpaRefreshTokenRepo.countActiveByFamilyId(familyId, now);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefreshToken> findByFamilyId(UUID familyId) {
        return jpaRefreshTokenRepo.findByFamilyId(familyId).stream().map(this::toDomain).toList();
    }

    @Override
    public void deleteByUserId(UUID userId) {
        jpaRefreshTokenRepo.deleteByUserId(userId);
    }

    private RefreshToken toDomain(RefreshTokenEntity entity) {
        return new RefreshToken(
                entity.getId(),
                entity.getUser().getId(),
                entity.getFamilyId(),
                entity.getTokenHash(),
                entity.getExpiresAt(),
                entity.isRevoked(),
                entity.getCreatedAt(),
                entity.getMembershipId()
        );
    }
}
