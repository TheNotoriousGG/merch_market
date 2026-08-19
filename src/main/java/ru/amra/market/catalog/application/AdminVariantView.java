package ru.amra.market.catalog.application;

import ru.amra.market.catalog.domain.ProductVariant;

/** Variant command result carrying the owning product concurrency tag. */
public record AdminVariantView(ProductVariant variant, String productEtag) {}
