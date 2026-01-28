package com.apex.backend.security;

import com.apex.backend.dto.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {
        ApiError error = ApiError.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpServletResponse.SC_FORBIDDEN)
                .error("FORBIDDEN")
                .errorCode("FORBIDDEN")
                .message("Access denied")
                .requestId(MDC.get("requestId"))
                .correlationId(MDC.get("correlationId"))
                .build();
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        objectMapper.writeValue(response.getOutputStream(), error);
    }
}
