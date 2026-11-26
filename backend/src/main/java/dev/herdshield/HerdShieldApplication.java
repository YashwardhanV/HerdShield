package dev.herdshield;

import dev.herdshield.config.LabProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(LabProperties.class)
public class HerdShieldApplication {

    public static void main(String[] args) {
        SpringApplication.run(HerdShieldApplication.class, args);
    }
}
