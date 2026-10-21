package com.example.retail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.Violations;
import org.springframework.modulith.docs.Documenter;

class ModulithArchitectureTests {

    private final ApplicationModules modules = ApplicationModules.of(RetailStoreModulithApplication.class);

    @Test
    @DisplayName("Should discover all business, shared, and UI modules")
    void shouldDiscoverAllBusinessModules() {
        assertThat(modules.getModuleByName("catalog")).isPresent();
        assertThat(modules.getModuleByName("inventory")).isPresent();
        assertThat(modules.getModuleByName("orders")).isPresent();
        assertThat(modules.getModuleByName("payments")).isPresent();
        assertThat(modules.getModuleByName("shared")).isPresent();
        assertThat(modules.getModuleByName("ui")).isPresent();
    }

    @Test
    @DisplayName("Should verify module boundaries and acyclic dependencies")
    void shouldVerifyModularity() {
        modules.verify();
    }

    @Test
    @DisplayName("Should detect prohibited module dependencies and internal access violations")
    void shouldDetectProhibitedModuleDependencies() {
        ApplicationModules invalidModules = ApplicationModules.of(
            com.example.retail.violationtest.InvalidApp.class,
            location -> true
        );
        assertThatThrownBy(invalidModules::verify)
            .isInstanceOf(Violations.class);
    }

    @Test
    @DisplayName("Should generate documentation for modules")
    void shouldGenerateDocumentation() {
        new Documenter(modules).writeDocumentation();
    }
}
