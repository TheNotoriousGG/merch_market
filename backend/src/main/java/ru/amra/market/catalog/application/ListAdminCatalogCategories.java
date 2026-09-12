package ru.amra.market.catalog.application;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.amra.market.catalog.application.port.AdminCategoryListReader;

/** Read-only administrative category-list use case. */
@Service
public class ListAdminCatalogCategories {

    private final AdminCategoryListReader categories;

    public ListAdminCatalogCategories(AdminCategoryListReader categories) {
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public List<AdminCategoryView> execute(AdminCategoryListCriteria criteria) {
        return categories.findAll(criteria);
    }
}
