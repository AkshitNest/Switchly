package live.switchly.api.dto;

import live.switchly.api.model.Environment;

import java.time.Instant;
import java.util.UUID;

public record EnvironmentResponse(
        UUID id,
        UUID projectId,
        String key,
        String name,
        String description,
        boolean isDefault,
        Instant createdAt,
        Instant updatedAt
) {
    public static EnvironmentResponse from(Environment environment) {
        return new EnvironmentResponse(
                environment.getId(),
                environment.getProjectId(),
                environment.getKey(),
                environment.getName(),
                environment.getDescription(),
                environment.isDefault(),
                environment.getCreatedAt(),
                environment.getUpdatedAt()
        );
    }
}

