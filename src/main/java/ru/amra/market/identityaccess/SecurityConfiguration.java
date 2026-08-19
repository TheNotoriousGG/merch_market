package ru.amra.market.identityaccess;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** Browser-facing OIDC, session, CSRF, CORS and authorization policy. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ClientRegistrationRepository clients,
            SecurityProperties properties,
            SessionLifetimePolicy lifetimePolicy,
            AbsoluteSessionLifetimeFilter absoluteLifetimeFilter)
            throws Exception {
        var requestResolver = new DefaultOAuth2AuthorizationRequestResolver(clients, "/oauth2/authorization");
        requestResolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());

        var csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieName("AMRA_CSRF");
        csrfRepository.setHeaderName("X-AMRA-CSRF");

        var oidcUserService = new OidcUserService();
        var loginSuccess = new SavedRequestAwareAuthenticationSuccessHandler();

        http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/api/v1/", "/api/v1/session", "/actuator/health/**")
                        .permitAll()
                        .requestMatchers("/internal/api-docs/**", "/api/v1/admin/**")
                        .access(adminWithMfa(properties))
                        .requestMatchers("/api/v1/**")
                        .access(verifiedIdentity())
                        .anyRequest()
                        .denyAll())
                .oauth2Login(oauth -> oauth.authorizationEndpoint(
                                endpoint -> endpoint.authorizationRequestResolver(requestResolver))
                        .userInfoEndpoint(userInfo -> userInfo.oidcUserService(request -> {
                            var user = oidcUserService.loadUser(request);
                            return new DefaultOidcUser(
                                    IdentityClaims.authorities(user), user.getIdToken(), user.getUserInfo(), "sub");
                        }))
                        .successHandler((request, response, authentication) -> {
                            var session = request.getSession();
                            session.setMaxInactiveInterval(Math.toIntExact(
                                    lifetimePolicy.idleTimeout(authentication).toSeconds()));
                            loginSuccess.onAuthenticationSuccess(request, response, authentication);
                        }))
                .logout(logout -> logout.logoutUrl("/api/v1/session/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("AMRA_SESSION", "AMRA_CSRF")
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(HttpStatus.NO_CONTENT.value())))
                .csrf(csrf -> csrf.csrfTokenRepository(csrfRepository))
                .cors(cors -> cors.configurationSource(corsConfigurationSource(properties)))
                .sessionManagement(session -> session.sessionFixation(fixation -> fixation.changeSessionId()))
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                request -> request.getRequestURI().startsWith("/api/"))
                        .accessDeniedHandler(
                                (request, response, exception) -> response.sendError(HttpStatus.FORBIDDEN.value())))
                .addFilterAfter(absoluteLifetimeFilter, BasicAuthenticationFilter.class)
                .addFilterAfter(new CsrfCookieExposureFilter(), CsrfFilter.class)
                .headers(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    SessionLifetimePolicy sessionLifetimePolicy(SecurityProperties properties) {
        return new SessionLifetimePolicy(properties);
    }

    @Bean
    AbsoluteSessionLifetimeFilter absoluteSessionLifetimeFilter(Clock clock, SessionLifetimePolicy policy) {
        return new AbsoluteSessionLifetimeFilter(clock, policy);
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
        var configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(
                List.of("Content-Type", "Accept", "X-AMRA-CSRF", "Idempotency-Key", "If-Match"));
        configuration.setExposedHeaders(List.of("ETag", "Retry-After", "X-Trace-Id"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(600L);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private static AuthorizationManager<org.springframework.security.web.access.intercept.RequestAuthorizationContext>
            verifiedIdentity() {
        return (authenticationSupplier, context) -> {
            var authentication = authenticationSupplier.get();
            var granted =
                    oidcUser(authentication).map(IdentityClaims::emailVerified).orElse(false);
            return new AuthorizationDecision(granted);
        };
    }

    private static AuthorizationManager<org.springframework.security.web.access.intercept.RequestAuthorizationContext>
            adminWithMfa(SecurityProperties properties) {
        var acceptedAcrValues = Set.copyOf(properties.adminMfaAcrValues());
        return (authenticationSupplier, context) -> {
            var authentication = authenticationSupplier.get();
            var acr = oidcUser(authentication)
                    .map(user -> user.getClaimAsString("acr"))
                    .orElse(null);
            var granted = authentication.getAuthorities().stream()
                            .anyMatch(authority -> AccessRole.ADMIN.authority().equals(authority.getAuthority()))
                    && acr != null
                    && acceptedAcrValues.contains(acr);
            return new AuthorizationDecision(granted);
        };
    }

    private static java.util.Optional<OidcUser> oidcUser(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof OidcUser user
                ? java.util.Optional.of(user)
                : java.util.Optional.empty();
    }
}
