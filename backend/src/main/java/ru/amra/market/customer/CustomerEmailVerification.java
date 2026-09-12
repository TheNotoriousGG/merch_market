package ru.amra.market.customer;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Authenticated email ownership challenge lifecycle. */
@Service
class CustomerEmailVerification {
    private static final Logger LOGGER = LoggerFactory.getLogger(CustomerEmailVerification.class);
    private static final Duration LIFETIME = Duration.ofMinutes(15);
    private static final Duration RESEND_INTERVAL = Duration.ofSeconds(60);
    private static final int MAX_ATTEMPTS = 5;
    private final NamedParameterJdbcTemplate jdbc;
    private final CustomerShoppingService shopping;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final boolean exposeDevelopmentCode;

    CustomerEmailVerification(
            NamedParameterJdbcTemplate jdbc,
            CustomerShoppingService shopping,
            Clock clock,
            @Value("${amra.customer.email-auth.expose-development-code:false}") boolean exposeDevelopmentCode) {
        this.jdbc = jdbc;
        this.shopping = shopping;
        this.clock = clock;
        this.exposeDevelopmentCode = exposeDevelopmentCode;
    }

    @Transactional
    Challenge start(HttpServletRequest request, String rawEmail) {
        var customerId = shopping.profile(request).id();
        var email = rawEmail.trim().toLowerCase(Locale.ROOT);
        var now = clock.instant();
        var recent = jdbc.queryForObject(
                "select count(*) from customer_email_challenges where customer_id = :id and created_at > :since",
                Map.of("id", customerId, "since", Timestamp.from(now.minus(RESEND_INTERVAL))),
                Long.class);
        if (recent != null && recent > 0) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Новый код можно запросить через минуту");
        }
        var id = UUID.randomUUID();
        var code = "%06d".formatted(random.nextInt(1_000_000));
        jdbc.update(
                """
                insert into customer_email_challenges(
                    id, customer_id, email, code_hash, expires_at, created_at)
                values (:id, :customer, :email, :hash, :expires, :now)
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("customer", customerId)
                        .addValue("email", email)
                        .addValue("hash", hash(code))
                        .addValue("expires", Timestamp.from(now.plus(LIFETIME)))
                        .addValue("now", Timestamp.from(now)));
        jdbc.update(
                "update customer_accounts set email = :email, email_verified = false, updated_at = :now where id = :id",
                Map.of("email", email, "now", Timestamp.from(now), "id", customerId));
        LOGGER.info("Customer email verification prepared for challenge {}", id);
        return new Challenge(id, email, LIFETIME.toSeconds(), exposeDevelopmentCode ? code : null);
    }

    @Transactional
    void verify(HttpServletRequest request, UUID challengeId, String code) {
        var customerId = shopping.profile(request).id();
        var rows = jdbc.query(
                """
                select email, code_hash, attempts, expires_at, consumed_at
                from customer_email_challenges
                where id = :id and customer_id = :customer for update
                """,
                Map.of("id", challengeId, "customer", customerId),
                (result, row) -> new ChallengeRow(
                        Objects.requireNonNull(result.getString("email")),
                        Objects.requireNonNull(result.getString("code_hash")),
                        result.getInt("attempts"),
                        Objects.requireNonNull(result.getTimestamp("expires_at")),
                        result.getTimestamp("consumed_at")));
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Проверка email не найдена");
        }
        var row = rows.getFirst();
        if (row.consumedAt() != null
                || row.expiresAt().toInstant().isBefore(clock.instant())
                || row.attempts() >= MAX_ATTEMPTS) {
            throw new ResponseStatusException(HttpStatus.GONE, "Код истёк. Запросите новый");
        }
        if (!MessageDigest.isEqual(
                row.codeHash().getBytes(StandardCharsets.US_ASCII),
                hash(code.trim()).getBytes(StandardCharsets.US_ASCII))) {
            jdbc.update(
                    "update customer_email_challenges set attempts = attempts + 1 where id = :id",
                    Map.of("id", challengeId));
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неверный код подтверждения");
        }
        var now = Timestamp.from(clock.instant());
        jdbc.update(
                "update customer_email_challenges set consumed_at = :now where id = :id",
                Map.of("now", now, "id", challengeId));
        jdbc.update(
                "update customer_accounts set email = :email, email_verified = true, updated_at = :now where id = :id",
                Map.of("email", row.email(), "now", now, "id", customerId));
    }

    private static String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    record Challenge(
            UUID id,
            String email,
            long expiresInSeconds,
            @Nullable String developmentCode) {}

    private record ChallengeRow(
            String email,
            String codeHash,
            int attempts,
            Timestamp expiresAt,
            @Nullable Timestamp consumedAt) {}
}
