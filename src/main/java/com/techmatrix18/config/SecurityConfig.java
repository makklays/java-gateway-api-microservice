package com.techmatrix18.config;

import com.techmatrix18.filter.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint;
import org.springframework.security.web.server.authorization.HttpStatusServerAccessDeniedHandler;

/**
 * Security configuration for the API Gateway.
 *
 * <p>Configures WebFlux security, including JWT authentication and request authorization.
 * All incoming requests are validated for proper authentication before being routed to downstream services.</p>
 *
 * <p>This configuration is intended for a stateless API Gateway using Spring Security and JWT tokens.</p>
 *
 * @author Alexander Kuziv <makklays@gmail.com>
 * @company TechMatrix18
 * @version 0.0.1
 * @since 28.01.2026
 */
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity // enables support @PreAuthorize and @RolesAllowed
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    // Inject the filter through the constructor
    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {

        ServerAuthenticationEntryPoint entryPoint = new HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED);
        var accessDeniedHandler = new HttpStatusServerAccessDeniedHandler(HttpStatus.FORBIDDEN);

        http
            // CSRF is disabled for stateless JWT
            .csrf(csrf -> csrf.disable())

            // Add our JWT filter at the Authentication position
            .addFilterAt(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)

            // URL authorization settings
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers("/actuator/**").permitAll()
                .pathMatchers("/admin/**").hasRole("ADMIN")
                .pathMatchers("/user/**").hasAnyRole("USER", "ADMIN")
                .pathMatchers("/api/v1/users/**").authenticated() // need JWT
                .anyExchange().denyAll()
            )

            // Handling authentication and access errors
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(entryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            );

        return http.build();
    }
}

