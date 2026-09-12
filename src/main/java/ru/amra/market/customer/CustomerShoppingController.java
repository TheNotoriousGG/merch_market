package ru.amra.market.customer;

import static java.util.Objects.requireNonNull;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.amra.market.platform.generated.api.CustomerApi;
import ru.amra.market.platform.generated.model.CartDto;
import ru.amra.market.platform.generated.model.CartItemDto;
import ru.amra.market.platform.generated.model.CartNoticeDto;
import ru.amra.market.platform.generated.model.CustomerAddressDto;
import ru.amra.market.platform.generated.model.CustomerContextDto;
import ru.amra.market.platform.generated.model.CustomerProfileDto;
import ru.amra.market.platform.generated.model.SaveCustomerAddressRequestDto;
import ru.amra.market.platform.generated.model.SetCartItemRequestDto;
import ru.amra.market.platform.generated.model.UpdateCustomerProfileRequestDto;

/** Generated-contract adapter for customer shopping state. */
@RestController
final class CustomerShoppingController implements CustomerApi {
    private final CustomerShoppingService shopping;
    private final HttpServletRequest request;
    private final HttpServletResponse response;

    CustomerShoppingController(
            CustomerShoppingService shopping, HttpServletRequest request, HttpServletResponse response) {
        this.shopping = shopping;
        this.request = request;
        this.response = response;
    }

    @Override
    public ResponseEntity<CustomerContextDto> getCustomerContext() {
        var context = shopping.context(owner());
        var dto = new CustomerContextDto(context.authenticated(), context.favorites());
        dto.setCustomerId(context.customerId());
        return ResponseEntity.ok(dto);
    }

    @Override
    public ResponseEntity<CustomerProfileDto> getCustomerProfile() {
        return ResponseEntity.ok(profile(shopping.profile(request)));
    }

    @Override
    public ResponseEntity<CustomerProfileDto> updateCustomerProfile(
            String csrf, UpdateCustomerProfileRequestDto update) {
        return ResponseEntity.ok(profile(shopping.updateProfile(request, update.getDisplayName(), update.getEmail())));
    }

    @Override
    public ResponseEntity<CustomerAddressDto> createCustomerAddress(String csrf, SaveCustomerAddressRequestDto body) {
        var address = shopping.createAddress(
                request,
                new CustomerShoppingService.AddressDraft(
                        requireNonNull(body.getLabel()),
                        requireNonNull(body.getRecipientName()),
                        requireNonNull(body.getPhone()),
                        requireNonNull(body.getPostalCode()),
                        requireNonNull(body.getCity()),
                        requireNonNull(body.getStreet()),
                        body.getApartment()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/customer/addresses/" + address.id()))
                .body(address(address));
    }

    @Override
    public ResponseEntity<Void> deleteCustomerAddress(UUID resourceId, String csrf) {
        shopping.deleteAddress(request, resourceId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> addFavorite(UUID productId, String csrf) {
        shopping.addFavorite(owner(), productId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> removeFavorite(UUID productId, String csrf) {
        shopping.removeFavorite(owner(), productId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<CartDto> getCart() {
        return cartResponse(shopping.cart(owner()));
    }

    @Override
    public ResponseEntity<CartDto> setCartItem(
            UUID variantId, String ifMatch, String csrf, SetCartItemRequestDto body) {
        return cartResponse(shopping.setItem(
                owner(), variantId, requireNonNull(body.getQuantity()), CustomerShoppingService.parseVersion(ifMatch)));
    }

    @Override
    public ResponseEntity<CartDto> removeCartItem(UUID variantId, String ifMatch, String csrf) {
        return cartResponse(shopping.removeItem(owner(), variantId, CustomerShoppingService.parseVersion(ifMatch)));
    }

    @Override
    public ResponseEntity<Void> clearCart(String ifMatch, String csrf) {
        shopping.clear(owner(), CustomerShoppingService.parseVersion(ifMatch));
        return ResponseEntity.noContent().build();
    }

    private CustomerShoppingService.ShoppingOwner owner() {
        return shopping.owner(request, response);
    }

    private static ResponseEntity<CartDto> cartResponse(CustomerShoppingService.Cart cart) {
        return ResponseEntity.ok()
                .eTag(CustomerShoppingService.etag(cart.version()))
                .body(cart(cart));
    }

    private static CartDto cart(CustomerShoppingService.Cart cart) {
        return new CartDto(
                cart.id(),
                cart.version(),
                cart.items().stream().map(CustomerShoppingController::item).toList(),
                cart.subtotal(),
                CartDto.CurrencyEnum.RUB,
                cart.notices().stream().map(CustomerShoppingController::notice).toList());
    }

    private static CartItemDto item(CustomerShoppingService.CartLine item) {
        return new CartItemDto(
                item.variantId(),
                item.productId(),
                item.slug(),
                item.name(),
                item.label(),
                item.quantity(),
                item.price(),
                item.available());
    }

    private static CartNoticeDto notice(CustomerShoppingService.Notice notice) {
        return new CartNoticeDto(notice.variantId(), CartNoticeDto.CodeEnum.fromValue(notice.code()), notice.message());
    }

    private static CustomerProfileDto profile(CustomerShoppingService.Profile profile) {
        var dto = new CustomerProfileDto(
                profile.id(),
                profile.phone(),
                profile.emailVerified(),
                profile.addresses().stream()
                        .map(CustomerShoppingController::address)
                        .toList());
        dto.setDisplayName(profile.displayName());
        dto.setEmail(profile.email());
        return dto;
    }

    private static CustomerAddressDto address(CustomerShoppingService.Address address) {
        var dto = new CustomerAddressDto(
                address.id(),
                address.label(),
                address.recipientName(),
                address.phone(),
                address.postalCode(),
                address.city(),
                address.street());
        dto.setApartment(address.apartment());
        return dto;
    }
}
