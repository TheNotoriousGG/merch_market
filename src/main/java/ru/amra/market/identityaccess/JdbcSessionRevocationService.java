package ru.amra.market.identityaccess;

import java.util.Objects;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.stereotype.Service;

/** PostgreSQL-backed implementation of identity session revocation. */
@Service
final class JdbcSessionRevocationService implements SessionRevocation {

    private final JdbcIndexedSessionRepository sessions;

    JdbcSessionRevocationService(JdbcIndexedSessionRepository sessions) {
        this.sessions = sessions;
    }

    @Override
    public int revokeAll(String subject) {
        Objects.requireNonNull(subject, "subject");
        var indexed = sessions.findByIndexNameAndIndexValue(
                FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, subject);
        indexed.keySet().forEach(sessions::deleteById);
        return indexed.size();
    }
}
