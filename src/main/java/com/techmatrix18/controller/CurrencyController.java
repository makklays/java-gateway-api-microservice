package com.techmatrix18.controller;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for handling currency-related requests.
 *
 * В паттерне Сага участвуют только изменяющие операции (POST, PUT, DELETE).
 * Паттерн Сага не применяется к GET-запросам, так как они не изменяют состояние системы.
 *
 * @author Alexander Kuziv <makklays@gmail.com>
 * @company TechMatrix18
 * @version 0.0.1
 * @since 29.06.2026
 */

@RestController
@RequestMapping("/api/v1/currencies")
public class CurrencyController {

    private static final Logger log = LoggerFactory.getLogger(CurrencyController.class);
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    private final WebClient webClient;

    public CurrencyController(
            @Value("${microservices.currency-url}") String currencyServiceUrl,
            WebClient.Builder webClient
    ) {
        this.webClient = webClient.baseUrl(currencyServiceUrl).build();
    }

    /**
     * Тестовый endpoint для проверки реактивного окружения
     */
    @GetMapping("/test")
    public Mono<ResponseEntity<String>> getTest() {
        return this.webClient.get()
                .uri("/test")
                .retrieve()
                .bodyToMono(String.class) // Получаю Mono<String>
                // Трансформируем строку в ResponseEntity внутри потока данных БЕЗ блокировки (.block())
                .map(responseBody -> ResponseEntity.ok(responseBody)); // Оператор .map меняет внутренности: Mono<ResponseEntity<String>>
    }

