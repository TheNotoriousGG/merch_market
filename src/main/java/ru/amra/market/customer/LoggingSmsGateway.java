package ru.amra.market.customer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Local adapter; production must replace it with a real SMS provider. */
@Component
final class LoggingSmsGateway implements SmsGateway {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingSmsGateway.class);
    private final boolean localDevelopment;

    LoggingSmsGateway(
            @Value("${amra.customer.phone-auth.expose-development-code:false}") boolean localDevelopment) {
        this.localDevelopment = localDevelopment;
    }

    @Override
    public void sendVerificationCode(String phone, String code) {
        if (localDevelopment) {
            LOGGER.info("Local phone verification code for {}: {}", phone, code);
            return;
        }
        throw new IllegalStateException("SMS gateway is not configured");
    }
}
