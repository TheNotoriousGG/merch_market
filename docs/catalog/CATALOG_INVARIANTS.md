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
- Attribute value code, localized label and optional color hex are separate values and must survive an administrative round trip.
- Variant label/order/attributes may change, but an archived variant cannot be reactivated.
- Media object key, content type and dimensions are immutable after attachment; only variant scope, alt text, order and primary state are editable.

## Concurrency and authorization

- Aggregate business changes increment `version` and produce a new strong `ETag`.
- Existing-resource commands require `If-Match`; missing and stale preconditions map to `428` and `412`.
- Administrative writes require CSRF, `CATALOG_MANAGER` or `ADMIN`, verified MFA, and an append-only audit event.
- Replayed create/transition commands with the same idempotency key cannot create duplicate resources or effects.
- An idempotency key is durable and scoped by actor plus operation. Reuse with a different request fingerprint is a conflict.
- Category creation always starts in `HIDDEN`; visibility changes are explicit optimistic commands.
- A partial category update distinguishes an omitted parent from an explicit move to the root through `clearParent`.
- Partial list fields preserve omitted versus explicit empty semantics; optional media variant scope uses `clearVariant` for intentional removal.
- Every parent change is validated against one complete hierarchy snapshot before persistence; database constraints remain the race barrier.

## Editorial collections

- A new collection is `HIDDEN`; activation is an explicit optimistic command.
- Collection owns deterministic product order and never changes product lifecycle.
- Duplicate product identifiers are removed at the application boundary while the domain rejects duplicate stored membership.
- Membership changes invalidate both the collection ETag and every affected administrative product ETag, regardless of which side initiated the command.

## Public reads

- Category navigation starts only from active roots and follows only active children, so a hidden parent hides its complete branch.
- Category siblings are returned in `displayOrder`, then UUID order; the complete tree uses one recursive query rather than per-node reads.
- The category-tree ETag is derived only from its deterministic public representation; HTTP caching never becomes an application correctness dependency.
- Only active products with active presentation data are visible.
- Page size is `24` by default and at most `60`; filters and sorting are allowlisted.
- Category filtering includes only reachable active descendants; size and color predicates must match the same active variant.
- Product pages use exact totals and at most three database queries: count, page projection and one variant-option batch.
- `MANUAL` uses collection membership order when a collection is selected, search rank for search results, and newest-first as the deterministic general fallback.
- Empty results are successful pages with exact zero totals.
- Product detail resolves one aggregate plus two batch reference projections; archived variants and media scoped to them are absent.
- An alias redirects directly to the current canonical API path only after the target is proven active; all other lifecycle states return the same public absence response.
- Catalog responses contain no price, availability, stock, internal notes, audit data or object-storage keys.
