package live.switchly.api.dto;

import tools.jackson.databind.JsonNode;
import live.switchly.api.model.FlagEnvironmentConfig;

import java.time.Instant;
import java.util.UUID;

public record FlagEnvironmentConfigResponse(
        UUID id,
        UUID flagId,
        UUID environmentId,
        UUID projectId,
        boolean enabled,
        JsonNode value,
        Instant createdAt,
        Instant updatedAt
) {

    public static FlagEnvironmentConfigResponse from(FlagEnvironmentConfig config) {
        return new FlagEnvironmentConfigResponse(
                config.getId(),
                config.getFlagId(),
                config.getEnvironmentId(),
                config.getProjectId(),
                config.isEnabled(),
                config.getValue(),
                config.getCreatedAt(),
                config.getUpdatedAt()
        );
    }
}

