package com.example.dreamdiary;

import com.example.dreamdiary.security.AppSecurityProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppSecurityProperties.class)
public class DreamDiaryApplication {

    public static void main(String[] args) {
        SpringApplication.run(DreamDiaryApplication.class, args);
    }
}
