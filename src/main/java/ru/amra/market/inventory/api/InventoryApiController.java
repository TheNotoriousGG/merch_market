package ru.amra.market.inventory.api;

import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.amra.market.inventory.application.AvailabilityStatus;
import ru.amra.market.inventory.application.GetInventoryAvailability;
import ru.amra.market.platform.generated.api.InventoryApi;
import ru.amra.market.platform.generated.model.InventoryAvailabilityDto;
import ru.amra.market.platform.generated.model.InventoryAvailabilityListDto;

/** HTTP adapter for anonymous privacy-preserving inventory availability. */
@RestController
public final class InventoryApiController implements InventoryApi {
    private final GetInventoryAvailability availability;

    public InventoryApiController(GetInventoryAvailability availability) {
        this.availability = availability;
    }

    @Override
    public ResponseEntity<InventoryAvailabilityListDto> getInventoryAvailability(List<UUID> variantId) {
        var snapshot = availability.execute(variantId);
        var items = snapshot.items().stream()
                .map(item -> new InventoryAvailabilityDto(
                        item.variantId(),
                        item.status() == AvailabilityStatus.IN_STOCK
                                ? InventoryAvailabilityDto.StatusEnum.IN_STOCK
                                : InventoryAvailabilityDto.StatusEnum.OUT_OF_STOCK))
                .toList();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new InventoryAvailabilityListDto(items, snapshot.asOf()));
    }
}
