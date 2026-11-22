package live.switchly.api.service;

import live.switchly.api.model.Role;
import live.switchly.api.model.SdkKey;
import live.switchly.api.repository.SdkKeyRepository;
import live.switchly.api.security.SdkSecrets;
import live.switchly.api.security.TenantAuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class SdkKeyService {
    public record IssuedKey(SdkKey key, String secret) {
        @Override public String toString() { return "IssuedKey[id=" + key.id() + "]"; }
    }

    private final TenantAuthorizationService auth;
    private final SdkKeyRepository keys;
    public SdkKeyService(TenantAuthorizationService auth, SdkKeyRepository keys) {
        this.auth = auth;
        this.keys = keys;
    }

    @Transactional
    public IssuedKey create(UUID project, String name, List<String> environments, UUID user) {
        auth.validateProjectAccess(project, user, Role.ADMIN);
        String secret = SdkSecrets.generate();
        UUID id = keys.create(project, name, SdkSecrets.digest(secret));
        if (keys.grant(project, id, environments) != environments.size()) {
            throw new IllegalArgumentException("Unknown or duplicate environment key");
        }
        return new IssuedKey(keys.get(project, id), secret);
    }

    public List<SdkKey> list(UUID project, UUID user) {
        auth.validateProjectAccess(project, user, Role.ADMIN);
        return keys.list(project);
    }

    @Transactional
    public void revoke(UUID project, UUID id, UUID user) {
        auth.validateProjectAccess(project, user, Role.ADMIN);
        keys.revoke(project, id);
    }
}
