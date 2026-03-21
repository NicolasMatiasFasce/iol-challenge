package iolchallenge.ratelimiter.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class IdentityExtractor {

    /**
     * Extrae identidad de cliente priorizando header configurado, luego X-Forwarded-For y finalmente remoteAddr.
     */
    public String extract(HttpServletRequest request, String identityHeader) {
        String headerValue = request.getHeader(identityHeader);
        if (headerValue != null && !headerValue.isBlank()) {
            return headerValue.trim();
        }

        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }
}

