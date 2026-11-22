package live.switchly.api.dto;

import tools.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateFlagRequest(
        @NotBlank
        @Size(max = 255)
        String name,

        @Size(max = 1000)
        String description,

        JsonNode defaultValue
) {}

