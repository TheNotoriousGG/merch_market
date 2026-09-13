SET search_path TO amra_shop, public;

-- Stable local-only identities make this seed safe to run more than once. Existing rows
-- are deliberately preserved so rerunning Compose never erases changes made in admin.
INSERT INTO catalog_attribute_definitions
    (id, code, display_name, attribute_type, filterable, variant_defining, display_order)
VALUES
    ('01999999-0002-7000-8000-000000000001', 'color', 'Цвет', 'COLOR', TRUE, TRUE, 10),
    ('01999999-0002-7000-8000-000000000002', 'size', 'Размер', 'SIZE', TRUE, TRUE, 20),
    ('01999999-0002-7000-8000-000000000003', 'material', 'Материал', 'TEXT', TRUE, FALSE, 30)
ON CONFLICT (code) DO NOTHING;

INSERT INTO catalog_products
    (id, canonical_slug, name, short_description, description, status, primary_category_id,
     published_at, price_minor, new_arrival, new_until, on_sale, sale_percent, featured)
VALUES
    ('01999999-0003-7000-8000-000000000001', 'mono-graphite-hoodie', 'Худи Mono Graphite',
     'Плотное худи свободного кроя в графитовом цвете.',
     'Базовое унисекс-худи из плотного хлопкового футера. Мягкая изнанка, глубокий капюшон и аккуратная вышивка AMRA.',
     'ACTIVE', '01999999-0001-7000-8000-000000000102', '2026-08-28 09:00:00+03', 899000, TRUE, '2099-01-01', FALSE, NULL, TRUE),
    ('01999999-0003-7000-8000-000000000002', 'urban-blue-hoodie', 'Худи Urban Blue',
     'Синее худи с контрастной городской графикой.',
     'Свободный силуэт, усиленные манжеты и принт, устойчивый к регулярной стирке. Для прохладных вечеров и многослойных образов.',
     'ACTIVE', '01999999-0001-7000-8000-000000000102', '2026-08-20 09:00:00+03', 949000, TRUE, '2099-01-01', TRUE, 20, TRUE),
    ('01999999-0003-7000-8000-000000000003', 'duo-contrast-hoodie', 'Худи Duo Contrast',
     'Контрастное двухцветное худи лимитированной серии.',
     'Выразительная модель из хлопкового футера с объемным капюшоном. Контрастные панели собраны вручную малыми партиями.',
     'ACTIVE', '01999999-0001-7000-8000-000000000102', '2026-07-15 09:00:00+03', 1099000, FALSE, NULL, FALSE, NULL, TRUE),
    ('01999999-0003-7000-8000-000000000004', 'signal-white-tee', 'Футболка Signal White',
     'Белая футболка oversize с лаконичным принтом.',
     'Дышащий хлопок средней плотности, спущенное плечо и свободная посадка. Универсальная основа летнего гардероба.',
     'ACTIVE', '01999999-0001-7000-8000-000000000101', '2026-08-30 09:00:00+03', 399000, TRUE, '2099-01-01', FALSE, NULL, FALSE),
    ('01999999-0003-7000-8000-000000000005', 'motion-black-joggers', 'Джоггеры Motion Black',
     'Черные джоггеры с регулируемой посадкой.',
     'Плотный футер держит форму, эластичный пояс регулируется шнуром, два боковых кармана дополнены скрытым задним карманом.',
     'ACTIVE', '01999999-0001-7000-8000-000000000401', '2026-06-10 09:00:00+03', 649000, FALSE, NULL, TRUE, 15, FALSE),
    ('01999999-0003-7000-8000-000000000006', 'canvas-natural-tote', 'Шоппер Canvas Natural',
     'Вместительный шоппер из натурального канваса.',
     'Укрепленное дно, внутренний карман и длинные ручки. Вмещает ноутбук, документы и покупки на каждый день.',
     'ACTIVE', '01999999-0001-7000-8000-000000000301', '2026-05-12 09:00:00+03', 299000, FALSE, NULL, FALSE, NULL, TRUE),
    ('01999999-0003-7000-8000-000000000007', 'orbit-crossbody', 'Сумка Orbit Crossbody',
     'Компактная сумка через плечо с тремя отделениями.',
     'Водоотталкивающий материал, регулируемый ремень и надежные молнии. Телефон, документы и ключи остаются под рукой.',
     'ACTIVE', '01999999-0001-7000-8000-000000000302', '2026-08-25 09:00:00+03', 459000, TRUE, '2099-01-01', TRUE, 25, FALSE),
    ('01999999-0003-7000-8000-000000000008', 'core-logo-cap', 'Кепка Core Logo',
     'Шестипанельная кепка с вышитым логотипом.',
     'Хлопковая кепка с металлической регулировкой сзади и вышивкой тон в тон. Один универсальный размер.',
     'ACTIVE', '01999999-0001-7000-8000-000000000201', '2026-04-18 09:00:00+03', 249000, FALSE, NULL, FALSE, NULL, FALSE),
    ('01999999-0003-7000-8000-000000000009', 'studio-bomber', 'Бомбер Studio',
     'Легкий черный бомбер с фирменной подкладкой.',
     'Ветрозащитная ткань, два внешних и один внутренний карман. Подходит для межсезонья и свободной многослойной посадки.',
     'ACTIVE', '01999999-0001-7000-8000-000000000103', '2026-08-12 09:00:00+03', 1299000, FALSE, NULL, FALSE, NULL, TRUE),
    ('01999999-0003-7000-8000-000000000010', 'archive-sample-tee', 'Футболка Archive Sample',
     'Черновик товара для проверки административного workflow.',
     'Этот товар намеренно оставлен черновиком: его можно отредактировать, дополнить и опубликовать через административную часть.',
     'DRAFT', '01999999-0001-7000-8000-000000000101', NULL, 349000, FALSE, NULL, FALSE, NULL, FALSE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO catalog_product_categories (product_id, category_id) VALUES
    ('01999999-0003-7000-8000-000000000001', '01999999-0001-7000-8000-000000000102'),
    ('01999999-0003-7000-8000-000000000002', '01999999-0001-7000-8000-000000000102'),
    ('01999999-0003-7000-8000-000000000003', '01999999-0001-7000-8000-000000000102'),
    ('01999999-0003-7000-8000-000000000004', '01999999-0001-7000-8000-000000000101'),
    ('01999999-0003-7000-8000-000000000005', '01999999-0001-7000-8000-000000000401'),
    ('01999999-0003-7000-8000-000000000006', '01999999-0001-7000-8000-000000000301'),
    ('01999999-0003-7000-8000-000000000007', '01999999-0001-7000-8000-000000000302'),
    ('01999999-0003-7000-8000-000000000008', '01999999-0001-7000-8000-000000000201'),
    ('01999999-0003-7000-8000-000000000009', '01999999-0001-7000-8000-000000000103'),
    ('01999999-0003-7000-8000-000000000010', '01999999-0001-7000-8000-000000000101')
ON CONFLICT DO NOTHING;

INSERT INTO catalog_product_characteristics
    (product_id, attribute_definition_id, attribute_value, display_order, value_label)
SELECT product_id, (SELECT id FROM catalog_attribute_definitions WHERE code = 'material'), material, 10, material
FROM (VALUES
    ('01999999-0003-7000-8000-000000000001'::uuid, 'Хлопок 80%, полиэстер 20%'),
    ('01999999-0003-7000-8000-000000000002'::uuid, 'Хлопок 80%, полиэстер 20%'),
    ('01999999-0003-7000-8000-000000000003'::uuid, 'Хлопок 100%'),
    ('01999999-0003-7000-8000-000000000004'::uuid, 'Хлопок 100%'),
    ('01999999-0003-7000-8000-000000000005'::uuid, 'Хлопок 80%, полиэстер 20%'),
    ('01999999-0003-7000-8000-000000000006'::uuid, 'Хлопковый канвас'),
    ('01999999-0003-7000-8000-000000000007'::uuid, 'Переработанный полиэстер'),
    ('01999999-0003-7000-8000-000000000008'::uuid, 'Хлопок 100%'),
    ('01999999-0003-7000-8000-000000000009'::uuid, 'Нейлон с водоотталкивающей пропиткой')
) AS data(product_id, material)
ON CONFLICT DO NOTHING;

INSERT INTO catalog_product_variants
    (id, product_id, sku, label, status, display_order, defining_signature)
VALUES
    ('01999999-0004-7000-8000-000000000001', '01999999-0003-7000-8000-000000000001', 'AMRA-HMG-M', 'Графит / M', 'ACTIVE', 10, 'color=GRAPHITE|size=M'),
    ('01999999-0004-7000-8000-000000000002', '01999999-0003-7000-8000-000000000001', 'AMRA-HMG-L', 'Графит / L', 'ACTIVE', 20, 'color=GRAPHITE|size=L'),
    ('01999999-0004-7000-8000-000000000003', '01999999-0003-7000-8000-000000000002', 'AMRA-HUB-M', 'Синий / M', 'ACTIVE', 10, 'color=BLUE|size=M'),
    ('01999999-0004-7000-8000-000000000004', '01999999-0003-7000-8000-000000000002', 'AMRA-HUB-L', 'Синий / L', 'ACTIVE', 20, 'color=BLUE|size=L'),
    ('01999999-0004-7000-8000-000000000005', '01999999-0003-7000-8000-000000000003', 'AMRA-HDC-M', 'Черный / M', 'ACTIVE', 10, 'color=BLACK|size=M'),
    ('01999999-0004-7000-8000-000000000006', '01999999-0003-7000-8000-000000000003', 'AMRA-HDC-L', 'Черный / L', 'ACTIVE', 20, 'color=BLACK|size=L'),
    ('01999999-0004-7000-8000-000000000007', '01999999-0003-7000-8000-000000000004', 'AMRA-TSW-S', 'Белый / S', 'ACTIVE', 10, 'color=WHITE|size=S'),
    ('01999999-0004-7000-8000-000000000008', '01999999-0003-7000-8000-000000000004', 'AMRA-TSW-M', 'Белый / M', 'ACTIVE', 20, 'color=WHITE|size=M'),
    ('01999999-0004-7000-8000-000000000009', '01999999-0003-7000-8000-000000000005', 'AMRA-JMB-M', 'Черный / M', 'ACTIVE', 10, 'color=BLACK|size=M'),
    ('01999999-0004-7000-8000-000000000010', '01999999-0003-7000-8000-000000000005', 'AMRA-JMB-L', 'Черный / L', 'ACTIVE', 20, 'color=BLACK|size=L'),
    ('01999999-0004-7000-8000-000000000011', '01999999-0003-7000-8000-000000000006', 'AMRA-TCN-ONE', 'Натуральный / ONE', 'ACTIVE', 10, 'color=NATURAL|size=ONE'),
    ('01999999-0004-7000-8000-000000000012', '01999999-0003-7000-8000-000000000007', 'AMRA-OCB-ONE', 'Черный / ONE', 'ACTIVE', 10, 'color=BLACK|size=ONE'),
    ('01999999-0004-7000-8000-000000000013', '01999999-0003-7000-8000-000000000008', 'AMRA-CLB-ONE', 'Черный / ONE', 'ACTIVE', 10, 'color=BLACK|size=ONE'),
    ('01999999-0004-7000-8000-000000000014', '01999999-0003-7000-8000-000000000009', 'AMRA-BST-M', 'Черный / M', 'ACTIVE', 10, 'color=BLACK|size=M'),
    ('01999999-0004-7000-8000-000000000015', '01999999-0003-7000-8000-000000000009', 'AMRA-BST-L', 'Черный / L', 'ACTIVE', 20, 'color=BLACK|size=L')
ON CONFLICT (id) DO UPDATE SET defining_signature = EXCLUDED.defining_signature;

INSERT INTO catalog_variant_attribute_values
    (variant_id, attribute_definition_id, attribute_value, display_order, value_label)
SELECT id, (SELECT id FROM catalog_attribute_definitions WHERE code = 'color'), code, 10, label FROM (VALUES
    ('01999999-0004-7000-8000-000000000001'::uuid,'GRAPHITE','Графит'), ('01999999-0004-7000-8000-000000000002'::uuid,'GRAPHITE','Графит'),
    ('01999999-0004-7000-8000-000000000003'::uuid,'BLUE','Синий'), ('01999999-0004-7000-8000-000000000004'::uuid,'BLUE','Синий'),
    ('01999999-0004-7000-8000-000000000005'::uuid,'BLACK','Черный'), ('01999999-0004-7000-8000-000000000006'::uuid,'BLACK','Черный'),
    ('01999999-0004-7000-8000-000000000007'::uuid,'WHITE','Белый'), ('01999999-0004-7000-8000-000000000008'::uuid,'WHITE','Белый'),
    ('01999999-0004-7000-8000-000000000009'::uuid,'BLACK','Черный'), ('01999999-0004-7000-8000-000000000010'::uuid,'BLACK','Черный'),
    ('01999999-0004-7000-8000-000000000011'::uuid,'NATURAL','Натуральный'), ('01999999-0004-7000-8000-000000000012'::uuid,'BLACK','Черный'),
    ('01999999-0004-7000-8000-000000000013'::uuid,'BLACK','Черный'), ('01999999-0004-7000-8000-000000000014'::uuid,'BLACK','Черный'),
    ('01999999-0004-7000-8000-000000000015'::uuid,'BLACK','Черный')) AS data(id,code,label)
ON CONFLICT (variant_id, attribute_definition_id) DO UPDATE
SET attribute_value = EXCLUDED.attribute_value, value_label = EXCLUDED.value_label;

INSERT INTO catalog_variant_attribute_values
    (variant_id, attribute_definition_id, attribute_value, display_order, value_label)
SELECT id, (SELECT id FROM catalog_attribute_definitions WHERE code = 'size'), size, 20, size FROM (VALUES
    ('01999999-0004-7000-8000-000000000001'::uuid,'M'), ('01999999-0004-7000-8000-000000000002'::uuid,'L'),
    ('01999999-0004-7000-8000-000000000003'::uuid,'M'), ('01999999-0004-7000-8000-000000000004'::uuid,'L'),
    ('01999999-0004-7000-8000-000000000005'::uuid,'M'), ('01999999-0004-7000-8000-000000000006'::uuid,'L'),
    ('01999999-0004-7000-8000-000000000007'::uuid,'S'), ('01999999-0004-7000-8000-000000000008'::uuid,'M'),
    ('01999999-0004-7000-8000-000000000009'::uuid,'M'), ('01999999-0004-7000-8000-000000000010'::uuid,'L'),
    ('01999999-0004-7000-8000-000000000011'::uuid,'ONE'), ('01999999-0004-7000-8000-000000000012'::uuid,'ONE'),
    ('01999999-0004-7000-8000-000000000013'::uuid,'ONE'), ('01999999-0004-7000-8000-000000000014'::uuid,'M'),
    ('01999999-0004-7000-8000-000000000015'::uuid,'L')) AS data(id,size)
ON CONFLICT DO NOTHING;

INSERT INTO catalog_product_media
    (id, product_id, media_type, object_key, content_type, width, height, alt_text, display_order, is_primary)
VALUES
    ('01999999-0005-7000-8000-000000000001','01999999-0003-7000-8000-000000000001','IMAGE','demo/products/mono-graphite-hoodie.jpg','image/jpeg',1200,1735,'Худи Mono Graphite',10,TRUE),
    ('01999999-0005-7000-8000-000000000002','01999999-0003-7000-8000-000000000002','IMAGE','demo/products/urban-blue-hoodie.jpg','image/jpeg',1200,1800,'Худи Urban Blue',10,TRUE),
    ('01999999-0005-7000-8000-000000000003','01999999-0003-7000-8000-000000000003','IMAGE','demo/products/duo-contrast-hoodie.jpg','image/jpeg',1200,1800,'Худи Duo Contrast',10,TRUE),
    ('01999999-0005-7000-8000-000000000004','01999999-0003-7000-8000-000000000004','IMAGE','demo/products/signal-white-tee.jpg','image/jpeg',1200,1735,'Футболка Signal White',10,TRUE),
    ('01999999-0005-7000-8000-000000000005','01999999-0003-7000-8000-000000000005','IMAGE','demo/products/motion-black-joggers.jpg','image/jpeg',1200,1735,'Джоггеры Motion Black',10,TRUE),
    ('01999999-0005-7000-8000-000000000006','01999999-0003-7000-8000-000000000006','IMAGE','demo/products/canvas-natural-tote.jpg','image/jpeg',1200,960,'Шоппер Canvas Natural',10,TRUE),
    ('01999999-0005-7000-8000-000000000007','01999999-0003-7000-8000-000000000007','IMAGE','demo/products/orbit-crossbody.jpg','image/jpeg',1200,960,'Сумка Orbit Crossbody',10,TRUE),
    ('01999999-0005-7000-8000-000000000008','01999999-0003-7000-8000-000000000008','IMAGE','demo/products/core-logo-cap.jpg','image/jpeg',1200,960,'Кепка Core Logo',10,TRUE),
    ('01999999-0005-7000-8000-000000000009','01999999-0003-7000-8000-000000000009','IMAGE','demo/products/studio-bomber.jpg','image/jpeg',1200,1800,'Бомбер Studio',10,TRUE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO catalog_collections (id, slug, name, description, status, display_order) VALUES
    ('01999999-0006-7000-8000-000000000001','new-season','Новый сезон','Свежие модели и новые цвета коллекции AMRA.','ACTIVE',10),
    ('01999999-0006-7000-8000-000000000002','editors-choice','Выбор редакции','Главные модели недели, собранные в одной подборке.','ACTIVE',20),
    ('01999999-0006-7000-8000-000000000003','city-essentials','Городской набор','Одежда и аксессуары для повседневного городского ритма.','ACTIVE',30)
ON CONFLICT (id) DO NOTHING;

INSERT INTO catalog_collection_products (collection_id, product_id, display_order) VALUES
    ('01999999-0006-7000-8000-000000000001','01999999-0003-7000-8000-000000000001',10),
    ('01999999-0006-7000-8000-000000000001','01999999-0003-7000-8000-000000000004',20),
    ('01999999-0006-7000-8000-000000000001','01999999-0003-7000-8000-000000000007',30),
    ('01999999-0006-7000-8000-000000000002','01999999-0003-7000-8000-000000000003',10),
    ('01999999-0006-7000-8000-000000000002','01999999-0003-7000-8000-000000000006',20),
    ('01999999-0006-7000-8000-000000000002','01999999-0003-7000-8000-000000000009',30),
    ('01999999-0006-7000-8000-000000000003','01999999-0003-7000-8000-000000000002',10),
    ('01999999-0006-7000-8000-000000000003','01999999-0003-7000-8000-000000000005',20),
    ('01999999-0006-7000-8000-000000000003','01999999-0003-7000-8000-000000000008',30)
ON CONFLICT DO NOTHING;

INSERT INTO inventory_balances (warehouse_id, variant_id, on_hand, reserved)
SELECT warehouse.id, stock.variant_id, stock.quantity, 0
FROM inventory_warehouses warehouse
CROSS JOIN (VALUES
    ('01999999-0004-7000-8000-000000000001'::uuid,18), ('01999999-0004-7000-8000-000000000002'::uuid,9),
    ('01999999-0004-7000-8000-000000000003'::uuid,14), ('01999999-0004-7000-8000-000000000004'::uuid,0),
    ('01999999-0004-7000-8000-000000000005'::uuid,6), ('01999999-0004-7000-8000-000000000006'::uuid,4),
    ('01999999-0004-7000-8000-000000000007'::uuid,22), ('01999999-0004-7000-8000-000000000008'::uuid,16),
    ('01999999-0004-7000-8000-000000000009'::uuid,11), ('01999999-0004-7000-8000-000000000010'::uuid,8),
    ('01999999-0004-7000-8000-000000000011'::uuid,30), ('01999999-0004-7000-8000-000000000012'::uuid,7),
    ('01999999-0004-7000-8000-000000000013'::uuid,25), ('01999999-0004-7000-8000-000000000014'::uuid,3),
    ('01999999-0004-7000-8000-000000000015'::uuid,2)
) AS stock(variant_id, quantity)
WHERE warehouse.code = 'PRIMARY'
ON CONFLICT DO NOTHING;

INSERT INTO inventory_movements
    (id, warehouse_id, variant_id, movement_type, quantity_delta, reason, reference, occurred_at)
SELECT ('01999999-0007-7000-8000-' || lpad(row_number() OVER (ORDER BY balance.variant_id)::text, 12, '0'))::uuid,
       balance.warehouse_id, balance.variant_id, 'RECEIPT', balance.on_hand,
       'Начальный остаток демонстрационного каталога', 'LOCAL-DEMO-SEED', '2026-01-01 00:00:00+03'
FROM inventory_balances balance
JOIN inventory_warehouses warehouse ON warehouse.id = balance.warehouse_id AND warehouse.code = 'PRIMARY'
WHERE balance.variant_id::text LIKE '01999999-0004-7000-8000-%' AND balance.on_hand > 0
ON CONFLICT (id) DO NOTHING;

INSERT INTO pricing_base_price_periods (id, variant_id, amount_minor, currency, starts_at)
SELECT ('01999999-0008-7000-8000-' || lpad(row_number() OVER (ORDER BY variant.id)::text, 12, '0'))::uuid,
       variant.id, product.price_minor, 'RUB', '2026-01-01 00:00:00+03'
FROM catalog_product_variants variant
JOIN catalog_products product ON product.id = variant.product_id
WHERE variant.id::text LIKE '01999999-0004-7000-8000-%'
ON CONFLICT (id) DO NOTHING;

INSERT INTO pricing_promotions
    (id, name, promotion_type, percent, amount_minor, paid_quantity, bundle_quantity, starts_at, ends_at, enabled)
VALUES
    ('01999999-0009-7000-8000-000000000001','Urban Blue −20%','PERCENT',20,NULL,NULL,NULL,'2026-01-01','2099-01-01',TRUE),
    ('01999999-0009-7000-8000-000000000002','Orbit: скидка 1 150 ₽','FIXED_LINE',NULL,115000,NULL,NULL,'2026-01-01','2099-01-01',TRUE),
    ('01999999-0009-7000-8000-000000000003','Кепки 3 по цене 2','MULTI_BUY',NULL,NULL,2,3,'2026-01-01','2099-01-01',TRUE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO pricing_promotion_variants (promotion_id, variant_id) VALUES
    ('01999999-0009-7000-8000-000000000001','01999999-0004-7000-8000-000000000003'),
    ('01999999-0009-7000-8000-000000000001','01999999-0004-7000-8000-000000000004'),
    ('01999999-0009-7000-8000-000000000002','01999999-0004-7000-8000-000000000012'),
    ('01999999-0009-7000-8000-000000000003','01999999-0004-7000-8000-000000000013')
ON CONFLICT DO NOTHING;

INSERT INTO storefront_banners
    (id, internal_name, eyebrow, title, description, button_label, target_type, target_value,
     desktop_object_key, mobile_object_key, status, display_order, starts_at, ends_at)
VALUES
    ('01999999-0010-7000-8000-000000000001','Новый сезон','НОВАЯ КОЛЛЕКЦИЯ','Новый сезон AMRA','Свежие формы, плотные материалы и спокойные цвета для города.','Смотреть новинки','COLLECTION','new-season',
     'banners/01999999-0010-7000-8000-000000000001/uploads/desktop.jpg','banners/01999999-0010-7000-8000-000000000001/uploads/mobile.jpg','PUBLISHED',10,'2026-01-01','2099-01-01'),
    ('01999999-0010-7000-8000-000000000002','Большая распродажа','SALE','До −25% на избранные модели','Худи, джоггеры и сумки по специальным ценам, пока размеры в наличии.','Перейти к распродаже','SALE','sale',
     'banners/01999999-0010-7000-8000-000000000002/uploads/desktop.jpg','banners/01999999-0010-7000-8000-000000000002/uploads/mobile.jpg','PUBLISHED',20,'2026-01-01','2099-01-01')
ON CONFLICT (id) DO NOTHING;
