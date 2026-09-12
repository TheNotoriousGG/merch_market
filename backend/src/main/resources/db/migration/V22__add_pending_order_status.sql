ALTER TABLE customer_orders DROP CONSTRAINT ck_customer_orders_status;
ALTER TABLE customer_orders ADD CONSTRAINT ck_customer_orders_status
    CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED'));
