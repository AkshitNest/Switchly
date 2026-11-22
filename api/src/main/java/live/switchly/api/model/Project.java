package live.switchly.api.model;

import java.time.Instant;
import java.util.UUID;

public class Project {

    private final UUID id;
    private final UUID organizationId;
    private final String name;
    private final String key;
    private final Instant createdAt;
    private final Instant updatedAt;

    public Project(UUID id, UUID organizationId, String name) {
        this(id, organizationId, name, null, null);
    }

    public Project(UUID id, UUID organizationId, String name, Instant createdAt, Instant updatedAt) {
        this(id, organizationId, name, null, createdAt, updatedAt);
    }

    public Project(UUID id, UUID organizationId, String name, String key, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.name = name;
        this.key = key == null ? "p-" + UUID.randomUUID() : key;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public String getName() { return name; }
    public String getKey() { return key; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
