package ru.amra.market.platform;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class PlatformConfiguration {

    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }
}
