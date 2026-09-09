package net.lesimc.guide_legowelt.wiki;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import net.kyori.adventure.text.Component;

public record WikiCatalog(
    Component title,
    Component introduction,
    Component background,
    int columns,
    List<WikiPage> pages
) {

    public WikiCatalog {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(introduction, "introduction");
        Objects.requireNonNull(background, "background");
        pages = List.copyOf(pages);

        if (pages.isEmpty()) {
            throw new IllegalArgumentException("A wiki catalog must contain at least one page");
        }
    }

    public Optional<WikiPage> findPage(String id) {
        String normalizedId = id.toLowerCase(Locale.ROOT);
        return pages.stream()
            .filter(page -> page.id().equals(normalizedId))
            .findFirst();
    }

    public List<String> pageIds() {
        return pages.stream().map(WikiPage::id).toList();
    }

    public List<WikiPage> rootPages() {
        return pages.stream().filter(page -> page.parentId().isEmpty()).toList();
    }

    public List<WikiPage> childPages(String parentId) {
        return pages.stream().filter(page -> page.parentId().equals(parentId)).toList();
    }

    public Optional<WikiPage> parentOf(WikiPage page) {
        return page.parentId().isEmpty() ? Optional.empty() : findPage(page.parentId());
    }
}
