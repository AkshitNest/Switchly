package live.switchly.api.dto;

import tools.jackson.databind.JsonNode;
import live.switchly.api.model.Flag;
import live.switchly.api.model.FlagValueType;

import java.time.Instant;
import java.util.UUID;

public record FlagResponse(
        UUID id,
        UUID organizationId,
        UUID projectId,
        String key,
        String name,
        String description,
        FlagValueType valueType,
        JsonNode defaultValue,
        Instant createdAt,
        Instant updatedAt
) {

    public static FlagResponse from(Flag flag) {
        return new FlagResponse(
                flag.getId(),
                flag.getOrganizationId(),
                flag.getProjectId(),
                flag.getKey(),
                flag.getName(),
                flag.getDescription(),
                flag.getValueType(),
                flag.getDefaultValue(),
                flag.getCreatedAt(),
                flag.getUpdatedAt()
        );
    }
}
