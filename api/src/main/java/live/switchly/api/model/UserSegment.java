package live.switchly.api.model;

import live.switchly.api.evaluation.ConditionGroup;
import java.time.Instant;
import java.util.UUID;

public record UserSegment(UUID id, UUID projectId, String key, String name, String description,
                          ConditionGroup conditions, Instant createdAt, Instant updatedAt) {}
