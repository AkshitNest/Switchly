
package live.switchly.api.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import live.switchly.api.model.User;
import live.switchly.api.repository.UserRepository;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    private final live.switchly.api.repository.OrganizationMembershipRepository memberships;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository,
                                  live.switchly.api.repository.OrganizationMembershipRepository memberships) {
        this.memberships = memberships;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            Optional<Claims> claimsOpt = jwtService.validateAndParseClaims(token);

            if (claimsOpt.isPresent()) {
                Claims claims = claimsOpt.get();
                try {
                    UUID userId = UUID.fromString(claims.getSubject());
                    Optional<User> userOpt = userRepository.findById(userId);

                    var membership = memberships.findById(UUID.fromString(String.valueOf(claims.get("membership"))))
                            .filter(m -> m.getUserId().equals(userId));
                    if (membership.isPresent() && userOpt.isPresent() && userOpt.get().isEnabled()) {
                        User user = userOpt.get();
                        UserPrincipal principal = new UserPrincipal(user.getId(), user.getEmail(), user.getFullName(), membership.get().getOrganizationId());
                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER")));
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                } catch (IllegalArgumentException ignored) {
                    // Invalid UUID subject in token
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
