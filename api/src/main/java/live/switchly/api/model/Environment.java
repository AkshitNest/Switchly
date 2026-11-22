package live.switchly.api.model;

import java.time.Instant;
import java.util.UUID;

public class Environment {

    private final UUID id;
    private final UUID projectId;
    private final String key;
    private String name;
    private String description;
    private final boolean isDefault;
    private final Instant createdAt;
    private final Instant updatedAt;

    public Environment(UUID id, UUID projectId, String key, String name, String description, boolean isDefault) {
        this(id, projectId, key, name, description, isDefault, null, null);
    }

    public Environment(UUID id, UUID projectId, String key, String name, String description, boolean isDefault, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.projectId = projectId;
        this.key = key;
        this.name = name;
        this.description = description;
        this.isDefault = isDefault;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

