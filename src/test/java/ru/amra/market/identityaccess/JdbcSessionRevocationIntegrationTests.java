package ru.amra.market.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.amra.market.testing.PostgreSqlIntegrationTest;

@SpringBootTest
class JdbcSessionRevocationIntegrationTests extends PostgreSqlIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SessionRevocation revocationService;

    @Test
    void revokesEveryPersistedSessionForAnOidcSubject() {
        insertSession("subject-42");
        insertSession("subject-42");

        assertThat(revocationService.revokeAll("subject-42")).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                        "select count(*) from amra_shop.http_sessions where principal_name = 'subject-42'",
                        Integer.class))
                .isZero();
    }

    private void insertSession(String subject) {
        var now = Instant.now().toEpochMilli();
        jdbc.update(
                """
                insert into amra_shop.http_sessions
                    (primary_id, session_id, creation_time, last_access_time,
                     max_inactive_interval, expiry_time, principal_name)
                values (?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                now,
                now,
                1800,
                now + 1_800_000,
                subject);
    }
}
