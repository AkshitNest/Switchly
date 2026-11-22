package live.switchly.api.model;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class FlagEnvironmentConfig {

    private final UUID id;
    private final UUID flagId;
    private final UUID environmentId;
    private final UUID projectId;
    private boolean enabled;
    private JsonNode value;
    private final Instant createdAt;
    private final Instant updatedAt;

    public FlagEnvironmentConfig(UUID id, UUID flagId, UUID environmentId, UUID projectId, boolean enabled, JsonNode value) {
        this(id, flagId, environmentId, projectId, enabled, value, null, null);
    }

    public FlagEnvironmentConfig(UUID id, UUID flagId, UUID environmentId, UUID projectId, boolean enabled,
                                 JsonNode value, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.flagId = Objects.requireNonNull(flagId, "flagId must not be null");
        this.environmentId = Objects.requireNonNull(environmentId, "environmentId must not be null");
        this.projectId = Objects.requireNonNull(projectId, "projectId must not be null");
        this.enabled = enabled;
        this.value = Objects.requireNonNull(value, "value must not be null");
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getFlagId() {
        return flagId;
    }

    public UUID getEnvironmentId() {
        return environmentId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public JsonNode getValue() {
        return value;
    }

    public void setValue(JsonNode value) {
        this.value = Objects.requireNonNull(value, "value must not be null");
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

