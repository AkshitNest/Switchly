package live.switchly.api.controller;

import jakarta.validation.Valid;
import live.switchly.api.dto.AuthResponse;
import live.switchly.api.dto.LoginRequest;
import live.switchly.api.dto.LogoutRequest;
import live.switchly.api.dto.RefreshTokenRequest;
import live.switchly.api.dto.RegisterRequest;
import live.switchly.api.dto.UpdateProfileRequest;
import live.switchly.api.dto.UserResponse;
import live.switchly.api.exception.UnauthorizedException;
import live.switchly.api.security.UserPrincipal;
import live.switchly.api.service.AuthService;
import live.switchly.api.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody LogoutRequest request
    ) {
        if (principal == null) {
            throw new UnauthorizedException("Full authentication is required to access this resource");
        }
        authService.logout(request, principal.id());
    }

    @GetMapping("/me")
    public UserResponse getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("Full authentication is required to access this resource");
        }
        return UserResponse.from(userService.getById(principal.id()));
    }

    @PutMapping("/me")
    public UserResponse updateProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        if (principal == null) {
            throw new UnauthorizedException("Full authentication is required to access this resource");
        }
        return UserResponse.from(userService.updateProfile(principal.id(), request.fullName()));
    }
}