    /**
     * Получение списка 12 валют и курсов
     * Этот метод делает HTTP GET запрос к удаленному микросервису валют.
     * Если микросервис недоступен, срабатывает fallback метод.
     *
     * @return ResponseEntity с JSON-ответом от микросервиса или сообщение об ошибке
     */
    @Retry(name = "currencyServiceRetry", fallbackMethod = "fallbackCurrencies12")
    @CircuitBreaker(name = "currencyServiceCB", fallbackMethod = "fallbackCurrencies12")
    @GetMapping("/list-12")
    public Mono<ResponseEntity<Map<String, Object>>> getCurrencies12 (
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        log.info("Fetching base currencies at {}", LocalDateTime.now().format(formatter));

        return this.webClient.get()
            .uri(uriBuilder -> uriBuilder.path("/api/v1/currencies").queryParam("recent").build())
            .headers(headers -> {
                if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                    headers.set("Idempotency-Key", idempotencyKey);
                }
            })
            .retrieve()
            // 1. Принимаем как поток Map-объектов (каждая валюта как Map)
            .bodyToFlux(Map.class)
            // 2. Ждем, пока соберутся все 12 элементов, и превращаем Flux<Map> в Mono<List<Map>>
            .collectList()
            // 3. Формируем финальный Map JSON-объект и оборачиваем в ResponseEntity БЕЗ .block()
            .map(currencyList -> {
                Map<String, Object> responseBody = Map.of(
                        "status", "success",
                        "data", currencyList
                );
                return ResponseEntity.ok(responseBody);
            });
    }

    /**
     * РЕЗЕРВНЫЙ МЕТОД (Fallback)
     * Должен иметь такое же имя, возвращаемый тип и принимать Exception последним аргументом!
     * В данном случае: я делаю проброс ошибки (без свежих данных работать нельзя)
     * Использовать данные из кеша нет смысла - они уже не актуальны
     */
    public ResponseEntity<Map<String, Object>> fallbackCurrencies12(Exception e) {
        log.error("Микросервис Валют недоступен. Операция отменена из соображений безопасности.");

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "status", "SERVICE_DOWN",
                "message", "Сервис обмена валют временно недоступен. Пожалуйста, повторите попытку позже.",
                "timestamp", LocalDateTime.now().format(formatter)
        ));
    }

    /**
     * Получение курса валюты по её коду (например, USD, EUR, GBP и т.д.)
     * Этот метод делает HTTP GET запрос к удаленному микросервису валют.
     * Если микросервис недоступен, срабатывает fallback метод.
     *
     * @param code
     * @return ResponseEntity с JSON-ответом от микросервиса или сообщение об ошибке
     */
    @Retry(name = "currencyServiceRetry", fallbackMethod = "fallbackCurrencyByCode")
    @CircuitBreaker(name = "currencyServiceCB", fallbackMethod = "fallbackCurrencyByCode")
    @GetMapping("/code-currency/{code}")
    public Mono<ResponseEntity<Map<String, Object>>> getCurrencyByCode(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @PathVariable String code
    ) {
        // Управляем трассировкой: если ID не пришел с фронтенда, генерируем новый UUID
        String finalCorrelationId = (correlationId != null && !correlationId.isBlank())
                ? correlationId
                : UUID.randomUUID().toString();

        log.info("[Correlation-ID: {}] Gateway: Запрос курса для валюты: {}", finalCorrelationId, code);

        // Передаем переменную code в URI удаленного микросервиса
        return this.webClient.get()
            .uri("/api/v1/currencies/code/{code}", code.toUpperCase())
            .headers(headers -> {
                // Передаем сквозной ID в микросервис для синхронизации логов
                headers.set("X-Correlation-ID", finalCorrelationId);
            })
            .retrieve()
            .bodyToMono(Map.class)
            // Маппим результат в ResponseEntity БЕЗ использования блокирующего .block()
            .map(responseMap -> ResponseEntity.ok((Map<String, Object>) responseMap))
            // Если микросервис вернул пустой ответ (404), шлюз корректно отдаст фронтенду 404 Not Found
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * РЕЗЕРВНЫЙ МЕТОД (Fallback)
     * Должен иметь такое же имя, возвращаемый тип и принимать Exception последним аргументом!
     * В данном случае: я делаю проброс ошибки (без свежих данных работать нельзя)
     * Использовать данные из кеша нет смысла - они уже не актуальны
     */
    public ResponseEntity<Map<String, Object>> fallbackCurrencyByCode(String code, Exception e) {
        log.error("Микросервис Валют недоступен при запросе валюты {}. Операция отменена. Ошибка: {}", code, e.getMessage());

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
            "status", "SERVICE_DOWN",
            "message", "Сервис обмена валют временно недоступен. Пожалуйста, повторите попытку позже.",
            "timestamp", LocalDateTime.now().format(formatter)
        ));
    }

    /**
     * End-to-end: Обновление курса валюты по её коду (запрос только к микросервису CurrencyService)
     * Метод PUT для обновления курса валюты по её коду
     *
     * @return ResponseEntity с JSON-ответом от микросервиса или сообщение об ошибке
     */
    @PutMapping("/update-currency/{code}")
    public Mono<ResponseEntity<Map<String, Object>>> updateCurrencyRate(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @PathVariable String code,
            @RequestBody Map<String, Object> incomingPrices
    ) {
        // Извлекаем переданные клиентом цены покупки и продажи
        Double buyPrice = incomingPrices.get("buy") != null ? Double.valueOf(incomingPrices.get("buy").toString()) : null;
        Double sellPrice = incomingPrices.get("sell") != null ? Double.valueOf(incomingPrices.get("sell").toString()) : null;

        // Проверяем, что correlationId - не null
        String finalCorrelationId = (correlationId != null && !correlationId.isBlank())
                ? correlationId
                : UUID.randomUUID().toString();

        // ВАЛИДАЦИЯ: Проверяю, что оба поля заполнены
        if (buyPrice == null || sellPrice == null) {
            return Mono.just(ResponseEntity.badRequest().body(Map.<String, Object>of(
                "status", "ERROR",
                "message", "Оба поля 'buy' и 'sell' обязательны для заполнения."
            )));
        }

        // ВАЛИДАЦИЯ: Курс продажи не должен быть меньше или равен курсу покупки
        if (sellPrice <= buyPrice) {
            log.warn("Валидация провалена для {}: курс продажи ({}) меньше или равен курсу покупки ({})", code, sellPrice, buyPrice);
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.<String, Object>of(
                "status", "VALIDATION_FAILED",
                "message", "Бизнес-ошибка: курс продажи ('sell') должен быть строго больше курса покупки ('buy')."
            )));
        }

        String currentTime = LocalDateTime.now().format(formatter);
        log.info("Обновление {}. Покупка: {}, Продажа: {}. Время: {}", code, buyPrice, sellPrice, currentTime);

        // Формирую полный объект со всеми метаданными для статистики на лету
        Map<String, Object> statRequestBody = Map.of(
            "code", code.toUpperCase(),
            "buy", buyPrice,
            "sell", sellPrice,
            "updatedAt", currentTime // Время отправки для истории/статистики
        );

        // Отправляю расширенный JSON в микросервис Валют
        return this.webClient.put()
            .uri("/api/v1/currencies/code/{code}", code)
            .bodyValue(statRequestBody) // Спринг сам превратит эту Map в полноценный JSON
            .headers(headers -> {
                // Передаю сквозной ID в микросервис для синхронизации логов
                headers.set("X-Correlation-ID", finalCorrelationId);
                // Передаю Idempotency Key
                if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                    headers.set("Idempotency-Key", idempotencyKey);
                }
            })
            .retrieve()
            .bodyToMono(Map.class) // Ожидаю Mono<Map> от микросервиса
            // Превращаю в 200 OK с телом ответа БЕЗ использования .block()
            .map(responseMap -> ResponseEntity.ok((Map<String, Object>) responseMap))
            // Если микросервис ответил 200/204 но без тела, отдаю пустой 200 OK
            .defaultIfEmpty(ResponseEntity.ok().build());
    }


    // TODO: перенести в микросервис агрегатор
    /**
     * Сага (централизованная синхронная)
     * Метод PUT для обновления курса валюты по её коду и последующей отправки обновленных данных в микросервис статистики
     * @param code
     * @param incomingPrices
     * @return
     */
    @PutMapping("/base-currencies/{code}/propagate")
    public ResponseEntity<Map<String, Object>> propagateCurrencyUpdate(
            @PathVariable String code,
            @RequestBody Map<String, Object> incomingPrices
    ) {
        // 1. Валидация входных данных на шлюзе
        Double buyPrice = incomingPrices.get("buy") != null ? Double.valueOf(incomingPrices.get("buy").toString()) : null;
        Double sellPrice = incomingPrices.get("sell") != null ? Double.valueOf(incomingPrices.get("sell").toString()) : null;

        if (buyPrice == null || sellPrice == null || sellPrice <= buyPrice) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", "Неверные курсы валют."));
        }

        // 2. Генерация сквозного Correlation ID для всей цепочки
        String correlationId = UUID.randomUUID().toString();
        String currentTime = LocalDateTime.now().format(formatter); // ISO-8601 формат времени
        String currencyCode = code.toUpperCase();

        log.info("[Correlation-ID: {}] Сага СТАРТ: Обновление курса {} во всей системе.", correlationId, currencyCode);

        boolean currencyUpdated = false;
        Map<String, Object> previousRate = null;

        try {
            // --- ШАГ 1: Бэкап и обновление курса в Микросервисе Валют ---
            log.info("[Correlation-ID: {}] Сага ШАГ 1: Запрос старого курса {} для бэкапа...", correlationId, currencyCode);
            previousRate = this.webClient.get()
                .uri("/api/v1/currencies/code/{code}", currencyCode)
                .header("X-Correlation-ID", correlationId) // Передаем Correlation ID
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            this.webClient.put()
                .uri("/api/v1/currencies/code/{code}", currencyCode)
                .header("X-Correlation-ID", correlationId)
                .bodyValue(Map.of("buy", buyPrice, "sell", sellPrice, "updatedAt", currentTime))
                .retrieve()
                .toBodilessEntity()
                .block();

            currencyUpdated = true;
            log.info("[Correlation-ID: {}] Сага ШАГ 1: Курс {} успешно обновлен.", correlationId, currencyCode);

            // --- ШАГ 2: Отправка лога в универсальный контроллер статистики ---
            log.info("[Correlation-ID: {}] Сага ШАГ 2: Фиксация события в микросервисе статистики...", correlationId);

            Map<String, Object> eventDto = Map.of(
                "serviceName", "GATEWAY-API",
                "eventType", "CURRENCY_RATE_UPDATED", // Строго совпадает с вашим Enum
                "entityType", "Currency",             // Строго совпадает с вашим Enum
                "entityId", 0L,                       // Для валют передаем заглушку Long
                "eventTime", currentTime,
                "payload", String.format("{\"code\":\"%s\",\"buy\":%s,\"sell\":%s}", currencyCode, buyPrice, sellPrice)
            );

            this.webClient.post()
                .uri("/api/v1/integration/events") // Наш новый URL
                .header("X-Correlation-ID", correlationId)
                .bodyValue(eventDto)
                .retrieve()
                .toBodilessEntity()
                .block();

            log.info("[Correlation-ID: {}] Сага УСПЕШНО ЗАВЕРШЕНА.", correlationId);
            return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Курс успешно изменен и залогирован.",
                "correlationId", correlationId
            ));

        } catch (Exception e) {
            log.error("[Correlation-ID: {}] Сага ПРЕРВАНА ошибкой: {}. Запуск отката...", correlationId, e.getMessage());

            // Переходим к компенсирующей транзакции
            rollbackSaga(correlationId, currencyCode, previousRate, currencyUpdated, currentTime, e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status", "SAGA_ROLLED_BACK",
                "correlationId", correlationId,
                "message", "Сбой операции. Данные успешно откатаны. Причина: " + e.getMessage()
            ));
        }
    }

    // Компенсирующая транзакция (Откат Саги)
    private void rollbackSaga(String correlationId, String code, Map<String, Object> previousRate,
                              boolean rollbackCurrency, String time, String errorMsg) {
        if (!rollbackCurrency || previousRate == null) {
            return;
        }

        try {
            log.info("[Correlation-ID: {}] Откат: Возвращаем старый курс валюты {} в микросервис Валют...", correlationId, code);
            this.webClient.put()
                .uri("/api/v1/currencies/code/{code}", code)
                .header("X-Correlation-ID", correlationId)
                .bodyValue(Map.of("buy", previousRate.get("buy"), "sell", previousRate.get("sell"), "updatedAt", time))
                .retrieve()
                .toBodilessEntity()
                .block();

            log.info("[Correlation-ID: {}] Откат: Фиксация отката в микросервисе статистики...", correlationId);
            Map<String, Object> rollbackEventDto = Map.of(
                "serviceName", "GATEWAY-API",
                "eventType", "CURRENCY_RATE_ROLLBACK", // Наш Enum для отката
                "entityType", "Currency",
                "entityId", 0L,
                "eventTime", time,
                "payload", String.format("{\"code\":\"%s\",\"status\":\"ROLLED_BACK\"}", code)
            );

            this.webClient.post()
                .uri("/api/v1/integration/events")
                .header("X-Correlation-ID", correlationId)
                .bodyValue(rollbackEventDto)
                .retrieve()
                .toBodilessEntity()
                .block();

        } catch (Exception rollbackException) {
            // КРИТИЧЕСКИЙ СЦЕНАРИЙ: Сбой самого отката (Запись инцидента в integration_failures)
            log.error("[Correlation-ID: {}] КРИТИЧЕСКИЙ СБОЙ: Не удалось выполнить откат! Пишем в DLQ...", correlationId, rollbackException);

            try {
                Map<String, Object> failureDto = Map.of(
                    "sourceService", "GATEWAY-API",
                    "targetService", "CURRENCY-SERVICE",
                    "operation", "ROLLBACK", // Наш Enum для типа операции сбоя
                    "errorCode", "COMPENSATION_FAILED: " + rollbackException.getMessage(),
                    "occurredAt", time,
                    "resolved", false
                );

                this.webClient.post()
                    .uri("/api/v1/integration/failures") // Наш URL для мертвой петли
                    .header("X-Correlation-ID", correlationId)
                    .bodyValue(failureDto)
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            } catch (Exception dlqException) {
                log.error("[Correlation-ID: {}] ТОТАЛЬНЫЙ КРАХ: Микросервис статистики недоступен для записи сбоя отката!", correlationId, dlqException);
            }
        }
    }
}

