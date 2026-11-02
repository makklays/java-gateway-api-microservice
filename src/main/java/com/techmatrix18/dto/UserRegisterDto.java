package com.techmatrix18.dto;

/**
 * DTO for user registration data.
 *
 * @author Alexander Kuziv <makklays@gmail.com>
 * @company TechMatrix18
 * @version 0.0.1
 * @since 02.11.2026
 */

public record UserRegisterDto(String username, String email, String password, String mobile, String gender, Integer age) {}

