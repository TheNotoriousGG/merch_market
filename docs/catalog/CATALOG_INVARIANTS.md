# Catalog invariants

Status: accepted implementation guardrails for stage 7. Product scope remains in `docs/requirements/CATALOG_VERTICAL_SLICE.md`; this document is the compact engineering checklist used during implementation and review.

## Aggregate boundaries

- `Category` owns its parent reference, display order, visibility and depth/cycle rules.
- `Product` owns content, lifecycle, category/collection assignments, variants, characteristics, media ordering and slug changes.
- `Collection` owns editorial membership order; membership never changes product lifecycle.
- Pricing and inventory reference product variants but never mutate catalog aggregates.

## Category

- A category cannot parent itself, form a cycle or exceed five levels.
- Sibling slugs are normalized and unique case-insensitively.
- Sibling order is `displayOrder`, then stable identifier.
- Hidden nodes are absent from the public tree.
- Category assignments and request arrays are de-duplicated before mutation.

## Product and slug

- A new product is always `DRAFT`; transitions are only `DRAFT → ACTIVE → ARCHIVED`.
- Publication requires complete descriptions, an active primary category, at least one active variant, and primary media with alt text.
- `ARCHIVED` is terminal and published products are never hard-deleted.
- A canonical slug and every historical alias belong to one product for their lifetime.
- A slug change creates a direct alias to the current canonical slug; chains, cycles and reuse are forbidden.

## Variant and media

- SKU is globally unique, uppercase ASCII, normalized and immutable.
- Variant-defining attribute combinations are unique inside a product.
- Externally referenced variants are archived, not deleted.
- Public media contains a delivery URL, never an object key.
- Visible media requires nonblank alt text; exactly one product presentation image is primary.

## Concurrency and authorization

- Aggregate business changes increment `version` and produce a new strong `ETag`.
- Existing-resource commands require `If-Match`; missing and stale preconditions map to `428` and `412`.
- Administrative writes require CSRF, `CATALOG_MANAGER` or `ADMIN`, verified MFA, and an append-only audit event.
- Replayed create/transition commands with the same idempotency key cannot create duplicate resources or effects.

## Public reads

- Only active products with active presentation data are visible.
- Page size is `24` by default and at most `60`; filters and sorting are allowlisted.
- Empty results are successful pages with exact zero totals.
- Catalog responses contain no price, availability, stock, internal notes, audit data or object-storage keys.
