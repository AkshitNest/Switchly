package live.switchly.api.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import live.switchly.api.evaluation.RuleDefinition;
import live.switchly.api.evaluation.EvaluationContext;
import live.switchly.api.evaluation.ConditionGroup;
import live.switchly.api.model.UserSegment;
import live.switchly.api.security.UserPrincipal;
import live.switchly.api.service.TargetingService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class TargetingController {
    private static final String SEGMENTS = "/projects/{project}/segments";
    private static final String RULES = "/environments/{env}/flags/{flag}/targeting";
    private static final String ROLLOUT = "/environments/{env}/flags/{flag}/rollout";
    private final TargetingService service;
    public TargetingController(TargetingService service) { this.service = service; }

    public record CreateSegment(@NotNull @Pattern(regexp = RuleDefinition.KEY_PATTERN) String key,
                                @NotBlank @Size(max = 255) String name, @Size(max = 4096) String description,
                                @NotNull JsonNode conditions) {}
    public record EditSegment(@NotBlank @Size(max = 255) String name, @Size(max = 4096) String description,
                              @NotNull JsonNode conditions) {}

    @PostMapping(SEGMENTS)
    @ResponseStatus(HttpStatus.CREATED)
    public UserSegment create(@PathVariable UUID project, @Valid @RequestBody CreateSegment body, @AuthenticationPrincipal UserPrincipal user) {
        return service.save(project, body.key(), body.name(), body.description(), body.conditions(), true, user.id());
    }

    @GetMapping(SEGMENTS)
    public List<UserSegment> list(@PathVariable UUID project, @AuthenticationPrincipal UserPrincipal user) {
        return service.list(project, user.id());
    }

    @GetMapping(SEGMENTS + "/{key}")
    public UserSegment get(@PathVariable UUID project, @PathVariable String key, @AuthenticationPrincipal UserPrincipal user) {
        return service.get(project, key, user.id());
    }

    @PutMapping(SEGMENTS + "/{key}")
    public UserSegment edit(@PathVariable UUID project, @PathVariable String key, @Valid @RequestBody EditSegment body,
                            @AuthenticationPrincipal UserPrincipal user) {
        return service.save(project, key, body.name(), body.description(), body.conditions(), false, user.id());
    }

    public record Membership(boolean member) {}

    @PostMapping(SEGMENTS + "/{key}/evaluate")
    public Membership membership(@PathVariable UUID project, @PathVariable String key, @RequestBody JsonNode body,
                                 @AuthenticationPrincipal UserPrincipal user) {
        var segment = service.get(project, key, user.id());
        ConditionGroup.fields(body, java.util.Set.of("context"));
        return new Membership(segment.conditions().matches(EvaluationContext.parse(body.get("context"))));
    }

    @DeleteMapping(SEGMENTS + "/{key}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID project, @PathVariable String key, @AuthenticationPrincipal UserPrincipal user) {
        service.delete(project, key, user.id());
    }

    @GetMapping(RULES)
    public List<RuleDefinition> rules(@PathVariable UUID env, @PathVariable UUID flag, @AuthenticationPrincipal UserPrincipal user) {
        return service.rules(env, flag, user.id());
    }

    public record Rollout(Integer percentage) {}

    @PostMapping("/environments/{env}/flags/{flag}/preview")
    public EvaluationController.Result preview(@PathVariable UUID env, @PathVariable UUID flag, @RequestBody JsonNode input,
                                               @AuthenticationPrincipal UserPrincipal user) {
        ConditionGroup.fields(input, java.util.Set.of("context"));
        return EvaluationController.Result.from(service.preview(env, flag, user.id(), EvaluationContext.parse(input.get("context"))));
    }

    @GetMapping(ROLLOUT)
    public Rollout rollout(@PathVariable UUID env, @PathVariable UUID flag, @AuthenticationPrincipal UserPrincipal user) {
        return new Rollout(service.rollout(env, flag, user.id()));
    }

    @PutMapping(ROLLOUT)
    public Rollout setRollout(@PathVariable UUID env, @PathVariable UUID flag, @RequestBody JsonNode input,
                              @AuthenticationPrincipal UserPrincipal user) {
        return new Rollout(service.setRollout(env, flag, input, user.id()));
    }

    @PutMapping(RULES)
    public List<RuleDefinition> replace(@PathVariable UUID env, @PathVariable UUID flag, @RequestBody JsonNode body,
                                        @AuthenticationPrincipal UserPrincipal user) {
        return service.replace(env, flag, body, user.id());
    }
}
