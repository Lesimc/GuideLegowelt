package net.lesimc.guide_legowelt;

import io.papermc.paper.datapack.DiscoveredDatapack;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.event.RegistryEvents;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.lesimc.guide_legowelt.wiki.WikiCatalog;
import net.lesimc.guide_legowelt.wiki.WikiConfigurationException;
import net.lesimc.guide_legowelt.wiki.WikiConfigurationLoader;
import net.lesimc.guide_legowelt.wiki.WikiPage;
import org.bukkit.configuration.file.YamlConfiguration;

public final class GuideLegoweltBootstrap implements PluginBootstrap {

    static final String LAUNCHER_DATAPACK_PATH = "/wiki-launcher-pack";
    static final String LAUNCHER_DATAPACK_ID = "wiki-launcher";
    private static final int BACKGROUND_WIDTH = 360;
    private static final int CONTENT_WIDTH = 320;
    private static final int TOPIC_BUTTON_WIDTH = 170;
    static final TypedKey<Dialog> LAUNCHER_DIALOG_KEY = TypedKey.create(
        RegistryKey.DIALOG,
        Key.key("guidelegowelt", "wiki_launcher")
    );

    @Override
    public void bootstrap(BootstrapContext context) {
        WikiCatalog catalog = loadPauseMenuCatalog(context);
        context.getLifecycleManager().registerEventHandler(
            RegistryEvents.DIALOG.compose(),
            event -> event.registry().register(LAUNCHER_DIALOG_KEY, builder -> builder
                .base(createContentsBase(catalog))
                .type(createContentsType(catalog)))
        );
        context.getLifecycleManager().registerEventHandler(
            LifecycleEvents.DATAPACK_DISCOVERY,
            event -> discoverLauncherDatapack(event.registrar())
        );
    }

    private DialogBase createContentsBase(WikiCatalog catalog) {
        List<DialogBody> body = new ArrayList<>();
        if (!catalog.background().equals(Component.empty())) {
            body.add(DialogBody.plainMessage(catalog.background(), BACKGROUND_WIDTH));
        }
        body.add(DialogBody.plainMessage(catalog.introduction(), CONTENT_WIDTH));

        return DialogBase.builder(catalog.title())
            .externalTitle(Component.text("Legowelt-Wiki", NamedTextColor.WHITE))
            .canCloseWithEscape(true)
            .afterAction(DialogBase.DialogAfterAction.CLOSE)
            .body(body)
            .build();
    }

    private DialogType createContentsType(WikiCatalog catalog) {
        List<ActionButton> pageButtons = catalog.rootPages().stream()
            .map(this::createPageButton)
            .toList();
        ActionButton closeButton = ActionButton.builder(Component.text("Close", NamedTextColor.GRAY))
            .width(100)
            .build();
        return DialogType.multiAction(pageButtons, closeButton, catalog.columns());
    }

    private ActionButton createPageButton(WikiPage page) {
        return ActionButton.create(
            page.title(),
            page.summary(),
            TOPIC_BUTTON_WIDTH,
            DialogAction.staticAction(ClickEvent.runCommand("/wiki " + page.id()))
        );
    }

    private WikiCatalog loadPauseMenuCatalog(BootstrapContext context) {
        WikiConfigurationLoader loader = new WikiConfigurationLoader();
        Path livePages = context.getDataDirectory().resolve("pages.yml");
        if (Files.isRegularFile(livePages)) {
            try {
                return loader.load(livePages.toFile());
            } catch (WikiConfigurationException exception) {
                context.getLogger().warn(
                    "Could not build the pause-menu wiki from the live pages.yml; using the bundled catalog.",
                    exception
                );
            }
        }

        try (InputStreamReader reader = new InputStreamReader(
            Objects.requireNonNull(
                GuideLegoweltBootstrap.class.getResourceAsStream("/pages.yml"),
                "The bundled pages.yml is missing"
            ),
            StandardCharsets.UTF_8
        )) {
            return loader.load(YamlConfiguration.loadConfiguration(reader));
        } catch (IOException | WikiConfigurationException exception) {
            throw new IllegalStateException("Could not load the bundled wiki catalog during bootstrap", exception);
        }
    }

    private void discoverLauncherDatapack(io.papermc.paper.datapack.DatapackRegistrar registrar) {
        URL resource = Objects.requireNonNull(
            GuideLegoweltBootstrap.class.getResource(LAUNCHER_DATAPACK_PATH),
            "The bundled wiki launcher datapack is missing"
        );

        try {
            URI uri = resource.toURI();
            DiscoveredDatapack datapack = registrar.discoverPack(uri, LAUNCHER_DATAPACK_ID, configurer -> configurer
                .title(Component.text("GuideLegowelt pause-menu launcher"))
                .autoEnableOnServerStart(true));
            if (datapack == null) {
                throw new IllegalStateException("Paper could not discover the bundled wiki launcher datapack");
            }
        } catch (URISyntaxException | IOException exception) {
            throw new IllegalStateException("Could not discover the bundled wiki launcher datapack", exception);
        }
    }
}
