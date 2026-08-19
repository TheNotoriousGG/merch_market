package ru.amra.market.inventory.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.jspecify.annotations.Nullable;

/** Collision-safe canonical fingerprint for inventory idempotency commands. */
final class InventoryCommandFingerprint {
    private InventoryCommandFingerprint() {}

    static String of(@Nullable Object... components) {
        var canonical = new StringBuilder();
        for (var component : components) {
            if (component == null) {
                canonical.append("-1:");
            } else {
                var value = component.toString();
                canonical.append(value.length()).append(':').append(value);
            }
            canonical.append(';');
        }
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }
}
