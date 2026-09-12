CREATE TABLE inventory_warehouses (
    id UUID NOT NULL DEFAULT uuidv7(),
    code VARCHAR(32) NOT NULL,
    name VARCHAR(160) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_inventory_warehouses PRIMARY KEY (id),
    CONSTRAINT uq_inventory_warehouses__code UNIQUE (code),
    CONSTRAINT ck_inventory_warehouses__uuid_v7 CHECK (uuid_extract_version(id) = 7),
    CONSTRAINT ck_inventory_warehouses__code_format CHECK (code ~ '^[A-Z][A-Z0-9_]{1,31}$'),
    CONSTRAINT ck_inventory_warehouses__name_not_blank CHECK (length(btrim(name)) BETWEEN 1 AND 160),
    CONSTRAINT ck_inventory_warehouses__status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_inventory_warehouses__version CHECK (version >= 0),
    CONSTRAINT ck_inventory_warehouses__timestamps CHECK (updated_at >= created_at)
);

INSERT INTO inventory_warehouses (code, name, status)
VALUES ('PRIMARY', 'Основной склад', 'ACTIVE');

CREATE TABLE inventory_balances (
    warehouse_id UUID NOT NULL,
    variant_id UUID NOT NULL,
    on_hand BIGINT NOT NULL DEFAULT 0,
    reserved BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_inventory_balances PRIMARY KEY (warehouse_id, variant_id),
    CONSTRAINT fk_inventory_balances__warehouse_id
        FOREIGN KEY (warehouse_id) REFERENCES inventory_warehouses (id) ON DELETE RESTRICT,
    CONSTRAINT ck_inventory_balances__variant_uuid_v7 CHECK (uuid_extract_version(variant_id) = 7),
    CONSTRAINT ck_inventory_balances__on_hand_non_negative CHECK (on_hand >= 0),
    CONSTRAINT ck_inventory_balances__reserved_range CHECK (reserved >= 0 AND reserved <= on_hand),
    CONSTRAINT ck_inventory_balances__version CHECK (version >= 0)
);

CREATE INDEX ix_inventory_balances__variant_warehouse
    ON inventory_balances (variant_id, warehouse_id);

CREATE TABLE inventory_reservations (
    id UUID NOT NULL DEFAULT uuidv7(),
    owner_reference UUID NOT NULL,
    status VARCHAR(16) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    extension_count SMALLINT NOT NULL DEFAULT 0,
    terminal_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_inventory_reservations PRIMARY KEY (id),
    CONSTRAINT ck_inventory_reservations__uuid_v7 CHECK (uuid_extract_version(id) = 7),
    CONSTRAINT ck_inventory_reservations__status
        CHECK (status IN ('ACTIVE', 'COMMITTED', 'RELEASED', 'EXPIRED')),
    CONSTRAINT ck_inventory_reservations__extension_count CHECK (extension_count BETWEEN 0 AND 1),
    CONSTRAINT ck_inventory_reservations__terminal_state CHECK (
        (status = 'ACTIVE' AND terminal_at IS NULL) OR
        (status IN ('COMMITTED', 'RELEASED', 'EXPIRED') AND terminal_at IS NOT NULL)
    ),
    CONSTRAINT ck_inventory_reservations__expiry_after_creation CHECK (expires_at > created_at),
    CONSTRAINT ck_inventory_reservations__version CHECK (version >= 0),
    CONSTRAINT ck_inventory_reservations__timestamps CHECK (
        updated_at >= created_at AND (terminal_at IS NULL OR terminal_at >= created_at)
    )
);

CREATE INDEX ix_inventory_reservations__active_expiry
    ON inventory_reservations (expires_at, id)
    WHERE status = 'ACTIVE';

CREATE INDEX ix_inventory_reservations__owner_created
    ON inventory_reservations (owner_reference, created_at DESC, id DESC);

CREATE TABLE inventory_reservation_lines (
    reservation_id UUID NOT NULL,
    warehouse_id UUID NOT NULL,
    variant_id UUID NOT NULL,
    quantity BIGINT NOT NULL,
    CONSTRAINT pk_inventory_reservation_lines PRIMARY KEY (reservation_id, warehouse_id, variant_id),
    CONSTRAINT fk_inventory_reservation_lines__reservation_id
        FOREIGN KEY (reservation_id) REFERENCES inventory_reservations (id) ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_reservation_lines__balance
        FOREIGN KEY (warehouse_id, variant_id)
        REFERENCES inventory_balances (warehouse_id, variant_id) ON DELETE RESTRICT,
    CONSTRAINT ck_inventory_reservation_lines__quantity_positive CHECK (quantity > 0)
);

CREATE INDEX ix_inventory_reservation_lines__balance_reservation
    ON inventory_reservation_lines (warehouse_id, variant_id, reservation_id);

