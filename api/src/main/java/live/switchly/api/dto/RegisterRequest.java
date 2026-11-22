package live.switchly.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Full name is required")
        @Size(max = 255, message = "Full name must not exceed 255 characters")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
        String password,

        @Size(max = 255, message = "Organization name must not exceed 255 characters")
        String organizationName
) {
    public RegisterRequest {
        if (fullName != null) fullName = fullName.trim();
        if (email != null) email = email.trim();
        if (organizationName != null) organizationName = organizationName.trim();
    }
}
