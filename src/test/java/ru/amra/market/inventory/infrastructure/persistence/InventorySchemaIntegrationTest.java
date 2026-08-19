package ru.amra.market.inventory.infrastructure.persistence;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.amra.market.testing.PostgreSqlIntegrationTest;

@SpringBootTest
class InventorySchemaIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Flyway flyway;

    @Test
    void createsOwnedTablesPrimaryWarehouseAndCriticalIndexes() {
        var jdbc = runtimeJdbc();

        assertThat(jdbc.queryForList("""
                        select tablename
                        from pg_catalog.pg_tables
                        where schemaname = 'amra_shop' and tablename like 'inventory_%'
                        order by tablename
                        """, String.class))
                .containsExactly(
                        "inventory_audit_events",
                        "inventory_balances",
                        "inventory_command_idempotency",
                        "inventory_job_leases",
                        "inventory_movements",
                        "inventory_reservation_command_results",
                        "inventory_reservation_events",
                        "inventory_reservation_lines",
                        "inventory_reservations",
                        "inventory_stock_command_results",
                        "inventory_warehouses");
        assertThat(jdbc.queryForObject(
                        "select count(*) from inventory_warehouses where code = 'PRIMARY' and status = 'ACTIVE'",
                        Integer.class))
                .isOne();
        assertThat(jdbc.queryForList("""
                        select indexname
                        from pg_catalog.pg_indexes
                        where schemaname = 'amra_shop'
                        """, String.class))
                .contains(
                        "ix_inventory_balances__variant_warehouse",
                        "ix_inventory_movements__balance_time",
                        "ix_inventory_reservations__active_expiry",
                        "ix_inventory_reservation_lines__balance_reservation");
    }

    @Test
    void balanceConstraintsRejectNegativeAndOversoldState() {
        var jdbc = runtimeJdbc();
        var warehouseId = primaryWarehouseId(jdbc);

        assertThatThrownBy(() -> jdbc.update("""
                            insert into inventory_balances (warehouse_id, variant_id, on_hand, reserved)
                            values (?, uuidv7(), -1, 0)
                            """, warehouseId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_inventory_balances__on_hand_non_negative");

        assertThatThrownBy(() -> jdbc.update("""
                            insert into inventory_balances (warehouse_id, variant_id, on_hand, reserved)
                            values (?, uuidv7(), 1, 2)
                            """, warehouseId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_inventory_balances__reserved_range");
    }

    @Test
    void balanceDoesNotCreateAForbiddenCatalogDatabaseDependency() {
        var jdbc = runtimeJdbc();
        var variantId = uuidV7(jdbc);

        assertThat(jdbc.update("""
                    insert into inventory_balances (warehouse_id, variant_id, on_hand, reserved)
                    select id, ?, 0, 0 from inventory_warehouses where code = 'PRIMARY'
                    """, variantId)).isOne();
    }

    @Test
    void movementConstraintsEnforceDirectionAndReservationBinding() {
        var jdbc = runtimeJdbc();
        var warehouseId = primaryWarehouseId(jdbc);
        var variantId = createBalance(jdbc, warehouseId, 10, 0);

        assertThatThrownBy(() -> jdbc.update("""
                            insert into inventory_movements (
                                warehouse_id, variant_id, movement_type, quantity_delta, reason
                            ) values (?, ?, 'RECEIPT', -1, 'invalid direction')
                            """, warehouseId, variantId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_inventory_movements__delta_direction");

        assertThatThrownBy(() -> jdbc.update("""
                            insert into inventory_movements (
                                warehouse_id, variant_id, movement_type, quantity_delta, reason
                            ) values (?, ?, 'RESERVATION_COMMIT', -1, 'missing reservation')
                            """, warehouseId, variantId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_inventory_movements__reservation_binding");
    }

    @Test
    void reservationConstraintsEnforceLifecycleAndPositiveLines() {
        var jdbc = runtimeJdbc();
        var warehouseId = primaryWarehouseId(jdbc);
        var variantId = createBalance(jdbc, warehouseId, 10, 0);

        assertThatThrownBy(() -> jdbc.update("""
                            insert into inventory_reservations (
                                owner_reference, status, expires_at, extension_count, terminal_at
                            ) values (uuidv7(), 'ACTIVE', clock_timestamp() + interval '15 minutes', 2, null)
                            """))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_inventory_reservations__extension_count");

        var reservationId = createActiveReservation(jdbc);
        assertThatThrownBy(() -> jdbc.update("""
                            insert into inventory_reservation_lines (
                                reservation_id, warehouse_id, variant_id, quantity
                            ) values (?, ?, ?, 0)
                            """, reservationId, warehouseId, variantId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_inventory_reservation_lines__quantity_positive");
    }

    @Test
    void reservationEventConstraintRejectsImpossibleTransition() {
        var jdbc = runtimeJdbc();
        var reservationId = createActiveReservation(jdbc);

        assertThatThrownBy(() -> jdbc.update("""
                            insert into inventory_reservation_events (
                                reservation_id, event_type, previous_status, current_status,
                                previous_expires_at, current_expires_at
                            ) values (
                                ?, 'COMMITTED', 'ACTIVE', 'RELEASED',
                                clock_timestamp() + interval '15 minutes',
                                clock_timestamp() + interval '15 minutes'
                            )
                            """, reservationId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_inventory_reservation_events__transition");
    }

    @Test
    void runtimePrivilegesAndTriggersProtectAppendOnlyRecords() {
        var runtimeJdbc = runtimeJdbc();
        var migrationJdbc = new JdbcTemplate(flyway.getConfiguration().getDataSource());

        for (var table : new String[] {
            "inventory_movements",
            "inventory_reservation_events",
            "inventory_reservation_command_results",
            "inventory_audit_events",
            "inventory_stock_command_results"
        }) {
            assertThat(runtimeJdbc.queryForObject(
                            "select has_table_privilege(current_user, ?, 'INSERT')", Boolean.class, table))
                    .isTrue();
            assertThat(runtimeJdbc.queryForObject(
                            "select has_table_privilege(current_user, ?, 'UPDATE')", Boolean.class, table))
                    .isFalse();
            assertThat(runtimeJdbc.queryForObject(
                            "select has_table_privilege(current_user, ?, 'DELETE')", Boolean.class, table))
                    .isFalse();
            assertThat(migrationJdbc.queryForObject("""
                            select count(*) from pg_trigger
                            where tgrelid = (?::regclass) and not tgisinternal
                            """, Integer.class, table)).isOne();
        }

        var warehouseId = primaryWarehouseId(runtimeJdbc);
        var variantId = createBalance(runtimeJdbc, warehouseId, 1, 0);
        var movementId = requireNonNull(runtimeJdbc.queryForObject("""
                insert into inventory_movements (
                    warehouse_id, variant_id, movement_type, quantity_delta, reason
                ) values (?, ?, 'RECEIPT', 1, 'append-only proof')
                returning id
                """, UUID.class, warehouseId, variantId));

        assertThatThrownBy(() -> migrationJdbc.update(
                        "update inventory_movements set reason = 'tampered' where id = ?", movementId))
                .hasStackTraceContaining("inventory ledger and audit records are append-only");
    }

    private JdbcTemplate runtimeJdbc() {
        return new JdbcTemplate(dataSource);
    }

    private static UUID primaryWarehouseId(JdbcTemplate jdbc) {
        return requireNonNull(
                jdbc.queryForObject("select id from inventory_warehouses where code = 'PRIMARY'", UUID.class));
    }

    private static UUID uuidV7(JdbcTemplate jdbc) {
        return requireNonNull(jdbc.queryForObject("select uuidv7()", UUID.class));
    }

    private static UUID createBalance(JdbcTemplate jdbc, UUID warehouseId, long onHand, long reserved) {
        return requireNonNull(jdbc.queryForObject("""
                insert into inventory_balances (warehouse_id, variant_id, on_hand, reserved)
                values (?, uuidv7(), ?, ?)
                returning variant_id
                """, UUID.class, warehouseId, onHand, reserved));
    }

    private static UUID createActiveReservation(JdbcTemplate jdbc) {
        return requireNonNull(jdbc.queryForObject("""
                insert into inventory_reservations (owner_reference, status, expires_at)
                values (uuidv7(), 'ACTIVE', clock_timestamp() + interval '15 minutes')
                returning id
                """, UUID.class));
    }
}
