package com.example.objectstorage.metadata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class MetadataApplication {
    public static void main(String[] args) {
        SpringApplication.run(MetadataApplication.class, args);
    }
}
