CREATE TABLE catalog_audit_events (
    id UUID NOT NULL DEFAULT uuidv7(),
    actor_scope VARCHAR(255) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    entity_type VARCHAR(48) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(64) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    correlation_id VARCHAR(64) NOT NULL,
    safe_diff JSONB NOT NULL,
    CONSTRAINT pk_catalog_audit_events PRIMARY KEY (id),
    CONSTRAINT ck_catalog_audit_events__uuid_v7 CHECK (uuid_extract_version(id) = 7),
    CONSTRAINT ck_catalog_audit_events__actor_not_blank
        CHECK (length(btrim(actor_scope)) BETWEEN 1 AND 255),
    CONSTRAINT ck_catalog_audit_events__entity_type_format
        CHECK (entity_type ~ '^[A-Z][A-Z0-9_]{1,47}$'),
    CONSTRAINT ck_catalog_audit_events__entity_uuid_v7 CHECK (uuid_extract_version(entity_id) = 7),
    CONSTRAINT ck_catalog_audit_events__action_format
        CHECK (action ~ '^[A-Z][A-Z0-9_]{1,63}$'),
    CONSTRAINT ck_catalog_audit_events__reason_not_blank
        CHECK (length(btrim(reason)) BETWEEN 1 AND 500),
    CONSTRAINT ck_catalog_audit_events__correlation_not_blank
        CHECK (length(btrim(correlation_id)) BETWEEN 8 AND 64),
    CONSTRAINT ck_catalog_audit_events__safe_diff_object
        CHECK (jsonb_typeof(safe_diff) = 'object')
);

CREATE INDEX ix_catalog_audit_events__entity_time
    ON catalog_audit_events (entity_type, entity_id, occurred_at DESC, id DESC);

CREATE INDEX ix_catalog_audit_events__actor_time
    ON catalog_audit_events (actor_scope, occurred_at DESC, id DESC);

CREATE INDEX ix_catalog_audit_events__correlation
    ON catalog_audit_events (correlation_id, occurred_at, id);

CREATE FUNCTION reject_catalog_audit_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'catalog audit events are append-only' USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER trg_catalog_audit_events__append_only
BEFORE UPDATE OR DELETE ON catalog_audit_events
FOR EACH ROW EXECUTE FUNCTION reject_catalog_audit_mutation();

REVOKE UPDATE, DELETE, TRUNCATE ON catalog_audit_events FROM amra_runtime;
