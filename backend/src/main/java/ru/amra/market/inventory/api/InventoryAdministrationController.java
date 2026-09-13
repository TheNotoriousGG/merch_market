package ru.amra.market.inventory.api;

import static java.util.Objects.requireNonNull;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.amra.market.inventory.application.InventoryAdminBalanceView;
import ru.amra.market.inventory.application.InventoryVersionEtag;
import ru.amra.market.inventory.application.ManageWarehouseInventory;
import ru.amra.market.inventory.application.WarehouseMutationResult;
import ru.amra.market.inventory.domain.InventoryMovement;
import ru.amra.market.platform.generated.api.InventoryAdministrationApi;
import ru.amra.market.platform.generated.model.AdjustStockRequestDto;
import ru.amra.market.platform.generated.model.InventoryBalanceDto;
import ru.amra.market.platform.generated.model.InventoryMovementDto;
import ru.amra.market.platform.generated.model.InventoryMovementViewDto;
import ru.amra.market.platform.generated.model.InventoryMutationResultDto;
import ru.amra.market.platform.generated.model.InventoryStockItemDto;
import ru.amra.market.platform.generated.model.InventoryWarehouseOverviewDto;
import ru.amra.market.platform.generated.model.ReceiveStockRequestDto;

/** Generated-contract HTTP adapter for protected primary-warehouse operations. */
@RestController
public final class InventoryAdministrationController implements InventoryAdministrationApi {
    private final ManageWarehouseInventory inventory;

    public InventoryAdministrationController(ManageWarehouseInventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public ResponseEntity<InventoryBalanceDto> getAdminInventoryBalance(UUID variantId) {
        var balance = inventory.get(variantId);
        return ResponseEntity.ok()
                .eTag(InventoryVersionEtag.format(balance.version()))
                .body(toDto(balance));
    }

    @Override
    public ResponseEntity<InventoryWarehouseOverviewDto> getInventoryWarehouseOverview() {
        var overview = inventory.overview();
        var items = overview.items().stream()
                .map(item -> new InventoryStockItemDto(
                                item.productId(),
                                item.productName(),
                                InventoryStockItemDto.ProductStatusEnum.valueOf(item.productStatus()),
                                item.variantId(),
                                item.sku(),
                                item.variantLabel(),
                                item.onHand(),
                                item.reserved(),
                                item.available(),
                                item.version())
                        .updatedAt(item.updatedAt()))
                .toList();
        var movements = overview.movements().stream()
                .map(movement -> new InventoryMovementViewDto(
                                movement.id(),
                                movement.variantId(),
                                movement.productName(),
                                movement.sku(),
                                movement.variantLabel(),
                                InventoryMovementViewDto.TypeEnum.valueOf(movement.type()),
                                movement.quantityDelta(),
                                movement.reason(),
                                movement.occurredAt())
                        .reference(movement.reference()))
                .toList();
        return ResponseEntity.ok(new InventoryWarehouseOverviewDto(items, movements));
    }

    @Override
    public ResponseEntity<InventoryMutationResultDto> receiveInventoryStock(
            UUID variantId, String idempotencyKey, String csrf, ReceiveStockRequestDto request) {
        var result = inventory.receive(
                variantId,
                requireNonNull(request.getQuantity()),
                requireNonNull(request.getReason()),
                request.getReference(),
                idempotencyKey);
        return mutationResponse(result);
    }

    @Override
    public ResponseEntity<InventoryMutationResultDto> adjustInventoryStock(
            UUID variantId, String ifMatch, String idempotencyKey, String csrf, AdjustStockRequestDto request) {
        var result = inventory.reconcile(
                variantId,
                requireNonNull(request.getOnHand()),
                InventoryVersionEtag.parse(ifMatch),
                requireNonNull(request.getReason()),
                request.getReference(),
                idempotencyKey);
        return mutationResponse(result);
    }

    private static ResponseEntity<InventoryMutationResultDto> mutationResponse(WarehouseMutationResult result) {
        var balance = result.mutation().balance();
        var balanceDto = new InventoryBalanceDto(
                result.warehouseCode(),
                balance.variantId().value(),
                balance.onHand().value(),
                balance.reserved().value(),
                balance.available().value(),
                balance.version(),
                result.balanceUpdatedAt());
        return ResponseEntity.ok()
                .eTag(InventoryVersionEtag.format(balance.version()))
                .body(new InventoryMutationResultDto(
                        balanceDto, toDto(result.mutation().movement())));
    }

    private static InventoryBalanceDto toDto(InventoryAdminBalanceView balance) {
        return new InventoryBalanceDto(
                balance.warehouseCode(),
                balance.variantId(),
                balance.onHand(),
                balance.reserved(),
                balance.available(),
                balance.version(),
                balance.updatedAt());
    }

    private static InventoryMovementDto toDto(InventoryMovement movement) {
        return new InventoryMovementDto(
                        movement.id().value(),
                        movement.variantId().value(),
                        InventoryMovementDto.TypeEnum.valueOf(movement.type().name()),
                        movement.quantityDelta(),
                        movement.reason().value(),
                        movement.occurredAt())
                .reference(
                        movement.reference() == null
                                ? null
                                : movement.reference().value());
    }
}
