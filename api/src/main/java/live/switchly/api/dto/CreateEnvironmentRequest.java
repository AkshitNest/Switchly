package live.switchly.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateEnvironmentRequest(
        @NotBlank(message = "Environment key is required")
        @Pattern(regexp = "[a-z0-9-]+", message = "use only lowercase letters, numbers and hyphens")
        @Size(max = 255, message = "Environment key must not exceed 255 characters")
        String key,

        @NotBlank(message = "Environment name is required")
        @Size(max = 255, message = "Environment name must not exceed 255 characters")
        String name,

        String description
) {
    public CreateEnvironmentRequest(String key, String name) {
        this(key, name, null);
    }
}

