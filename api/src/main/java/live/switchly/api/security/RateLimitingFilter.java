package live.switchly.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimiterService rateLimiterService;

    public RateLimitingFilter(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String uri = request.getRequestURI();
        String method = request.getMethod();

        // Enforce Cache-Control: no-store on sensitive auth endpoints
        if (uri.startsWith("/api/v1/auth")) {
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0");
            response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        }

        String endpointKey = null;
        if ("POST".equalsIgnoreCase(method)) {
            if (SdkAuthenticationFilter.ENDPOINTS.matches(request)) {
                endpointKey = "EVALUATE";
            } else if (uri.equals("/api/v1/auth/login") || uri.equals("/api/v1/auth/accept-invite")) {
                endpointKey = "LOGIN";
            } else if (uri.equals("/api/v1/auth/register")) {
                endpointKey = "REGISTER";
            } else if (uri.equals("/api/v1/auth/refresh")) {
                endpointKey = "REFRESH";
            }
        }

        if (endpointKey != null) {
            String clientIp = rateLimiterService.resolveClientIp(request);
            RateLimiterService.RateLimitResult result = rateLimiterService.tryAcquire(endpointKey, clientIp);

            if (!result.allowed()) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(result.retryAfterSeconds()));
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);

                String json = "{\"error\":{\"code\":\"RATE_LIMIT_EXCEEDED\",\"message\":\"Rate limit exceeded. Try again in "
                        + result.retryAfterSeconds() + " seconds.\"}}";

                response.getWriter().write(json);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
