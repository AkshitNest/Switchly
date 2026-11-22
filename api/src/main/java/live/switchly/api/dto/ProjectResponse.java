package live.switchly.api.dto;

import live.switchly.api.model.Project;

import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        UUID organizationId,
        String name,
        String key,
        Instant createdAt,
        Instant updatedAt
) {

    public static ProjectResponse from(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getOrganizationId(),
                project.getName(),
                project.getKey(),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }
}
