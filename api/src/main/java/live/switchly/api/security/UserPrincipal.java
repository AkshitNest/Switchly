package live.switchly.api.security;

import java.security.Principal;
import java.util.UUID;

public record UserPrincipal(UUID id, String email, String fullName, UUID organizationId) implements Principal {
    public static UUID activeOrganization() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof UserPrincipal user ? user.organizationId() : null;
    }
    @Override
    public String getName() {
        return email;
    }
}

