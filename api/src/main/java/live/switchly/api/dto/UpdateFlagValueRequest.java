package live.switchly.api.dto;

import tools.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

public record UpdateFlagValueRequest(
        @NotNull(message = "value must not be null")
        JsonNode value
) {}

