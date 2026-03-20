package iolchallenge.controller;

import iolchallenge.model.metric.ServiceName;
import com.newrelic.api.agent.NewRelic;
import io.micrometer.core.annotation.Timed;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthCheckController {

    public record HealthCheck(String version, String environment, String mavenBuildTimestamp){}

    private final HealthCheck healthCheck;

    @Autowired
    public HealthCheckController(
        @Value("${app.version}") String version,
        @Value("${environment.name}") String environment,
        @Value("${maven.build.timestamp}") String mavenBuildTimestamp
    ) {
        this.healthCheck = new HealthCheck(version, environment, mavenBuildTimestamp);
    }

    @Timed(percentiles = { 0.5, 0.8, 0.95, 0.99, 0.999 }, histogram = true, value = ServiceName.HEALTH_CHECK_GET)
    @GetMapping("/health-check")
    public ResponseEntity<HealthCheck> get() {
        NewRelic.ignoreTransaction();
        return ResponseEntity.ok(healthCheck);
    }
}
