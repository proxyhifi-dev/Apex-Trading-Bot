package com.apex.backend.config;

import com.apex.backend.dto.ApiError;
import com.apex.backend.security.UserPrincipal;
import com.apex.backend.service.RateLimitService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Instant;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    public RateLimitInterceptor(RateLimitService rateLimitService, ObjectMapper objectMapper) {
        this.rateLimitService = rateLimitService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }
        if (isScannerRequest(path, method)) {
            return handleRateLimit(rateLimitService.allowScannerWithRetryAfter(resolveKey(request)), request, response);
        }
        if (isTradeRequest(path, method)) {
            return handleRateLimit(rateLimitService.allowTradeWithRetryAfter(resolveKey(request)), request, response);
        }
        return true;
    }

    private boolean handleRateLimit(RateLimitService.RateLimitDecision decision,
                                    HttpServletRequest request,
                                    HttpServletResponse response) throws Exception {
        if (decision.allowed()) {
            return true;
        }
        ApiError error = ApiError.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(429)
                .error("TOO_MANY_REQUESTS")
                .message("Rate limit exceeded")
                .requestId(MDC.get("requestId"))
                .correlationId(MDC.get("correlationId"))
                .build();
        response.setStatus(429);
        response.setContentType("application/json");
        response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
        objectMapper.writeValue(response.getOutputStream(), error);
        return false;
    }

    private boolean isScannerRequest(String path, String method) {
        return "POST".equalsIgnoreCase(method)
                && ("/api/scanner/run".equals(path)
                || "/api/signals/scan-now".equals(path)
                || "/api/strategy/scan-now".equals(path));
    }

    private boolean isTradeRequest(String path, String method) {
        if ("GET".equalsIgnoreCase(method)) {
            return false;
        }
        return path.startsWith("/api/orders")
                || path.startsWith("/api/positions");
    }

    private String resolveKey(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return "user:" + principal.getUserId();
        }
        return "ip:" + request.getRemoteAddr();
    }
}
