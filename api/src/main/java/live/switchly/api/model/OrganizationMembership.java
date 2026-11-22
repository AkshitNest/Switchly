package live.switchly.api.model;

import java.time.Instant;
import java.util.UUID;

public class OrganizationMembership {

    private final UUID projectId;
    public UUID getProjectId() { return projectId; }
    private final UUID id;
    private final UUID organizationId;
    private final UUID userId;
    private final Role role;
    private final Instant createdAt;
    private final Instant updatedAt;

    public OrganizationMembership(UUID id, UUID organizationId, UUID userId, Role role) {
        this(id, organizationId, userId, role, null, null);
    }

    public OrganizationMembership(UUID id, UUID organizationId, UUID userId, Role role, Instant createdAt, Instant updatedAt) {
        this(id, organizationId, userId, role, createdAt, updatedAt, null);
    }

    public OrganizationMembership(UUID id, UUID organizationId, UUID userId, Role role, Instant createdAt, Instant updatedAt, UUID projectId) {
        this.projectId = projectId;
        this.id = id;
        this.organizationId = organizationId;
        this.userId = userId;
        this.role = role;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getUserId() { return userId; }
    public Role getRole() { return role; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

