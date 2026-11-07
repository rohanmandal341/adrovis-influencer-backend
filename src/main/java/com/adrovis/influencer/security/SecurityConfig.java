package com.adrovis.influencer.security;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.ObjectPostProcessor;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, @Qualifier("coreCorsSource") CorsConfigurationSource coreCorsSource)
            throws Exception {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);
        return http
                .cors(cors -> cors.configurationSource(coreCorsSource))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, error) -> unauthorized(response, error))
                        .accessDeniedHandler((request, response, error) -> error(response, 403, "FORBIDDEN")))
                .oauth2ResourceServer(resource -> resource
                        .withObjectPostProcessor(new ObjectPostProcessor<BearerTokenAuthenticationFilter>() {
                            @Override
                            public <O extends BearerTokenAuthenticationFilter> O postProcess(O filter) {
                                filter.setAuthenticationFailureHandler(
                                        (request, response, error) -> unauthorized(response, error));
                                return filter;
                            }
                        })
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint((request, response, error) -> unauthorized(response, error))
                        .accessDeniedHandler((request, response, error) -> error(response, 403, "FORBIDDEN")))
                .authorizeHttpRequests(routes -> routes
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**", "/api/v1/health",
                                "/api/v1/agencies", "/api/v1/campaigns", "/api/v1/campaigns/{id}",
                                "/api/v1/campaigns/{id}/apply").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/inquiries", "/api/v1/newsletter/subscribe",
                                "/api/v1/analytics/track", "/api/v1/analytics/heartbeat",
                                "/api/v1/analytics/conversion", "/api/v1/analytics/lead",
                                "/api/v1/campaigns/{id}/apply-access").permitAll()
                        .requestMatchers(HttpMethod.GET, "/swagger-ui.html", "/swagger-ui/**",
                                "/v3/api-docs", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/account/me").authenticated()
                        .requestMatchers("/api/v1/collector/**", "/api/v1/inquiries/**", "/api/v1/inquiries",
                                "/api/v1/newsletter/subscribers", "/api/v1/analytics/**",
                                "/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().denyAll())
                .build();
    }

    private static void unauthorized(HttpServletResponse response, Exception exception) throws IOException {
        if (exception instanceof AuthenticationServiceException) {
            error(response, 503, "AUTHENTICATION_UNAVAILABLE");
        } else {
            response.setHeader("WWW-Authenticate", "Bearer");
            error(response, 401, "UNAUTHORIZED");
        }
    }

    private static void error(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"success\":false,\"errorCode\":\"" + code + "\"}");
    }
}
