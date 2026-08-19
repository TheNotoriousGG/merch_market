package ru.amra.market.catalog.application;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/** Length-delimited SHA-256 builder for deterministic strong representation ETags. */
final class RepresentationHasher {

    private final MessageDigest digest;

    RepresentationHasher() {
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available in every supported Java runtime", exception);
        }
    }

    RepresentationHasher add(int value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
        return this;
    }

    RepresentationHasher add(long value) {
        digest.update(ByteBuffer.allocate(Long.BYTES).putLong(value).array());
        return this;
    }

    RepresentationHasher add(String value) {
        var bytes = value.getBytes(StandardCharsets.UTF_8);
        add(bytes.length);
        digest.update(bytes);
        return this;
    }

    RepresentationHasher add(UUID value) {
        return add(value.getMostSignificantBits()).add(value.getLeastSignificantBits());
    }

    RepresentationHasher add(Instant value) {
        return add(value.getEpochSecond()).add(value.getNano());
    }

    String etag() {
        return '"' + HexFormat.of().formatHex(digest.digest()) + '"';
    }
}
