package ru.amra.market.inventory.infrastructure.persistence;

import java.time.Duration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.amra.market.inventory.application.port.InventoryJobLease;

/** PostgreSQL-clock lease adapter supporting renewal and safe expired-owner takeover. */
@Repository
class JdbcInventoryJobLease implements InventoryJobLease {
    private final JdbcTemplate jdbc;

    JdbcInventoryJobLease(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean tryAcquire(String jobName, String ownerInstanceId, Duration leaseDuration) {
        var acquired = jdbc.query(
                """
                insert into inventory_job_leases (
                    job_name, owner_instance_id, leased_until, updated_at, version
                ) values (
                    ?, ?, clock_timestamp() + (? * interval '1 millisecond'), clock_timestamp(), 0
                )
                on conflict (job_name) do update
                set owner_instance_id = excluded.owner_instance_id,
                    leased_until = clock_timestamp() + (? * interval '1 millisecond'),
                    updated_at = clock_timestamp(),
                    version = inventory_job_leases.version + 1
                where inventory_job_leases.owner_instance_id = excluded.owner_instance_id
                   or inventory_job_leases.leased_until <= clock_timestamp()
                returning true
                """,
                (result, row) -> result.getBoolean(1),
                jobName,
                ownerInstanceId,
                leaseDuration.toMillis(),
                leaseDuration.toMillis());
        return !acquired.isEmpty();
    }
}