CREATE TABLE inventory_movements (
    id UUID NOT NULL DEFAULT uuidv7(),
    warehouse_id UUID NOT NULL,
    variant_id UUID NOT NULL,
    movement_type VARCHAR(32) NOT NULL,
    quantity_delta BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    reference VARCHAR(255),
    reservation_id UUID,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT pk_inventory_movements PRIMARY KEY (id),
    CONSTRAINT fk_inventory_movements__balance
        FOREIGN KEY (warehouse_id, variant_id)
        REFERENCES inventory_balances (warehouse_id, variant_id) ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_movements__reservation_id
        FOREIGN KEY (reservation_id) REFERENCES inventory_reservations (id) ON DELETE RESTRICT,
    CONSTRAINT ck_inventory_movements__uuid_v7 CHECK (uuid_extract_version(id) = 7),
    CONSTRAINT ck_inventory_movements__type CHECK (
        movement_type IN ('RECEIPT', 'ADJUSTMENT_INCREASE', 'ADJUSTMENT_DECREASE', 'RESERVATION_COMMIT')
    ),
    CONSTRAINT ck_inventory_movements__delta_direction CHECK (
        (movement_type IN ('RECEIPT', 'ADJUSTMENT_INCREASE') AND quantity_delta > 0) OR
        (movement_type IN ('ADJUSTMENT_DECREASE', 'RESERVATION_COMMIT') AND quantity_delta < 0)
    ),
    CONSTRAINT ck_inventory_movements__reason_not_blank CHECK (length(btrim(reason)) BETWEEN 1 AND 500),
    CONSTRAINT ck_inventory_movements__reference_not_blank
        CHECK (reference IS NULL OR length(btrim(reference)) BETWEEN 1 AND 255),
    CONSTRAINT ck_inventory_movements__reservation_binding CHECK (
        (movement_type = 'RESERVATION_COMMIT' AND reservation_id IS NOT NULL) OR
        (movement_type <> 'RESERVATION_COMMIT' AND reservation_id IS NULL)
    )
);

CREATE INDEX ix_inventory_movements__balance_time
    ON inventory_movements (warehouse_id, variant_id, occurred_at, id);

CREATE INDEX ix_inventory_movements__reservation
    ON inventory_movements (reservation_id, id)
    WHERE reservation_id IS NOT NULL;

CREATE TABLE inventory_reservation_events (
    id UUID NOT NULL DEFAULT uuidv7(),
    reservation_id UUID NOT NULL,
    event_type VARCHAR(16) NOT NULL,
    previous_status VARCHAR(16),
    current_status VARCHAR(16) NOT NULL,
    previous_expires_at TIMESTAMPTZ,
    current_expires_at TIMESTAMPTZ NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT pk_inventory_reservation_events PRIMARY KEY (id),
    CONSTRAINT fk_inventory_reservation_events__reservation_id
        FOREIGN KEY (reservation_id) REFERENCES inventory_reservations (id) ON DELETE RESTRICT,
    CONSTRAINT ck_inventory_reservation_events__uuid_v7 CHECK (uuid_extract_version(id) = 7),
    CONSTRAINT ck_inventory_reservation_events__type
        CHECK (event_type IN ('CREATED', 'EXTENDED', 'COMMITTED', 'RELEASED', 'EXPIRED')),
    CONSTRAINT ck_inventory_reservation_events__current_status
        CHECK (current_status IN ('ACTIVE', 'COMMITTED', 'RELEASED', 'EXPIRED')),
    CONSTRAINT ck_inventory_reservation_events__previous_status
        CHECK (previous_status IS NULL OR previous_status = 'ACTIVE'),
    CONSTRAINT ck_inventory_reservation_events__transition CHECK (
        (event_type = 'CREATED' AND previous_status IS NULL AND current_status = 'ACTIVE'
            AND previous_expires_at IS NULL) OR
        (event_type = 'EXTENDED' AND previous_status = 'ACTIVE' AND current_status = 'ACTIVE'
            AND previous_expires_at IS NOT NULL AND current_expires_at > previous_expires_at) OR
        (event_type = 'COMMITTED' AND previous_status = 'ACTIVE' AND current_status = 'COMMITTED'
            AND previous_expires_at = current_expires_at) OR
        (event_type = 'RELEASED' AND previous_status = 'ACTIVE' AND current_status = 'RELEASED'
            AND previous_expires_at = current_expires_at) OR
        (event_type = 'EXPIRED' AND previous_status = 'ACTIVE' AND current_status = 'EXPIRED'
            AND previous_expires_at = current_expires_at)
    )
);

CREATE INDEX ix_inventory_reservation_events__reservation_time
    ON inventory_reservation_events (reservation_id, occurred_at, id);

