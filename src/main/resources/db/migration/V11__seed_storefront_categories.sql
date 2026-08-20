INSERT INTO catalog_categories (id, parent_id, slug, name, display_order, status)
VALUES
    (uuidv7(), NULL, 'clothes', 'Одежда', 0, 'ACTIVE'),
    (uuidv7(), NULL, 'accessories', 'Аксессуары', 1, 'ACTIVE'),
    (uuidv7(), NULL, 'bags', 'Сумки', 2, 'ACTIVE'),
    (uuidv7(), NULL, 'pants', 'Брюки', 3, 'ACTIVE')
ON CONFLICT DO NOTHING;
