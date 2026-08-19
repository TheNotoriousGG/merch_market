CREATE TABLE inventory_reservation_command_results (
    event_id UUID NOT NULL,
    reservation_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    extension_count SMALLINT NOT NULL,
    reservation_version BIGINT NOT NULL,
    CONSTRAINT pk_inventory_reservation_command_results PRIMARY KEY (event_id),
    CONSTRAINT fk_inventory_reservation_command_results__event
        FOREIGN KEY (event_id) REFERENCES inventory_reservation_events (id) ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_reservation_command_results__reservation
        FOREIGN KEY (reservation_id) REFERENCES inventory_reservations (id) ON DELETE RESTRICT,
    CONSTRAINT ck_inventory_reservation_command_results__status
        CHECK (status IN ('ACTIVE', 'COMMITTED', 'RELEASED', 'EXPIRED')),
    CONSTRAINT ck_inventory_reservation_command_results__extension_count
        CHECK (extension_count BETWEEN 0 AND 1),
    CONSTRAINT ck_inventory_reservation_command_results__version
        CHECK (reservation_version >= 0)
);

INSERT INTO inventory_reservation_command_results (
    event_id, reservation_id, status, expires_at, extension_count, reservation_version
)
SELECT event_id,
       reservation_id,
       current_status,
       current_expires_at,
       extension_count,
       reservation_version
FROM (
    SELECT event.id AS event_id,
           event.reservation_id,
           event.current_status,
           event.current_expires_at,
           (count(*) FILTER (WHERE event.event_type = 'EXTENDED') OVER history)::smallint AS extension_count,
           row_number() OVER history - 1 AS reservation_version
    FROM inventory_reservation_events event
    WINDOW history AS (
        PARTITION BY event.reservation_id ORDER BY event.occurred_at, event.id
        ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
    )
) historical_results;

UPDATE inventory_command_idempotency command
SET resource_id = created.id
FROM inventory_reservation_events created
WHERE command.operation = 'CREATE_RESERVATION'
  AND command.resource_id = created.reservation_id
  AND created.event_type = 'CREATED';

CREATE INDEX ix_inventory_reservation_command_results__reservation_version
    ON inventory_reservation_command_results (reservation_id, reservation_version);

CREATE TRIGGER trg_inventory_reservation_command_results__append_only
BEFORE UPDATE OR DELETE ON inventory_reservation_command_results
FOR EACH ROW EXECUTE FUNCTION reject_inventory_append_only_mutation();

REVOKE UPDATE, DELETE, TRUNCATE ON inventory_reservation_command_results FROM amra_runtime;
