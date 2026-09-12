package ru.amra.market;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import ru.amra.market.testing.PostgreSqlIntegrationTest;

@SpringBootTest
class AmraMerchMarketBackendApplicationTests extends PostgreSqlIntegrationTest {

    @Test
    void contextLoads() {
        // The initial smoke test proves that the generated application context starts.
    }
}
