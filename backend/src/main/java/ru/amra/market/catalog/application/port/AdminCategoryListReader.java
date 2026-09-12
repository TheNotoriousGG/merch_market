package ru.amra.market.catalog.application.port;

import java.util.List;
import ru.amra.market.catalog.application.AdminCategoryListCriteria;
import ru.amra.market.catalog.application.AdminCategoryView;

/** Administrative category-tree projection boundary. */
public interface AdminCategoryListReader {

    List<AdminCategoryView> findAll(AdminCategoryListCriteria criteria);
}
