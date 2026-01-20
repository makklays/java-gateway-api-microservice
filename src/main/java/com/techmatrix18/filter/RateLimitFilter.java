package com.techmatrix18.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WebFlux filter for rate limiting incoming requests.
 *
 * <p>This filter is intended to be used in an API Gateway to limit the number of requests
 * from a single client (IP address, API key, or other identifier) within a specified time window.
 * It helps to prevent abuse, brute-force attacks, and mitigate potential DDoS attacks,
 * improving system stability and protecting downstream services.</p>
 *
 * <p>Example usage: limit requests per IP to 5 requests per minute.</p>
 *
 * <p>Implementation can use an in-memory counter, Redis, or other distributed stores
 * to track request counts for high-availability setups.</p>
 *
 * @author Alexander Kuziv
 * @company TechMatrix18
 * @version 0.0.1
 * @since 19.01.2026
 */
@Component
public class RateLimitFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    // простой in-memory rate limiter (IP → счетчик)
    private final Map<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();
    private final int MAX_REQUESTS = 5; // максимум запросов
    private final Duration WINDOW = Duration.ofMinutes(1); // временное окно

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String ip = exchange.getRequest().getRemoteAddress() != null
            ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
            : "unknown";

        requestCounts.putIfAbsent(ip, new AtomicInteger(0));
        int requests = requestCounts.get(ip).incrementAndGet();

        if (requests > MAX_REQUESTS) {
            log.warn("Rate limit exceeded for IP: {}", ip);
            exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS);
            return exchange.getResponse().setComplete();
        }

        // сброс счетчика через WINDOW
        Mono.delay(WINDOW).subscribe(aLong -> requestCounts.get(ip).decrementAndGet());

        return chain.filter(exchange);
    }
}

