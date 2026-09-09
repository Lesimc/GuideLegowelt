package net.lesimc.guide_legowelt.wiki;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class WikiDialogService {

    private static final Key DEFAULT_FONT = Key.key("minecraft:default");
    private static final int BACKGROUND_WIDTH = 360;
    private static final int CONTENT_WIDTH = 320;
    private static final int TOPIC_BUTTON_WIDTH = 170;

    private final JavaPlugin plugin;
    private final WikiConfigurationLoader loader = new WikiConfigurationLoader();
    private volatile WikiCatalog catalog;

    public WikiDialogService(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public int reload() throws WikiConfigurationException {
        WikiCatalog loadedCatalog = loader.load(new File(plugin.getDataFolder(), "pages.yml"));
        catalog = loadedCatalog;
        return loadedCatalog.pages().size();
    }

    public void openContents(Player player) {
        WikiCatalog currentCatalog = requireCatalog();
        player.showDialog(createContentsDialog(currentCatalog));
    }

    public boolean openPage(Player player, String pageId) {
        return requireCatalog().findPage(pageId)
            .map(page -> {
                player.showDialog(createPageDialog(page));
                return true;
            })
            .orElse(false);
    }

    public List<String> pageIds() {
        return requireCatalog().pageIds();
    }

    private Dialog createContentsDialog(WikiCatalog currentCatalog) {
        List<ActionButton> pageButtons = currentCatalog.rootPages().stream()
            .map(page -> createPageButton(page, this::openContents))
            .toList();

        ActionButton closeButton = ActionButton.builder(Component.text("Close", NamedTextColor.GRAY))
            .width(100)
            .build();

        return Dialog.create(builder -> builder.empty()
            .base(DialogBase.builder(currentCatalog.title())
                .canCloseWithEscape(true)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(createBody(currentCatalog.background(), List.of(currentCatalog.introduction()), List.of()))
                .build())
            .type(DialogType.multiAction(pageButtons, closeButton, currentCatalog.columns()))
        );
    }

    private Dialog createPageDialog(WikiPage page) {
        WikiCatalog currentCatalog = requireCatalog();
        Component background = page.showBackground() ? currentCatalog.background() : Component.empty();
        List<? extends DialogBody> body = createBody(background, page.content(), page.images());
        List<ActionButton> childButtons = currentCatalog.childPages(page.id()).stream()
            .map(child -> createPageButton(child, player -> openPage(player, page.id())))
            .toList();

        ActionButton returnButton = ActionButton.builder(Component.text("Back", NamedTextColor.GOLD))
            .tooltip(createReturnTooltip(currentCatalog, page))
            .width(180)
            .action(navigationAction(player -> returnToParent(currentCatalog, page, player)))
            .build();

        DialogType dialogType = childButtons.isEmpty()
            ? DialogType.notice(returnButton)
            : DialogType.multiAction(childButtons, returnButton, currentCatalog.columns());

        return Dialog.create(builder -> builder.empty()
            .base(DialogBase.builder(page.title())
                .externalTitle(page.title())
                .canCloseWithEscape(true)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(body)
                .build())
            .type(dialogType)
        );
    }

    private ActionButton createPageButton(WikiPage page, Consumer<Player> fallback) {
        return ActionButton.create(
            page.title(),
            page.summary(),
            TOPIC_BUTTON_WIDTH,
            navigationAction(player -> {
                if (!openPage(player, page.id())) {
                    fallback.accept(player);
                }
            })
        );
    }

    private Component createReturnTooltip(WikiCatalog currentCatalog, WikiPage page) {
        return currentCatalog.parentOf(page)
            .map(parent -> Component.text("Return to ", NamedTextColor.GRAY).append(parent.title()))
            .orElseGet(() -> Component.text("Return to the table of contents", NamedTextColor.GRAY));
    }

    private void returnToParent(WikiCatalog currentCatalog, WikiPage page, Player player) {
        WikiPage parent = currentCatalog.parentOf(page).orElse(null);
        if (parent == null || !openPage(player, parent.id())) {
            openContents(player);
        }
    }

    private DialogAction navigationAction(Consumer<Player> navigation) {
        return DialogAction.customClick(
            (response, audience) -> {
                if (!(audience instanceof Player player) || !plugin.isEnabled()) {
                    return;
                }

                Runnable action = () -> navigation.accept(player);
                if (Bukkit.isPrimaryThread()) {
                    action.run();
                } else {
                    Bukkit.getScheduler().runTask(plugin, action);
                }
            },
            ClickCallback.Options.builder().uses(1).build()
        );
    }

    private List<? extends DialogBody> createBody(
        Component background,
        List<Component> content,
        List<WikiImage> images
    ) {
        List<DialogBody> body = new ArrayList<>();
        if (!background.equals(Component.empty())) {
            body.add(DialogBody.plainMessage(background, BACKGROUND_WIDTH));
        }
        content.stream()
            .map(component -> DialogBody.plainMessage(component, CONTENT_WIDTH))
            .forEach(body::add);
        images.stream().map(this::createImageBody).forEach(body::add);
        return List.copyOf(body);
    }

    private DialogBody createImageBody(WikiImage image) {
        int reservedLines = Math.ceilDiv(image.height(), 9);
        int rowSpacing = Math.ceilDiv(image.height(), image.rows().size() * 9);
        Component content = Component.empty();
        for (int index = 0; index < image.rows().size(); index++) {
            if (index > 0) {
                content = content.append(Component.text("\n".repeat(rowSpacing)).font(DEFAULT_FONT));
            }
            content = content.append(image.rows().get(index));
        }

        int usedLines = 1 + ((image.rows().size() - 1) * rowSpacing);
        int remainingLines = Math.max(0, reservedLines - usedLines);
        Component contentWithReservedHeight = content
            .append(Component.text("\n".repeat(remainingLines) + "\u200C").font(DEFAULT_FONT));
        return DialogBody.plainMessage(contentWithReservedHeight, image.width());
    }

    private WikiCatalog requireCatalog() {
        return Objects.requireNonNull(catalog, "The wiki catalog has not been loaded");
    }
}
