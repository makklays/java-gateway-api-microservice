package com.techmatrix18.config;

// В Spring Cloud Gateway есть два взаимоисключающих способа настраивать маршруты: либо жестко в Java-коде (как в этом файле),
// либо динамически в конфигурационном файле application.yml - как в данном случае (! в файле application.yml)


/*import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;*/

/**
 * Configuration class for defining API Gateway routes.
 * <p>
 * This class sets up route mappings from the Gateway to downstream microservices,
 * including path rewriting and load balancing if needed.
 * </p>
 *
 * @author Alexander Kuziv <makklays@gmail.com>
 * @company TechMatrix18
 * @version 0.0.1
 * @since 19.01.2026
 */
/*@Configuration
public class GatewayRoutesConfig {

    @Bean
    public RouteLocator gatewayRoutes(RouteLocatorBuilder builder) {
        return builder.routes()

            // Route to User Service
            .route("user_service_route", r -> r
                .path("/api/users/**") // incoming path from client
                .filters(f -> f
                        .rewritePath("/api/users/(?<segment>.*)", "/users/${segment}") // rewrite to internal path
                        .addRequestHeader("X-Gateway", "API-Gateway") // optional header
                )
                .uri("http://user-service:8080") // service URL (could be Kubernetes service)
            )

            // Route to Order Service
            .route("order_service_route", r -> r
                .path("/api/orders/**")
                .filters(f -> f
                        .rewritePath("/api/orders/(?<segment>.*)", "/orders/${segment}")
                        .addRequestHeader("X-Gateway", "API-Gateway")
                )
                .uri("http://order-service:8080")
            )

            .build();
    }
}*/

