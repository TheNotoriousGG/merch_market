INSERT INTO catalog_categories (id, parent_id, slug, name, display_order, status)
SELECT uuidv7(), parent.id, seed.slug, seed.name, seed.display_order, 'ACTIVE'
FROM catalog_categories parent
JOIN (VALUES
    ('clothes', 't-shirts-and-polos', 'Футболки и поло', 0),
    ('clothes', 'longsleeves', 'Лонгсливы', 1),
    ('clothes', 'shirts', 'Рубашки', 2),
    ('clothes', 'sweatshirts', 'Свитшоты и олимпийки', 3),
    ('clothes', 'hoodies', 'Худи', 4),
    ('clothes', 'pants-and-shorts', 'Брюки и шорты', 5),
    ('accessories', 'cardholders', 'Картхолдеры', 0),
    ('accessories', 'backpacks-and-bags', 'Рюкзаки и сумки', 1),
    ('accessories', 'cosmetic-bags', 'Косметички', 2),
    ('accessories', 'keychains', 'Брелоки', 3),
    ('accessories', 'bottles-and-mugs', 'Бутылки и кружки', 4),
    ('accessories', 'headwear', 'Головные уборы', 5),
    ('bags', 'tote-bags', 'Шоперы', 0),
    ('bags', 'backpacks', 'Рюкзаки', 1),
    ('bags', 'crossbody-bags', 'Сумки через плечо', 2),
    ('bags', 'laptop-cases', 'Чехлы для ноутбука', 3),
    ('bags', 'organizers', 'Органайзеры', 4),
    ('pants', 'joggers', 'Джоггеры', 0),
    ('pants', 'classic-pants', 'Классические брюки', 1),
    ('pants', 'casual-shorts', 'Повседневные шорты', 2),
    ('pants', 'sport', 'Спортивные', 3),
    ('pants', 'home', 'Домашние', 4)
) AS seed(parent_slug, slug, name, display_order) ON parent.slug = seed.parent_slug
ON CONFLICT DO NOTHING;
