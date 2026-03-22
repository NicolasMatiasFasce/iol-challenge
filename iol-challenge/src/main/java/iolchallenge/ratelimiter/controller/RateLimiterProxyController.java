package iolchallenge.ratelimiter.controller;

import iolchallenge.ratelimiter.model.RateLimitDecision;
import iolchallenge.ratelimiter.model.RateLimitOutcome;
import iolchallenge.ratelimiter.service.RateLimiterOrchestratorService;
import iolchallenge.ratelimiter.service.UpstreamForwardingService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${rate-limiter.endpoint-prefix:/rl}")
public class RateLimiterProxyController {

    private final RateLimiterOrchestratorService orchestratorService;
    private final UpstreamForwardingService forwardingService;

    /**
     * Construye el controlador intermedio que aplica rate limiting antes de reenviar.
     */
    public RateLimiterProxyController(
        RateLimiterOrchestratorService orchestratorService,
        UpstreamForwardingService forwardingService)
    {
        this.orchestratorService = orchestratorService;
        this.forwardingService = forwardingService;
    }

    /**
     * Punto unico de entrada del middleware: decide cuota y luego reenvia o responde 429.
     */
    @RequestMapping("/**")
    public ResponseEntity<byte[]> proxy(HttpServletRequest request) {
        RateLimitOutcome outcome = orchestratorService.evaluate(request);
        RateLimitDecision decision = outcome.decision();

        if (!outcome.allowed()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("X-RateLimit-Limit", String.valueOf(decision.limit()))
                .header("X-RateLimit-Remaining", String.valueOf(decision.remaining()))
                .header("X-RateLimit-Retry-After", String.valueOf(decision.retryAfterSeconds()))
                .body(new byte[0]);
        }

        return forwardingService.forward(request, outcome.policy().upstreamUrl(), outcome.forwardedPath());
    }
}


