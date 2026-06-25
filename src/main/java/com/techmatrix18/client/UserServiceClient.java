package com.techmatrix18.client;

// В вашей текущей архитектуре этот файл не нужен и его следует удалить.
// Данный класс пытается вручную делать HTTP-запросы из кода. В правильном API Gateway вся маршрутизация
// происходит автоматически через настройки application.yml
// Шлюзу не нужно знать про OrderDto.class и парсить списки данных, его задача — просто перенаправить байты трафика

// Пример использования CircuitBreaker и Retry из Resilience4j для демонстрации устойчивости к сбоям при вызовах внешнего сервиса.


/*import com.techmatrix18.dto.UserDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.List;*/

/**
 * Client for interacting with the User Service via HTTP.
 *
 * <p>This component is used by the API Gateway to forward requests to the User Service.
 * It leverages Spring's {@link WebClient} for non-blocking, reactive HTTP calls.
 * All requests are expected to be authenticated via JWT, validated by the API Gateway.</p>
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Forward API calls from the Gateway to the User Service.</li>
 *   <li>Handle URI construction and query parameters.</li>
 *   <li>Map responses to DTOs for further processing.</li>
 * </ul>
 * </p>
 *
 * <p>This client does <strong>not</strong> contain business logic or persistence code.
 * It acts purely as an integration layer between the Gateway and the User Service.</p>
 *
 * <p>Example usage:
 * <pre>{@code
 *    UserDto user = userServiceClient.getUserById(1L).block();
 * }</pre>
 * </p>
 *
 * <p>Resilience4j:
 * <ul>
 *   <li>Retry — повторный вызов при ошибках</li>
 *   <li>CircuitBreaker — «размыкает» цепочку при частых ошибках, чтобы сервис не перегружался</li>
 *   <li>RateLimiter — ограничение числа вызовов</li>
 *   <li>Bulkhead — изоляция потоков / ресурсов</li>
 *   <li>TimeLimiter — таймаут для медленных вызовов</li>
 * </ul>
 * </p>
 *
 * @author Alexander Kuziv <makklays@gmail.com>
 * @company TechMatrix18
 * @version 0.0.1
 * @since 19.01.2026
 */
/*@Component
public class UserServiceClient {

    private final WebClient webClient;

    public UserServiceClient(WebClient webClient) {
        this.webClient = webClient;
    }

    // get user id
    @Retry(name = "userServiceRetry", fallbackMethod = "fallbackGetUserById")
    @CircuitBreaker(name = "userServiceCB", fallbackMethod = "fallbackGetUserById")
    public Mono<UserDto> getUserById(Long userId) {
        return webClient.get()
            .uri("/users/{id}", userId)
            .retrieve()
            .bodyToMono(UserDto.class);
    }

    // get latest users
    public Mono<List<UserDto>> getLatestUsers() {
        return webClient.get()
            .uri("/users/latest")
            .retrieve()
            .bodyToFlux(UserDto.class)
            .collectList();
    }

    // fallback при ошибке
    public Mono<UserDto> fallbackGetUserById(Long userId, Throwable ex) {
        // можно вернуть дефолтного пользователя или пустой Mono
        return Mono.empty();
    }
}*/

