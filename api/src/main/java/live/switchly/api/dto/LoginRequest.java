package live.switchly.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        String email,

        @NotBlank(message = "Password is required")
        String password,
        java.util.UUID organizationId
) {
    public LoginRequest(String email, String password) { this(email, password, null); }

    public LoginRequest {
        if (email != null) email = email.trim();
    }
}
