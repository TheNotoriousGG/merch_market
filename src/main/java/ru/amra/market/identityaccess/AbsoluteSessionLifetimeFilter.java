package ru.amra.market.identityaccess;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Enforces an absolute lifetime in addition to Spring Session's inactivity timeout. */
final class AbsoluteSessionLifetimeFilter extends OncePerRequestFilter {

    private final Clock clock;
    private final SessionLifetimePolicy lifetimePolicy;

    AbsoluteSessionLifetimeFilter(Clock clock, SessionLifetimePolicy lifetimePolicy) {
        this.clock = clock;
        this.lifetimePolicy = lifetimePolicy;
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
                response.sendError(HttpStatus.UNAUTHORIZED.value());
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
