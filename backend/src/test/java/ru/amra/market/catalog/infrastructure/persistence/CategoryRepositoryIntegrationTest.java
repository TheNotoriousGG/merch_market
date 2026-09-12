package ru.amra.market.catalog.infrastructure.persistence;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import ru.amra.market.catalog.application.ConcurrentCatalogModificationException;
import ru.amra.market.catalog.application.port.CategoryRepository;
import ru.amra.market.catalog.domain.Category;
import ru.amra.market.catalog.domain.CategoryId;
import ru.amra.market.catalog.domain.CategoryName;
import ru.amra.market.catalog.domain.CategorySlug;
import ru.amra.market.catalog.domain.CategoryStatus;
import ru.amra.market.testing.PostgreSqlIntegrationTest;

@SpringBootTest
@Transactional
class CategoryRepositoryIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private CategoryRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void roundTripsCategoryAndLetsJpaAdvanceTheOptimisticVersion() {
        var created = repository.save(category("clothes", "Одежда", 20));
        var idempotentlySaved = repository.save(created);

        var changed = created.change(
                null, new CategorySlug("apparel"), new CategoryName("Новая одежда"), 10, CategoryStatus.ACTIVE);
        var saved = repository.save(changed);
        var reloaded = repository.findById(saved.id()).orElseThrow();

        assertThat(saved.version()).isEqualTo(1);
        assertThat(idempotentlySaved.version()).isZero();
        assertThat(reloaded.id()).isEqualTo(saved.id());
        assertThat(reloaded.slug()).isEqualTo(saved.slug());
        assertThat(reloaded.name()).isEqualTo(saved.name());
        assertThat(reloaded.status()).isEqualTo(saved.status());
        assertThat(reloaded.version()).isEqualTo(saved.version());
        assertThat(jdbc.queryForObject(
                        "select version from catalog_categories where id = ?",
                        Long.class,
                        saved.id().value()))
                .isEqualTo(1);
    }

    @Test
    void rejectsAStaleAggregateInsteadOfOverwritingTheWinner() {
        var created = repository.save(category("clothes", "Одежда", 0));
        var firstReader = repository.findById(created.id()).orElseThrow();
        var secondReader = repository.findById(created.id()).orElseThrow();
        repository.save(firstReader.change(
                null, firstReader.slug(), new CategoryName("Одежда и обувь"), 0, CategoryStatus.HIDDEN));

        var staleChange = secondReader.change(
                null, secondReader.slug(), new CategoryName("Только одежда"), 0, CategoryStatus.HIDDEN);

        assertThatThrownBy(() -> repository.save(staleChange))
                .isInstanceOf(ConcurrentCatalogModificationException.class);
    }

    @Test
    void returnsCompleteSnapshotInStableOrderForHierarchyValidation() {
        var later = repository.save(category("accessories", "Аксессуары", 20));
        var earlier = repository.save(category("clothes", "Одежда", 10));

        assertThat(repository.findAll()).extracting(Category::id).containsExactly(earlier.id(), later.id());
    }

    @Test
    void rejectsUnknownAggregateThatPretendsToHaveBeenPersisted() {
        var unknown = category("unknown", "Unknown", 0);
        var forgedVersion = Category.restore(
                unknown.id(), null, unknown.slug(), unknown.name(), unknown.displayOrder(), unknown.status(), 1);

        assertThatThrownBy(() -> repository.save(forgedVersion))
                .isInstanceOf(ConcurrentCatalogModificationException.class);
    }

    private Category category(String slug, String name, int displayOrder) {
        var id = requireNonNull(jdbc.queryForObject("select uuidv7()", UUID.class));
        return Category.create(new CategoryId(id), null, new CategorySlug(slug), new CategoryName(name), displayOrder);
    }
}
