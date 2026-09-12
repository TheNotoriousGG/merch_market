package ru.amra.market.persistence;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.amra.market.testing.PostgreSqlIntegrationTest;

@SpringBootTest
class PostgreSqlFoundationTests extends PostgreSqlIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Flyway flyway;

    @Test
    void migratesFromScratchAndRemainsIdempotent() {
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        var current = requireNonNull(flyway.info().current());
        assertThat(current.getVersion().getVersion()).isEqualTo("17");
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test
    void rejectsAnIncompatibleMigrationHistory() {
        var migrationJdbc = new JdbcTemplate(flyway.getConfiguration().getDataSource());
        var originalChecksum = requireNonNull(migrationJdbc.queryForObject(
                "select checksum from amra_shop.flyway_schema_history where version = '1'", Integer.class));

        try {
            migrationJdbc.update(
                    "update amra_shop.flyway_schema_history set checksum = ? where version = '1'",
                    originalChecksum + 1);

            assertThatThrownBy(flyway::migrate).hasMessageContaining("Migration checksum mismatch");
        } finally {
            migrationJdbc.update(
                    "update amra_shop.flyway_schema_history set checksum = ? where version = '1'", originalChecksum);
        }
    }

    @Test
    void runtimeRoleUsesBoundedSettingsAndCannotCreateSchemaObjects() {
        var jdbc = new JdbcTemplate(dataSource);

        assertThat(jdbc.queryForObject("select current_user", String.class)).isEqualTo("amra_runtime");
        assertThat(jdbc.queryForObject("select current_schema()", String.class)).isEqualTo("amra_shop");
        assertThat(jdbc.queryForObject("select current_setting('statement_timeout')", String.class))
                .isEqualTo("2s");
        assertThat(jdbc.queryForObject(
                        "select has_schema_privilege(current_user, 'amra_shop', 'USAGE')", Boolean.class))
                .isTrue();
        assertThat(jdbc.queryForObject(
                        "select has_schema_privilege(current_user, 'amra_shop', 'CREATE')", Boolean.class))
                .isFalse();
        assertThatThrownBy(() -> jdbc.execute("create table forbidden_runtime_table (id bigint primary key)"))
                .cause()
                .hasMessageContaining("permission denied for schema amra_shop");
    }

    @Test
    void runsOnPostgreSql18WithNativeUuidVersionSeven() {
        var jdbc = new JdbcTemplate(dataSource);

        assertThat(jdbc.queryForObject("show server_version_num", Integer.class))
                .isGreaterThanOrEqualTo(180000);
        assertThat(jdbc.queryForObject("select uuid_extract_version(uuidv7())", Integer.class))
                .isEqualTo(7);
    }

    @Test
    void grantsRuntimeAppendOnlyAccessToCatalogAuditTrail() {
        var runtimeJdbc = new JdbcTemplate(dataSource);
        var migrationJdbc = new JdbcTemplate(flyway.getConfiguration().getDataSource());

        assertThat(runtimeJdbc.queryForObject(
                        "select has_table_privilege(current_user, 'catalog_audit_events', 'SELECT')", Boolean.class))
                .isTrue();
        assertThat(runtimeJdbc.queryForObject(
                        "select has_table_privilege(current_user, 'catalog_audit_events', 'INSERT')", Boolean.class))
                .isTrue();
        assertThat(runtimeJdbc.queryForObject(
                        "select has_table_privilege(current_user, 'catalog_audit_events', 'UPDATE')", Boolean.class))
                .isFalse();
        assertThat(runtimeJdbc.queryForObject(
                        "select has_table_privilege(current_user, 'catalog_audit_events', 'DELETE')", Boolean.class))
                .isFalse();
        assertThat(migrationJdbc.queryForObject("""
                        select count(*) from pg_trigger
                        where tgrelid = 'amra_shop.catalog_audit_events'::regclass
                          and tgname = 'trg_catalog_audit_events__append_only'
                          and not tgisinternal
                        """, Integer.class)).isEqualTo(1);
    }
}
