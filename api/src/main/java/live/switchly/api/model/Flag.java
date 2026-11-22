package live.switchly.api.model;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class Flag {

    private final UUID id;
    private final UUID organizationId;
    private final UUID projectId;
    private final String key;
    private String name;
    private String description;
    private final FlagValueType valueType;
    private JsonNode defaultValue;
    private final Instant createdAt;
    private final Instant updatedAt;

    public Flag(UUID id, UUID organizationId, UUID projectId, String key, String name) {
        this(id, organizationId, projectId, key, name, null, FlagValueType.BOOLEAN, null, null, null);
    }

    public Flag(UUID id, UUID organizationId, UUID projectId, String key, String name, String description) {
        this(id, organizationId, projectId, key, name, description, FlagValueType.BOOLEAN, null, null, null);
    }

    public Flag(UUID id, UUID organizationId, UUID projectId, String key, String name, String description,
                FlagValueType valueType, JsonNode defaultValue) {
        this(id, organizationId, projectId, key, name, description, valueType, defaultValue, null, null);
    }

    public Flag(UUID id, UUID organizationId, UUID projectId, String key, String name, String description,
                FlagValueType valueType, JsonNode defaultValue, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.organizationId = organizationId;
        this.projectId = Objects.requireNonNull(projectId, "projectId must not be null");
        this.key = Objects.requireNonNull(key, "key must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.description = description;
        this.valueType = valueType != null ? valueType : FlagValueType.BOOLEAN;

        JsonNode resolvedDefault = defaultValue != null ? defaultValue : FlagValueValidator.defaultFor(this.valueType);
        FlagValueValidator.validate(this.valueType, resolvedDefault);
        this.defaultValue = resolvedDefault;

        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
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

    public FlagValueType getValueType() {
        return valueType;
    }

    public JsonNode getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(JsonNode defaultValue) {
        JsonNode resolved = defaultValue != null ? defaultValue : FlagValueValidator.defaultFor(this.valueType);
        FlagValueValidator.validate(this.valueType, resolved);
        this.defaultValue = resolved;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
