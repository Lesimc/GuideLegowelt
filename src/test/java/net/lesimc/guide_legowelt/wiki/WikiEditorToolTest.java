package net.lesimc.guide_legowelt.wiki;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WikiEditorToolTest {

    private static final Path EDITOR = Path.of("tools", "wiki-editor");

    @Test
    void editorShipsItsOfflineRuntimeAndWikiContracts() throws Exception {
        String html = Files.readString(EDITOR.resolve("index.html"));
        String script = Files.readString(EDITOR.resolve("app.js"));
        String build = Files.readString(Path.of("build.gradle.kts"));

        assertAll(
            () -> assertTrue(Files.isRegularFile(EDITOR.resolve("app.css"))),
            () -> assertTrue(Files.isRegularFile(EDITOR.resolve("vendor/js-yaml.umd.min.js"))),
            () -> assertTrue(Files.isRegularFile(EDITOR.resolve("vendor/js-yaml.LICENSE.txt"))),
            () -> assertTrue(Files.isRegularFile(EDITOR.resolve("vendor/lucide.min.js"))),
            () -> assertTrue(Files.isRegularFile(EDITOR.resolve("vendor/lucide.LICENSE.txt"))),
            () -> assertTrue(html.contains("id=\"page-tree\"")),
            () -> assertTrue(html.contains("id=\"image-dialog\"")),
            () -> assertTrue(script.contains("const TILE_SIZE = 256")),
            () -> assertTrue(script.contains("const FIRST_GLYPH = 0xe010")),
            () -> assertTrue(script.contains("quoteStyle: \"double\"")),
            () -> assertTrue(script.contains("/api/source-image/")),
            () -> assertTrue(script.contains("handle.draggable = true")),
            () -> assertFalse(script.contains("item.draggable = true")),
            () -> assertTrue(build.contains("tasks.register<RunWikiEditor>(\"runWikiEditor\")"))
        );
    }
}
