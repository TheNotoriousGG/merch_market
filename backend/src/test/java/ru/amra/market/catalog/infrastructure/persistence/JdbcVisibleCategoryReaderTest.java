package ru.amra.market.catalog.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import ru.amra.market.catalog.application.port.VisibleCategoryRecord;

class JdbcVisibleCategoryReaderTest {

    @Test
    void usesOneFixedQueryInsteadOfPerNodeQueries() {
        var jdbc = mock(JdbcTemplate.class);
        var expected = List.<VisibleCategoryRecord>of();
        when(jdbc.query(eq(JdbcVisibleCategoryReader.FIND_ALL_REACHABLE_SQL), categoryRowMapper()))
                .thenReturn(expected);

        var result = new JdbcVisibleCategoryReader(jdbc).findAllReachable();

        assertThat(result).isSameAs(expected);
        verify(jdbc).query(eq(JdbcVisibleCategoryReader.FIND_ALL_REACHABLE_SQL), categoryRowMapper());
        verifyNoMoreInteractions(jdbc);
    }

    @SuppressWarnings("unchecked")
    private static RowMapper<VisibleCategoryRecord> categoryRowMapper() {
        return any(RowMapper.class);
    }
}
