package ru.amra.market.identityaccess;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Enforces an absolute lifetime in addition to Spring Session's inactivity timeout. */
final class AbsoluteSessionLifetimeFilter extends OncePerRequestFilter {

    private final Clock clock;
    private final SessionLifetimePolicy lifetimePolicy;
    private final SecurityProblemWriter problemWriter;

    AbsoluteSessionLifetimeFilter(
            Clock clock, SessionLifetimePolicy lifetimePolicy, SecurityProblemWriter problemWriter) {
        this.clock = clock;
        this.lifetimePolicy = lifetimePolicy;
        this.problemWriter = problemWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        var session = request.getSession(false);
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (session != null && authentication != null && authentication.isAuthenticated()) {
            var ageMillis = clock.millis() - session.getCreationTime();
            if (ageMillis >= lifetimePolicy.absoluteTimeout(authentication).toMillis()) {
                session.invalidate();
                SecurityContextHolder.clearContext();
                problemWriter.commence(
                        request,
                        response,
                        new InsufficientAuthenticationException("The absolute session lifetime has elapsed"));
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
