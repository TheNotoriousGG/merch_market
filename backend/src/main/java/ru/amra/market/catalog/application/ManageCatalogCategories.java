package ru.amra.market.catalog.application;

import java.util.ArrayList;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.amra.market.catalog.application.port.AdminCategoryReader;
import ru.amra.market.catalog.application.port.CatalogIdGenerator;
import ru.amra.market.catalog.application.port.CatalogIdempotencyStore;
import ru.amra.market.catalog.application.port.CategoryRepository;
import ru.amra.market.catalog.domain.Category;
import ru.amra.market.catalog.domain.CategoryHierarchy;
import ru.amra.market.catalog.domain.CategoryId;
import ru.amra.market.catalog.domain.CategoryInvariantViolation;
import ru.amra.market.catalog.domain.CategoryName;
import ru.amra.market.catalog.domain.CategorySlug;
import ru.amra.market.catalog.domain.CategoryStatus;

/** Transactional administrative category command/query facade. */
@Service
public class ManageCatalogCategories {

    private static final String CREATE_OPERATION = "CREATE_CATEGORY";

    private final CategoryRepository categories;
    private final AdminCategoryReader adminReader;
    private final CatalogIdGenerator ids;
    private final CatalogIdempotencyStore idempotency;
    private final CatalogAuditTrail audit;

    public ManageCatalogCategories(
            CategoryRepository categories,
            AdminCategoryReader adminReader,
            CatalogIdGenerator ids,
            CatalogIdempotencyStore idempotency,
            CatalogAuditTrail audit) {
        this.categories = categories;
        this.adminReader = adminReader;
        this.ids = ids;
        this.idempotency = idempotency;
        this.audit = audit;
    }

    /** Creates a hidden category or replays an identical caller-scoped command. */
    @Transactional
    public AdminCategoryView create(CreateCatalogCategoryCommand command) {
        var fingerprint = fingerprint(command);
        var replay = idempotency.claim(
                required(command.actorScope(), "actor scope"),
                required(command.idempotencyKey(), "idempotency key"),
                CREATE_OPERATION,
                fingerprint);
        if (replay.isPresent()) {
            return get(new CategoryId(replay.orElseThrow()));
        }

        var category = Category.create(
                new CategoryId(ids.next()),
                command.parentId(),
                new CategorySlug(command.slug()),
                new CategoryName(command.name()),
                command.displayOrder());
        var prospective = new ArrayList<>(categories.findAll());
        prospective.add(category);
        new CategoryHierarchy(prospective);
        var saved = categories.save(category);
        audit.record(
                "CATEGORY",
                saved.id().value(),
                "CREATED",
                null,
                saved.version(),
                Map.of("slug", saved.slug().value(), "status", saved.status().name()));
        idempotency.complete(
                command.actorScope(), command.idempotencyKey(), saved.id().value());
        return get(saved.id());
    }

    /** Reads a category including persistence timestamps and concurrency metadata. */
    @Transactional(readOnly = true)
    public AdminCategoryView get(CategoryId id) {
        return adminReader
                .findById(id)
                .map(ManageCatalogCategories::view)
                .orElseThrow(CatalogCategoryNotFoundException::new);
    }

    /** Applies a partial change after ETag and complete prospective-hierarchy validation. */
    @Transactional
    public AdminCategoryView update(UpdateCatalogCategoryCommand command) {
        var current = categories.findById(command.id()).orElseThrow(CatalogCategoryNotFoundException::new);
        if (current.version() != command.expectedVersion()) {
            throw new StaleCatalogVersionException();
        }
        var parentId = command.parentSpecified()
                ? command.parentId()
                : current.parentId().orElse(null);
        var changed = current.change(
                parentId,
                command.slug() == null ? current.slug() : new CategorySlug(command.slug()),
                command.name() == null ? current.name() : new CategoryName(command.name()),
                command.displayOrder() == null ? current.displayOrder() : command.displayOrder(),
                command.status() == null ? current.status() : command.status());
        var prospective = new ArrayList<>(categories.findAll());
        prospective.removeIf(category -> category.id().equals(current.id()));
        prospective.add(changed);
        new CategoryHierarchy(prospective);
        var saved = categories.save(changed);
        audit.record(
                "CATEGORY",
                saved.id().value(),
                "UPDATED",
                current.version(),
                saved.version(),
                Map.of(
                        "statusFrom",
                        current.status().name(),
                        "statusTo",
                        saved.status().name()));
        return get(saved.id());
    }

    /** Permanently removes an archived category when it has no children or product assignments. */
    @Transactional
    public void delete(CategoryId id, long expectedVersion) {
        var current = categories.findById(id).orElseThrow(CatalogCategoryNotFoundException::new);
        if (current.version() != expectedVersion) {
            throw new StaleCatalogVersionException();
        }
        if (current.status() != CategoryStatus.ARCHIVED) {
            throw new IllegalArgumentException("Only archived categories can be permanently deleted");
        }
        if (!categories.deleteIfUnused(id)) {
            throw new CategoryInvariantViolation(
                    ru.amra.market.catalog.domain.CategoryInvariant.ORPHAN_PARENT,
                    "Category with children or products cannot be permanently deleted");
        }
        audit.record("CATEGORY", id.value(), "DELETED", current.version(), current.version(), Map.of());
    }

    private static AdminCategoryView view(AdminCategoryReader.Snapshot category) {
        return new AdminCategoryView(
                category.id().value(),
                category.parentId() == null ? null : category.parentId().value(),
                category.slug(),
                category.name(),
                category.displayOrder(),
                category.status(),
                category.version(),
                category.updatedAt(),
                CatalogVersionEtag.format(category.version()));
    }

    private static String fingerprint(CreateCatalogCategoryCommand command) {
        var hash = new RepresentationHasher().add(command.parentId() == null ? 0 : 1);
        if (command.parentId() != null) {
            hash.add(command.parentId().value());
        }
        return hash.add(command.slug())
                .add(command.name())
                .add(command.displayOrder())
                .hexDigest();
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Catalog " + field + " must not be blank");
        }
        return value;
    }
}
