package com.smarttrip.oraclewallet;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class OracleWalletTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(OracleWalletTestApplication.class, args);
    }

    @Bean
    CommandLineRunner testOracleConnection() {
        return new OracleConnectionCheck();
    }
}
