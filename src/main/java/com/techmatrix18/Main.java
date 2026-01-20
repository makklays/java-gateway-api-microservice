package com.techmatrix18;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main class to start the service discovery microservice application.
 *
 * @author Alexander Kuziv <makklays@gmail.com>
 * @company TechMatrix18
 * @version 0.0.1
 * @since 19.01.2026
 */
@SpringBootApplication
public class Main {
    public static void main(String[] args) {
        System.out.println("Hello world!");

        SpringApplication.run(Main.class, args);
    }
}

