package com.kronos.chiron.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${chiron.uploads-dir:./uploads/images}")
    private String uploadsDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // WHY: chemin résolu en absolu pour rester valide quel que soit le répertoire courant.
        String absolutePath = "file:" + Paths.get(uploadsDir).toAbsolutePath().normalize() + "/";
        registry.addResourceHandler("/api/images/**")
                .addResourceLocations(absolutePath, "classpath:/static/images/");
    }
}
