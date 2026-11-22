package live.switchly.api.model;

import java.time.Instant;
import java.util.UUID;

public class RefreshToken {

    private final UUID membershipId;
    public UUID getMembershipId() { return membershipId; }
    private final UUID id;
    private final UUID userId;
    private final UUID familyId;
    private final String tokenHash;
    private final Instant expiresAt;
    private final boolean revoked;
    private final Instant createdAt;

    public RefreshToken(UUID id, UUID userId, String tokenHash, Instant expiresAt, boolean revoked) {
        this(id, userId, UUID.randomUUID(), tokenHash, expiresAt, revoked, null);
    }

    public RefreshToken(UUID id, UUID userId, UUID familyId, String tokenHash, Instant expiresAt, boolean revoked) {
        this(id, userId, familyId, tokenHash, expiresAt, revoked, null);
    }

    public RefreshToken(UUID id, UUID userId, UUID familyId, String tokenHash, Instant expiresAt, boolean revoked, Instant createdAt) {
        this(id, userId, familyId, tokenHash, expiresAt, revoked, createdAt, null);
    }

    public RefreshToken(UUID id, UUID userId, UUID familyId, String tokenHash, Instant expiresAt, boolean revoked, Instant createdAt, UUID membershipId) {
        this.membershipId = membershipId;
        this.id = id;
        this.userId = userId;
        this.familyId = familyId != null ? familyId : UUID.randomUUID();
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.revoked = revoked;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getFamilyId() { return familyId; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isRevoked() { return revoked; }
    public Instant getCreatedAt() { return createdAt; }
}
