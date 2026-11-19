package com.project.back_end_proyecto.config;

import com.project.back_end_proyecto.service.publicplaces.OpenStreetMapParser;
import com.project.back_end_proyecto.service.publicplaces.OpenStreetMapPlaceSource;
import com.project.back_end_proyecto.service.publicplaces.DestinoCoordinateCache;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class PublicPlacesHttpConfigurationTest {

    @Test
    void createsOpenStreetMapSourceByDependencyInjectionWhenOracleProfileIsActive() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("oracle");
            context.register(PublicPlacesHttpConfiguration.class, OpenStreetMapPlaceSource.class,
                    OpenStreetMapParser.class, DestinoCoordinateCache.class);
            context.refresh();

            assertNotNull(context.getBean(RestClient.Builder.class));
            assertNotNull(context.getBean(OpenStreetMapPlaceSource.class));
        }
    }

    @Test
    void exposesSharedHttpClientBuilderInLocalNoDbProfile() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("local-no-db");
            context.register(PublicPlacesHttpConfiguration.class);
            context.refresh();

            assertNotNull(context.getBean(RestClient.Builder.class));
        }
    }
}
