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

    public CurrencyController(WebClient.Builder webClientBuilder,
                              @Value("${microservices.currency-url}") String currencyServiceUrl) {
        // Spring подставит сюда готовый URL, и я соберу WebClient
        this.webClient = webClientBuilder.baseUrl(currencyServiceUrl).build();
    }

    /**
     * Получение списка базовых валют и курсов валют
     * Этот метод делает HTTP GET запрос к удаленному микросервису валют.
     * Если микросервис недоступен, срабатывает fallback метод.
     *
     * @return ResponseEntity с JSON-ответом от микросервиса или сообщение об ошибке
     */
    @Retry(name = "currencyServiceRetry", fallbackMethod = "fallbackBaseCurrencies")
    @CircuitBreaker(name = "currencyServiceCB", fallbackMethod = "fallbackBaseCurrencies")
    @GetMapping("/base-currencies")
    public ResponseEntity<Map<String, Object>> getBaseCurrencies() {
        log.info("Fetching base currencies at {}", LocalDateTime.now().format(formatter));

        // 1. Делаем HTTP GET запрос к удаленному микросервису
        Map response = this.webClient.get()
            .uri("/api/v1/currencies/base-currencies")  // Эндпоинт на том конце
            .retrieve()                                     // Получаем ответ
            .bodyToMono(Map.class)                          // Превращаем JSON в Map
            .block();                                       // Ждем ответ (синхронно)

        // 2. Возвращаем полученный от микросервиса JSON обратно клиенту
        return ResponseEntity.ok(response);
    }

    /**
     * РЕЗЕРВНЫЙ МЕТОД (Fallback)
     * Должен иметь такое же имя, возвращаемый тип и принимать Exception последним аргументом!
     * В данном случае: я делаю проброс ошибки (без свежих данных работать нельзя)
     * Использовать данные из кеша нет смысла - они уже добавлены (они такие же)
     */
    public ResponseEntity<Map<String, Object>> fallbackBaseCurrencies(Exception e) {
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
    @GetMapping("/currency/{code}")
    public ResponseEntity<Map> getCurrencyByCode(@PathVariable String code) {
        log.info("Шлюз: Запрос курса для валюты: {}", code);

        // Передаем переменную code в URI удаленного микросервиса
        Map response = this.webClient.get()
            .uri("/api/v1/currencies/currency/{code}", code)   // Подстановка параметра
            .retrieve()
            .bodyToMono(Map.class)
            .block(); // Синхронное ожидание ответа

        return ResponseEntity.ok(response);
    }

    /**
     * РЕЗЕРВНЫЙ МЕТОД (Fallback)
     * Должен иметь такое же имя, возвращаемый тип и принимать Exception последним аргументом!
     * В данном случае: я делаю проброс ошибки (без свежих данных работать нельзя)
     * Использовать данные из кеша нет смысла - они уже добавлены (они такие же)
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
     * End-to-end: обновление курса валюты по её коду (запрос только к микросервису CurrencyService)
     * Метод PUT для обновления курса валюты по её коду
     *
     * @return ResponseEntity с JSON-ответом от микросервиса или сообщение об ошибке
     */
    @PutMapping("/base-currencies/{code}")
    public ResponseEntity<Map> updateCurrencyRate(
            @PathVariable String code,
            @RequestBody Map<String, Object> incomingPrices) { // Принимаем только цены от клиента

        // Извлекаем переданные клиентом цены покупки и продажи
        Double buyPrice = incomingPrices.get("buy") != null ? Double.valueOf(incomingPrices.get("buy").toString()) : null;
        Double sellPrice = incomingPrices.get("sell") != null ? Double.valueOf(incomingPrices.get("sell").toString()) : null;

        // ВАЛИДАЦИЯ: Проверяем, что оба поля заполнены
        if (buyPrice == null || sellPrice == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "ERROR",
                "message", "Оба поля 'buy' и 'sell' обязательны для заполнения."
            ));
        }

        // ВАЛИДАЦИЯ: Курс продажи не должен быть меньше или равен курсу покупки
        if (sellPrice <= buyPrice) {
            log.warn("Валидация провалена для {}: курс продажи ({}) меньше или равен курсу покупки ({})", code, sellPrice, buyPrice);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "status", "VALIDATION_FAILED",
                "message", "Бизнес-ошибка: курс продажи ('sell') должен быть строго больше курса покупки ('buy')."
            ));
        }

        String currentTime = LocalDateTime.now().format(formatter);
        log.info("Шлюз: Обновление {}. Покупка: {}, Продажа: {}. Время: {}", code, buyPrice, sellPrice, currentTime);

        // Формируем полный объект со всеми метаданными для статистики на лету
        Map<String, Object> statRequestBody = Map.of(
            "сode", code.toUpperCase(),
            "buy", buyPrice,
            "sell", sellPrice,
            "updatedAt", currentTime // Время отправки для истории/статистики
        );

        // Отправляем расширенный JSON в микросервис Валют
        Map response = this.webClient.put()
            .uri("/api/v1/currencies/base-currencies/{code}", code)
            .bodyValue(statRequestBody) // Спринг сам превратит эту Map в полноценный JSON
            .retrieve()
            .bodyToMono(Map.class)
            .block();

        return ResponseEntity.ok(response);
    }

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
            @RequestBody Map<String, Object> incomingPrices) {

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
                .uri("/api/v1/currencies/base-currencies/{code}", currencyCode)
                .header("X-Correlation-ID", correlationId) // Передаем Correlation ID
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            this.webClient.put()
                .uri("/api/v1/currencies/base-currencies/{code}", currencyCode)
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
                .uri("/api/v1/currencies/base-currencies/{code}", code)
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

