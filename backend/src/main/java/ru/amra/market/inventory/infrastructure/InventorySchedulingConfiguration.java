package ru.amra.market.inventory.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables bounded inventory scheduling with validated deployment properties. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(InventoryExpiryProperties.class)
class InventorySchedulingConfiguration {}
