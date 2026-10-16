package com.vinsguru.netflux.recommendation;

import org.springframework.boot.restclient.autoconfigure.RestClientBuilderConfigurer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

/**
 * Provides one shared {@link RestClient.Builder} so the auto-configured mock server customizer is applied only once
 * and a single {@code MockRestServiceServer} covers both upstream clients.
 */
@TestConfiguration(proxyBeanMethods = false)
class SharedRestClientBuilderConfig {

    @Bean
    RestClient.Builder restClientBuilder(RestClientBuilderConfigurer configurer) {
        return configurer.configure(RestClient.builder());
    }
}
