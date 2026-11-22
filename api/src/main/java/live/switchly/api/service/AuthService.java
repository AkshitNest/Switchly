package live.switchly.api.service;

import live.switchly.api.dto.AuthResponse;
import live.switchly.api.dto.LoginRequest;
import live.switchly.api.dto.LogoutRequest;
import live.switchly.api.dto.RefreshTokenRequest;
import live.switchly.api.dto.RegisterRequest;
import live.switchly.api.dto.UserResponse;
import live.switchly.api.exception.ForbiddenException;
import live.switchly.api.exception.UnauthorizedException;
import live.switchly.api.exception.WorkspaceSelectionRequired;
import live.switchly.api.model.Organization;
import live.switchly.api.model.OrganizationMembership;
import live.switchly.api.model.RefreshToken;
import live.switchly.api.model.Role;
import live.switchly.api.model.User;
import live.switchly.api.repository.OrganizationMembershipRepository;
import live.switchly.api.repository.RefreshTokenRepository;
import live.switchly.api.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class AuthService {

    private final UserService userService;
    private final OrganizationService organizationService;
    private final OrganizationMembershipRepository membershipRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserService userService,
            OrganizationService organizationService,
            OrganizationMembershipRepository membershipRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userService = userService;
        this.organizationService = organizationService;
        this.membershipRepository = membershipRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse register(RegisterRequest request) {
        // 1. Create User
        User user = userService.create(request.fullName(), request.email(), request.password());

        // 2. Create Initial Organization
        String orgName = StringUtils.hasText(request.organizationName())
                ? request.organizationName().trim()
                : user.getFullName() + "'s Organization";
        Organization organization = organizationService.create(orgName);

        // 3. Assign admin membership
        OrganizationMembership membership = new OrganizationMembership(
                UUID.randomUUID(),
                organization.getId(),
                user.getId(),
                Role.ADMIN
        );
        membershipRepository.save(membership);

        // 4. Issue Tokens with a new token family
        return issueTokens(user, membership.getId(), UUID.randomUUID());
    }

    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = UserService.normalizeEmail(request.email());

        User user = userService.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!user.isEnabled()) {
            throw new UnauthorizedException("Account is disabled");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        var memberships = membershipRepository.findByUserId(user.getId());
        if (request.organizationId() == null && memberships.size() > 1) {
            throw new WorkspaceSelectionRequired(memberships.stream()
                    .map(m -> organizationService.getById(m.getOrganizationId()))
                    .map(o -> new WorkspaceSelectionRequired.Choice(o.getId(), o.getName())).toList());
        }
        var membership = memberships.stream()
                .filter(m -> request.organizationId() == null || m.getOrganizationId().equals(request.organizationId()))
                .findFirst().orElseThrow(() -> new UnauthorizedException("Invalid workspace or credentials"));
        return issueTokens(user, membership.getId(), UUID.randomUUID());
    }

    @Transactional(noRollbackFor = {UnauthorizedException.class})
    public AuthResponse refresh(RefreshTokenRequest request) {
        String tokenHash = jwtService.hashToken(request.refreshToken().trim());

        // Acquire pessimistic write lock on the token row to serialize concurrent refresh requests
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired refresh token"));

        if (refreshToken.isRevoked()) {
            // REUSE DETECTED: A previously rotated or revoked token was presented again!
            // Revoke all tokens belonging to this specific token family to neutralize stolen credentials,
            // while preserving unrelated device sessions for the user.
            refreshTokenRepository.revokeAllByFamilyId(refreshToken.getFamilyId());
            throw new UnauthorizedException("Refresh token has been revoked");
        }

        if (refreshToken.getExpiresAt().isBefore(Instant.now())) {
            throw new UnauthorizedException("Refresh token has expired");
        }

        User user = userService.getById(refreshToken.getUserId());
        if (!user.isEnabled()) {
            throw new UnauthorizedException("Account is disabled");
        }

        // Single-use rotation: Revoke current token
        refreshTokenRepository.revoke(refreshToken.getId());

        // Issue new token pair preserving the exact same family ID
        if (refreshToken.getMembershipId() == null || membershipRepository.findById(refreshToken.getMembershipId())
                .filter(m -> m.getUserId().equals(user.getId())).isEmpty()) {
            throw new UnauthorizedException("Organization membership has ended");
        }
        return issueTokens(user, refreshToken.getMembershipId(), refreshToken.getFamilyId());
    }

    public void logout(LogoutRequest request) {
        logout(request, null);
    }

    public void logout(LogoutRequest request, UUID requestingUserId) {
        if (!StringUtils.hasText(request.refreshToken())) {
            return;
        }

        String tokenHash = jwtService.hashToken(request.refreshToken().trim());
        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByTokenHash(tokenHash);
        if (tokenOpt.isEmpty()) {
            // Idempotent: non-existent token is a no-op
            return;
        }

        RefreshToken token = tokenOpt.get();
        if (requestingUserId != null && !token.getUserId().equals(requestingUserId)) {
            throw new ForbiddenException("Cannot revoke another user's session");
        }

        // Idempotent: revoking an already revoked token is a no-op
        if (!token.isRevoked()) {
            refreshTokenRepository.revoke(token.getId());
        }
    }

    private AuthResponse issueTokens(User user, UUID membershipId, UUID familyId) {
        String accessToken = jwtService.generateAccessToken(user, membershipId);
        String rawRefreshToken = jwtService.generateRefreshTokenRaw();
        String tokenHash = jwtService.hashToken(rawRefreshToken);
        Instant expiresAt = jwtService.calculateRefreshTokenExpiry();

        RefreshToken refreshToken = new RefreshToken(
                UUID.randomUUID(),
                user.getId(),
                familyId != null ? familyId : UUID.randomUUID(),
                tokenHash,
                expiresAt,
                false, null, membershipId
        );
        refreshTokenRepository.save(refreshToken);

        return new AuthResponse(
                accessToken,
                rawRefreshToken,
                "Bearer",
                jwtService.getAccessTokenExpirationSeconds(),
                UserResponse.from(user)
        );
    }
}
