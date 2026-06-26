package com.netzero.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class ApiKeyFilter extends OncePerRequestFilter {

    private final String configuredKey;

    public ApiKeyFilter(String configuredKey) {
        this.configuredKey = configuredKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String path = req.getRequestURI();
        boolean isWriteEndpoint = isProtected(path, req.getMethod());

        if (isWriteEndpoint && !configuredKey.isBlank()) {
            String key = req.getHeader("X-API-Key");
            if (!configuredKey.equals(key)) {
                res.setStatus(401);
                res.setContentType("application/json;charset=UTF-8");
                res.getWriter().write("{\"success\":false,\"error\":{\"code\":\"VALIDATION_ERROR\",\"message\":\"Invalid or missing API key\"}}");
                return;
            }
        }
        chain.doFilter(req, res);
    }

    private boolean isProtected(String path, String method) {
        return (path.startsWith("/api/v1/ingest/") && "POST".equalsIgnoreCase(method))
            || (path.startsWith("/api/v1/pipeline/") && "POST".equalsIgnoreCase(method))
            || (path.equals("/api/v1/export/archive") && "POST".equalsIgnoreCase(method))
            || (path.equals("/api/v1/recommendations/actual") && "PUT".equalsIgnoreCase(method));
    }
}
