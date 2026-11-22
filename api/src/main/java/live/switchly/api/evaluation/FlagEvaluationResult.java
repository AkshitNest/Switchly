package live.switchly.api.evaluation;

import live.switchly.api.model.FlagValueType;
import tools.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record FlagEvaluationResult(UUID flagId, String flagKey, UUID environmentId, String environmentKey,
                                   FlagValueType valueType, JsonNode effectiveValue, boolean enabled,
                                   EvaluationReason reason, Instant evaluatedAt) {}
