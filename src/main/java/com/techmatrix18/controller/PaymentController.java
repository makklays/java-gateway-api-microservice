package com.techmatrix18.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

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
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    private final WebClient webClient;

    public PaymentController(
            @Value("${microservices.payments-url}") String paymentServiceUrl,
            WebClient.Builder webClient
    ) {
        this.webClient = webClient.baseUrl(paymentServiceUrl).build();
    }

    /**
     * Handles the creation of a new payment.
     *
     * @param paymentPayload The payload containing payment details.
     * @param correlationId  The correlation ID for tracking the request (optional).
     * @return A Mono containing a map with the status and message of the payment creation process.
     */
    @PostMapping
    public Mono<Map<String, String>> createPayment(
            @RequestBody Map<String, Object> paymentPayload,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {

        // Управляем трассировкой: если ID не пришел с фронтенда, генерируем новый UUID
        String finalCorrelationId = (correlationId != null && !correlationId.isBlank())
                ? correlationId
                : UUID.randomUUID().toString();

        // Логируем запрос, используя Correlation ID, который прилетел со шлюза!
        log.info("Received payment request. [cid={}]", finalCorrelationId);

        // Имитируем быструю реактивную обработку
        return Mono.just(Map.of(
            "status", "PENDING",
            "message", "Payment initialization started successfully",
            "correlationId", finalCorrelationId != null ? finalCorrelationId : "none"
        ));
    }
}

