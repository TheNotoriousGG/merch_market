package ru.amra.market.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import ru.amra.market.catalog.domain.CategoryStatus;

class AdminCategoryListCriteriaTest {

    @Test
    void normalizesOptionalQueryAndPreservesStatus() {
        assertThat(new AdminCategoryListCriteria(null, null).query()).isNull();
        var criteria = new AdminCategoryListCriteria("  Худи  ", CategoryStatus.ARCHIVED);
        assertThat(criteria.query()).isEqualTo("Худи");
        assertThat(criteria.status()).isEqualTo(CategoryStatus.ARCHIVED);
    }

    @Test
    void rejectsEmptyAndOversizedQueries() {
        assertThatThrownBy(() -> new AdminCategoryListCriteria("   ", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AdminCategoryListCriteria("x".repeat(161), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
