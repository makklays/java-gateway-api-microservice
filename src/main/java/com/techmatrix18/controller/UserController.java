package com.techmatrix18.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * REST controller for managing users.
 * Exposes endpoints for retrieving and manipulating user data.
 *
 * @author Alexander Kuziv <makklays@gmail.com>
 * @company TechMatrix18
 * @version 0.0.1
 * @since 28.01.2026
 */

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "User Controller", description = "Управление пользователями и аутентификация")
public class UserController {

    private final WebClient webClient;

    public UserController(
            @Value("${microservices.oauth2-url}") String oauth2ServiceUrl,
            WebClient.Builder webClient)
    {
        this.webClient = webClient.baseUrl(oauth2ServiceUrl).build();
    }

    /**
     * Public endpoint that forwards a request to the registration endpoint of 'OAuth2 service'.
     * This method demonstrates how to use WebClient to make an HTTP GET request to another microservice.
     *
     * @return A Mono containing the response from the OAuth2 service.
     */
    @Operation(
            summary = "Регистрация нового пользователя",
            description = "Принимает данные пользователя и пробрасывает запрос в OAuth2 сервис с поддержкой идемпотентности.",
            parameters = {
                    @Parameter(
                            name = "Idempotency-Key",
                            in = ParameterIn.HEADER,
                            description = "Уникальный UUID ключ для предотвращения дубликатов запросов",
                            required = false,
                            schema = @Schema(type = "string", example = "123e4567-e89b-12d3-a456-426614174000")
                    )
            },
            responses = {
                    @ApiResponse(responseCode = "201", description = "Пользователь успешно создан / Токены сгенерированы"),
                    @ApiResponse(responseCode = "200", description = "Повторный запрос с тем же Idempotency-Key - возвращает тот же результат"),
                    @ApiResponse(responseCode = "400", description = "Неверный формат входных данных")
            }
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Данные для регистрации пользователя",
            required = true,
            content = @Content(schema = @Schema(implementation = com.techmatrix18.dto.UserRegisterDto.class))
    )
    @PostMapping("/registration")
    public Mono<ResponseEntity<String>> publicEndpoint(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody String request    // Принимаю просто как сырой текст JSON - без валидации и использовании DTO-класса
    ) {
        // отправляю запрос
        return this.webClient.post()
                .uri("/api/v1/oauth2/registration")    // Путь к эндпоинту в OAuth2 сервисе
                .bodyValue(request)                        // Передаю JSON-строку, полученную от фронтенда
                //.header("Idempotency-Key", idempotencyKey)
                .headers(headers -> {
                    // 1. Явно жестко задаем Content-Type (хотя WebClient делает это сам)
                    headers.set("Content-Type", "application/json");
                    // 2. Если фронтенд передал ключ идемпотентности, пробрасываю его в микросервис
                    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                        headers.set("Idempotency-Key", idempotencyKey);
                    }
                })
                .retrieve()
                // Использую toEntity вместо bodyToMono, чтобы Gateway вернул фронтенду
                // точный HTTP-статус (например, 201 Created или 200 OK в случае повтора)
                .toEntity(String.class);
    }

    /**
     * Public endpoint that forwards a request to the login endpoint of 'OAuth2 service'.
     *
     * @return A Mono containing the response from the OAuth2 service.
     */
    @Operation(
            summary = "Аутентификация пользователя (Вход)",
            description = "Принимает учетные данные пользователя, обогащает запрос сетевыми заголовками (IP, User-Agent) и запрашивает токены у сервиса OAuth2.",
            parameters = {
                    @Parameter(
                            name = "Idempotency-Key",
                            in = ParameterIn.HEADER,
                            description = "Уникальный UUID ключ для предотвращения повторной отправки одной и той же формы входа",
                            required = false,
                            schema = @Schema(type = "string", example = "987f6543-e89b-12d3-a456-426614174000")
                    )
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "Успешный вход — возвращена пара токенов (Access & Refresh Token)"),
                    @ApiResponse(responseCode = "401", description = "Неверный логин или пароль"),
                    @ApiResponse(responseCode = "400", description = "Неверный формат входных данных")
            }
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Учетные данные для входа (обычно LoginRequestDto/AuthDto)",
            required = true,
            // Здесь вы можете указать класс DTO для логина (например, UserLoginDto.class), если он у вас есть
            content = @Content(schema = @Schema(type = "object", example = "{\"username\":\"alex\",\"password\":\"secret123\"}"))
    )
    @PostMapping("/login")
    public Mono<ResponseEntity<String>> login(
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestBody String request,
        ServerHttpRequest httpRequest
    ) {
        // 1. Получаем IP-адрес реального пользователя
        String clientIp = httpRequest.getRemoteAddress() != null
                ? httpRequest.getRemoteAddress().getAddress().getHostAddress()
                : "unknown";

        // 2. Получаем User-Agent (браузер / устройство) пользователя
        String userAgent = httpRequest.getHeaders().getFirst("User-Agent");

        // отправляю запрос
        return this.webClient.post()
                .uri("/api/v1/oauth2/login")   // Путь к эндпоинту в OAuth2 сервисе
                .bodyValue(request)                // Передаю JSON-строку, полученную от фронтенда
                .headers(headers -> {
                    // 1. Явно жестко задаем Content-Type (хотя WebClient делает это сам)
                    headers.set("Content-Type", "application/json");
                    // 2. Если фронтенд передал ключ идемпотентности, пробрасываю его в микросервис
                    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                        headers.set("Idempotency-Key", idempotencyKey);
                    }
                    // 3. Пробрасываем сетевые данные в заголовках по стандарту HTTP
                    headers.set("X-Forwarded-For", clientIp);
                    if (userAgent != null) {
                        headers.set("User-Agent", userAgent);
                    }
                })
                .retrieve()
                .toEntity(String.class);
    }

    /**
     * Public endpoint that forwards a request to the refresh-token endpoint of 'OAuth2 service'.
     *
     * @return A Mono containing the response from the OAuth2 service.
     */
    @Operation(
            summary = "Обновление пары токенов (Refresh Token)",
            description = "Принимает текущий Refresh Token, проверяет его валидность в сервисе OAuth2 и возвращает новую пару токенов доступа.",
            parameters = {
                    @Parameter(
                            name = "Idempotency-Key",
                            in = ParameterIn.HEADER,
                            description = "Уникальный UUID ключ для предотвращения одновременных повторных запросов на обновление токенов",
                            required = false,
                            schema = @Schema(type = "string", example = "a1b2c3d4-e89b-12d3-a456-426614174000")
                    )
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "Токены успешно обновлены — возвращена новая пара токенов"),
                    @ApiResponse(responseCode = "403", description = "Refresh Token недействителен или его время жизни истекло"),
                    @ApiResponse(responseCode = "400", description = "Неверный формат входящего запроса")
            }
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Тело запроса, содержащее Refresh Token",
            required = true,
            content = @Content(schema = @Schema(type = "object", example = "{\"refreshToken\":\"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...\"}"))
    )
    @PostMapping("/refresh-token")
    public Mono<ResponseEntity<String>> refreshToken(
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestBody String request
    ) {
        // отправляю запрос
        return this.webClient.post()
                .uri("/api/v1/oauth2/refresh-token")   // Путь к эндпоинту в OAuth2 сервисе
                .bodyValue(request)                        // Передаю JSON-строку, полученную от фронтенда
                .headers(headers -> {
                    // 1. Явно жестко задаем Content-Type (хотя WebClient делает это сам)
                    headers.set("Content-Type", "application/json");
                    // 2. Если фронтенд передал ключ идемпотентности, пробрасываю его в микросервис
                    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                        headers.set("Idempotency-Key", idempotencyKey);
                    }
                })
                .retrieve()
                .toEntity(String.class);
    }

    //---- остальные эндпоинты ----

    @GetMapping("/user")
    @PreAuthorize("hasRole('USER')") // доступ только для роли USER
    public Mono<String> userEndpoint() {
        return Mono.just("Hello, USER!");
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')") // доступ только для роли ADMIN
    public Mono<String> adminEndpoint() {
        return Mono.just("Hello, ADMIN!");
    }
}

