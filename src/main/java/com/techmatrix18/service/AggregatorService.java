package com.techmatrix18.service;

// Этот класс — AggregatorService — реализует классический паттерн API Composition (Агрегатор). Он параллельно
// (с помощью реактивного метода Mono.zip) запрашивал данные из двух разных мест (user-service и order-service)
// и объединял их в один общий объект UserWithOrdersDto


/*import com.techmatrix18.client.OrderServiceClient;
import com.techmatrix18.client.UserServiceClient;
import com.techmatrix18.dto.OrderDto;
import com.techmatrix18.dto.UserDto;
import com.techmatrix18.dto.UserWithOrdersDto;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import java.util.List;*/

/**
 * Aggregator service that combines data from multiple microservices.
 *
 * <p>This service fetches user information from UserService and related orders
 * from OrderService, then aggregates them into a single DTO for the API Gateway
 * or frontend clients.</p>
 *
 * <p>It handles asynchronous calls using WebClient and Reactor (Mono/Flux) to
 * efficiently aggregate data in a non-blocking manner.</p>
 *
 * @author Alexander Kuziv <makklays@gmail.com>
 * @company TechMatrix18
 * @version 0.0.1
 * @since 20.01.2026
 */
/*@Service
public class AggregatorService {

    private final UserServiceClient userServiceClient;
    private final OrderServiceClient orderServiceClient;

    public AggregatorService(UserServiceClient userServiceClient, OrderServiceClient orderServiceClient) {
        this.userServiceClient = userServiceClient;
        this.orderServiceClient = orderServiceClient;
    }

    public Mono<UserWithOrdersDto> getUserWithOrders(Long userId) {
        Mono<UserDto> userMono = userServiceClient.getUserById(userId);
        Mono<List<OrderDto>> ordersMono = orderServiceClient.getOrdersByUserId(userId);

        return Mono.zip(userMono, ordersMono)
            .map(tuple -> new UserWithOrdersDto(
                tuple.getT1().getId(),
                tuple.getT1().getUsername(),
                tuple.getT1().getEmail(),
                tuple.getT2()
            ));
    }
}*/

