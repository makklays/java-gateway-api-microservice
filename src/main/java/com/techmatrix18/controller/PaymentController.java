package com.techmatrix18.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * REST controller for handling payment-related requests.
 *
 * @author Alexander Kuziv <makklays@gmail.com>
 * @company TechMatrix18
 * @version 0.0.1
 * @since 26.06.2026
 */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    @PostMapping
    public Mono<Map<String, String>> createPayment(
            @RequestBody Map<String, Object> paymentPayload,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {

        // Логируем запрос, используя Correlation ID, который прилетел со шлюза!
        log.info("Received payment request. [cid={}]", correlationId);

        // Имитируем быструю реактивную обработку
        return Mono.just(Map.of(
            "status", "PENDING",
            "message", "Payment initialization started successfully",
            "correlationId", correlationId != null ? correlationId : "none"
        ));
    }
}

