package ru.amra.market.inventory.application;

import java.time.Instant;
import ru.amra.market.inventory.domain.InventoryMutation;

/** Exact protected warehouse mutation response with representation metadata. */
public record WarehouseMutationResult(String warehouseCode, InventoryMutation mutation, Instant balanceUpdatedAt) {}
