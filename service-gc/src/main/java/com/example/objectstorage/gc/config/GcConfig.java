package com.example.objectstorage.gc.config;

import com.example.objectstorage.core.http.RoundRobinClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(GcProperties.class)
public class GcConfig {

    @Bean
    public RestClient httpClient() {
        HttpClient jdk = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(jdk);
        factory.setReadTimeout(Duration.ofSeconds(60));
        return RestClient.builder().requestFactory(factory).build();
    }

    @Bean
    public RoundRobinClient placementHttp(GcProperties p, RestClient http) {
        return new RoundRobinClient(p.placementBaseUrls(), http);
    }

    @Bean
    public RoundRobinClient dataRoutingHttp(GcProperties p, RestClient http) {
        return new RoundRobinClient(p.dataRoutingBaseUrls(), http);
    }
}
