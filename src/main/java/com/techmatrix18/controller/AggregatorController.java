package com.techmatrix18.controller;

import com.techmatrix18.dto.UserWithOrdersDto;
import com.techmatrix18.service.AggregatorService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * REST controller for aggregated APIs.
 *
 * <p>Exposes endpoints that combine data from multiple microservices
 * (e.g., UserService and OrderService) into a single response.
 * Used by the API Gateway to provide unified data to frontend clients.</p>
 *
 * <p>Delegates aggregation logic to {@link AggregatorService}.</p>
 *
 * @author Alexander Kuziv
 * @company TechMatrix18
 * @version 0.0.1
 * @since 19.01.2026
 */
@RestController
@RequestMapping("/api/aggregate")
public class AggregatorController {

    private final AggregatorService aggregatorService;

    public AggregatorController(AggregatorService aggregatorService) {
        this.aggregatorService = aggregatorService;
    }

    @GetMapping("/users/{id}")
    public Mono<UserWithOrdersDto> getUserWithOrders(@PathVariable Long id) {
        return aggregatorService.getUserWithOrders(id);
    }
}

