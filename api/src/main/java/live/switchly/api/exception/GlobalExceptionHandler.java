package live.switchly.api.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(NotFoundException e) {
        return ErrorResponse.of("NOT_FOUND", e.getMessage());
    }

    public record WorkspaceChoiceResponse(ErrorResponse.ErrorDetail error, java.util.List<WorkspaceSelectionRequired.Choice> organizations) {}

    @ExceptionHandler(WorkspaceSelectionRequired.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public WorkspaceChoiceResponse selectWorkspace(WorkspaceSelectionRequired e) {
        return new WorkspaceChoiceResponse(new ErrorResponse.ErrorDetail("WORKSPACE_REQUIRED", e.getMessage()), e.choices());
    }

    @ExceptionHandler(ConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleConflict(ConflictException e) {
        return ErrorResponse.of("CONFLICT", e.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleUnauthorized(UnauthorizedException e) {
        return ErrorResponse.of("UNAUTHORIZED", e.getMessage());
    }

    @ExceptionHandler(org.springframework.security.authentication.BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleBadCredentials(org.springframework.security.authentication.BadCredentialsException e) {
        return ErrorResponse.of("UNAUTHORIZED", "Invalid email or password");
    }

    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleForbidden(ForbiddenException e) {
        return ErrorResponse.of("FORBIDDEN", e.getMessage());
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleAccessDenied(org.springframework.security.access.AccessDeniedException e) {
        return ErrorResponse.of("FORBIDDEN", "Access denied");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ErrorResponse.of("VALIDATION_FAILED", message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleNotReadable(HttpMessageNotReadableException e) {
        return ErrorResponse.of("MALFORMED_REQUEST", "Malformed or unreadable request body");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ErrorResponse.of("INVALID_ARGUMENT", "Invalid value for parameter '" + e.getName() + "'");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleMissingParam(MissingServletRequestParameterException e) {
        return ErrorResponse.of("MISSING_PARAMETER", "Required parameter '" + e.getParameterName() + "' is missing");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ErrorResponse handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return ErrorResponse.of("METHOD_NOT_ALLOWED", "Method " + e.getMethod() + " is not supported for this endpoint");
    }

    @ExceptionHandler(DeprecatedEndpointException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleDeprecatedEndpoint(DeprecatedEndpointException e) {
        return ErrorResponse.of("DEPRECATED_ENDPOINT", e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleIllegalArgument(IllegalArgumentException e) {
        return ErrorResponse.of("INVALID_ARGUMENT", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGenericException(Exception e) {
        log.error("Unhandled request failure ({})", e.getClass().getName());
        return ErrorResponse.of("INTERNAL_SERVER_ERROR", "An unexpected error occurred. Please try again later.");
    }
}
