package com.kronos.chiron;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import io.github.cdimascio.dotenv.Dotenv;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.TimeZone;

@SpringBootApplication
public class ChironApplication {

    @PostConstruct
    public void init() {
        // Force le fuseau horaire de toute l'application sur Paris
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Paris"));
        System.out.println("⏰ Fuseau horaire réglé sur : " + TimeZone.getDefault().getID());
    }

    @Bean
    public ChatMemory chatMemory() {
        // Chiron se souviendra des 10 derniers échanges de la conversation
        return MessageWindowChatMemory.withMaxMessages(10);
    }

	public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .load();

        dotenv.entries().forEach(entry -> {
            System.setProperty(entry.getKey(), entry.getValue());
        });

        SpringApplication.run(ChironApplication.class, args);
    }

}
