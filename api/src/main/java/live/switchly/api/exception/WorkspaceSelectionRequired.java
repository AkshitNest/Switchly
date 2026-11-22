package live.switchly.api.exception;

import java.util.List;
import java.util.UUID;

public class WorkspaceSelectionRequired extends RuntimeException {
    public record Choice(UUID id, String name) {}
    private final List<Choice> choices;

    public WorkspaceSelectionRequired(List<Choice> choices) {
        super("Choose an organization to sign in.");
        this.choices = List.copyOf(choices);
    }
    public List<Choice> choices() { return choices; }
}
