package live.switchly.api.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SdkKey(UUID id, String name, List<String> environmentKeys, Instant createdAt, Instant revokedAt) {}
