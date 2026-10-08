package com.example.demo.config;

import com.example.demo.service.DemoDataCleanupService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "app.cleanup-demo-data", havingValue = "true")
public class DemoDataCleanup {
    @Bean
    CommandLineRunner removeDemoProducts(DemoDataCleanupService cleanupService) {
        return args -> cleanupService.removeSeedProducts();
    }
}
