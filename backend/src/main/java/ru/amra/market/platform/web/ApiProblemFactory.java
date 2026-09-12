package ru.amra.market.platform.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import ru.amra.market.platform.generated.model.ProblemDetailsDto;
import ru.amra.market.platform.generated.model.ValidationViolationDto;

/** Creates the single RFC 9457 representation used by MVC and Spring Security. */
@Component
public final class ApiProblemFactory {

    private static final String PROBLEM_BASE = "https://api.amra.shop/problems/";

    /** Creates a safe problem response body for one request failure. */
    public ProblemDetailsDto create(
            HttpStatus status,
            String code,
            String title,
            String detail,
            HttpServletRequest request,
            List<ValidationViolationDto> violations) {
        return new ProblemDetailsDto(
                        URI.create(PROBLEM_BASE + code.toLowerCase(Locale.ROOT).replace('_', '-')),
                        title,
                        status.value(),
                        code,
                        TraceContext.currentId())
                .detail(detail)
                .instance(URI.create(request.getRequestURI()))
                .violations(violations);
    }
}
