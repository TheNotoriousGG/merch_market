package ru.amra.market.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class AdminProductListCriteriaTest {

    @Test
    void normalizesQueryAndCalculatesStablePagination() {
        var criteria =
                new AdminProductListCriteria("  Худи  ", null, null, 2, 24, AdminProductListCriteria.Sort.UPDATED_DESC);
        var page = new AdminProductListPage(List.of(), 2, 24, 49);

        assertThat(criteria.query()).isEqualTo("Худи");
        assertThat(criteria.offset()).isEqualTo(48);
        assertThat(page.totalPages()).isEqualTo(3);
    }

    @Test
    void rejectsInvalidPageShapeAndBlankSearch() {
        assertThatThrownBy(() -> new AdminProductListCriteria(
                        null, null, null, -1, 24, AdminProductListCriteria.Sort.UPDATED_DESC))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AdminProductListCriteria(
                        "  ", null, null, 0, 24, AdminProductListCriteria.Sort.UPDATED_DESC))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
