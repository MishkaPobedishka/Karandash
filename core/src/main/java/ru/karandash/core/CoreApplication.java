package ru.karandash.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@EnableScheduling
@SpringBootApplication
public class CoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoreApplication.class, args);
    }

    /** Отдельный бин, чтобы в тестах дневника можно было зафиксировать день и время. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}

