package ru.amra.market.identityaccess;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;
import ru.amra.market.platform.web.ApiProblemFactory;
import tools.jackson.databind.ObjectMapper;

/** Writes authentication, authorization and CSRF failures in the shared API problem format. */
@Component
public final class SecurityProblemWriter implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityProblemWriter.class);

    private final ApiProblemFactory problems;
    private final ObjectMapper objectMapper;

    public SecurityProblemWriter(ApiProblemFactory problems, ObjectMapper objectMapper) {
        this.problems = problems;
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        write(
                request,
                response,
                HttpStatus.UNAUTHORIZED,
                "AUTHENTICATION_REQUIRED",
                "Authentication required",
                "Authenticate before accessing this resource.");
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            org.springframework.security.access.AccessDeniedException exception)
            throws IOException, ServletException {
        var csrfFailure = exception instanceof CsrfException;
        write(
                request,
                response,
                HttpStatus.FORBIDDEN,
                csrfFailure ? "CSRF_INVALID" : "ACCESS_DENIED",
                csrfFailure ? "CSRF validation failed" : "Access denied",
                csrfFailure
                        ? "Supply a valid CSRF cookie and matching request header."
                        : "The authenticated identity cannot access this resource.");
    }

    private void write(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String code,
            String title,
            String detail)
            throws IOException {
        LOGGER.warn(
                "Security request rejected: code={}, method={}, path={}",
                code,
                request.getMethod(),
                request.getRequestURI());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(), problems.create(status, code, title, detail, request, List.of()));
    }
}
