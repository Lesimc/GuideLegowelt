package net.lesimc.guide_legowelt;

import java.util.List;
import java.util.logging.Level;
import net.lesimc.guide_legowelt.command.WikiCommand;
import net.lesimc.guide_legowelt.wiki.WikiConfigurationException;
import net.lesimc.guide_legowelt.wiki.WikiDialogService;
import org.bukkit.plugin.java.JavaPlugin;

public final class Guide_legowelt extends JavaPlugin {

    @Override
    public void onEnable() {
        saveResource("pages.yml", false);

        WikiDialogService wikiService = new WikiDialogService(this);
        try {
            int pageCount = wikiService.reload();
            registerWikiCommand(wikiService);
            getLogger().info("GuideLegowelt is enabled with " + pageCount + " wiki pages.");
        } catch (WikiConfigurationException exception) {
            getLogger().log(Level.SEVERE, "Could not load pages.yml. GuideLegowelt will be disabled.", exception);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("GuideLegowelt is disabled.");
    }

    private void registerWikiCommand(WikiDialogService wikiService) {
        registerCommand(
            "wiki",
            "Open the Legowelt wiki",
            List.of("guide"),
            new WikiCommand(this, wikiService)
        );
    }
}
