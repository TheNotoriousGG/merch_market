package ru.amra.market.customer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Customer-facing phone sign-in and account API. */
@RestController
@RequestMapping("/api/v1/customer")
final class CustomerAccountController {

    private final CustomerPhoneAuthentication authentication;
    private final CustomerShoppingService shopping;

    CustomerAccountController(CustomerPhoneAuthentication authentication, CustomerShoppingService shopping) {
        this.authentication = authentication;
        this.shopping = shopping;
    }

    @PostMapping("/auth/phone/start")
    ChallengeResponse start(@Valid @RequestBody StartRequest request) {
        var challenge = authentication.start(request.phone());
        return new ChallengeResponse(
                challenge.id(), challenge.phone(), challenge.expiresInSeconds(), challenge.developmentCode());
    }

    @PostMapping("/auth/phone/verify")
    AccountResponse verify(
            @Valid @RequestBody VerifyRequest request, HttpSession session, HttpServletRequest servletRequest) {
        var customer = authentication.verify(request.challengeId(), request.code(), session);
        shopping.mergeGuestIntoCustomer(
                CustomerShoppingService.cookie(servletRequest, CustomerShoppingService.GUEST_COOKIE), customer.id());
        return account(customer);
    }

    @GetMapping("/account")
    AccountResponse current(HttpSession session) {
        return account(authentication.current(session));
    }

    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(HttpSession session) {
        authentication.logout(session);
    }

    private static AccountResponse account(CustomerPhoneAuthentication.Customer customer) {
        return new AccountResponse(customer.id(), customer.phone(), customer.displayName());
    }

    record StartRequest(@NotBlank String phone) {}

    record VerifyRequest(
            @NotNull UUID challengeId,
            @Pattern(regexp = "^[0-9]{6}$") String code) {}

    record ChallengeResponse(
            UUID challengeId,
            String phone,
            long expiresInSeconds,
            @Nullable String developmentCode) {}

    record AccountResponse(UUID id, String phone, @Nullable String displayName) {}
}
