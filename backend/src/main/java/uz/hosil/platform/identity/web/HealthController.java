package uz.hosil.platform.identity.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Public platform health and version inquiry controller.
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    public record HealthResponse(
        String status,
        String service,
        String version,
        Instant timestamp
    ) {}

    @GetMapping
    public ResponseEntity<HealthResponse> getHealth() {
        return ResponseEntity.ok(new HealthResponse(
            "UP",
            "hosil-platform",
            "0.1.0-SNAPSHOT",
            Instant.now()
        ));
    }
}
