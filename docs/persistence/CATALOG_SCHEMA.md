# Catalog relational schema

Status: stage 7 persistence design, introduced by Flyway `V3__create_catalog_schema.sql`.

## Ownership boundaries

- `catalog_products` stores catalog content and lifecycle, but no price or stock columns.
- `catalog_product_variants` owns immutable SKU identity and the normalized defining signature.
- `catalog_product_media` stores private object keys; public URLs are created by the media delivery adapter.
- Attribute definitions and values remain relational because they drive validation, SKU combinations and filters.
- Collections own ordered editorial membership without changing product lifecycle.

## Integrity strategy

- Aggregate/entity identifiers are UUIDv7 and checked in PostgreSQL.
- Lowercase slug and uppercase SKU formats are checked before case-insensitive unique indexes.
- Category sibling uniqueness includes root categories through `NULLS NOT DISTINCT`.
- A product has at most one primary image through a partial unique index.
- A media-to-variant composite foreign key proves that the variant belongs to the same product.
- Product publication timestamp and lifecycle status are mutually consistent.
- Cross-row rules such as category cycles, full publishability, canonical-versus-alias namespace and primary-category assignment are validated by the domain inside the write transaction; database uniqueness and foreign keys remain the final race barrier where expressible.

## Query support

- Category navigation uses `(parent_id, display_order, id)`.
- Active product pages use partial newest/name indexes with stable `id` tie-breakers.
- Search uses a stored weighted Russian `tsvector` with GIN plus a partial `pg_trgm` name index.
- Category, collection and typed attribute join tables have reverse indexes matching storefront filters.
- Media and variants are fetched by product in deterministic display order.

## Persistence mapping

- Category and product aggregate roots use JPA entities with `@Version` optimistic locking.
- Product children use a bounded JDBC store rather than a wide mutable ORM graph. A complete product load uses a fixed set of batch queries and groups rows in memory, so child count does not introduce N+1 queries.
- Root and child writes share one Spring transaction. The root version advances for child-only domain changes, while an identical aggregate save is treated as an idempotent retry.
- Canonical and historical alias lookups both restore the same current aggregate; the lookup result records whether the requested slug was an alias so a later HTTP adapter can issue the approved redirect.
- JDBC synchronization never bypasses domain invariants. It persists only an already validated aggregate, while database constraints remain the final concurrent-write barrier.

Production query acceptance still requires realistic data and `EXPLAIN (ANALYZE, BUFFERS)` before release. Indexes in V3 correspond only to approved stage 7 access paths.
