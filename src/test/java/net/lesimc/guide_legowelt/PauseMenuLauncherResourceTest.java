package net.lesimc.guide_legowelt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class PauseMenuLauncherResourceTest {

    @Test
    void declaresPaperBootstrapAndPermissions() {
        YamlConfiguration metadata = yamlResource("/paper-plugin.yml");

        assertEquals("net.lesimc.guide_legowelt.Guide_legowelt", metadata.getString("main"));
        assertEquals(
            "net.lesimc.guide_legowelt.GuideLegoweltBootstrap",
            metadata.getString("bootstrapper")
        );
        assertEquals(true, metadata.getBoolean("permissions.guidelegowelt.wiki.default"));
        assertEquals("op", metadata.getString("permissions.guidelegowelt.admin.default"));
        assertFalse(metadata.contains("commands"));
    }

    @Test
    void addsOnlyTheWikiLauncherToThePauseMenuTag() {
        YamlConfiguration tag = yamlResource(
            "/wiki-launcher-pack/data/minecraft/tags/dialog/pause_screen_additions.json"
        );

        assertEquals("guidelegowelt:wiki_launcher", GuideLegoweltBootstrap.LAUNCHER_DIALOG_KEY.asString());
        assertFalse(tag.getBoolean("replace"));
        assertEquals(List.of("guidelegowelt:wiki_launcher"), tag.getStringList("values"));
    }

    @Test
    void targetsTheMinecraftDataPackFormat() {
        YamlConfiguration metadata = yamlResource("/wiki-launcher-pack/pack.mcmeta");

        assertEquals(List.of(107, 1), metadata.getIntegerList("pack.min_format"));
        assertEquals(List.of(107, 1), metadata.getIntegerList("pack.max_format"));
    }

    private YamlConfiguration yamlResource(String path) {
        try (InputStreamReader reader = new InputStreamReader(
            Objects.requireNonNull(getClass().getResourceAsStream(path)),
            StandardCharsets.UTF_8
        )) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Could not read test resource " + path, exception);
        }
    }
}
