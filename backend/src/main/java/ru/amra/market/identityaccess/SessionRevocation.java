package ru.amra.market.identityaccess;

/** Application boundary used when logout, blocking or a critical permission change revokes sessions. */
public interface SessionRevocation {

    /**
     * Removes every backend session indexed by an immutable OIDC subject.
     *
     * @param subject opaque OIDC subject, never an email address
     * @return number of sessions selected for revocation
     */
    int revokeAll(String subject);
}
