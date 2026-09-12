# Identity and access conventions

Обновлено: 19 августа 2026 года.

## Trust boundaries

- Keycloak authenticates users and owns passwords, MFA enrollment, brute-force protection and identity lifecycle.
- Backend validates the OIDC login result, maps only allowlisted realm roles and owns the browser session.
- Frontend never receives an access token or refresh token. It works only with the session cookie and CSRF header contract.
- The application database and the Keycloak database are separate. Application code never queries Keycloak tables.

## Browser protocol

1. Frontend sends the browser to `/oauth2/authorization/keycloak`.
2. Spring Security uses Authorization Code with PKCE and a confidential client.
3. Successful login rotates the session identifier and stores the authorized client only in the server-side JDBC session.
4. Frontend calls `GET /api/v1/session`; the response contains authentication state, opaque subject, display name, verified-email flag and allowlisted permissions.
5. Unsafe requests send the `AMRA_CSRF` value in `X-AMRA-CSRF`. The CSRF cookie is not a credential; `AMRA_SESSION` remains HttpOnly.
6. `POST /api/v1/session/logout` requires CSRF, invalidates the session and deletes both cookies.

## Authorization invariants

- Authentication is insufficient for customer-owned protected APIs until `email_verified=true`.
- Role strings not present in `AccessRole` are discarded.
- A realm-role claim alone is insufficient for admin access: the mapped `ROLE_ADMIN` authority and accepted MFA `acr` must both be present.
- Current accepted admin ACR is configured by `amra.security.admin-mfa-acr-values`; local/default value is `2`.
- API documentation follows the same admin + MFA policy when it is enabled.
- Denial is fail-closed: unauthenticated API access returns 401, insufficient permission or MFA returns 403.
- API denial responses use RFC 9457 `application/problem+json`: `AUTHENTICATION_REQUIRED`, `ACCESS_DENIED` and `CSRF_INVALID` are distinct stable codes.
- Every request receives a bounded `X-Trace-Id`; a syntactically safe caller value may be propagated, otherwise the backend generates one. The same value appears in problem bodies and catalog audit correlation.

## Session lifecycle

| Session | Idle timeout | Absolute timeout |
| --- | ---: | ---: |
| Customer/non-admin | 30 minutes | 30 days |
| Administrator | 15 minutes | 8 hours |

Spring Session enforces inactivity. `AbsoluteSessionLifetimeFilter` independently enforces maximum age. The OIDC subject (`sub`) is the only principal index used for bulk revocation; email and display name are mutable PII and must never be revocation keys.

Blocking, critical role changes and identity compromise call `SessionRevocation.revokeAll(subject)`. The operation is idempotent: zero removed sessions is a successful result.

## Deployment configuration

Required secrets are supplied externally:

- `AMRA_OIDC_CLIENT_SECRET`;
- `AMRA_KEYCLOAK_DB_PASSWORD`;
- `AMRA_KEYCLOAK_ADMIN_USERNAME` and `AMRA_KEYCLOAK_ADMIN_PASSWORD` for local bootstrap only.

Production additionally sets issuer/endpoints to the externally verified Keycloak hostname, keeps `AMRA_SESSION_COOKIE_SECURE=true`, supplies an exact HTTPS CORS allowlist and configures a Keycloak level-2 MFA authentication flow. Realm import is a local bootstrap mechanism, not a backup or production reconciliation strategy.

## Verification

- Security integration tests cover anonymous bootstrap, verified email, CSRF logout, allowed/disallowed CORS, MFA denial and forged role escalation.
- Problem contract tests additionally assert content type, trace propagation and separate 401/authorization/CSRF codes.
- Session tests cover customer/admin lifetimes, absolute expiry and PostgreSQL-backed bulk revocation.
- Migration tests create session tables from scratch and validate Flyway history on PostgreSQL 18.4.
