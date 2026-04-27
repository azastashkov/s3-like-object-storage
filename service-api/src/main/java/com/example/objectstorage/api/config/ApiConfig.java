package com.example.objectstorage.api.config;

import com.example.objectstorage.api.client.DataRoutingClient;
import com.example.objectstorage.api.client.IamClient;
import com.example.objectstorage.api.client.MetadataClient;
import com.example.objectstorage.api.client.PlacementApiClient;
import com.example.objectstorage.api.security.AclChecker;
import com.example.objectstorage.core.auth.JwtValidator;
import com.example.objectstorage.core.http.RoundRobinClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(ApiProperties.class)
public class ApiConfig {

    @Bean
    public RestClient restClient() {
        HttpClient jdk = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(jdk);
        factory.setReadTimeout(Duration.ofSeconds(60));
        return RestClient.builder().requestFactory(factory).build();
    }

    @Bean
    public JwtValidator jwtValidator(ApiProperties props) {
        return new JwtValidator(props.jwt().secret(), props.jwt().issuer());
    }

    @Bean
    public IamClient iamClient(ApiProperties p, RestClient http) {
        return new IamClient(new RoundRobinClient(p.iamBaseUrls(), http));
    }

    @Bean
    public MetadataClient metadataClient(ApiProperties p, RestClient http) {
        return new MetadataClient(new RoundRobinClient(p.metadataBaseUrls(), http));
    }

    @Bean
    public PlacementApiClient placementApiClient(ApiProperties p, RestClient http) {
        return new PlacementApiClient(new RoundRobinClient(p.placementBaseUrls(), http));
    }

    @Bean
    public DataRoutingClient dataRoutingClient(ApiProperties p, RestClient http) {
        return new DataRoutingClient(new RoundRobinClient(p.dataRoutingBaseUrls(), http));
    }

    @Bean
    public AclChecker aclChecker(IamClient iam, ApiProperties p) {
        return new AclChecker(iam, p.aclCacheTtlSeconds());
    }
}
