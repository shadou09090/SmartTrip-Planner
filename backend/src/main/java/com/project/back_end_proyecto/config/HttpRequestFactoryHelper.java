package com.project.back_end_proyecto.config;

import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.net.http.HttpClient;
import java.time.Duration;

public final class HttpRequestFactoryHelper {

    private HttpRequestFactoryHelper() {
    }

    public static ClientHttpRequestFactory create(long connectTimeoutMillis, long readTimeoutMillis) {
        try {
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(Math.max(1000, connectTimeoutMillis)))
                    .build();
            JdkClientHttpRequestFactory jdk = new JdkClientHttpRequestFactory(httpClient);
            jdk.setReadTimeout(Duration.ofMillis(Math.max(1000, readTimeoutMillis)));
            return jdk;
        } catch (Throwable fallback) {
            SimpleClientHttpRequestFactory simple = new SimpleClientHttpRequestFactory();
            simple.setConnectTimeout((int) Math.max(1000, connectTimeoutMillis));
            simple.setReadTimeout((int) Math.max(1000, readTimeoutMillis));
            return simple;
        }
    }
}
