package ru.amra.market.customer;

import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.jspecify.annotations.Nullable;

/** Phone challenge lifecycle and customer session boundary. */
@Service
class CustomerPhoneAuthentication {

    static final String CUSTOMER_ID_SESSION_ATTRIBUTE = "AMRA_CUSTOMER_ID";
    private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{7,14}$");
    private static final Logger LOGGER = LoggerFactory.getLogger(CustomerPhoneAuthentication.class);
    private static final Duration CODE_LIFETIME = Duration.ofMinutes(5);
    private static final Duration RESEND_INTERVAL = Duration.ofSeconds(60);
    private static final int MAX_ATTEMPTS = 5;

    private final NamedParameterJdbcTemplate jdbc;
    private final SmsGateway smsGateway;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final boolean exposeDevelopmentCode;

    CustomerPhoneAuthentication(
            NamedParameterJdbcTemplate jdbc,
            SmsGateway smsGateway,
            Clock clock,
            @Value("${amra.customer.phone-auth.expose-development-code:false}") boolean exposeDevelopmentCode) {
        this.jdbc = jdbc;
        this.smsGateway = smsGateway;
        this.clock = clock;
        this.exposeDevelopmentCode = exposeDevelopmentCode;
    }

    Challenge start(String rawPhone) {
        var phone = normalizePhone(rawPhone);
        var code = "%06d".formatted(random.nextInt(1_000_000));
        var id = UUID.randomUUID();
        var now = clock.instant();
        var recentChallenges = jdbc.queryForObject(
                """
                select count(*) from customer_phone_challenges
                where phone = :phone and created_at > :allowedSince
                """,
                new MapSqlParameterSource()
                        .addValue("phone", phone)
                        .addValue("allowedSince", Timestamp.from(now.minus(RESEND_INTERVAL))),
                Long.class);
        if (recentChallenges != null && recentChallenges > 0) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS, "Новый код можно запросить через минуту");
        }
        try {
            jdbc.update(
                    """
                    insert into customer_phone_challenges(id, phone, code_hash, expires_at, created_at)
                    values (:id, :phone, :codeHash, :expiresAt, :createdAt)
                    """,
                    new MapSqlParameterSource()
                            .addValue("id", id)
                            .addValue("phone", phone)
                            .addValue("codeHash", hash(code))
                            .addValue("expiresAt", Timestamp.from(now.plus(CODE_LIFETIME)))
                            .addValue("createdAt", Timestamp.from(now)));
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to create phone verification challenge", exception);
            throw exception;
        }
        smsGateway.sendVerificationCode(phone, code);
        return new Challenge(id, phone, CODE_LIFETIME.toSeconds(), exposeDevelopmentCode ? code : null);
    }

    @Transactional
    Customer verify(UUID challengeId, String code, HttpSession session) {
        var rows = jdbc.query(
                """
                select phone, code_hash, attempts, expires_at, consumed_at
                from customer_phone_challenges where id = :id for update
                """,
                Map.of("id", challengeId),
                (result, row) -> new ChallengeRow(
                        Objects.requireNonNull(result.getString("phone")),
                        Objects.requireNonNull(result.getString("code_hash")),
                        result.getInt("attempts"),
                        Objects.requireNonNull(result.getTimestamp("expires_at")).toInstant(),
                        result.getTimestamp("consumed_at")));
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Код подтверждения не найден");
        }
        var row = rows.getFirst();
        if (row.consumedAt() != null || row.expiresAt().isBefore(clock.instant()) || row.attempts() >= MAX_ATTEMPTS) {
            throw new ResponseStatusException(HttpStatus.GONE, "Код истёк. Запросите новый");
        }
        if (!MessageDigest.isEqual(
                row.codeHash().getBytes(StandardCharsets.US_ASCII),
                hash(code.trim()).getBytes(StandardCharsets.US_ASCII))) {
            jdbc.update(
                    "update customer_phone_challenges set attempts = attempts + 1 where id = :id",
                    Map.of("id", challengeId));
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неверный код подтверждения");
        }

        var phone = row.phone();
        var now = clock.instant();
        var customerId = jdbc.query(
                        "select id from customer_accounts where phone = :phone",
                        Map.of("phone", phone),
                        (result, rowNumber) -> result.getObject("id", UUID.class))
                .stream()
                .findFirst()
                .orElseGet(() -> createCustomer(phone, now));
        jdbc.update(
                "update customer_phone_challenges set consumed_at = :now where id = :id",
                Map.of("now", Timestamp.from(now), "id", challengeId));
        session.setAttribute(CUSTOMER_ID_SESSION_ATTRIBUTE, customerId.toString());
        return new Customer(customerId, phone, null);
    }

    Customer current(HttpSession session) {
        var value = session.getAttribute(CUSTOMER_ID_SESSION_ATTRIBUTE);
        if (!(value instanceof String customerId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Войдите по номеру телефона");
        }
        return jdbc.query(
                        "select id, phone, display_name from customer_accounts where id = :id",
                        Map.of("id", UUID.fromString(customerId)),
                        (result, row) -> new Customer(
                                result.getObject("id", UUID.class),
                                result.getString("phone"),
                                result.getString("display_name")))
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    void logout(HttpSession session) {
        session.removeAttribute(CUSTOMER_ID_SESSION_ATTRIBUTE);
    }

    private UUID createCustomer(String phone, Instant now) {
        var id = UUID.randomUUID();
        jdbc.update(
                """
                insert into customer_accounts(id, phone, created_at, updated_at)
                values (:id, :phone, :now, :now)
                """,
                Map.of("id", id, "phone", phone, "now", Timestamp.from(now)));
        return id;
    }

    private static String normalizePhone(String rawPhone) {
        var digits = rawPhone.replaceAll("[^0-9+]", "");
        if (digits.startsWith("8") && digits.length() == 11) {
            digits = "+7" + digits.substring(1);
        } else if (!digits.startsWith("+")) {
            digits = digits.length() == 10 ? "+7" + digits : "+" + digits;
        }
        if (!E164.matcher(digits).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Введите корректный номер телефона");
        }
        return digits;
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    record Challenge(UUID id, String phone, long expiresInSeconds, @Nullable String developmentCode) {}

    record Customer(UUID id, String phone, @Nullable String displayName) {}

    private record ChallengeRow(
            String phone,
            String codeHash,
            int attempts,
            Instant expiresAt,
            @Nullable Timestamp consumedAt) {}
}
