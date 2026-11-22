package live.switchly.api.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Request matcher for HTTP Strict Transport Security (HSTS).
 *
 * Enforces RFC 6797:
 * - Emits HSTS on direct HTTPS connections (request.isSecure() == true).
 * - When behind a reverse proxy terminating TLS, honors X-Forwarded-Proto ONLY
 *   if the immediate client IP (remoteAddr) matches a configured trusted proxy.
 * - Never emits HSTS for plain HTTP.
 * - Never blindly trusts arbitrary client-supplied X-Forwarded-Proto headers.
 */
public class HstsRequestMatcher implements RequestMatcher {

    private final RateLimitProperties rateLimitProperties;

    public HstsRequestMatcher(RateLimitProperties rateLimitProperties) {
        this.rateLimitProperties = rateLimitProperties;
    }

    @Override
    public boolean matches(HttpServletRequest request) {
        // 1. Direct HTTPS connection
        if (request.isSecure()) {
            return true;
        }

        // 2. Behind reverse proxy: only trust X-Forwarded-Proto if the direct remote address is a configured trusted proxy
        String remoteAddr = request.getRemoteAddr();
        List<String> trusted = rateLimitProperties != null ? rateLimitProperties.getTrustedProxies() : null;
        if (trusted != null && !trusted.isEmpty() && remoteAddr != null && trusted.contains(remoteAddr)) {
            String protoHeader = request.getHeader("X-Forwarded-Proto");
            if (StringUtils.hasText(protoHeader)) {
                String proto = protoHeader.split(",")[0].trim();
                return "https".equalsIgnoreCase(proto);
            }
        }

        return false;
    }
}

