package com.project.back_end_proyecto.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class PublicPlacesHttpConfiguration {

    @Bean
    @ConditionalOnMissingBean(RestClient.Builder.class)
    public RestClient.Builder publicPlacesRestClientBuilder() {
        return RestClient.builder();
    }
}
