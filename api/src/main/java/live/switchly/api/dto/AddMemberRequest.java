package live.switchly.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddMemberRequest(
        @NotBlank(message = "User email is required")
        @jakarta.validation.constraints.Email @jakarta.validation.constraints.Size(max=255) String email,

        @NotNull(message = "Role is required")
        live.switchly.api.model.Role role, java.util.UUID projectId
) {}

