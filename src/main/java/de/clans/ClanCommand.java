package de.clans;

import de.clans.gui.View;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** /clan öffnet die GUI. Alles Weitere läuft über das Menü. /cc <Nachricht> = Clan-Chat. */
public final class ClanCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("invites", "members", "allies", "color", "clans", "pack", "help");

    private final ClansPlugin plugin;

    public ClanCommand(ClansPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        if (cmd.getName().equalsIgnoreCase("clan") && a.length > 0 && a[0].equalsIgnoreCase("reload")
                && sender.hasPermission("clans.admin")) {
            plugin.reloadConfig();
            plugin.clans().refreshAll();
            plugin.send(sender, "<green>Konfiguration neu geladen.");
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Nur Spieler können diesen Befehl nutzen.");
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("cc")) {
            if (a.length == 0) plugin.err(p, "Verwendung: <yellow>/cc [Nachricht]");
            else plugin.clans().chat(p, String.join(" ", a));
            return true;
        }

        if (a.length == 0) {
            plugin.gui().open(p, null);
            return true;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "invites" -> plugin.gui().open(p, View.INVITES);
            case "members" -> plugin.gui().open(p, View.MEMBERS);
            case "allies" -> plugin.gui().open(p, View.ALLIES);
            case "color", "farbe" -> plugin.gui().open(p, View.COLOR);
            case "clans", "list" -> plugin.gui().open(p, View.CLANS);
            case "pack" -> {
                if (plugin.pack().send(p)) plugin.send(p, "<gray>Resource Pack wird gesendet ...");
                else plugin.err(p, "Es ist kein Pack-Link konfiguriert.");
            }
            case "help" -> {
                plugin.send(p, "<gray>Alles läuft über das Menü: <yellow>/clan");
                plugin.send(p, "<yellow>/cc [Nachricht] <gray>- Clan-Chat");
                plugin.send(p, "<yellow>/clan pack <gray>- Icon-Pack erneut laden");
            }
            default -> plugin.gui().open(p, null);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (cmd.getName().equalsIgnoreCase("clan") && a.length == 1) {
            out.addAll(SUBS);
            if (sender.hasPermission("clans.admin")) out.add("reload");
            String last = a[0].toLowerCase(Locale.ROOT);
            out.removeIf(s -> !s.startsWith(last));
        }
        return out;
    }
}
