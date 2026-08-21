package ru.amra.market.customer;

/** Replace this port with the adapter for the selected SMS provider. */
public interface SmsGateway {
    void sendVerificationCode(String phone, String code);
}
