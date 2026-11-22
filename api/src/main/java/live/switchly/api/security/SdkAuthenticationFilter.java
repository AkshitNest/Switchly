package live.switchly.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import live.switchly.api.repository.SdkKeyRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class SdkAuthenticationFilter extends OncePerRequestFilter {
    public static final RequestMatcher ENDPOINTS = new OrRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/v1/evaluate"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/v1/evaluate/batch"));
    private static final Pattern HEADER = Pattern.compile("SDK swk_[A-Za-z0-9_-]{43}");
    private final SdkKeyRepository keys;
    private final Http401AuthenticationEntryPoint unauthorized;

    public SdkAuthenticationFilter(SdkKeyRepository keys, Http401AuthenticationEntryPoint unauthorized) {
        this.keys = keys;
        this.unauthorized = unauthorized;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) { return !ENDPOINTS.matches(request); }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var headers = Collections.list(request.getHeaders(HttpHeaders.AUTHORIZATION));
        if (headers.size() != 1 || !HEADER.matcher(headers.getFirst()).matches()) {
            unauthorized.commence(request, response, null);
            return;
        }
        var principal = keys.authenticate(SdkSecrets.digest(headers.getFirst().substring(4)));
        if (principal.isEmpty()) {
            unauthorized.commence(request, response, null);
            return;
        }
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal.get(), null,
                List.of(new SimpleGrantedAuthority("EVALUATE"))));
        SecurityContextHolder.setContext(context);
        chain.doFilter(request, response);
    }
}
