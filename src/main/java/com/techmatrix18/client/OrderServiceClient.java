package com.techmatrix18.client;

import com.techmatrix18.dto.OrderDto;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.util.List;

// В вашей текущей архитектуре этот файл не нужен и его следует удалить.
// Данный класс пытается вручную делать HTTP-запросы из кода. В правильном API Gateway вся маршрутизация
// происходит автоматически через настройки application.yml
// Шлюзу не нужно знать про OrderDto.class и парсить списки данных, его задача — просто перенаправить байты трафика

/**
 * Client for interacting with the Order Service via HTTP.
 *
 * <p>This component is used by the API Gateway to forward requests to the Order Service.
 * It leverages Spring's {@link WebClient} for non-blocking, reactive HTTP calls.
 * All requests are expected to be authenticated via JWT, validated by the API Gateway.</p>
 *
 * @author Alexander Kuziv <makklays@gmail.com>
 * @company TechMatrix18
 * @version 0.0.1
 * @since 20.01.2026
 */
/*@Component
public class OrderServiceClient {

    private final WebClient webClient;

    public OrderServiceClient(WebClient webClient) {
        this.webClient = webClient;
    }

    // get orders by user_id
    public Mono<List<OrderDto>> getOrdersByUserId(Long userId) {
        return webClient.get()
            .uri("/orders?userId={id}", userId)
            .retrieve()
            .bodyToFlux(OrderDto.class)
            .collectList();
    }
}*/

