package live.switchly.api.dto;

import tools.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import live.switchly.api.model.FlagValueType;

public record CreateFlagRequest(
        @NotBlank
        @Pattern(regexp = "[a-z0-9-]+", message = "use only lowercase letters, numbers and hyphens")
        String key,

        @NotBlank
        String name,

        String description,

        FlagValueType valueType,

        JsonNode defaultValue
) {
    public CreateFlagRequest(String key, String name) {
        this(key, name, null, FlagValueType.BOOLEAN, null);
    }

    public CreateFlagRequest(String key, String name, String description) {
        this(key, name, description, FlagValueType.BOOLEAN, null);
    }
}
