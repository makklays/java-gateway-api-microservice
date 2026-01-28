package com.techmatrix18.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * WebFlux filter for logging incoming HTTP requests and outgoing responses.
 *
 * <p>Can be used in API Gateway to track request paths, headers, and response status.
 * This filter does not modify requests or responses, only logs them for monitoring/debugging.</p>
 *
 * @author Alexander Kuziv
 * @company TechMatrix18
 * @version 0.0.1
 * @since 19.01.2026
 */
@Component
@Order(4)
public class LoggingFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(LoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {

        // Safely retrieve the method
        String method = exchange.getRequest().getMethod() != null
            ? exchange.getRequest().getMethod().name()
            : "UNKNOWN";

        String path = exchange.getRequest().getURI().getPath();

        log.info("Incoming request: {} {}", method, path);

        /*return chain.filter(exchange)
            .doOnSuccess(aVoid -> {
                int status = exchange.getResponse().getStatusCode() != null
                        ? exchange.getResponse().getStatusCode().value()
                        : 0;
                log.info("Response status: {} for {} {}", status, method, path);
            });*/

        return chain.filter(exchange)
            .doOnEach(signal -> {
                if (signal.isOnNext()) {
                    signal.getContextView()
                        .getOrEmpty("X-Correlation-Id")
                        .ifPresent(cid ->
                            log.info("Request {} {} [cid={}]", exchange.getRequest().getMethod(), exchange.getRequest().getURI(), cid)
                        );
                }
            });
    }
}

