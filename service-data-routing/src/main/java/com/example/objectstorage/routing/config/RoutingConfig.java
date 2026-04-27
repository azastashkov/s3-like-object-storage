package com.example.objectstorage.routing.config;

import com.example.objectstorage.core.http.RoundRobinClient;
import com.example.objectstorage.routing.client.PlacementClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(RoutingProperties.class)
public class RoutingConfig {

    @Bean
    public RestClient httpClient(RoutingProperties props) {
        HttpClient jdk = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(5_000))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(jdk);
        factory.setReadTimeout(Duration.ofMillis(props.readTimeoutMs()));
        return RestClient.builder().requestFactory(factory).build();
    }

    @Bean
    public RoundRobinClient placementHttp(RoutingProperties props, RestClient httpClient) {
        return new RoundRobinClient(props.placementBaseUrls(), httpClient);
    }

    @Bean
    public PlacementClient placementClient(RoundRobinClient placementHttp, RoutingProperties props) {
        return new PlacementClient(placementHttp, props.clusterCacheTtlSeconds());
    }
}
