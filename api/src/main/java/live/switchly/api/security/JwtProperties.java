package live.switchly.api.security;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * Configuration properties for JWT issuance and validation.
 *
 * Secret must be provided via SWITCHLY_JWT_SECRET environment variable (or switchly.jwt.secret)
 * as either a 64-character hexadecimal string (256 bits) or a raw UTF-8 string with at least 32 bytes.
 * Weak secrets and hardcoded defaults are rejected on startup.
 */
@Component
@ConfigurationProperties(prefix = "switchly.jwt")
public class JwtProperties {

    public static final String DEFAULT_DEV_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    private final Environment environment;

    private String secret;
    private long accessTokenExpirationMinutes = 15;
    private long refreshTokenExpirationDays = 7;

    public JwtProperties() {
        this(null);
    }

    public JwtProperties(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validate() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT secret must not be blank. Please set SWITCHLY_JWT_SECRET.");
        }

        byte[] keyBytes;
        if (secret.length() == 64 && secret.matches("^[0-9a-fA-F]+$")) {
            keyBytes = HexFormat.of().parseHex(secret);
        } else {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }

        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT secret must provide at least 256 bits (32 bytes) of entropy.");
        }

        if (environment != null && Arrays.asList(environment.getActiveProfiles()).contains("prod")) {
            if (DEFAULT_DEV_SECRET.equalsIgnoreCase(secret)) {
                throw new IllegalStateException("Default insecure development JWT secret cannot be used in production profile.");
            }
        }
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getAccessTokenExpirationMinutes() {
        return accessTokenExpirationMinutes;
    }

    public void setAccessTokenExpirationMinutes(long accessTokenExpirationMinutes) {
        this.accessTokenExpirationMinutes = accessTokenExpirationMinutes;
    }

    public long getRefreshTokenExpirationDays() {
        return refreshTokenExpirationDays;
    }

    public void setRefreshTokenExpirationDays(long refreshTokenExpirationDays) {
        this.refreshTokenExpirationDays = refreshTokenExpirationDays;
    }
}
