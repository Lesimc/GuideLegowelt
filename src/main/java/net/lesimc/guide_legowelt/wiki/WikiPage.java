package net.lesimc.guide_legowelt.wiki;

import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;

public record WikiPage(
    String id,
    Component title,
    Component summary,
    String parentId,
    boolean showBackground,
    List<Component> content,
    List<WikiImage> images
) {

    public WikiPage {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(parentId, "parentId");
        content = List.copyOf(content);
        images = List.copyOf(images);
    }
}
