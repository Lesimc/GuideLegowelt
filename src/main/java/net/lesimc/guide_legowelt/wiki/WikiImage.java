package net.lesimc.guide_legowelt.wiki;

import java.util.List;
import net.kyori.adventure.text.Component;

public record WikiImage(List<Component> rows, int width, int height) {

    public WikiImage {
        rows = List.copyOf(rows);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("A wiki image must contain at least one glyph row");
        }
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("Wiki image dimensions must be positive");
        }
    }
}
