package pt.lisboapedala;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LisboaPedalaApplication {

    public static void main(String[] args) {
        SpringApplication.run(LisboaPedalaApplication.class, args);
    }

    /** Relógio único injetado em todos os módulos (ARCHITECTURE.md §2.1, FF-16). */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
