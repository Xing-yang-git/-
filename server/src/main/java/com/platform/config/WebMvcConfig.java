package com.platform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path absPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        String location = "file:" + absPath.toString().replace('\\', '/') + "/";
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
