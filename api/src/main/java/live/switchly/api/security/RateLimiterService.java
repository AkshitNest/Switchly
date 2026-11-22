package live.switchly.api.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimiterService {

    private final RateLimitProperties properties;
    private final Map<String, Deque<Long>> requestWindows = new ConcurrentHashMap<>();
    private final Object cleanupLock = new Object();
    private long lastCleanupTime = System.currentTimeMillis();

    public RateLimiterService(RateLimitProperties properties) {
        this.properties = properties;
    }

    public record RateLimitResult(boolean allowed, long retryAfterSeconds) {}

    public String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        List<String> trusted = properties.getTrustedProxies();
        if (trusted != null && !trusted.isEmpty() && remoteAddr != null && trusted.contains(remoteAddr)) {
            String xff = request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(xff)) {
                String[] parts = xff.split(",");
                for (String part : parts) {
                    String candidate = part.trim();
                    if (!candidate.isEmpty()) {
                        return candidate;
                    }
                }
            }
        }
        return remoteAddr != null ? remoteAddr : "unknown";
    }

    public RateLimitResult tryAcquire(String endpoint, String clientIp) {
        if (!properties.isEnabled()) {
            return new RateLimitResult(true, 0);
        }

        int limit;
        int windowSeconds;

        switch (endpoint) {
            case "EVALUATE" -> {
                limit = properties.getEvaluationLimit();
                windowSeconds = 60;
            }
            case "LOGIN" -> {
                limit = properties.getLoginLimit();
                windowSeconds = properties.getLoginWindowSeconds();
            }
            case "REGISTER" -> {
                limit = properties.getRegisterLimit();
                windowSeconds = properties.getRegisterWindowSeconds();
            }
            case "REFRESH" -> {
                limit = properties.getRefreshLimit();
                windowSeconds = properties.getRefreshWindowSeconds();
            }
            default -> {
                return new RateLimitResult(true, 0);
            }
        }

        long now = System.currentTimeMillis();
        long windowMillis = windowSeconds * 1000L;
        long windowStart = now - windowMillis;
        String key = endpoint + ":" + clientIp;

        maybeCleanup(now);

        Deque<Long> timestamps = requestWindows.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() <= windowStart) {
                timestamps.pollFirst();
            }

            if (timestamps.size() >= limit) {
                long oldest = timestamps.peekFirst();
                long retryAfter = Math.max(1, ((oldest + windowMillis) - now + 999) / 1000);
                return new RateLimitResult(false, retryAfter);
            }

            timestamps.addLast(now);
            return new RateLimitResult(true, 0);
        }
    }

    private void maybeCleanup(long now) {
        if (now - lastCleanupTime > 60_000L || requestWindows.size() > 5000) {
            synchronized (cleanupLock) {
                if (now - lastCleanupTime > 60_000L || requestWindows.size() > 5000) {
                    lastCleanupTime = now;
                    List<String> toRemove = new ArrayList<>();
                    for (Map.Entry<String, Deque<Long>> entry : requestWindows.entrySet()) {
                        Deque<Long> deque = entry.getValue();
                        synchronized (deque) {
                            while (!deque.isEmpty() && deque.peekFirst() <= now - 120_000L) {
                                deque.pollFirst();
                            }
                            if (deque.isEmpty()) {
                                toRemove.add(entry.getKey());
                            }
                        }
                    }
                    toRemove.forEach(requestWindows::remove);
                }
            }
        }
    }

    public void reset() {
        requestWindows.clear();
    }
}

