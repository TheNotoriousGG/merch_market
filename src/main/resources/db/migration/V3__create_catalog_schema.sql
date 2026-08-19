CREATE TABLE catalog_categories (
    id UUID NOT NULL,
    parent_id UUID,
    slug VARCHAR(120) NOT NULL,
    name VARCHAR(160) NOT NULL,
    display_order INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_catalog_categories PRIMARY KEY (id),
    CONSTRAINT fk_catalog_categories__parent_id
        FOREIGN KEY (parent_id) REFERENCES catalog_categories (id) ON DELETE RESTRICT,
    CONSTRAINT ck_catalog_categories__uuid_v7 CHECK (uuid_extract_version(id) = 7),
    CONSTRAINT ck_catalog_categories__not_self_parent CHECK (parent_id IS NULL OR parent_id <> id),
    CONSTRAINT ck_catalog_categories__slug_format
        CHECK (slug ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$'),
    CONSTRAINT ck_catalog_categories__name_not_blank CHECK (length(btrim(name)) BETWEEN 1 AND 160),
    CONSTRAINT ck_catalog_categories__display_order CHECK (display_order >= 0),
    CONSTRAINT ck_catalog_categories__status CHECK (status IN ('ACTIVE', 'HIDDEN')),
    CONSTRAINT ck_catalog_categories__version CHECK (version >= 0),
    CONSTRAINT ck_catalog_categories__timestamps CHECK (updated_at >= created_at)
);

CREATE UNIQUE INDEX uq_catalog_categories__parent_slug_ci
    ON catalog_categories (parent_id, lower(slug)) NULLS NOT DISTINCT;

CREATE INDEX ix_catalog_categories__parent_order
    ON catalog_categories (parent_id, display_order, id);

CREATE TABLE catalog_products (
    id UUID NOT NULL,
    canonical_slug VARCHAR(120) NOT NULL,
    name VARCHAR(200) NOT NULL,
    short_description VARCHAR(500) NOT NULL,
    description VARCHAR(10000) NOT NULL,
    status VARCHAR(16) NOT NULL,
    primary_category_id UUID NOT NULL,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    search_document TSVECTOR GENERATED ALWAYS AS (
        setweight(to_tsvector('russian', coalesce(name, '')), 'A') ||
        setweight(to_tsvector('russian', coalesce(short_description, '')), 'B') ||
        setweight(to_tsvector('russian', coalesce(description, '')), 'C')
    ) STORED,
    CONSTRAINT pk_catalog_products PRIMARY KEY (id),
    CONSTRAINT fk_catalog_products__primary_category_id
        FOREIGN KEY (primary_category_id) REFERENCES catalog_categories (id) ON DELETE RESTRICT,
    CONSTRAINT ck_catalog_products__uuid_v7 CHECK (uuid_extract_version(id) = 7),
    CONSTRAINT ck_catalog_products__slug_format
        CHECK (canonical_slug ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$'),
    CONSTRAINT ck_catalog_products__name_not_blank CHECK (length(btrim(name)) BETWEEN 1 AND 200),
    CONSTRAINT ck_catalog_products__short_description_not_blank
        CHECK (length(btrim(short_description)) BETWEEN 1 AND 500),
    CONSTRAINT ck_catalog_products__description_not_blank
        CHECK (length(btrim(description)) BETWEEN 1 AND 10000),
    CONSTRAINT ck_catalog_products__status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_catalog_products__publication_state CHECK (
        (status = 'DRAFT' AND published_at IS NULL) OR
        (status IN ('ACTIVE', 'ARCHIVED') AND published_at IS NOT NULL)
    ),
    CONSTRAINT ck_catalog_products__version CHECK (version >= 0),
    CONSTRAINT ck_catalog_products__timestamps CHECK (updated_at >= created_at)
);

CREATE UNIQUE INDEX uq_catalog_products__canonical_slug_ci
    ON catalog_products (lower(canonical_slug));

CREATE INDEX ix_catalog_products__active_newest
    ON catalog_products (published_at DESC, id)
    WHERE status = 'ACTIVE';

CREATE INDEX ix_catalog_products__active_name
    ON catalog_products (lower(name), id)
    WHERE status = 'ACTIVE';

CREATE INDEX ix_catalog_products__search_document
    ON catalog_products USING GIN (search_document);

CREATE INDEX ix_catalog_products__name_trigram
    ON catalog_products USING GIN (name public.gin_trgm_ops)
    WHERE status = 'ACTIVE';

CREATE TABLE catalog_product_slug_aliases (
    alias_slug VARCHAR(120) NOT NULL,
    product_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_catalog_product_slug_aliases PRIMARY KEY (alias_slug),
    CONSTRAINT fk_catalog_product_slug_aliases__product_id
        FOREIGN KEY (product_id) REFERENCES catalog_products (id) ON DELETE CASCADE,
    CONSTRAINT ck_catalog_product_slug_aliases__slug_format
        CHECK (alias_slug ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$')
);

CREATE UNIQUE INDEX uq_catalog_product_slug_aliases__alias_ci
    ON catalog_product_slug_aliases (lower(alias_slug));

CREATE INDEX ix_catalog_product_slug_aliases__product_id
    ON catalog_product_slug_aliases (product_id);

CREATE TABLE catalog_attribute_definitions (
    id UUID NOT NULL,
    code VARCHAR(64) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    attribute_type VARCHAR(16) NOT NULL,
    filterable BOOLEAN NOT NULL,
    variant_defining BOOLEAN NOT NULL,
    display_order INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_catalog_attribute_definitions PRIMARY KEY (id),
    CONSTRAINT uq_catalog_attribute_definitions__code UNIQUE (code),
    CONSTRAINT ck_catalog_attribute_definitions__uuid_v7 CHECK (uuid_extract_version(id) = 7),
    CONSTRAINT ck_catalog_attribute_definitions__code_format CHECK (code ~ '^[a-z][a-z0-9_]{0,63}$'),
    CONSTRAINT ck_catalog_attribute_definitions__display_name_not_blank
        CHECK (length(btrim(display_name)) BETWEEN 1 AND 120),
    CONSTRAINT ck_catalog_attribute_definitions__type
        CHECK (attribute_type IN ('TEXT', 'COLOR', 'SIZE', 'DIMENSION')),
    CONSTRAINT ck_catalog_attribute_definitions__display_order CHECK (display_order >= 0),
    CONSTRAINT ck_catalog_attribute_definitions__version CHECK (version >= 0),
    CONSTRAINT ck_catalog_attribute_definitions__timestamps CHECK (updated_at >= created_at)
);

CREATE INDEX ix_catalog_attribute_definitions__filter_order
    ON catalog_attribute_definitions (filterable, display_order, code);

CREATE TABLE catalog_product_categories (
    product_id UUID NOT NULL,
    category_id UUID NOT NULL,
    CONSTRAINT pk_catalog_product_categories PRIMARY KEY (product_id, category_id),
    CONSTRAINT fk_catalog_product_categories__product_id
        FOREIGN KEY (product_id) REFERENCES catalog_products (id) ON DELETE CASCADE,
    CONSTRAINT fk_catalog_product_categories__category_id
        FOREIGN KEY (category_id) REFERENCES catalog_categories (id) ON DELETE RESTRICT
);

CREATE INDEX ix_catalog_product_categories__category_product
    ON catalog_product_categories (category_id, product_id);

CREATE TABLE catalog_product_characteristics (
    product_id UUID NOT NULL,
    attribute_definition_id UUID NOT NULL,
    attribute_value VARCHAR(300) NOT NULL,
    display_order INTEGER NOT NULL,
    CONSTRAINT pk_catalog_product_characteristics PRIMARY KEY (product_id, attribute_definition_id),
    CONSTRAINT fk_catalog_product_characteristics__product_id
        FOREIGN KEY (product_id) REFERENCES catalog_products (id) ON DELETE CASCADE,
    CONSTRAINT fk_catalog_product_characteristics__attribute_definition_id
        FOREIGN KEY (attribute_definition_id) REFERENCES catalog_attribute_definitions (id) ON DELETE RESTRICT,
    CONSTRAINT ck_catalog_product_characteristics__value_not_blank
        CHECK (length(btrim(attribute_value)) BETWEEN 1 AND 300),
    CONSTRAINT ck_catalog_product_characteristics__display_order CHECK (display_order >= 0)
);

CREATE TABLE catalog_product_variants (
    id UUID NOT NULL,
    product_id UUID NOT NULL,
    sku VARCHAR(64) NOT NULL,
    label VARCHAR(160) NOT NULL,
    status VARCHAR(16) NOT NULL,
    display_order INTEGER NOT NULL,
    defining_signature VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_catalog_product_variants PRIMARY KEY (id),
    CONSTRAINT fk_catalog_product_variants__product_id
        FOREIGN KEY (product_id) REFERENCES catalog_products (id) ON DELETE RESTRICT,
    CONSTRAINT ck_catalog_product_variants__uuid_v7 CHECK (uuid_extract_version(id) = 7),
    CONSTRAINT ck_catalog_product_variants__sku_format CHECK (sku ~ '^[A-Z0-9]+(?:-[A-Z0-9]+)*$'),
    CONSTRAINT ck_catalog_product_variants__label_not_blank CHECK (length(btrim(label)) BETWEEN 1 AND 160),
    CONSTRAINT ck_catalog_product_variants__status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_catalog_product_variants__display_order CHECK (display_order >= 0),
    CONSTRAINT ck_catalog_product_variants__defining_signature_not_blank
        CHECK (length(btrim(defining_signature)) BETWEEN 1 AND 1000),
    CONSTRAINT ck_catalog_product_variants__version CHECK (version >= 0),
    CONSTRAINT ck_catalog_product_variants__timestamps CHECK (updated_at >= created_at),
    CONSTRAINT uq_catalog_product_variants__id_product UNIQUE (id, product_id),
    CONSTRAINT uq_catalog_product_variants__product_signature UNIQUE (product_id, defining_signature)
);

CREATE UNIQUE INDEX uq_catalog_product_variants__sku_ci
    ON catalog_product_variants (lower(sku));

CREATE INDEX ix_catalog_product_variants__product_order
    ON catalog_product_variants (product_id, display_order, id);

CREATE TABLE catalog_variant_attribute_values (
    variant_id UUID NOT NULL,
    attribute_definition_id UUID NOT NULL,
    attribute_value VARCHAR(300) NOT NULL,
    display_order INTEGER NOT NULL,
    CONSTRAINT pk_catalog_variant_attribute_values PRIMARY KEY (variant_id, attribute_definition_id),
    CONSTRAINT fk_catalog_variant_attribute_values__variant_id
        FOREIGN KEY (variant_id) REFERENCES catalog_product_variants (id) ON DELETE CASCADE,
    CONSTRAINT fk_catalog_variant_attribute_values__attribute_definition_id
        FOREIGN KEY (attribute_definition_id) REFERENCES catalog_attribute_definitions (id) ON DELETE RESTRICT,
    CONSTRAINT ck_catalog_variant_attribute_values__value_not_blank
        CHECK (length(btrim(attribute_value)) BETWEEN 1 AND 300),
    CONSTRAINT ck_catalog_variant_attribute_values__display_order CHECK (display_order >= 0)
);

CREATE INDEX ix_catalog_variant_attribute_values__filter
    ON catalog_variant_attribute_values (attribute_definition_id, attribute_value, variant_id);

CREATE TABLE catalog_product_media (
    id UUID NOT NULL,
    product_id UUID NOT NULL,
    variant_id UUID,
    media_type VARCHAR(16) NOT NULL,
    object_key VARCHAR(512) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    alt_text VARCHAR(300) NOT NULL,
    display_order INTEGER NOT NULL,
    is_primary BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_catalog_product_media PRIMARY KEY (id),
    CONSTRAINT uq_catalog_product_media__object_key UNIQUE (object_key),
    CONSTRAINT fk_catalog_product_media__product_id
        FOREIGN KEY (product_id) REFERENCES catalog_products (id) ON DELETE RESTRICT,
    CONSTRAINT fk_catalog_product_media__variant_product
        FOREIGN KEY (variant_id, product_id)
        REFERENCES catalog_product_variants (id, product_id) ON DELETE RESTRICT,
    CONSTRAINT ck_catalog_product_media__uuid_v7 CHECK (uuid_extract_version(id) = 7),
    CONSTRAINT ck_catalog_product_media__type CHECK (media_type = 'IMAGE'),
    CONSTRAINT ck_catalog_product_media__content_type CHECK (content_type LIKE 'image/%'),
    CONSTRAINT ck_catalog_product_media__dimensions CHECK (width > 0 AND height > 0),
    CONSTRAINT ck_catalog_product_media__alt_not_blank CHECK (length(btrim(alt_text)) BETWEEN 1 AND 300),
    CONSTRAINT ck_catalog_product_media__display_order CHECK (display_order >= 0),
    CONSTRAINT ck_catalog_product_media__version CHECK (version >= 0),
    CONSTRAINT ck_catalog_product_media__timestamps CHECK (updated_at >= created_at)
);

CREATE UNIQUE INDEX uq_catalog_product_media__one_primary
    ON catalog_product_media (product_id)
    WHERE is_primary;

CREATE INDEX ix_catalog_product_media__product_order
    ON catalog_product_media (product_id, display_order, id);

CREATE TABLE catalog_collections (
    id UUID NOT NULL,
    slug VARCHAR(120) NOT NULL,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    status VARCHAR(16) NOT NULL,
    display_order INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_catalog_collections PRIMARY KEY (id),
    CONSTRAINT ck_catalog_collections__uuid_v7 CHECK (uuid_extract_version(id) = 7),
    CONSTRAINT ck_catalog_collections__slug_format CHECK (slug ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$'),
    CONSTRAINT ck_catalog_collections__name_not_blank CHECK (length(btrim(name)) BETWEEN 1 AND 160),
    CONSTRAINT ck_catalog_collections__description_not_blank
        CHECK (length(btrim(description)) BETWEEN 1 AND 2000),
    CONSTRAINT ck_catalog_collections__status CHECK (status IN ('ACTIVE', 'HIDDEN')),
    CONSTRAINT ck_catalog_collections__display_order CHECK (display_order >= 0),
    CONSTRAINT ck_catalog_collections__version CHECK (version >= 0),
    CONSTRAINT ck_catalog_collections__timestamps CHECK (updated_at >= created_at)
);

CREATE UNIQUE INDEX uq_catalog_collections__slug_ci
    ON catalog_collections (lower(slug));

CREATE INDEX ix_catalog_collections__status_order
    ON catalog_collections (status, display_order, id);

CREATE TABLE catalog_collection_products (
    collection_id UUID NOT NULL,
    product_id UUID NOT NULL,
    display_order INTEGER NOT NULL,
    CONSTRAINT pk_catalog_collection_products PRIMARY KEY (collection_id, product_id),
    CONSTRAINT uq_catalog_collection_products__collection_order UNIQUE (collection_id, display_order),
    CONSTRAINT fk_catalog_collection_products__collection_id
        FOREIGN KEY (collection_id) REFERENCES catalog_collections (id) ON DELETE CASCADE,
    CONSTRAINT fk_catalog_collection_products__product_id
        FOREIGN KEY (product_id) REFERENCES catalog_products (id) ON DELETE RESTRICT,
    CONSTRAINT ck_catalog_collection_products__display_order CHECK (display_order >= 0)
);

CREATE INDEX ix_catalog_collection_products__product_id
    ON catalog_collection_products (product_id, collection_id);
