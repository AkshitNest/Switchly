package live.switchly.api.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import live.switchly.api.dto.AuthResponse;
import live.switchly.api.dto.LoginRequest;
import live.switchly.api.service.AuthService;
import live.switchly.api.service.OrganizationMembershipService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
public class InvitationController {
    private final OrganizationMembershipService members;
    private final AuthService auth;

    public InvitationController(OrganizationMembershipService members, AuthService auth) {
        this.members = members;
        this.auth = auth;
    }

    public record Accept(@NotBlank @Size(max=80) String token, @NotBlank @Email @Size(max=255) String email,
                         @NotBlank @Size(max=255) String fullName, @NotBlank @Size(min=8,max=100) String password) {}

    @PostMapping("/api/v1/auth/accept-invite")
    @Transactional
    public AuthResponse accept(@Valid @RequestBody Accept body) {
        var org = members.accept(body.token(), body.email(), body.fullName(), body.password());
        return auth.login(new LoginRequest(body.email(), body.password(), org));
    }
}
