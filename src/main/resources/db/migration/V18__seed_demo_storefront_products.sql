INSERT INTO catalog_attribute_definitions (
    id, code, display_name, attribute_type, filterable, variant_defining, display_order
)
VALUES ('01990000-0030-7000-8000-000000000001', 'size', 'Размер', 'SIZE', TRUE, TRUE, 0)
ON CONFLICT (code) DO NOTHING;

WITH demo(id, category_slug, slug, name, short_description, description, price_minor, new_arrival, on_sale, sale_percent, featured) AS (
    VALUES
        ('01990000-0001-7000-8000-000000000001'::uuid, 'hoodies', 'mono-graphite-hoodie', 'Худи Mono Graphite', 'Плотное графитовое худи свободного кроя.', 'Демонстрационный товар Amra: мягкое худи со свободной посадкой и глубоким капюшоном.', 649000::bigint, TRUE, FALSE, NULL::integer, TRUE),
        ('01990000-0001-7000-8000-000000000002'::uuid, 'hoodies', 'urban-blue-hoodie', 'Худи Urban Blue', 'Синее худи для многослойных городских образов.', 'Демонстрационный товар Amra: комфортная посадка, плотный трикотаж и спокойный синий оттенок.', 599000::bigint, TRUE, TRUE, 20, TRUE),
        ('01990000-0001-7000-8000-000000000003'::uuid, 'tote-bags', 'canvas-natural-tote', 'Шопер Canvas Natural', 'Вместительный хлопковый шопер натурального оттенка.', 'Демонстрационный товар Amra: лёгкая повседневная сумка с длинными ручками.', 299000::bigint, TRUE, TRUE, 25, FALSE),
        ('01990000-0001-7000-8000-000000000004'::uuid, 'hoodies', 'duo-contrast-hoodie', 'Худи Duo Contrast', 'Контрастное худи в современной уличной стилистике.', 'Демонстрационный товар Amra: выразительный цвет и расслабленный унисекс-силуэт.', 729000::bigint, TRUE, TRUE, 15, FALSE)
)
INSERT INTO catalog_products (
    id, canonical_slug, name, short_description, description, status, primary_category_id,
    published_at, price_minor, new_arrival, new_until, on_sale, sale_percent, featured
)
SELECT demo.id, demo.slug, demo.name, demo.short_description, demo.description, 'ACTIVE', category.id,
       CURRENT_TIMESTAMP, demo.price_minor, demo.new_arrival, NULL, demo.on_sale, demo.sale_percent, demo.featured
FROM demo
JOIN catalog_categories category ON category.slug = demo.category_slug
ON CONFLICT (id) DO NOTHING;

WITH assignments(product_id, category_slug) AS (
    VALUES
        ('01990000-0001-7000-8000-000000000001'::uuid, 'hoodies'),
        ('01990000-0001-7000-8000-000000000002'::uuid, 'hoodies'),
        ('01990000-0001-7000-8000-000000000003'::uuid, 'tote-bags'),
        ('01990000-0001-7000-8000-000000000004'::uuid, 'hoodies')
)
INSERT INTO catalog_product_categories (product_id, category_id)
SELECT assignments.product_id, category.id
FROM assignments
JOIN catalog_categories category ON category.slug = assignments.category_slug
ON CONFLICT DO NOTHING;

INSERT INTO catalog_product_variants (
    id, product_id, sku, label, status, display_order, defining_signature
)
VALUES
    ('01990000-0020-7000-8000-000000000001', '01990000-0001-7000-8000-000000000001', 'DEMO-MONO-GRAPHITE-OS', 'One size', 'ACTIVE', 0, 'size=one-size'),
    ('01990000-0020-7000-8000-000000000002', '01990000-0001-7000-8000-000000000002', 'DEMO-URBAN-BLUE-OS', 'One size', 'ACTIVE', 0, 'size=one-size'),
    ('01990000-0020-7000-8000-000000000003', '01990000-0001-7000-8000-000000000003', 'DEMO-CANVAS-TOTE-OS', 'One size', 'ACTIVE', 0, 'size=one-size'),
    ('01990000-0020-7000-8000-000000000004', '01990000-0001-7000-8000-000000000004', 'DEMO-DUO-CONTRAST-OS', 'One size', 'ACTIVE', 0, 'size=one-size')
ON CONFLICT (id) DO NOTHING;

INSERT INTO catalog_variant_attribute_values (
    variant_id, attribute_definition_id, attribute_value, value_label, color_hex, display_order
)
SELECT variant.id, definition.id, 'one-size', 'One size', NULL, 0
FROM catalog_product_variants variant
JOIN catalog_attribute_definitions definition ON definition.code = 'size'
WHERE variant.id IN (
    '01990000-0020-7000-8000-000000000001',
    '01990000-0020-7000-8000-000000000002',
    '01990000-0020-7000-8000-000000000003',
    '01990000-0020-7000-8000-000000000004'
)
ON CONFLICT DO NOTHING;

INSERT INTO catalog_product_media (
    id, product_id, variant_id, media_type, object_key, content_type,
    width, height, alt_text, display_order, is_primary
)
VALUES
    ('01990000-0010-7000-8000-000000000001', '01990000-0001-7000-8000-000000000001', NULL, 'IMAGE', 'demo/mono-graphite-hoodie.jpg', 'image/jpeg', 1200, 1735, 'Худи Mono Graphite', 0, TRUE),
    ('01990000-0010-7000-8000-000000000002', '01990000-0001-7000-8000-000000000002', NULL, 'IMAGE', 'demo/urban-blue-hoodie.jpg', 'image/jpeg', 1200, 1800, 'Худи Urban Blue', 0, TRUE),
    ('01990000-0010-7000-8000-000000000003', '01990000-0001-7000-8000-000000000003', NULL, 'IMAGE', 'demo/canvas-natural-tote.jpg', 'image/jpeg', 1200, 960, 'Шопер Canvas Natural', 0, TRUE),
    ('01990000-0010-7000-8000-000000000004', '01990000-0001-7000-8000-000000000004', NULL, 'IMAGE', 'demo/duo-contrast-hoodie.jpg', 'image/jpeg', 1200, 1800, 'Худи Duo Contrast', 0, TRUE)
ON CONFLICT (id) DO NOTHING;
