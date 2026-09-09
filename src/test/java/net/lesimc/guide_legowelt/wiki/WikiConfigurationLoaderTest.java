package net.lesimc.guide_legowelt.wiki;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class WikiConfigurationLoaderTest {

    private static final String VALID_CONFIGURATION = """
        wiki:
          title: '<gold>Wiki</gold>'
          introduction: 'Choose a page.'
          background: "<font:guidelegowelt:wiki>\\uE000</font>"
          columns: 2
        pages:
          start:
            title: 'Start'
            summary: 'Begin here.'
            content:
              - 'First line.'
          rules:
            title: 'Rules'
            summary: 'Read the rules.'
            parent: start
            content:
              - 'Be respectful.'
        """;

    private final WikiConfigurationLoader loader = new WikiConfigurationLoader();

    @Test
    void loadsPagesInConfigurationOrder() throws Exception {
        WikiCatalog catalog = loader.load(configuration(VALID_CONFIGURATION));

        assertEquals(2, catalog.columns());
        assertEquals(Component.text("\uE000").font(Key.key("guidelegowelt:wiki")), catalog.background());
        assertEquals(List.of("start", "rules"), catalog.pageIds());
        assertEquals(List.of("start"), catalog.rootPages().stream().map(WikiPage::id).toList());
        assertEquals(List.of("rules"), catalog.childPages("start").stream().map(WikiPage::id).toList());
        assertEquals("start", catalog.parentOf(catalog.findPage("rules").orElseThrow()).orElseThrow().id());
        assertEquals("rules", catalog.findPage("RULES").orElseThrow().id());
        assertEquals(true, catalog.findPage("start").orElseThrow().showBackground());
    }

    @Test
    void rejectsInvalidColumnCount() throws Exception {
        YamlConfiguration configuration = configuration(VALID_CONFIGURATION.replace("columns: 2", "columns: 0"));

        assertThrows(WikiConfigurationException.class, () -> loader.load(configuration));
    }

    @Test
    void supportsConfigurationsWithoutABackground() throws Exception {
        YamlConfiguration configuration = configuration(
            VALID_CONFIGURATION.replace("  background: \"<font:guidelegowelt:wiki>\\uE000</font>\"\n", "")
        );

        assertEquals(Component.empty(), loader.load(configuration).background());
    }

    @Test
    void supportsAnOverallDisabledBackground() throws Exception {
        YamlConfiguration configuration = configuration(
            VALID_CONFIGURATION.replace(
                "background: \"<font:guidelegowelt:wiki>\\uE000</font>\"",
                "background: false"
            )
        );

        assertEquals(Component.empty(), loader.load(configuration).background());
    }

    @Test
    void rejectsAnOverallEnabledBooleanBackground() throws Exception {
        YamlConfiguration configuration = configuration(
            VALID_CONFIGURATION.replace(
                "background: \"<font:guidelegowelt:wiki>\\uE000</font>\"",
                "background: true"
            )
        );

        assertThrows(WikiConfigurationException.class, () -> loader.load(configuration));
    }

    @Test
    void rejectsInvalidPageId() throws Exception {
        YamlConfiguration configuration = configuration(VALID_CONFIGURATION.replace("start:", "Invalid Page:"));

        assertThrows(WikiConfigurationException.class, () -> loader.load(configuration));
    }

    @Test
    void createsStyledCommandLinksWithPageTag() throws Exception {
        YamlConfiguration configuration = configuration(
            VALID_CONFIGURATION.replace("First line.", "Read <page:rules>the rules</page>.")
        );

        Component content = loader.load(configuration).findPage("start").orElseThrow().content().getFirst();
        Component expected = MiniMessage.miniMessage().deserialize(
            "Read <blue><underlined><bold><click:run_command:/wiki rules>the rules</click></bold></underlined></blue>."
        );

        assertEquals(expected, content);
    }

    @Test
    void createsPageLinksUsingTheTargetTitle() throws Exception {
        YamlConfiguration configuration = configuration(
            VALID_CONFIGURATION
                .replace("title: 'Rules'", "title: '<gold>Rules</gold>'")
                .replace("First line.", "Read <page:rules/>.")
        );

        Component content = loader.load(configuration).findPage("start").orElseThrow().content().getFirst();
        Component expected = MiniMessage.miniMessage().deserialize(
            "Read <blue><underlined><bold><click:run_command:/wiki rules>Rules</click></bold></underlined></blue>."
        );

        assertEquals(expected, content);
    }

    @Test
    void rejectsPageTagWithUnknownPageId() throws Exception {
        YamlConfiguration configuration = configuration(
            VALID_CONFIGURATION.replace("First line.", "Read <page:missing>the missing page</page>.")
        );

        assertThrows(WikiConfigurationException.class, () -> loader.load(configuration));
    }

    @Test
    void rejectsAutomaticPageTagWithUnknownPageId() throws Exception {
        YamlConfiguration configuration = configuration(
            VALID_CONFIGURATION.replace("First line.", "Read <page:missing/>.")
        );

        assertThrows(WikiConfigurationException.class, () -> loader.load(configuration));
    }

    @Test
    void loadsPageWithEmptySummaryAndContent() throws Exception {
        WikiCatalog catalog = loader.load(configuration("""
            wiki:
              title: 'Wiki'
              introduction: 'Choose a page.'
            pages:
              empty:
                title: 'Empty Page'
                summary: ''
                content: []
            """));

        WikiPage page = catalog.findPage("empty").orElseThrow();
        assertEquals(Component.empty(), page.summary());
        assertEquals(List.of(), page.content());
        assertEquals(List.of(), page.images());
    }

    @Test
    void treatsOmittedSummaryAndContentAsEmpty() throws Exception {
        WikiCatalog catalog = loader.load(configuration("""
            wiki:
              title: 'Wiki'
              introduction: 'Choose a page.'
            pages:
              empty:
                title: 'Empty Page'
            """));

        WikiPage page = catalog.findPage("empty").orElseThrow();
        assertEquals(Component.empty(), page.summary());
        assertEquals(List.of(), page.content());
    }

    @Test
    void treatsNullSummaryAndContentAsEmpty() throws Exception {
        WikiCatalog catalog = loader.load(configuration("""
            wiki:
              title: 'Wiki'
              introduction: 'Choose a page.'
            pages:
              empty:
                title: 'Empty Page'
                summary:
                content:
            """));

        WikiPage page = catalog.findPage("empty").orElseThrow();
        assertEquals(Component.empty(), page.summary());
        assertEquals(List.of(), page.content());
    }

    @Test
    void rejectsNonTextSummary() throws Exception {
        YamlConfiguration configuration = configuration("""
            wiki:
              title: 'Wiki'
              introduction: 'Choose a page.'
            pages:
              invalid:
                title: 'Invalid Page'
                summary: []
                content: []
            """);

        assertThrows(WikiConfigurationException.class, () -> loader.load(configuration));
    }

    @Test
    void rejectsScalarContent() throws Exception {
        YamlConfiguration configuration = configuration("""
            wiki:
              title: 'Wiki'
              introduction: 'Choose a page.'
            pages:
              invalid:
                title: 'Invalid Page'
                summary: ''
                content: 'not a list'
            """);

        assertThrows(WikiConfigurationException.class, () -> loader.load(configuration));
    }

    @Test
    void rejectsUnknownParent() throws Exception {
        YamlConfiguration configuration = configuration(VALID_CONFIGURATION.replace("parent: start", "parent: missing"));

        assertThrows(WikiConfigurationException.class, () -> loader.load(configuration));
    }

    @Test
    void rejectsParentCycle() throws Exception {
        YamlConfiguration configuration = configuration(
            VALID_CONFIGURATION.replace("  start:\n", "  start:\n    parent: rules\n")
        );

        assertThrows(WikiConfigurationException.class, () -> loader.load(configuration));
    }

    @Test
    void loadsBundledCatalog() throws Exception {
        try (InputStreamReader reader = new InputStreamReader(
            Objects.requireNonNull(getClass().getResourceAsStream("/pages.yml")),
            StandardCharsets.UTF_8
        )) {
            loader.load(YamlConfiguration.loadConfiguration(reader));
        }
    }

    private YamlConfiguration configuration(String source) throws InvalidConfigurationException {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.loadFromString(source);
        return configuration;
    }

}
