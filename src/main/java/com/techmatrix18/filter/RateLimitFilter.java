package com.techmatrix18.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
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
@Order(2)
public class RateLimitFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    // Храним объект Окна, чтобы знать, когда сбросить лимит
    private static class WindowCounter {
        final AtomicInteger count = new AtomicInteger(0);
        long timestamp = System.currentTimeMillis();
    }

    // simple in-memory rate limiter (IP → counter)
    private final Map<String, WindowCounter> requestCounts = new ConcurrentHashMap<>();
    private final int MAX_REQUESTS = 500; // maximum requests per window
    private final long WINDOW_MS = Duration.ofMinutes(1).toMillis(); // time window

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String ip = exchange.getRequest().getRemoteAddress() != null
            ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
            : "unknown";

        long now = System.currentTimeMillis();

        // Извлекаем или атомарно создаем счетчик для IP
        WindowCounter counter = requestCounts.compute(ip, (key, currentWindow) -> {
            if (currentWindow == null || (now - currentWindow.timestamp) > WINDOW_MS) {
                // Если окна нет или оно устарело — создаем новое (чистый сброс лимита)
                WindowCounter newWindow = new WindowCounter();
                newWindow.count.set(1);
                return newWindow;
            }
            // Иначе просто увеличиваем текущий счетчик
            currentWindow.count.incrementAndGet();
            return currentWindow;
        });

        if (counter.count.get() > MAX_REQUESTS) {
            log.warn("Rate limit exceeded for IP: {}. Current count: {}", ip, counter.count.get());
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return exchange.getResponse().setComplete();
        }

        // Периодическая фоновая очистка мапы от «мертвых» IP (чтобы не было OutOfMemory)
        // Удаляем записи, которые не обновлялись больше 5 минут
        if (requestCounts.size() > 5000) {
            requestCounts.entrySet().removeIf(entry -> (now - entry.getValue().timestamp) > (WINDOW_MS * 5));
        }

        return chain.filter(exchange);
    }
}

