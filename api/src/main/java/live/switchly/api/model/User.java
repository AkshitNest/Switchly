package live.switchly.api.model;

import java.time.Instant;
import java.util.UUID;

public class User {

    private final UUID id;
    private final String fullName;
    private final String email;
    private final String passwordHash;
    private final boolean enabled;
    private final boolean emailVerified;
    private final Instant createdAt;
    private final Instant updatedAt;

    public User(UUID id, String fullName, String email, String passwordHash, boolean enabled, boolean emailVerified) {
        this(id, fullName, email, passwordHash, enabled, emailVerified, null, null);
    }

    public User(UUID id, String fullName, String email, String passwordHash, boolean enabled, boolean emailVerified, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
        this.emailVerified = emailVerified;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isEnabled() { return enabled; }
    public boolean isEmailVerified() { return emailVerified; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

