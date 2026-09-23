package io.github.andis382.carelog;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class CareLogApplication {

    public static void main(String[] args) {
        // Hibernate binds LocalTime and LocalDate through java.sql types in the JVM's zone. In UTC a
        // dose at 08:00 is stored as 08:00 whatever zone the server runs in; circle times are
        // always computed with the circle's own zone.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(CareLogApplication.class, args);
    }
}
