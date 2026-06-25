package com.techmatrix18.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

// Этот шлюз нужен, если он делает кастомные запросы в коде.
// Например, если нужно самостоятельно сходить по сети в другие микросервисы.

/**
 * Configuration class for creating and configuring {@link WebClient} beans.
 *
 * <p>
 * This configuration provides a centrally managed {@link WebClient} instance
 * used for non-blocking HTTP communication with downstream microservices.
 * The {@link WebClient} is built using {@link WebClient.Builder}, which allows
 * further customization such as base URLs, filters, interceptors, timeouts,
 * and security headers.
 * </p>
 *
 * @author Alexander Kuziv <makklays@gmail.com>
 * @company TechMatrix18
 * @version 0.0.1
 * @since 20.01.2026
 */
/*@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        return builder.build();
    }
}*/

