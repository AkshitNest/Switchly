package live.switchly.api.evaluation;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public final class PercentageRollout {
    private PercentageRollout() {}

    static int bucket(UUID flag, UUID environment, String userId) {
        byte[] input = (flag + ":" + environment + ":" + userId).getBytes(StandardCharsets.UTF_8);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(input);
            return (int) (Integer.toUnsignedLong(ByteBuffer.wrap(hash).getInt()) % 10_000);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    static EvaluationReason decide(UUID flag, UUID environment, String userId, int percentage) {
        if (percentage < 0 || percentage > 100) throw new IllegalStateException("Invalid stored rollout percentage");
        if (userId == null) return EvaluationReason.ROLLOUT_MISSING_IDENTITY;
        return bucket(flag, environment, userId) < percentage * 100
                ? EvaluationReason.ROLLOUT_INCLUDED : EvaluationReason.ROLLOUT_EXCLUDED;
    }
}