CREATE TABLE inventory_command_idempotency (
    actor_scope VARCHAR(255) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    operation VARCHAR(80) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    resource_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    CONSTRAINT pk_inventory_command_idempotency PRIMARY KEY (actor_scope, idempotency_key),
    CONSTRAINT ck_inventory_command_idempotency__actor_not_blank
        CHECK (length(btrim(actor_scope)) BETWEEN 1 AND 255),
    CONSTRAINT ck_inventory_command_idempotency__key_not_blank
        CHECK (length(btrim(idempotency_key)) BETWEEN 16 AND 128),
    CONSTRAINT ck_inventory_command_idempotency__operation_format
        CHECK (operation ~ '^[A-Z][A-Z0-9_]{1,79}$'),
    CONSTRAINT ck_inventory_command_idempotency__fingerprint_format
        CHECK (request_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_inventory_command_idempotency__completion_state
        CHECK ((resource_id IS NULL) = (completed_at IS NULL))
);

CREATE INDEX ix_inventory_command_idempotency__created_at
    ON inventory_command_idempotency (created_at);

CREATE TABLE inventory_job_leases (
    job_name VARCHAR(80) NOT NULL,
    owner_instance_id VARCHAR(128) NOT NULL,
    leased_until TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_inventory_job_leases PRIMARY KEY (job_name),
    CONSTRAINT ck_inventory_job_leases__job_format CHECK (job_name ~ '^[a-z][a-z0-9_-]{1,79}$'),
    CONSTRAINT ck_inventory_job_leases__owner_not_blank
        CHECK (length(btrim(owner_instance_id)) BETWEEN 1 AND 128),
    CONSTRAINT ck_inventory_job_leases__version CHECK (version >= 0)
);

CREATE TABLE inventory_audit_events (
    id UUID NOT NULL DEFAULT uuidv7(),
    actor_scope VARCHAR(255) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    warehouse_id UUID NOT NULL,
    variant_id UUID NOT NULL,
    action VARCHAR(64) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    reference VARCHAR(255),
    correlation_id VARCHAR(64) NOT NULL,
    safe_diff JSONB NOT NULL,
    CONSTRAINT pk_inventory_audit_events PRIMARY KEY (id),
    CONSTRAINT fk_inventory_audit_events__warehouse_id
        FOREIGN KEY (warehouse_id) REFERENCES inventory_warehouses (id) ON DELETE RESTRICT,
    CONSTRAINT ck_inventory_audit_events__uuid_v7 CHECK (uuid_extract_version(id) = 7),
    CONSTRAINT ck_inventory_audit_events__actor_not_blank
        CHECK (length(btrim(actor_scope)) BETWEEN 1 AND 255),
    CONSTRAINT ck_inventory_audit_events__variant_uuid_v7 CHECK (uuid_extract_version(variant_id) = 7),
    CONSTRAINT ck_inventory_audit_events__action_format CHECK (action ~ '^[A-Z][A-Z0-9_]{1,63}$'),
    CONSTRAINT ck_inventory_audit_events__reason_not_blank CHECK (length(btrim(reason)) BETWEEN 1 AND 500),
    CONSTRAINT ck_inventory_audit_events__reference_not_blank
        CHECK (reference IS NULL OR length(btrim(reference)) BETWEEN 1 AND 255),
    CONSTRAINT ck_inventory_audit_events__correlation_not_blank
        CHECK (length(btrim(correlation_id)) BETWEEN 8 AND 64),
    CONSTRAINT ck_inventory_audit_events__safe_diff_object CHECK (jsonb_typeof(safe_diff) = 'object')
);

CREATE INDEX ix_inventory_audit_events__balance_time
    ON inventory_audit_events (warehouse_id, variant_id, occurred_at DESC, id DESC);

CREATE INDEX ix_inventory_audit_events__correlation
    ON inventory_audit_events (correlation_id, occurred_at, id);

CREATE FUNCTION reject_inventory_append_only_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'inventory ledger and audit records are append-only' USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER trg_inventory_movements__append_only
BEFORE UPDATE OR DELETE ON inventory_movements
FOR EACH ROW EXECUTE FUNCTION reject_inventory_append_only_mutation();

CREATE TRIGGER trg_inventory_reservation_events__append_only
BEFORE UPDATE OR DELETE ON inventory_reservation_events
FOR EACH ROW EXECUTE FUNCTION reject_inventory_append_only_mutation();

CREATE TRIGGER trg_inventory_audit_events__append_only
BEFORE UPDATE OR DELETE ON inventory_audit_events
FOR EACH ROW EXECUTE FUNCTION reject_inventory_append_only_mutation();

REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON inventory_warehouses FROM amra_runtime;
REVOKE DELETE, TRUNCATE ON inventory_balances FROM amra_runtime;
REVOKE UPDATE, DELETE, TRUNCATE ON inventory_movements FROM amra_runtime;
REVOKE DELETE, TRUNCATE ON inventory_reservations FROM amra_runtime;
REVOKE UPDATE, DELETE, TRUNCATE ON inventory_reservation_lines FROM amra_runtime;
REVOKE UPDATE, DELETE, TRUNCATE ON inventory_reservation_events FROM amra_runtime;
REVOKE DELETE, TRUNCATE ON inventory_command_idempotency FROM amra_runtime;
REVOKE DELETE, TRUNCATE ON inventory_job_leases FROM amra_runtime;
REVOKE UPDATE, DELETE, TRUNCATE ON inventory_audit_events FROM amra_runtime;
