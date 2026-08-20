package ru.amra.market.catalog.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.amra.market.catalog.application.port.AdminProductListReader;

/** Read-only administrative product-page use case. */
@Service
public class ListAdminCatalogProducts {

    private final AdminProductListReader products;

    public ListAdminCatalogProducts(AdminProductListReader products) {
        this.products = products;
    }

    @Transactional(readOnly = true)
    public AdminProductListPage execute(AdminProductListCriteria criteria) {
        return products.findPage(criteria);
    }
}
