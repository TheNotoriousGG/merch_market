package ru.amra.market.catalog.application;

import ru.amra.market.catalog.domain.ProductMedia;

/** Media command result carrying the owning product concurrency tag. */
public record AdminMediaView(ProductMedia media, String productEtag) {}
