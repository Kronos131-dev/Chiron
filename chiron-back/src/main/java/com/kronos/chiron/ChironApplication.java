package com.kronos.chiron;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.ZoneId;
import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class ChironApplication {

    private static final ZoneId APPLICATION_ZONE = ZoneId.of("Europe/Paris");

    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone(APPLICATION_ZONE));
    }

    @Bean
    public Clock clock() {
        return Clock.system(APPLICATION_ZONE);
    }

    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.withMaxMessages(10);
    }

    public static void main(String[] args) {
        SpringApplication.run(ChironApplication.class, args);
    }

}
