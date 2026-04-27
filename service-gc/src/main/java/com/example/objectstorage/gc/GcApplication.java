package com.example.objectstorage.gc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GcApplication {
    public static void main(String[] args) {
        SpringApplication.run(GcApplication.class, args);
    }
}
