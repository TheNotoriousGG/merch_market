ALTER TABLE inventory_warehouses
    DROP CONSTRAINT ck_inventory_warehouses__uuid_v7,
    ADD CONSTRAINT ck_inventory_warehouses__uuid_v7
        CHECK ((uuid_extract_version(id) = 7) IS TRUE);

ALTER TABLE inventory_balances
    DROP CONSTRAINT ck_inventory_balances__variant_uuid_v7,
    ADD CONSTRAINT ck_inventory_balances__variant_uuid_v7
        CHECK ((uuid_extract_version(variant_id) = 7) IS TRUE);

ALTER TABLE inventory_reservations
    DROP CONSTRAINT ck_inventory_reservations__uuid_v7,
    ADD CONSTRAINT ck_inventory_reservations__uuid_v7
        CHECK ((uuid_extract_version(id) = 7) IS TRUE);

ALTER TABLE inventory_movements
    DROP CONSTRAINT ck_inventory_movements__uuid_v7,
    ADD CONSTRAINT ck_inventory_movements__uuid_v7
        CHECK ((uuid_extract_version(id) = 7) IS TRUE);

ALTER TABLE inventory_reservation_events
    DROP CONSTRAINT ck_inventory_reservation_events__uuid_v7,
    ADD CONSTRAINT ck_inventory_reservation_events__uuid_v7
        CHECK ((uuid_extract_version(id) = 7) IS TRUE);

ALTER TABLE inventory_audit_events
    DROP CONSTRAINT ck_inventory_audit_events__uuid_v7,
    ADD CONSTRAINT ck_inventory_audit_events__uuid_v7
        CHECK ((uuid_extract_version(id) = 7) IS TRUE),
    DROP CONSTRAINT ck_inventory_audit_events__variant_uuid_v7,
    ADD CONSTRAINT ck_inventory_audit_events__variant_uuid_v7
        CHECK ((uuid_extract_version(variant_id) = 7) IS TRUE);
