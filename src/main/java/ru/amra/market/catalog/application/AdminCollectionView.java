package ru.amra.market.catalog.application;

import ru.amra.market.catalog.domain.EditorialCollection;

/** Administrative editorial collection and its strong version tag. */
public record AdminCollectionView(EditorialCollection collection, String etag) {}
