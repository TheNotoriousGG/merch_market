CREATE TABLE inventory_stock_command_results (
    movement_id UUID NOT NULL,
    warehouse_id UUID NOT NULL,
    variant_id UUID NOT NULL,
    on_hand BIGINT NOT NULL,
    reserved BIGINT NOT NULL,
    balance_version BIGINT NOT NULL,
    balance_updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_inventory_stock_command_results PRIMARY KEY (movement_id),
    CONSTRAINT fk_inventory_stock_command_results__movement_id
        FOREIGN KEY (movement_id) REFERENCES inventory_movements (id) ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_stock_command_results__balance
        FOREIGN KEY (warehouse_id, variant_id)
        REFERENCES inventory_balances (warehouse_id, variant_id) ON DELETE RESTRICT,
    CONSTRAINT ck_inventory_stock_command_results__quantities
        CHECK (on_hand >= 0 AND reserved >= 0 AND reserved <= on_hand),
    CONSTRAINT ck_inventory_stock_command_results__version CHECK (balance_version >= 0)
);

CREATE INDEX ix_inventory_stock_command_results__balance
    ON inventory_stock_command_results (warehouse_id, variant_id, balance_version);

CREATE TRIGGER trg_inventory_stock_command_results__append_only
BEFORE UPDATE OR DELETE ON inventory_stock_command_results
FOR EACH ROW EXECUTE FUNCTION reject_inventory_append_only_mutation();

REVOKE UPDATE, DELETE, TRUNCATE ON inventory_stock_command_results FROM amra_runtime;
