package ru.amra.market.customer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.amra.market.ordering.OrderingService;
import ru.amra.market.platform.generated.api.OrderingApi;
import ru.amra.market.platform.generated.model.CheckoutRequestDto;
import ru.amra.market.platform.generated.model.CustomerOrderDto;
import ru.amra.market.platform.generated.model.CustomerOrderListDto;
import ru.amra.market.platform.generated.model.CustomerOrderSummaryDto;
import ru.amra.market.platform.generated.model.OrderLineDto;

/** Browser ordering adapter sharing the established guest/customer owner boundary. */
@RestController
final class CustomerOrderingController implements OrderingApi {
    private final CustomerShoppingService shopping;
    private final OrderingService ordering;
    private final HttpServletRequest request;
    private final HttpServletResponse response;

    CustomerOrderingController(
            CustomerShoppingService shopping,
            OrderingService ordering,
            HttpServletRequest request,
            HttpServletResponse response) {
        this.shopping = shopping;
        this.ordering = ordering;
        this.request = request;
        this.response = response;
    }

    @Override
    public ResponseEntity<CustomerOrderDto> checkout(String idempotencyKey, String csrf, CheckoutRequestDto body) {
        var owner = shopping.owner(request, response);
        var order = ordering.checkout(
                new OrderingService.Owner(owner.type(), owner.id()),
                idempotencyKey,
                new OrderingService.Checkout(
                        body.getCartVersion(),
                        body.getEmail(),
                        body.getRecipientName(),
                        body.getPhone(),
                        body.getPostalCode(),
                        body.getCity(),
                        body.getStreet(),
                        body.getApartment()));
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(order));
    }

    @Override
    public ResponseEntity<CustomerOrderDto> getCustomerOrder(String publicNumber, @Nullable String guestToken) {
        var owner = shopping.owner(request, response);
        return ResponseEntity.ok(
                toDto(ordering.get(new OrderingService.Owner(owner.type(), owner.id()), publicNumber, guestToken)));
    }

    @Override
    public ResponseEntity<CustomerOrderListDto> listCustomerOrders() {
        var owner = shopping.owner(request, response);
        var items = ordering.list(new OrderingService.Owner(owner.type(), owner.id())).stream()
                .map(order -> new CustomerOrderSummaryDto(
                        order.publicNumber(),
                        CustomerOrderSummaryDto.StatusEnum.fromValue(order.status()),
                        order.total(),
                        CustomerOrderSummaryDto.CurrencyEnum.RUB,
                        order.itemCount(),
                        order.createdAt()))
                .toList();
        return ResponseEntity.ok(new CustomerOrderListDto(items));
    }

    private static CustomerOrderDto toDto(OrderingService.Order order) {
        var dto = new CustomerOrderDto(
                order.id(),
                order.publicNumber(),
                CustomerOrderDto.StatusEnum.fromValue(order.status()),
                CustomerOrderDto.CurrencyEnum.RUB,
                order.subtotal(),
                order.discount(),
                order.total(),
                order.email(),
                order.recipient(),
                order.phone(),
                order.postalCode(),
                order.city(),
                order.street(),
                order.lines().stream().map(CustomerOrderingController::toDto).toList(),
                order.createdAt());
        dto.setApartment(order.apartment());
        dto.setGuestAccessToken(order.guestAccessToken());
        return dto;
    }

    private static OrderLineDto toDto(OrderingService.Line line) {
        var dto = new OrderLineDto(
                line.variantId(),
                line.sku(),
                line.name(),
                line.label(),
                line.quantity(),
                line.unitPrice(),
                line.discount(),
                line.total());
        dto.setPromotionName(line.promotionName());
        return dto;
    }
}
