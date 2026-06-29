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

    // Метод PUT для обновления курса валюты по её коду
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
}

