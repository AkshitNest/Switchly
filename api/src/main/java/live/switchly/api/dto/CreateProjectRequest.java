package live.switchly.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateProjectRequest(@NotBlank String name,
        @jakarta.validation.constraints.Pattern(regexp = "[a-z0-9][a-z0-9-]{0,254}") String key) {
    public CreateProjectRequest(String name) { this(name, null); }
}
