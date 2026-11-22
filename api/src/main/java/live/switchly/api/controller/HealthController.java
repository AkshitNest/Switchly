package live.switchly.api.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Map;

/**
 * Minimal, unauthenticated health endpoint for container orchestrators (Kubernetes, Docker, ECS).
 *
 * Exposes ONLY {"status":"UP"} or {"status":"DOWN"} - never leaks database credentials,
 * stack traces, internal configuration, or sensitive infrastructure details.
 */
@RestController
public class HealthController {

    private final DataSource dataSource;

    public HealthController(@Autowired(required = false) DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/healthz")
    public ResponseEntity<Map<String, String>> health() {
        if (dataSource != null) {
            try (Connection conn = dataSource.getConnection()) {
                if (!conn.isValid(2)) {
                    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                            .body(Map.of("status", "DOWN"));
                }
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of("status", "DOWN"));
            }
        }
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}

