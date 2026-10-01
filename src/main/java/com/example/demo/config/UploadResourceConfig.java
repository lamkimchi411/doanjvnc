package com.example.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;
import java.nio.file.Path;

@Configuration
public class UploadResourceConfig implements WebMvcConfigurer {
    @Value("${app.upload-dir:uploads}") private String uploadDir;
    @Override public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path folder = Path.of(uploadDir).toAbsolutePath().normalize();
        registry.addResourceHandler("/uploads/**").addResourceLocations(folder.toUri().toString());
    }
}
