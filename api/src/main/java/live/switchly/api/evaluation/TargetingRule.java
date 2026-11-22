package live.switchly.api.evaluation;

public record TargetingRule(Action action, ConditionGroup conditions) {
    public enum Action { SERVE, DEFAULT }
}
