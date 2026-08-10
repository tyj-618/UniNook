package com.uninook.user;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class AvatarResourceConfig implements WebMvcConfigurer {

    private final String resourceLocation;

    public AvatarResourceConfig(AvatarStorageProperties properties) {
        String location = Path.of(properties.getAvatarDirectory()).toAbsolutePath().normalize().toUri().toString();
        this.resourceLocation = location.endsWith("/") ? location : location + "/";
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/avatars/**")
                .addResourceLocations(resourceLocation);
    }
}
