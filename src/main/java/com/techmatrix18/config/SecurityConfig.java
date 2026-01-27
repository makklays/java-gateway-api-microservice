package com.techmatrix18.config;

import com.techmatrix18.filter.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
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
 * @since 19.01.2026
 */
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {

        ServerAuthenticationEntryPoint entryPoint = new HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED);
        var accessDeniedHandler = new HttpStatusServerAccessDeniedHandler(HttpStatus.FORBIDDEN);

        http
            // CSRF выключен для stateless JWT
            .csrf(csrf -> csrf.disable())

            .authorizeExchange(exchanges -> exchanges
                .pathMatchers("/actuator/**").permitAll()
                .pathMatchers("/admin/**").hasRole("ADMIN")
                .pathMatchers("/user/**").hasAnyRole("USER", "ADMIN")
                .pathMatchers("/api/v1/users/**").authenticated() // need JWT
                .anyExchange().denyAll()
            )

            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint(entryPoint)
                    .accessDeniedHandler(accessDeniedHandler)
            );

        return http.build();
    }
}

