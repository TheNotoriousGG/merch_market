# ADR-0005: Backend-managed OIDC session

- Status: Accepted
- Date: 2026-08-19

## Context

The browser storefront needs Keycloak authentication without exposing access or refresh tokens to JavaScript. Customer and administrative traffic have different risk profiles and session lifetimes. Administrative APIs additionally require evidence of multi-factor authentication, not only possession of an administrator role.

## Decision

- Keycloak 26.7.0 is the OIDC provider. Local Compose pins the official multi-platform image digest; production runs Keycloak as a separately operated HA component with its own PostgreSQL database.
- Spring Security acts as a confidential OIDC client using Authorization Code with PKCE. OIDC tokens remain in the server-side session and are never part of the public API contract.
- Spring Session JDBC persists sessions in `amra_shop.http_sessions` and `amra_shop.http_sessions_attributes`; application runtime remains unable to perform DDL.
- The browser receives `AMRA_SESSION` as Secure, HttpOnly and SameSite=Lax. The non-secret CSRF token is exposed in `AMRA_CSRF` and must be echoed as `X-AMRA-CSRF` for unsafe methods.
- CORS allows credentials only for an explicit deployment allowlist. Wildcard origins are not accepted.
- Keycloak realm roles are mapped through a fixed application allowlist: `CUSTOMER`, `CATALOG_MANAGER`, `ORDER_MANAGER`, `WAREHOUSE_MANAGER`, `SUPPORT`, `ADMIN`. Unknown roles never become application authorities.
- Customer sessions expire after 30 minutes of inactivity or 30 days absolute. Sessions carrying `ADMIN` expire after 15 minutes of inactivity or 8 hours absolute.
- `/api/v1/admin/**` and internal API documentation require both `ROLE_ADMIN` and an accepted OIDC `acr` value. Initial accepted level is `2`; the Keycloak administrative authentication flow must emit that value only after MFA.
- The immutable OIDC `sub` is the backend principal and Spring Session index. Logout invalidates the current session. Blocking and critical permission changes call the `SessionRevocation` application boundary to remove all sessions for that subject.
- Verified email is mandatory for protected customer APIs. `GET /api/v1/session` is intentionally public and returns only safe session metadata so the frontend can bootstrap.

## Consequences

- A compromised browser script cannot read the authentication cookie or OIDC credentials, but it can read the CSRF token by design; same-origin policy and the CORS allowlist remain required.
- PostgreSQL is part of authentication readiness and permits session continuity across backend instances.
- Role changes are not trusted until existing sessions are revoked; identity-management workflows must invoke the revocation boundary.
- Production must configure TLS, external secrets, Keycloak HA, a level-2 MFA flow and strict redirect origins before release.

## Alternatives considered

- Tokens in browser storage: rejected because XSS would directly expose bearer and refresh credentials.
- Stateless JWT authentication at every backend endpoint: rejected because immediate revocation, browser logout and permission-change semantics become weaker and more complex.
- Application-owned passwords: rejected because credentials and authentication policy belong to Keycloak.
- Admin role without an MFA claim: rejected because a stolen single-factor session would retain full administrative authority.
