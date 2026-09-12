# ADR-0004: PostgreSQL persistence foundation

- Status: Accepted
- Date: 2026-08-19

## Context

The application needs a relational source of truth with predictable schema evolution, least-privilege access and tests against the same database engine used in production. Virtual threads increase request concurrency but do not increase safe database concurrency, so the JDBC pool must remain explicitly bounded.

## Decision

- Production uses managed PostgreSQL. Local development and integration tests use PostgreSQL 18.4 from the official image pinned by multi-platform digest.
- The database is `amra_market`; application objects live only in schema `amra_shop`.
- Infrastructure bootstrap owns creation of the database roles, schema and approved extensions. Flyway owns every application object after bootstrap.
- Roles are separated:
  - `amra_owner` owns the database/schema and is never used by the application;
  - `amra_migrator` runs Flyway and can create objects only in `amra_shop`;
  - `amra_runtime` is the application identity and receives only DML/execute privileges granted by migrations.
- Flyway SQL migrations are immutable after merge, use validated names and run before Hibernate initialization.
- Hibernate uses `ddl-auto=validate`; Open Session in View is disabled.
- Runtime connections use `amra_shop, pg_catalog` search path and a two-second statement timeout. The Hikari pool defaults to 10 connections and remains configurable but bounded.
- PostgreSQL 18 native `uuidv7()` is available for database-side operations. Application aggregates will normally assign UUIDv7 before persistence so identifiers exist before insert.
- `pg_trgm` is installed by privileged infrastructure bootstrap, not by an application migration.

## Consequences

- A fresh environment requires the bootstrap step before application migrations.
- The application cannot silently create or repair tables at startup.
- A checksum mismatch or failed migration prevents startup before repositories become available.
- Integration tests require Docker and exercise PostgreSQL rather than an in-memory substitute.
- Schema ownership and managed-service role provisioning must be adapted to the selected production provider without collapsing the three logical roles.

## Alternatives considered

- One database superuser for migration and runtime: rejected because application compromise would permit destructive DDL and privilege escalation.
- Hibernate schema generation: rejected because generated DDL is not a reviewed, reversible deployment artifact.
- H2 for tests: rejected because it cannot prove PostgreSQL privileges, SQL, extensions or concurrency semantics.
- R2DBC: rejected because the approved stack is Spring MVC with JDBC on virtual threads.
