package net.lesimc.guide_legowelt.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.lesimc.guide_legowelt.wiki.WikiConfigurationException;
import net.lesimc.guide_legowelt.wiki.WikiDialogService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class WikiCommand implements BasicCommand {

    private static final String ADMIN_PERMISSION = "guidelegowelt.admin";
    private static final String WIKI_PERMISSION = "guidelegowelt.wiki";

    private final JavaPlugin plugin;
    private final WikiDialogService wikiService;

    public WikiCommand(JavaPlugin plugin, WikiDialogService wikiService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.wikiService = Objects.requireNonNull(wikiService, "wikiService");
    }

    @Override
    public void execute(CommandSourceStack commandSourceStack, String[] args) {
        CommandSender sender = commandSourceStack.getSender();
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reload(sender);
            return;
        }

        if (args.length > 1) {
            sender.sendMessage(Component.text("Usage: /wiki [page|reload]", NamedTextColor.RED));
            return;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can open wiki dialogs.", NamedTextColor.RED));
            return;
        }

        if (args.length == 0) {
            wikiService.openContents(player);
            return;
        }

        if (!wikiService.openPage(player, args[0])) {
            sender.sendMessage(Component.text("Unknown wiki page: " + args[0], NamedTextColor.RED));
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack commandSourceStack, String[] args) {
        if (args.length != 1) {
            return List.of();
        }

        CommandSender sender = commandSourceStack.getSender();
        String input = args[0].toLowerCase(Locale.ROOT);
        List<String> suggestions = new ArrayList<>(wikiService.pageIds());
        if (sender.hasPermission(ADMIN_PERMISSION)) {
            suggestions.add("reload");
        }
        return suggestions.stream().filter(value -> value.startsWith(input)).sorted().toList();
    }

    @Override
    public String permission() {
        return WIKI_PERMISSION;
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(Component.text("You do not have permission to reload the wiki.", NamedTextColor.RED));
            return;
        }

        try {
            int pageCount = wikiService.reload();
            sender.sendMessage(Component.text("Reloaded " + pageCount + " wiki pages.", NamedTextColor.GREEN));
        } catch (WikiConfigurationException exception) {
            sender.sendMessage(Component.text("Could not reload pages.yml. The previous pages are still active.", NamedTextColor.RED));
            plugin.getLogger().log(Level.WARNING, "Could not reload pages.yml", exception);
        }
    }
}
