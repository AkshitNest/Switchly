package live.switchly.api.controller;

import live.switchly.api.evaluation.*;
import live.switchly.api.model.FlagValueType;
import live.switchly.api.security.SdkPrincipal;
import live.switchly.api.service.FlagEvaluationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/evaluate")
public class EvaluationController {
    private static final Pattern KEY = Pattern.compile("[a-z0-9-]{1,255}");
    private final FlagEvaluationService service;
    public EvaluationController(FlagEvaluationService service) { this.service = service; }

    public record Result(String flagKey, String environmentKey, FlagValueType valueType, JsonNode value, boolean enabled,
                         EvaluationReason reason, Instant evaluatedAt) {
        static Result from(FlagEvaluationResult result) {
            return new Result(result.flagKey(), result.environmentKey(), result.valueType(), result.effectiveValue(),
                    result.enabled(), result.reason(), result.evaluatedAt());
        }
    }

    @PostMapping
    public Result single(@RequestBody JsonNode body, @AuthenticationPrincipal SdkPrincipal principal) {
        ConditionGroup.fields(body, Set.of("projectKey", "environmentKey", "flagKey", "context"));
        return evaluate(body, List.of(key(body.get("flagKey"))), principal).getFirst();
    }

    @PostMapping("/batch")
    public List<Result> batch(@RequestBody JsonNode body, @AuthenticationPrincipal SdkPrincipal principal) {
        ConditionGroup.fields(body, Set.of("projectKey", "environmentKey", "flagKeys", "context"));
        JsonNode array = body.get("flagKeys");
        if (array == null || !array.isArray() || array.isEmpty() || array.size() > 100) {
            throw new IllegalArgumentException("flagKeys must contain 1 to 100 unique keys");
        }
        var keys = array.valueStream().map(EvaluationController::key).toList();
        if (new HashSet<>(keys).size() != keys.size()) throw new IllegalArgumentException("Duplicate flag keys");
        return evaluate(body, keys, principal);
    }

    private List<Result> evaluate(JsonNode body, List<String> keys, SdkPrincipal principal) {
        return service.evaluate(principal, key(body.get("projectKey")), key(body.get("environmentKey")), keys,
                EvaluationContext.parse(body.get("context"))).stream().map(Result::from).toList();
    }

    private static String key(JsonNode node) {
        if (node == null || !node.isString() || !KEY.matcher(node.stringValue()).matches()) {
            throw new IllegalArgumentException("Invalid or missing project, environment or flag key");
        }
        return node.stringValue();
    }
}
