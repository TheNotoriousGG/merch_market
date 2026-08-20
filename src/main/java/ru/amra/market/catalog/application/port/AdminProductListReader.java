package ru.amra.market.catalog.application.port;

import ru.amra.market.catalog.application.AdminProductListCriteria;
import ru.amra.market.catalog.application.AdminProductListPage;

/** Read port for filtered and paged administrative product projections. */
public interface AdminProductListReader {

    AdminProductListPage findPage(AdminProductListCriteria criteria);
}
