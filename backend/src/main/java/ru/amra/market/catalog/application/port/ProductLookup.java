package ru.amra.market.catalog.application.port;

import ru.amra.market.catalog.domain.Product;

/** Product slug lookup result distinguishing canonical content from a permanent alias redirect. */
public record ProductLookup(Product product, boolean alias) {}
