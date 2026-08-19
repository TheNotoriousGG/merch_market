# Catalog relational schema

Status: stage 7 persistence design, introduced by Flyway `V3__create_catalog_schema.sql` and extended by V4 command idempotency plus V5 attribute presentation preservation.

## Ownership boundaries

- `catalog_products` stores catalog content and lifecycle, but no price or stock columns.
- `catalog_product_variants` owns immutable SKU identity and the normalized defining signature.
- `catalog_product_media` stores private object keys; public URLs are created by the media delivery adapter.
- Attribute definitions and values remain relational because they drive validation, SKU combinations and filters.
- Collections own ordered editorial membership without changing product lifecycle.
- `catalog_command_idempotency` durably binds an actor-scoped command key and operation to one request fingerprint and resulting resource.
- Product and variant attribute rows keep stable filter value, localized label and optional color hex in separate constrained columns.

## Integrity strategy

- Aggregate/entity identifiers are UUIDv7 and checked in PostgreSQL.
- Lowercase slug and uppercase SKU formats are checked before case-insensitive unique indexes.
- Category sibling uniqueness includes root categories through `NULLS NOT DISTINCT`.
- A product has at most one primary image through a partial unique index.
- A media-to-variant composite foreign key proves that the variant belongs to the same product.
- Product publication timestamp and lifecycle status are mutually consistent.
- Cross-row rules such as category cycles, full publishability, canonical-versus-alias namespace and primary-category assignment are validated by the domain inside the write transaction; database uniqueness and foreign keys remain the final race barrier where expressible.
- Command idempotency claims use one unique `(actor_scope, idempotency_key)` key and bind the operation plus fingerprint as immutable claim data. A completed claim stores its resource UUID; the same key with another operation or fingerprint never executes the command.

## Query support

- Category navigation uses `(parent_id, display_order, id)` and one recursive CTE rooted only at visible roots; hidden branches are not reachable and node count never creates N+1 queries.
- Active product pages use partial newest/name indexes with stable `id` tie-breakers.
- Search uses a stored weighted Russian `tsvector` with GIN plus a partial `pg_trgm` name index.
- Runtime SQL schema-qualifies the `public` pg_trgm operator/function because the application `search_path` intentionally contains only `amra_shop`.
- Category, collection and typed attribute join tables have reverse indexes matching storefront filters.
- Media and variants are fetched by product in deterministic display order.
- Public detail composes the aggregate with one visible-category and one active-collection batch projection; reference counts never create per-row queries.

## Persistence mapping

- Category and product aggregate roots use JPA entities with `@Version` optimistic locking.
- Product children use a bounded JDBC store rather than a wide mutable ORM graph. A complete product load uses a fixed set of batch queries and groups rows in memory, so child count does not introduce N+1 queries.
- Root and child writes share one Spring transaction. The root version advances for child-only domain changes, while an identical aggregate save is treated as an idempotent retry.
- Ordered collection membership is synchronized transactionally. Membership changes bump the collection version and affected product root versions so both administrative ETags cover their complete representations.
- Canonical and historical alias lookups both restore the same current aggregate; the lookup result records whether the requested slug was an alias so a later HTTP adapter can issue the approved redirect.
- JDBC synchronization never bypasses domain invariants. It persists only an already validated aggregate, while database constraints remain the final concurrent-write barrier.

Production query acceptance still requires realistic data and `EXPLAIN (ANALYZE, BUFFERS)` before release. Indexes in V3 correspond only to approved stage 7 access paths.
