# ADR-0003: OpenAPI contract and generation toolchain

- Status: Accepted
- Date: 2026-08-19

## Context

The backend and frontend need one versioned HTTP contract. Java transport types and the frontend client must be generated without allowing generated types to become domain or persistence models. The toolchain must validate modular references, detect incompatible changes and remain compatible with Spring Boot 4 and Java 25.

OpenAPI Generator 7.22.0 describes OpenAPI 3.1 support as beta. OpenAPI Diff also has unresolved OpenAPI 3.1 compatibility gaps. Selecting 3.1 now would weaken validation and breaking-change guarantees without providing an MVP capability we need.

## Decision

- The canonical contract uses OpenAPI 3.0.3 and lives in `src/main/openapi`.
- The public major version is `/api/v1`; the specification version follows SemVer independently.
- OpenAPI Generator 7.22.0 generates Spring API interfaces and transport/error DTOs under `build/generated`. It also generates a TypeScript Fetch client.
- Domain and persistence models remain handwritten and must not depend on generated transport code.
- OpenAPI validation and project policy tests run on every local quality gate.
- OpenAPI Diff 2.1.7 compares the candidate contract with the local `main` contract and fails on incompatible changes. The first adoption has no baseline and is explicitly skipped.
- Generated sources and clients are disposable build outputs and are never edited or committed.
- The TypeScript client is packaged locally. GitLab Package Registry publication remains deferred under ADR-0002.
- Documentation is disabled by default and can be enabled explicitly with `amra.api-docs.enabled=true`. Production authentication for documentation is completed with the identity/security stage before any deployment.

## Consequences

- Contract changes precede controller implementation and frontend integration.
- A breaking `/api/v1` change requires a new API major or an explicitly reviewed compatibility strategy.
- OpenAPI 3.1 will be reconsidered only after generator, validator and diff tooling all provide stable support and a migration branch passes the same gates.
- Runtime code may implement generated interfaces and map generated DTOs at the HTTP boundary, but generated code cannot cross into domain packages.

## Alternatives considered

- OpenAPI 3.1 immediately: rejected because the selected production toolchain still marks or exhibits incomplete support.
- Code-first Spring annotations: rejected because they make the backend implementation, rather than the reviewed contract, the source of truth.
- Commit generated sources: rejected because it creates noisy diffs and invites manual edits.
