package live.switchly.api.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtPropertiesTest {

    @Test
    @DisplayName("Blank or null secret fails startup")
    void blankSecretThrows() {
        JwtProperties props = new JwtProperties();
        props.setSecret("");
        assertThatThrownBy(props::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not be blank");

        props.setSecret(null);
        assertThatThrownBy(props::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    @DisplayName("Secret with less than 256 bits (32 bytes) fails startup")
    void shortSecretThrows() {
        JwtProperties props = new JwtProperties();
        props.setSecret("too-short-secret");
        assertThatThrownBy(props::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 256 bits");
    }

    @Test
    @DisplayName("Valid 64-character hexadecimal secret succeeds")
    void validHexSecretSucceeds() {
        JwtProperties props = new JwtProperties();
        props.setSecret("404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
        assertThatCode(props::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Valid raw UTF-8 secret with >= 32 bytes succeeds")
    void validUtf8SecretSucceeds() {
        JwtProperties props = new JwtProperties();
        props.setSecret("this-is-a-very-long-and-secure-raw-secret-key-that-exceeds-thirty-two-bytes");
        assertThatCode(props::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Default development secret in prod profile fails startup")
    void defaultDevSecretInProdFails() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        JwtProperties props = new JwtProperties(env);
        props.setSecret(JwtProperties.DEFAULT_DEV_SECRET);

        assertThatThrownBy(props::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Default insecure development JWT secret cannot be used in production profile");
    }

    @Test
    @DisplayName("Custom strong secret in prod profile succeeds")
    void strongSecretInProdSucceeds() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        JwtProperties props = new JwtProperties(env);
        props.setSecret("e9b3d0a1c2f4e5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0");

        assertThatCode(props::validate).doesNotThrowAnyException();
    }
}

