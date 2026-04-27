package com.example.objectstorage.iam.config;

import com.example.objectstorage.core.auth.JwtIssuer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(IamProperties.class)
public class IamConfig {

    @Bean
    public JwtIssuer jwtIssuer(IamProperties props) {
        return new JwtIssuer(props.secret(), Duration.ofMinutes(props.ttlMinutes()), props.issuer());
    }
}
