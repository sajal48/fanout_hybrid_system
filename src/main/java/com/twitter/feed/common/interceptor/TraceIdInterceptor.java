package com.twitter.feed.common.interceptor;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter to add custom trace IDs to request/response headers
 * Works with Spring Cloud Sleuth/Micrometer Tracing for distributed tracing
 *
 * Automatically adds X-Trace-ID and X-Span-ID headers to every response
 * so clients can correlate their logs with server logs.
 */
@Component
@Slf4j
public class TraceIdInterceptor extends OncePerRequestFilter {

    private static final String TRACE_ID_HEADER = "X-Trace-ID";
    private static final String SPAN_ID_HEADER = "X-Span-ID";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        // Generate or extract trace ID from header
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString();
        }
        
        // Generate span ID
        String spanId = UUID.randomUUID().toString().substring(0, 16);
        
        // Add trace ID to response header for client reference
        response.addHeader(TRACE_ID_HEADER, traceId);
        response.addHeader(SPAN_ID_HEADER, spanId);
        
        // Log the request with trace ID
        log.info("Incoming Request - Method: {}, URI: {}, RemoteAddr: {}", 
                request.getMethod(), 
                request.getRequestURI(), 
                request.getRemoteAddr());
        
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Log response status with trace ID
            log.info("Response - Status: {}, ContentType: {}", 
                    response.getStatus(), 
                    response.getContentType());
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Skip filtering for static resources and actuator endpoints
        String path = request.getRequestURI();
        return path.startsWith("/actuator") || 
               path.startsWith("/swagger") || 
               path.startsWith("/api-docs") || 
               path.startsWith("/webjars");
    }
}
