package com.netzero.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        String method = req.getMethod();
        String uri = req.getRequestURI();
        String query = req.getQueryString();
        String full = query != null ? uri + "?" + query : uri;

        try {
            chain.doFilter(req, res);
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            int status = res.getStatus();
            if (status >= 500) {
                log.error("[{}] {} → {} ({}ms)", method, full, status, elapsed);
            } else if (status >= 400) {
                log.warn("[{}] {} → {} ({}ms)", method, full, status, elapsed);
            } else {
                log.info("[{}] {} → {} ({}ms)", method, full, status, elapsed);
            }
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        String uri = req.getRequestURI();
        return uri.startsWith("/actuator") || uri.equals("/favicon.ico");
    }
}
