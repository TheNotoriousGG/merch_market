# ADR-0006: Catalog API boundaries

- Status: Accepted
- Date: 2026-08-19

## Context

The storefront needs stable category and product contracts before catalog persistence or domain code is written. Catalog data will later be composed with pricing and inventory without allowing those concerns to leak into the catalog model. Administrative authoring also needs stronger authorization and concurrency semantics than public reads.

## Decision

- Public catalog reads live under `/api/v1/catalog/**`; authoring commands live under `/api/v1/admin/catalog/**` and use separate DTOs.
- The public contract exposes only `ACTIVE` presentation data. It never contains draft status, audit fields, object-storage keys, price or stock assumptions.
- Product listing uses zero-based page pagination with exact totals, default size `24`, maximum size `60`, typed filters and the sorting allowlist `MANUAL`, `NEWEST`, `NAME_ASC`.
- Canonical product slugs return a detail representation with an `ETag`. Historical aliases return `301` directly to the canonical URL; aliases never produce duplicate bodies or redirect chains.
- Public product representations contain catalog-owned structure only. Pricing is composed in stage 10 and availability in stage 8 through their own contracts.
- Administrative create commands use durable actor-and-operation-scoped idempotency keys bound to a request fingerprint. Mutating commands use CSRF protection; commands against existing aggregates additionally require `If-Match` and return `428` when absent or `412` when stale.
- Nullable partial updates use explicit intent fields when omission and clearing have different meanings. Category parent removal is represented by `clearParent`, never inferred from an omitted `parentId`.
- Administrative catalog operations require a backend session, `CATALOG_MANAGER` or `ADMIN`, and verified MFA. OpenAPI documents this policy; Spring Security and application authorization enforce it.
- Generated Java API interfaces/DTOs and the TypeScript Fetch client are transport artifacts. Domain and persistence models remain handwritten.
- Collection/category/product identifiers transported as arrays are de-duplicated at the application boundary. OpenAPI `uniqueItems` is intentionally omitted because OpenAPI Generator 7.22 otherwise emits Jackson 2 annotations incompatible with Spring Boot 4's Jackson 3 runtime.

## Consequences

- Frontend integration can begin from a generated client before persistence is complete.
- Price and stock cannot accidentally become catalog source-of-truth fields.
- Every concurrent authoring flow has explicit optimistic-lock semantics.
- A transport retry can safely recover the original create result, while accidental idempotency-key reuse with different content fails closed.
- Array uniqueness must have dedicated application/domain tests rather than relying on JSON deserialization.
- Adding price sorting or availability filters is a later contract change owned by the corresponding module.

## Alternatives considered

- One DTO for public and admin traffic: rejected because it risks leaking internal state and storage metadata.
- Offset-less cursor pagination: rejected for this storefront because exact page counts and direct page navigation are approved product behavior.
- Returning the canonical body for alias slugs: rejected because it creates multiple URLs for the same representation.
- Adding Jackson 2 only for generated `uniqueItems` sets: rejected because a mixed Jackson runtime is unnecessary technical debt.
