package ru.amra.market.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogProductListCriteriaTest {

    @Test
    void trimsSearchAndDeduplicatesTypedFiltersWithoutChangingOrder() {
        var criteria = new CatalogProductListCriteria(
                2,
                24,
                "clothes",
                "base",
                "  худи  ",
                true,
                List.of("M", "S", "M"),
                List.of("BLACK", "BLACK"),
                CatalogProductSort.NEWEST);

        assertThat(criteria.search()).isEqualTo("худи");
        assertThat(criteria.sizeValues()).containsExactly("M", "S");
        assertThat(criteria.colorValues()).containsExactly("BLACK");
    }

    @Test
    void rejectsUnboundedOrMalformedInputAtTheApplicationBoundary() {
        assertThatThrownBy(() -> criteria(-1, 24, null, List.of(), List.of(), CatalogProductSort.MANUAL))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> criteria(0, 61, null, List.of(), List.of(), CatalogProductSort.MANUAL))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> criteria(0, 24, " ", List.of(), List.of(), CatalogProductSort.MANUAL))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> criteria(0, 24, null, List.of("bad value"), List.of(), CatalogProductSort.MANUAL))
                .isInstanceOf(IllegalArgumentException.class);
        var tooMany = new ArrayList<String>();
        for (var index = 0; index < 21; index++) {
            tooMany.add("S" + index);
        }
        assertThatThrownBy(() -> criteria(0, 24, null, tooMany, List.of(), CatalogProductSort.MANUAL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static CatalogProductListCriteria criteria(
            int page,
            int size,
            @org.jspecify.annotations.Nullable String search,
            List<String> sizes,
            List<String> colors,
            CatalogProductSort sort) {
        return new CatalogProductListCriteria(page, size, null, null, search, false, sizes, colors, sort);
    }
}
