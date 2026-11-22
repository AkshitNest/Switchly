package live.switchly.api.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateMemberRoleRequest(
        @NotNull(message = "Role is required")
        live.switchly.api.model.Role role, java.util.UUID projectId
) {}

