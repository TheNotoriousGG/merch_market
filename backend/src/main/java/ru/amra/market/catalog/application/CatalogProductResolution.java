package ru.amra.market.catalog.application;

/** Canonical public product body or a direct resolution from an historical alias. */
public sealed interface CatalogProductResolution {

    record Found(CatalogProductDetail detail) implements CatalogProductResolution {}

    record Redirect(String canonicalSlug) implements CatalogProductResolution {}
}
