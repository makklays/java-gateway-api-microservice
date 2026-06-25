package com.techmatrix18.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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
public class UserController {

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

