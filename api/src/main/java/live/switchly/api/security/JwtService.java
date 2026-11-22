package live.switchly.api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import live.switchly.api.model.User;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtService {

    private final JwtProperties jwtProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    private SecretKey getSigningKey() {
        String secret = jwtProperties.getSecret();
        byte[] keyBytes;
        if (secret.length() == 64 && secret.matches("^[0-9a-fA-F]+$")) {
            keyBytes = HexFormat.of().parseHex(secret);
        } else {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }

        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT signing key must be at least 256 bits (32 bytes)");
        }

        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(User user) {
        return generateAccessToken(user, null, jwtProperties.getAccessTokenExpirationMinutes(), ChronoUnit.MINUTES);
    }

    public String generateAccessToken(User user, long duration, ChronoUnit unit) {
        return generateAccessToken(user, null, duration, unit);
    }

    public String generateAccessToken(User user, UUID membershipId) {
        return generateAccessToken(user, membershipId, jwtProperties.getAccessTokenExpirationMinutes(), ChronoUnit.MINUTES);
    }

    private String generateAccessToken(User user, UUID membershipId, long duration, ChronoUnit unit) {
        Instant now = Instant.now();
        Instant expiry = now.plus(duration, unit);

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("membership", membershipId == null ? null : membershipId.toString())
                .claim("email", user.getEmail())
                .claim("fullName", user.getFullName())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(getSigningKey())
                .compact();
    }

    public Optional<Claims> validateAndParseClaims(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public Optional<UUID> extractUserId(String token) {
        return validateAndParseClaims(token)
                .map(Claims::getSubject)
                .flatMap(subject -> {
                    try {
                        return Optional.of(UUID.fromString(subject));
                    } catch (IllegalArgumentException e) {
                        return Optional.empty();
                    }
                });
    }

    public String generateRefreshTokenRaw() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    public Instant calculateRefreshTokenExpiry() {
        return Instant.now().plus(jwtProperties.getRefreshTokenExpirationDays(), ChronoUnit.DAYS);
    }

    public long getAccessTokenExpirationSeconds() {
        return jwtProperties.getAccessTokenExpirationMinutes() * 60;
    }
}

