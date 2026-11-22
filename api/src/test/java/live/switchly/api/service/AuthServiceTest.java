package live.switchly.api.service;

import live.switchly.api.dto.AuthResponse;
import live.switchly.api.dto.LoginRequest;
import live.switchly.api.dto.LogoutRequest;
import live.switchly.api.dto.RefreshTokenRequest;
import live.switchly.api.dto.RegisterRequest;
import live.switchly.api.exception.ForbiddenException;
import live.switchly.api.exception.UnauthorizedException;
import live.switchly.api.model.Organization;
import live.switchly.api.model.OrganizationMembership;
import live.switchly.api.model.RefreshToken;
import live.switchly.api.model.User;
import live.switchly.api.repository.OrganizationMembershipRepository;
import live.switchly.api.repository.RefreshTokenRepository;
import live.switchly.api.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private OrganizationService organizationService;

    @Mock
    private OrganizationMembershipRepository membershipRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    @Test
    void register_success() {
        RegisterRequest request = new RegisterRequest("Alice", "alice@example.com", "Password123", "Acme");
        User user = new User(UUID.randomUUID(), "Alice", "alice@example.com", "hash", true, false);
        Organization org = new Organization(UUID.randomUUID(), "Acme");

        when(userService.create("Alice", "alice@example.com", "Password123")).thenReturn(user);
        when(organizationService.create("Acme")).thenReturn(org);
        when(jwtService.generateAccessToken(eq(user), any(UUID.class))).thenReturn("jwt.token.here");
        when(jwtService.generateRefreshTokenRaw()).thenReturn("raw.refresh.token");
        when(jwtService.hashToken("raw.refresh.token")).thenReturn("hashed.refresh.token");
        when(jwtService.calculateRefreshTokenExpiry()).thenReturn(Instant.now().plus(7, ChronoUnit.DAYS));
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);

        AuthResponse response = authService.register(request);

        assertThat(response.accessToken()).isEqualTo("jwt.token.here");
        assertThat(response.refreshToken()).isEqualTo("raw.refresh.token");
        assertThat(response.user().email()).isEqualTo("alice@example.com");
        verify(membershipRepository).save(any(OrganizationMembership.class));
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void login_success() {
        LoginRequest request = new LoginRequest("alice@example.com", "Password123");
        User user = new User(UUID.randomUUID(), "Alice", "alice@example.com", "hash", true, true);

        when(userService.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(membershipRepository.findByUserId(user.getId())).thenReturn(java.util.List.of(
                new OrganizationMembership(UUID.randomUUID(), UUID.randomUUID(), user.getId(), live.switchly.api.model.Role.ADMIN)));
        when(passwordEncoder.matches("Password123", "hash")).thenReturn(true);
        when(jwtService.generateAccessToken(eq(user), any(UUID.class))).thenReturn("jwt.token");
        when(jwtService.generateRefreshTokenRaw()).thenReturn("raw.refresh");
        when(jwtService.hashToken("raw.refresh")).thenReturn("hash.refresh");
        when(jwtService.calculateRefreshTokenExpiry()).thenReturn(Instant.now().plus(7, ChronoUnit.DAYS));
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);

        AuthResponse response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("jwt.token");
    }

    @Test
    void login_wrongPassword_throwsUnauthorized() {
        LoginRequest request = new LoginRequest("alice@example.com", "WrongPassword");
        User user = new User(UUID.randomUUID(), "Alice", "alice@example.com", "hash", true, true);

        when(userService.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword", "hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void login_disabledUser_throwsUnauthorized() {
        LoginRequest request = new LoginRequest("alice@example.com", "Password123");
        User user = new User(UUID.randomUUID(), "Alice", "alice@example.com", "hash", false, true);

        when(userService.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Account is disabled");
    }

    @Test
    void login_userNotFound_throwsUnauthorized() {
        LoginRequest request = new LoginRequest("missing@example.com", "Password123");
        when(userService.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void refresh_revokedToken_triggersFamilyRevocationAndThrowsUnauthorized() {
        RefreshTokenRequest request = new RefreshTokenRequest("revoked.raw");
        UUID tokenId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        RefreshToken token = new RefreshToken(tokenId, UUID.randomUUID(), familyId, "hash", Instant.now().plus(1, ChronoUnit.DAYS), true);

        when(jwtService.hashToken("revoked.raw")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHashForUpdate("hash")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Refresh token has been revoked");

        verify(refreshTokenRepository).revokeAllByFamilyId(familyId);
    }

    @Test
    void logout_revokesToken() {
        LogoutRequest request = new LogoutRequest("logout.token");
        UUID tokenId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        RefreshToken token = new RefreshToken(tokenId, userId, UUID.randomUUID(), "hash", Instant.now().plus(1, ChronoUnit.DAYS), false);

        when(jwtService.hashToken("logout.token")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(token));

        authService.logout(request, userId);

        verify(refreshTokenRepository).revoke(tokenId);
    }

    @Test
    void logout_crossUserSessionRevocation_throwsForbidden() {
        LogoutRequest request = new LogoutRequest("other.token");
        UUID tokenId = UUID.randomUUID();
        UUID victimUserId = UUID.randomUUID();
        UUID attackerUserId = UUID.randomUUID();
        RefreshToken token = new RefreshToken(tokenId, victimUserId, UUID.randomUUID(), "hash", Instant.now().plus(1, ChronoUnit.DAYS), false);

        when(jwtService.hashToken("other.token")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.logout(request, attackerUserId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Cannot revoke another user's session");
    }
}
