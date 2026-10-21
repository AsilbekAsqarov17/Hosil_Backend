package uz.hosil.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application entry point for the Hosil Platform Modular Monolith.
 * Houses core domain modules: Identity, Farm, Marketplace, Logistics, Reputation, and Admin.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class HosilPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(HosilPlatformApplication.class, args);
    }
}
