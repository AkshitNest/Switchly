package live.switchly.api.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public final class SdkSecrets {
    private static final SecureRandom RANDOM = new SecureRandom();
    private SdkSecrets() {}

    public static String generate() {
        byte[] entropy = new byte[32];
        RANDOM.nextBytes(entropy);
        return "swk_" + Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
    }

    public static byte[] digest(String key) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
