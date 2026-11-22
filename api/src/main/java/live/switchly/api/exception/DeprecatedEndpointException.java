package live.switchly.api.exception;

public class DeprecatedEndpointException extends RuntimeException {
    public DeprecatedEndpointException(String message) {
        super(message);
    }
}

