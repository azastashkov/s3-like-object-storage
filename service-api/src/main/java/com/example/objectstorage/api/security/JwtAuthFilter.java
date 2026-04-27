package com.example.objectstorage.api.security;

import com.example.objectstorage.core.auth.InvalidJwtException;
import com.example.objectstorage.core.auth.JwtClaims;
import com.example.objectstorage.core.auth.JwtValidator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String CLAIMS_ATTR = "jwtClaims";
    public static final String PRINCIPAL_ATTR = "principalId";

    private final JwtValidator validator;
    private final Counter authFailures;

    public JwtAuthFilter(JwtValidator validator, MeterRegistry meters) {
        this.validator = validator;
        this.authFailures = meters.counter("api.auth.failures");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String p = request.getRequestURI();
        return p.startsWith("/actuator/") || p.equals("/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            authFailures.increment();
            response.setStatus(401);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"NoCredentials\",\"message\":\"Missing Bearer token\"}");
            return;
        }
        String token = header.substring("Bearer ".length()).trim();
        try {
            JwtClaims claims = validator.validate(token);
            request.setAttribute(CLAIMS_ATTR, claims);
            request.setAttribute(PRINCIPAL_ATTR, claims.principalId());
            chain.doFilter(request, response);
        } catch (InvalidJwtException e) {
            authFailures.increment();
            response.setStatus(401);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"InvalidToken\",\"message\":\"" + e.getMessage().replace("\"","'") + "\"}");
        }
    }
}
