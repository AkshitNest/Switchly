package live.switchly.api.security;

import java.util.UUID;

public record SdkPrincipal(UUID keyId, UUID projectId, String projectKey) {}
