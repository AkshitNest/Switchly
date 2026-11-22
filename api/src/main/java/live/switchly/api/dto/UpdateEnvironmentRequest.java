package live.switchly.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateEnvironmentRequest(
        @NotBlank(message = "Environment name is required")
        @Size(max = 255, message = "Environment name must not exceed 255 characters")
        String name,

        String description
) {}

