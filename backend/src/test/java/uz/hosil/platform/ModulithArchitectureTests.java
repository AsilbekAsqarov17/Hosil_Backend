package uz.hosil.platform;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Validates module encapsulation boundaries across:
 * identity, farm, marketplace, logistics, reputation, and admin.
 */
class ModulithArchitectureTests {

    @Test
    void verifyModularStructure() {
        ApplicationModules.of(HosilPlatformApplication.class).verify();
    }
}
