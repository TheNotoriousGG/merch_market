SET search_path TO amra_shop, public;

INSERT INTO catalog_categories (id, parent_id, slug, name, display_order, status) VALUES
    ('01999999-0001-7000-8000-000000000001', NULL, 'clothes', 'Одежда', 10, 'ACTIVE'),
    ('01999999-0001-7000-8000-000000000002', NULL, 'accessories', 'Аксессуары', 20, 'ACTIVE'),
    ('01999999-0001-7000-8000-000000000003', NULL, 'bags', 'Сумки', 30, 'ACTIVE'),
    ('01999999-0001-7000-8000-000000000004', NULL, 'pants', 'Брюки', 40, 'ACTIVE')
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, display_order = EXCLUDED.display_order,
    status = EXCLUDED.status, updated_at = CURRENT_TIMESTAMP;

INSERT INTO catalog_categories (id, parent_id, slug, name, display_order, status) VALUES
    ('01999999-0001-7000-8000-000000000101', '01999999-0001-7000-8000-000000000001', 't-shirts', 'Футболки', 10, 'ACTIVE'),
    ('01999999-0001-7000-8000-000000000102', '01999999-0001-7000-8000-000000000001', 'hoodies', 'Худи и свитшоты', 20, 'ACTIVE'),
    ('01999999-0001-7000-8000-000000000103', '01999999-0001-7000-8000-000000000001', 'outerwear', 'Верхняя одежда', 30, 'ACTIVE'),
    ('01999999-0001-7000-8000-000000000201', '01999999-0001-7000-8000-000000000002', 'headwear', 'Головные уборы', 10, 'ACTIVE'),
    ('01999999-0001-7000-8000-000000000202', '01999999-0001-7000-8000-000000000002', 'jewelry', 'Украшения', 20, 'ACTIVE'),
    ('01999999-0001-7000-8000-000000000301', '01999999-0001-7000-8000-000000000003', 'totes', 'Шопперы', 10, 'ACTIVE'),
    ('01999999-0001-7000-8000-000000000302', '01999999-0001-7000-8000-000000000003', 'crossbody', 'Через плечо', 20, 'ACTIVE'),
    ('01999999-0001-7000-8000-000000000401', '01999999-0001-7000-8000-000000000004', 'joggers', 'Джоггеры', 10, 'ACTIVE'),
    ('01999999-0001-7000-8000-000000000402', '01999999-0001-7000-8000-000000000004', 'shorts', 'Шорты', 20, 'ACTIVE')
ON CONFLICT (id) DO UPDATE SET parent_id = EXCLUDED.parent_id, name = EXCLUDED.name,
    display_order = EXCLUDED.display_order, status = EXCLUDED.status, updated_at = CURRENT_TIMESTAMP;
