package net.lesimc.guide_legowelt.wiki;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.Context;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.ParsingException;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

public final class WikiConfigurationLoader {

    private static final Pattern PAGE_ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");
    private static final Pattern AUTOMATIC_PAGE_LINK = Pattern.compile(
        "<page:([a-z0-9][a-z0-9_-]{0,63})/>"
    );
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final PlainTextComponentSerializer plainText = PlainTextComponentSerializer.plainText();

    public WikiCatalog load(File file) throws WikiConfigurationException {
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.load(file);
        } catch (IOException | InvalidConfigurationException exception) {
            throw new WikiConfigurationException("Could not read " + file.getName(), exception);
        }

        return load(configuration);
    }

    public WikiCatalog load(ConfigurationSection configuration) throws WikiConfigurationException {
        ConfigurationSection pagesSection = configuration.getConfigurationSection("pages");
        if (pagesSection == null || pagesSection.getKeys(false).isEmpty()) {
            throw new WikiConfigurationException("pages.yml must define at least one page under pages");
        }

        Set<String> pageIds = Set.copyOf(pagesSection.getKeys(false));
        for (String pageId : pageIds) {
            validatePageId(pageId);
        }
        PageLinkResolver pageLinks = new PageLinkResolver(pageIds, loadPageLinkLabels(pagesSection, pageIds));

        Component title = deserialize(requiredString(configuration, "wiki.title"), pageLinks);
        Component introduction = deserialize(requiredString(configuration, "wiki.introduction"), pageLinks);
        Component background = optionalBackground(configuration, pageLinks);
        int columns = configuration.getInt("wiki.columns", 2);
        if (columns < 1 || columns > 4) {
            throw new WikiConfigurationException("wiki.columns must be between 1 and 4");
        }

        List<WikiPage> pages = new ArrayList<>();
        for (String id : pagesSection.getKeys(false)) {
            pages.add(loadPage(pagesSection, id, pageLinks));
        }
        validateHierarchy(pages);

        return new WikiCatalog(title, introduction, background, columns, pages);
    }

    private Map<String, String> loadPageLinkLabels(ConfigurationSection pagesSection, Set<String> pageIds)
        throws WikiConfigurationException {
        Map<String, String> fallbackLabels = new HashMap<>();
        pageIds.forEach(pageId -> fallbackLabels.put(pageId, pageId));
        PageLinkResolver fallbackLinks = new PageLinkResolver(pageIds, fallbackLabels);

        Map<String, String> labels = new HashMap<>();
        for (String pageId : pageIds) {
            ConfigurationSection pageSection = pagesSection.getConfigurationSection(pageId);
            if (pageSection == null) {
                throw new WikiConfigurationException("Page '" + pageId + "' must be a configuration section");
            }
            Component title = deserialize(requiredString(pageSection, "title"), fallbackLinks);
            String label = plainText.serialize(title);
            labels.put(pageId, label.isBlank() ? pageId : label);
        }
        return Map.copyOf(labels);
    }

    private void validatePageId(String id) throws WikiConfigurationException {
        if (!PAGE_ID.matcher(id).matches()) {
            throw new WikiConfigurationException("Invalid page ID '" + id + "'");
        }
        if (id.equals("reload")) {
            throw new WikiConfigurationException("Page ID 'reload' is reserved for the admin command");
        }
    }

    private WikiPage loadPage(ConfigurationSection pagesSection, String id, PageLinkResolver pageLinks)
        throws WikiConfigurationException {
        ConfigurationSection pageSection = pagesSection.getConfigurationSection(id);
        if (pageSection == null) {
            throw new WikiConfigurationException("Page '" + id + "' must be a configuration section");
        }

        Component title = deserialize(requiredString(pageSection, "title"), pageLinks);
        Component summary = deserialize(optionalEmptyString(pageSection, "summary", id), pageLinks);
        String parentId = optionalString(pageSection, "parent");
        boolean showBackground = optionalBoolean(pageSection, "background", id);
        List<Component> content = loadContent(pageSection, id, pageLinks);
        List<WikiImage> images = loadImages(pageSection, id, pageLinks);

        return new WikiPage(id, title, summary, parentId, showBackground, content, images);
    }

    private List<Component> loadContent(ConfigurationSection pageSection, String pageId, PageLinkResolver pageLinks)
        throws WikiConfigurationException {
        if (!pageSection.contains("content") || pageSection.get("content") == null) {
            return List.of();
        }
        if (!pageSection.isList("content")) {
            throw new WikiConfigurationException("Page '" + pageId + "' must define content as a list");
        }

        List<?> configuredContent = pageSection.getList("content");
        if (configuredContent == null || configuredContent.isEmpty()) {
            return List.of();
        }

        List<Component> content = new ArrayList<>();
        for (Object configuredLine : configuredContent) {
            if (!(configuredLine instanceof String line)) {
                throw new WikiConfigurationException(
                    "Page '" + pageId + "' must define every content entry as text"
                );
            }
            if (!line.isBlank()) {
                content.add(deserialize(line, pageLinks));
            }
        }
        return List.copyOf(content);
    }

    private List<WikiImage> loadImages(ConfigurationSection pageSection, String pageId, PageLinkResolver pageLinks)
        throws WikiConfigurationException {
        if (!pageSection.contains("images")) {
            return List.of();
        }
        if (!pageSection.isList("images")) {
            throw new WikiConfigurationException("Page '" + pageId + "' must define images as a list");
        }

        List<?> configuredImages = pageSection.getList("images");
        if (configuredImages == null) {
            return List.of();
        }

        List<WikiImage> images = new ArrayList<>();
        for (int index = 0; index < configuredImages.size(); index++) {
            Object configuredImage = configuredImages.get(index);
            if (!(configuredImage instanceof Map<?, ?> imageValues)) {
                throw imageError(pageId, index, "must be a configuration section");
            }

            List<Component> rows = loadImageRows(imageValues, pageId, index, pageLinks);
            int width = imageDimension(imageValues, "width", pageId, index);
            int height = imageDimension(imageValues, "height", pageId, index);
            images.add(new WikiImage(rows, width, height));
        }
        return List.copyOf(images);
    }

    private List<Component> loadImageRows(
        Map<?, ?> imageValues,
        String pageId,
        int index,
        PageLinkResolver pageLinks
    )
        throws WikiConfigurationException {
        Object glyphValue = imageValues.get("glyph");
        Object glyphsValue = imageValues.get("glyphs");
        if (glyphValue != null && glyphsValue != null) {
            throw imageError(pageId, index, "cannot define both glyph and glyphs");
        }
        if (glyphValue instanceof String glyph && !glyph.isBlank()) {
            return List.of(deserialize(glyph, pageLinks));
        }
        if (!(glyphsValue instanceof List<?> glyphs) || glyphs.isEmpty()) {
            throw imageError(pageId, index, "must define glyph or glyphs as non-empty text");
        }

        List<Component> rows = new ArrayList<>();
        for (Object row : glyphs) {
            if (!(row instanceof String glyph) || glyph.isBlank()) {
                throw imageError(pageId, index, "must define every glyphs row as non-empty text");
            }
            rows.add(deserialize(glyph, pageLinks));
        }
        return List.copyOf(rows);
    }

    private int imageDimension(Map<?, ?> imageValues, String key, String pageId, int index)
        throws WikiConfigurationException {
        Object value = imageValues.get(key);
        if (!(value instanceof Number number) || number.intValue() < 1 || number.intValue() > 1024) {
            throw imageError(pageId, index, key + " must be between 1 and 1024");
        }
        return number.intValue();
    }

    private WikiConfigurationException imageError(String pageId, int index, String message) {
        return new WikiConfigurationException("Image " + (index + 1) + " on page '" + pageId + "' " + message);
    }

    private void validateHierarchy(List<WikiPage> pages) throws WikiConfigurationException {
        Map<String, WikiPage> pagesById = new HashMap<>();
        pages.forEach(page -> pagesById.put(page.id(), page));

        for (WikiPage page : pages) {
            if (!page.parentId().isEmpty() && !pagesById.containsKey(page.parentId())) {
                throw new WikiConfigurationException(
                    "Page '" + page.id() + "' has unknown parent '" + page.parentId() + "'"
                );
            }
        }

        for (WikiPage page : pages) {
            Set<String> visited = new HashSet<>();
            WikiPage current = page;
            while (!current.parentId().isEmpty()) {
                if (!visited.add(current.id())) {
                    throw new WikiConfigurationException("Page hierarchy contains a cycle at '" + current.id() + "'");
                }
                current = pagesById.get(current.parentId());
            }
        }
    }

    private String requiredString(ConfigurationSection section, String path) throws WikiConfigurationException {
        String value = section.getString(path);
        if (value == null || value.isBlank()) {
            throw new WikiConfigurationException(path + " must be a non-empty string");
        }
        return value;
    }

    private String optionalEmptyString(ConfigurationSection section, String path, String pageId)
        throws WikiConfigurationException {
        if (!section.contains(path) || section.get(path) == null) {
            return "";
        }
        Object value = section.get(path);
        if (!(value instanceof String text)) {
            throw new WikiConfigurationException("Page '" + pageId + "' must define " + path + " as text");
        }
        return text;
    }

    private Component optionalBackground(ConfigurationSection section, PageLinkResolver pageLinks)
        throws WikiConfigurationException {
        if (!section.contains("wiki.background")) {
            return Component.empty();
        }

        Object value = section.get("wiki.background");
        if (Boolean.FALSE.equals(value)) {
            return Component.empty();
        }
        if (value instanceof String background) {
            return background.isBlank() ? Component.empty() : deserialize(background, pageLinks);
        }
        throw new WikiConfigurationException("wiki.background must be a MiniMessage string or false");
    }

    private String optionalString(ConfigurationSection section, String path) {
        String value = section.getString(path);
        return value == null ? "" : value.strip();
    }

    private boolean optionalBoolean(ConfigurationSection section, String path, String pageId)
        throws WikiConfigurationException {
        if (!section.contains(path)) {
            return true;
        }
        if (!section.isBoolean(path)) {
            throw new WikiConfigurationException("Page '" + pageId + "' must define " + path + " as true or false");
        }
        return section.getBoolean(path);
    }

    private Component deserialize(String value, PageLinkResolver pageLinks) throws WikiConfigurationException {
        try {
            String expandedValue = AUTOMATIC_PAGE_LINK.matcher(value).replaceAll(match -> {
                String pageId = match.group(1);
                return "<page:" + pageId + "><page-label:" + pageId + "/></page>";
            });
            Component component = miniMessage.deserialize(expandedValue, pageLinks);
            pageLinks.validate();
            return component;
        } catch (ParsingException exception) {
            throw new WikiConfigurationException("Invalid MiniMessage: " + exception.getMessage(), exception);
        }
    }

    private static final class PageLinkResolver implements TagResolver {

        private final Set<String> pageIds;
        private final Map<String, String> pageLabels;
        private String validationError;

        private PageLinkResolver(Set<String> pageIds, Map<String, String> pageLabels) {
            this.pageIds = pageIds;
            this.pageLabels = pageLabels;
        }

        @Override
        public Tag resolve(String name, ArgumentQueue arguments, Context context) {
            if (!has(name)) {
                return null;
            }
            if (name.equalsIgnoreCase("page-label")) {
                return pageLabelTag(arguments);
            }
            if (!arguments.hasNext()) {
                // MiniMessage resolves the argument-free closing tag through the same resolver.
                return pageLinkTag("");
            }

            String pageId = arguments.pop().value();
            if (arguments.hasNext()) {
                recordError("<page> accepts exactly one page ID");
            } else if (!PAGE_ID.matcher(pageId).matches() || !pageIds.contains(pageId)) {
                recordError("<page> references unknown page '" + pageId + "'");
            }
            return pageLinkTag(pageId);
        }

        @Override
        public boolean has(String name) {
            return name.equalsIgnoreCase("page") || name.equalsIgnoreCase("page-label");
        }

        private Tag pageLabelTag(ArgumentQueue arguments) {
            if (!arguments.hasNext()) {
                recordError("<page-label> requires exactly one page ID");
                return Tag.selfClosingInserting(Component.empty());
            }

            String pageId = arguments.pop().value();
            if (arguments.hasNext()) {
                recordError("<page-label> accepts exactly one page ID");
            } else if (!PAGE_ID.matcher(pageId).matches() || !pageIds.contains(pageId)) {
                recordError("<page> references unknown page '" + pageId + "'");
            }
            return Tag.selfClosingInserting(Component.text(pageLabels.getOrDefault(pageId, pageId)));
        }

        private Tag pageLinkTag(String pageId) {
            return Tag.styling(style -> {
                style.color(NamedTextColor.BLUE)
                    .decoration(TextDecoration.UNDERLINED, TextDecoration.State.TRUE)
                    .decoration(TextDecoration.BOLD, TextDecoration.State.TRUE);
                if (!pageId.isEmpty()) {
                    style.clickEvent(ClickEvent.runCommand("/wiki " + pageId));
                }
            });
        }

        private void recordError(String error) {
            if (validationError == null) {
                validationError = error;
            }
        }

        private void validate() throws WikiConfigurationException {
            if (validationError == null) {
                return;
            }
            String error = validationError;
            validationError = null;
            throw new WikiConfigurationException(error);
        }
    }
}
