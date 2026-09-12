package ru.amra.market.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import ru.amra.market.AmraMerchMarketBackendApplication;

class ModularityTests {

    private static final ApplicationModules MODULES = ApplicationModules.of(AmraMerchMarketBackendApplication.class);

    @Test
    void moduleStructureIsValid() {
        MODULES.verify();
    }

    @Test
    void expectedModulesAreDetected() {
        var moduleNames = StreamSupport.stream(MODULES.spliterator(), false)
                .map(module -> module.getIdentifier().toString())
                .toList();

        assertThat(moduleNames)
                .containsExactlyInAnyOrder(
                        "administration",
                        "cart",
                        "catalog",
                        "customer",
                        "identityaccess",
                        "inventory",
                        "ordering",
                        "outbox",
                        "platform",
                        "pricing");
    }
}
