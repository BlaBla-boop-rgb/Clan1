package de.clans.gui;

import de.clans.Clan;
import de.clans.ClanColor;
import de.clans.ClanManager;
import de.clans.ClansPlugin;
import de.clans.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Eine geöffnete Clan-GUI (54 Slots, komplett aus Kacheln aufgebaut).
 *
 * Layout:
 *   Reihe 0      Logo | Tabs | Schließen
 *   Reihe 1-4    Inhalt (Spalten 0-7, 32 Einträge pro Seite) + Scrollleiste (Spalte 8)
 *   Reihe 5      Zurück | Aktionen | Meldung | Aktionen   (freie Kacheln = animierter Lichtstreifen)
 *
 * Animationen: Lichtstreifen in der Fußzeile, Welle beim Ansichtswechsel, Aufblitzen bei Klicks,
 * pulsierende Umrandung um die Auswahl, Auswahl-Rahmen auf dem Icon selbst.
 */
public final class ClanGui implements InventoryHolder {

    private static final int PER_PAGE = 32;
    private static final int[] SCROLL = {17, 26, 35, 44};
    private static final int SLOT_LOGO = 0;
    private static final int SLOT_CLOSE = 8;
    private static final int BACK = 45;
    private static final int ACT_A = 47;
    private static final int TOAST = 49;
    private static final int ACT_B = 51;
    private static final int ACT_C = 52;
    private static final String DEFAULT_ACCENT = "#ffb347";

    private record Button(ItemStack item, Consumer<ClickType> action) {}

    private record Tab(View view, int slot, Icon icon, String name, String desc) {}

    private record AllyEntry(Clan clan, boolean ally) {}

    private final ClansPlugin plugin;
    private final Player player;
    private final Inventory inventory;
    private final boolean custom;
    private final boolean animated;
    private final boolean sounds;
    private final String stamp;

    private final Map<Integer, Button> buttons = new HashMap<>();
    private final Map<Integer, String> marked = new HashMap<>();
    private final String[] shown = new String[54];

    private View view;
    private int page;
    private String selected;
    private String c1 = "";
    private String c2 = "";

    private String confirmText = "";
    private Supplier<Boolean> confirmAct;
    private View confirmBack = View.HOME;
    private View confirmNext = View.HOME;

    private String toast;
    private long toastUntil;

    private int frame;
    private int waveStart;
    private int flash;
    private int sinceRefresh;

    public ClanGui(ClansPlugin plugin, Player player, View start) {
        this.plugin = plugin;
        this.player = player;
        var cfg = plugin.getConfig();
        this.animated = cfg.getBoolean("gui.animation", true);
        this.sounds = cfg.getBoolean("gui.sounds", true);
        this.custom = plugin.pack().custom(player);
        Clan c = clan();
        this.stamp = computeStamp();
        this.view = start != null ? start : (c != null ? View.OVERVIEW : View.HOME);
        if (c != null) {
            c1 = c.c1;
            c2 = c.c2;
        }
        this.inventory = Bukkit.createInventory(this, 54, title());
        render();
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public Player player() {
        return player;
    }

    public View view() {
        return view;
    }

    // ------------------------------------------------------------ Grundlagen

    private Clan clan() {
        return plugin.clans().getClan(player);
    }

    private boolean leader() {
        return plugin.clans().isLeader(player);
    }

    private String computeStamp() {
        Clan c = clan();
        return c == null ? "-" : c.name + "|" + c.c1 + "|" + c.c2;
    }

    private Component title() {
        Clan c = clan();
        String g1 = DEFAULT_ACCENT, g2 = "#ffe29a", name = "CLANS";
        if (c != null) {
            name = c.name.toUpperCase(Locale.ROOT);
            String h1 = ClanColor.toHex(c.c1);
            if (h1 != null) {
                g1 = h1;
                String h2 = ClanColor.toHex(c.c2);
                g2 = h2 != null ? h2 : h1;
            }
        }
        return Text.MM.deserialize("<gradient:" + g1 + ":" + g2 + "><bold>" + name + "</bold></gradient>");
    }

    private ClanColor accentColor() {
        Clan c = clan();
        if (c != null) {
            ClanColor col = ClanColor.nearest(c.c1);
            if (col != null) return col;
        }
        return ClanColor.ORANGE;
    }

    private String accentHex() {
        Clan c = clan();
        if (c != null) {
            String h = ClanColor.toHex(c.c1);
            if (h != null) return h;
        }
        return DEFAULT_ACCENT;
    }

    private void snd(Sound s, float pitch) {
        if (sounds) player.playSound(player.getLocation(), s, 0.6f, pitch);
    }

    private void click() {
        snd(Sound.UI_BUTTON_CLICK, 1.3f);
        flash = 4;
    }

    private void pick() {
        snd(Sound.BLOCK_NOTE_BLOCK_CHIME, 1.5f);
    }

    private void result(boolean ok) {
        if (ok) snd(Sound.BLOCK_NOTE_BLOCK_PLING, 1.6f);
        else snd(Sound.ENTITY_VILLAGER_NO, 1.0f);
        flash = 6;
    }

    private void later(Runnable r) {
        Bukkit.getScheduler().runTask(plugin, r);
    }

    private void prompt(String hint, Consumer<String> cb, View backTo) {
        later(() -> plugin.prompts().ask(player, hint, cb, () -> plugin.gui().open(player, backTo)));
    }

    private static UUID uuid(String s) {
        try {
            return s == null ? null : UUID.fromString(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** i-ter Inhalts-Slot: Reihen 1-4, Spalten 0-7. */
    private static int cs(int i) {
        return (1 + i / 8) * 9 + (i % 8);
    }

    private void set(int slot, ItemStack it, Consumer<ClickType> act) {
        buttons.put(slot, new Button(it, act));
        inventory.setItem(slot, it);
    }

    private ItemStack btn(Icon icon, String name, String... lore) {
        return Items.btn(custom, icon, name, lore);
    }

    private void go(View v) {
        view = v;
        page = 0;
        selected = null;
        waveStart = frame;
        render();
    }

    private void back(View v) {
        set(BACK, btn(Icon.ARROW_LEFT, "<yellow>Zurück"), t -> {
            click();
            go(v);
        });
    }

    public void opened() {
        snd(Sound.BLOCK_ENDER_CHEST_OPEN, 1.5f);
    }

    public void toast(String mini) {
        toast = mini;
        toastUntil = System.currentTimeMillis() + 6000L;
        render();
    }

    // ------------------------------------------------------------ Klicks

    public void click(int slot, ClickType type) {
        Button b = buttons.get(slot);
        if (b == null || b.action() == null) return;
        if (!(type.isLeftClick() || type.isRightClick())) return;
        b.action().accept(type);
    }

    // ------------------------------------------------------------ Tick / Animation

    public void tick(int refreshEvery) {
        if (++sinceRefresh >= refreshEvery) {
            sinceRefresh = 0;
            refresh();
            if (inventory.getViewers().isEmpty()) return;
        }
        if (animated) frame++;
        paintFree();
        if (flash > 0) flash--;
    }

    /** Daten haben sich geändert -> neu zeichnen (oder komplett neu öffnen, wenn sich der Titel ändert). */
    public void refresh() {
        if (!stamp.equals(computeStamp())) {
            plugin.gui().open(player, view);
            return;
        }
        render();
    }

    private Set<Integer> outline() {
        Set<Integer> out = new HashSet<>();
        for (int s : marked.keySet()) {
            int r = s / 9, c = s % 9;
            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    if (dr == 0 && dc == 0) continue;
                    int nr = r + dr, nc = c + dc;
                    if (nr < 0 || nr > 5 || nc < 0 || nc > 8) continue;
                    int n = nr * 9 + nc;
                    if (!buttons.containsKey(n)) out.add(n);
                }
            }
        }
        return out;
    }

    private String tileFor(int s, Set<Integer> outline, String acc) {
        int row = s / 9, col = s % 9;
        if (outline.contains(s)) {
            return (animated && (frame / 2) % 2 == 1) ? "glow_white" : "glow_" + acc;
        }
        if (row == 0) return "header";
        if (row == 5) {
            if (!animated) return "foot";
            if (flash > 0) return (flash % 2 == 0) ? "glow_white" : "glow_" + acc;
            int d = (frame % 13) - col; // Lichtstreifen läuft von links nach rechts
            return switch (d) {
                case 0 -> "glow_white";
                case 1, 2 -> "glow_" + acc;
                case 3 -> "foot_dim";
                default -> "foot";
            };
        }
        if (animated) {
            int w = frame - waveStart; // Welle über den Inhalt beim Ansichtswechsel
            if (w >= 0 && w < 10 && col == w) return "foot_dim";
        }
        return "bg";
    }

    /** Malt alle Slots ohne Button: Hintergrund, Header, animierte Fußzeile, Auswahl-Umrandung. */
    private void paintFree() {
        Set<Integer> outline = outline();
        String acc = accentColor().id();
        for (int s = 0; s < 54; s++) {
            if (buttons.containsKey(s)) continue;
            String id = tileFor(s, outline, acc);
            if (!id.equals(shown[s])) {
                shown[s] = id;
                inventory.setItem(s, Items.tile(custom, id));
            }
        }
    }

    // ------------------------------------------------------------ Rendern

    private void validate() {
        boolean hasClan = clan() != null;
        switch (view) {
            case HOME, INVITES -> {
                if (hasClan) view = View.OVERVIEW;
            }
            case OVERVIEW, MEMBERS, ALLIES, COLOR -> {
                if (!hasClan) view = View.HOME;
            }
            case ALLY_PICK -> {
                if (!hasClan) view = View.HOME;
                else if (!leader()) view = View.ALLIES;
            }
            case INVITE_PICK -> {
                if (!hasClan) view = View.HOME;
                else if (!leader()) view = View.OVERVIEW;
            }
            case CONFIRM -> {
                if (confirmAct == null) view = confirmBack;
            }
            default -> { }
        }
    }

    public void render() {
        validate();
        buttons.clear();
        marked.clear();
        inventory.clear();
        Arrays.fill(shown, null);

        buildHeader();
        switch (view) {
            case HOME -> buildHome();
            case INVITES -> buildInvites();
            case CLANS -> buildClans();
            case OVERVIEW -> buildOverview();
            case MEMBERS -> buildMembers();
            case ALLIES -> buildAllies();
            case ALLY_PICK -> buildAllyPick();
            case INVITE_PICK -> buildInvitePick();
            case COLOR -> buildColor();
            case CONFIRM -> buildConfirm();
        }
        buildTabs();

        if (toast != null) {
            if (System.currentTimeMillis() < toastUntil) set(TOAST, btn(Icon.CHAT, toast), null);
            else toast = null;
        }

        for (Map.Entry<Integer, String> e : marked.entrySet()) {
            Button b = buttons.get(e.getKey());
            if (b == null) continue;
            ItemStack it = Items.select(b.item(), custom, accentHex(), e.getValue());
            buttons.put(e.getKey(), new Button(it, b.action()));
            inventory.setItem(e.getKey(), it);
        }
        paintFree();
    }

    private void buildHeader() {
        set(SLOT_LOGO, btn(Icon.LOGO, "<gold><bold>CLANS", "<gray>Clan-System"), null);
        set(SLOT_CLOSE, btn(Icon.CLOSE, "<red><bold>Schließen"), t -> {
            click();
            later(player::closeInventory);
        });
    }

    // ------------------------------------------------------------ Tabs

    private View tabOf(View v) {
        return switch (v) {
            case INVITE_PICK -> View.OVERVIEW;
            case ALLY_PICK -> View.ALLIES;
            case CONFIRM -> tabOf(confirmBack);
            default -> v;
        };
    }

    private void buildTabs() {
        boolean vip = plugin.clans().hasVip(player);
        List<Tab> tabs;
        if (clan() == null) {
            tabs = List.of(
                    new Tab(View.HOME, 3, Icon.HOME, "Start", "Gründe einen Clan oder tritt einem bei"),
                    new Tab(View.INVITES, 4, Icon.INVITES, "Einladungen", "Deine offenen Einladungen"),
                    new Tab(View.CLANS, 5, Icon.CLANS, "Alle Clans", "Alle Clans des Servers"));
        } else {
            tabs = List.of(
                    new Tab(View.OVERVIEW, 2, Icon.OVERVIEW, "Übersicht", "Statistiken & Aktionen"),
                    new Tab(View.MEMBERS, 3, Icon.MEMBERS, "Mitglieder", "Mitglieder verwalten"),
                    new Tab(View.ALLIES, 4, Icon.ALLIES, "Allianzen", "Verbündete Clans"),
                    new Tab(View.COLOR, 5, Icon.COLOR, "Clanfarbe", vip ? "Farbe & Farbverlauf" : "Nur für VIP-Booster"),
                    new Tab(View.CLANS, 6, Icon.CLANS, "Alle Clans", "Alle Clans des Servers"));
        }
        View active = tabOf(view);
        for (Tab t : tabs) {
            boolean act = t.view() == active;
            ItemStack it = Items.tab(custom, t.icon(),
                    (act ? "<color:" + accentHex() + ">" : "<gray>") + "<bold>" + t.name(),
                    "<dark_gray>" + t.desc());
            set(t.slot(), it, c -> {
                click();
                go(t.view());
            });
            if (act) marked.put(t.slot(), null);
        }
    }

    // ------------------------------------------------------------ Listen-Helfer

    private <T> List<T> slice(List<T> all) {
        int pages = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
        if (page >= pages) page = pages - 1;
        if (page < 0) page = 0;
        scroll(pages);
        return all.subList(page * PER_PAGE, Math.min(all.size(), page * PER_PAGE + PER_PAGE));
    }

    /** Scrollleiste rechts: Pfeil hoch, Schiene mit Griff, Pfeil runter. */
    private void scroll(int pages) {
        for (int s : SCROLL) set(s, Items.tile(custom, "scroll_track"), null);
        if (pages <= 1) return;
        String info = "<gray>Seite <white>" + (page + 1) + "<gray>/<white>" + pages;
        if (page > 0) {
            set(SCROLL[0], btn(Icon.ARROW_UP, "<yellow>Vorherige Seite", info), t -> {
                click();
                page--;
                render();
            });
        }
        if (page < pages - 1) {
            set(SCROLL[3], btn(Icon.ARROW_DOWN, "<yellow>Nächste Seite", info), t -> {
                click();
                page++;
                render();
            });
        }
        double ratio = page / (double) (pages - 1);
        set(ratio < 0.5 ? SCROLL[1] : SCROLL[2], Items.tile(custom, "scroll_handle"), null);
    }

    private void entry(int idx, ItemStack it, String id) {
        int slot = cs(idx);
        set(slot, it, t -> {
            pick();
            selected = id;
            render();
        });
        if (id.equals(selected)) marked.put(slot, "Ausgewählt");
    }

    private void emptyHint(String text) {
        set(22, btn(Icon.INFO, "<red>" + text), null);
    }

    private ItemStack clanItem(Clan c, String... extra) {
        ClanManager m = plugin.clans();
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Leiter: <gold>" + Text.esc(c.leaderName()));
        lore.add("<gray>Mitglieder: <yellow>" + c.members.size());
        lore.add("<gray>Kills: <green>" + c.kills + " <dark_gray>| <gray>Tode: <red>" + c.deaths
                + " <dark_gray>| <gray>K/D: <yellow>" + String.format(Locale.ROOT, "%.2f", c.kd()));
        lore.add("<gray>Allianzen: <aqua>" + c.allies.size());
        Clan mine = m.getClan(player);
        if (mine == c) lore.add("<gold>★ Dein Clan");
        else if (mine != null && mine.allies.contains(c.key)) lore.add("<aqua>✦ Verbündet mit deinem Clan");
        lore.addAll(Arrays.asList(extra));
        return Items.banner(custom, ClanColor.nearest(c.c1), c.tag, lore.toArray(new String[0]));
    }

    // ------------------------------------------------------------ HOME (ohne Clan)

    private void buildHome() {
        ClanManager m = plugin.clans();
        int min = plugin.getConfig().getInt("min-length", 3);
        int max = plugin.getConfig().getInt("max-length", 10);

        set(20, btn(Icon.PLUS, "<green><bold>Clan gründen",
                "<gray>Gründe deinen eigenen Clan.", "",
                "<dark_gray>Länge: <gray>" + min + "-" + max + " Zeichen",
                "<dark_gray>Erlaubt: <gray>A-Z, 0-9 und _", "",
                "<yellow>» Klicken, dann Namen in den Chat schreiben"), t -> {
            click();
            prompt("<gray>Schreibe den <gold>Clannamen <gray>in den Chat.", name -> {
                boolean ok = m.create(player, name);
                plugin.gui().open(player, ok ? View.OVERVIEW : View.HOME);
            }, View.HOME);
        });

        int inv = m.invitesFor(player.getUniqueId()).size();
        set(22, btn(Icon.INVITES, "<yellow><bold>Einladungen",
                "<gray>Offene Einladungen: " + (inv > 0 ? "<green>" : "<red>") + inv, "",
                "<yellow>» Klicken zum Ansehen"), t -> {
            click();
            go(View.INVITES);
        });

        set(24, btn(Icon.CLANS, "<aqua><bold>Alle Clans",
                "<gray>Clans auf dem Server: <white>" + m.all().size(), "",
                "<yellow>» Klicken zum Ansehen"), t -> {
            click();
            go(View.CLANS);
        });
    }

    // ------------------------------------------------------------ INVITES

    private void buildInvites() {
        ClanManager m = plugin.clans();
        List<Clan> invs = new ArrayList<>(m.invitesFor(player.getUniqueId()));
        invs.sort(Comparator.comparing(c -> c.name.toLowerCase(Locale.ROOT)));
        if (invs.isEmpty()) {
            emptyHint("Keine offenen Einladungen");
            return;
        }
        List<Clan> sub = slice(invs);
        for (int i = 0; i < sub.size(); i++) {
            entry(i, clanItem(sub.get(i)), sub.get(i).key);
        }
        Clan sel = m.byKey(selected);
        if (sel != null && sel.invites.containsKey(player.getUniqueId())) {
            set(ACT_A, btn(Icon.CROSS, "<red><bold>Ablehnen", "<gray>Einladung von <gold>" + sel.name + " <gray>ablehnen."), t -> {
                result(m.declineInvite(player, sel.key));
                selected = null;
                render();
            });
            set(ACT_B, btn(Icon.CHECK, "<green><bold>Annehmen", "<gray>Dem Clan <gold>" + sel.name + " <gray>beitreten."), t -> {
                boolean ok = m.accept(player, sel.key);
                result(ok);
                later(() -> plugin.gui().open(player, ok ? View.OVERVIEW : View.INVITES));
            });
        }
    }

    // ------------------------------------------------------------ CLANS

    private void buildClans() {
        ClanManager m = plugin.clans();
        List<Clan> all = new ArrayList<>(m.all());
        all.sort(Comparator.<Clan>comparingInt(c -> -c.members.size()).thenComparing(c -> c.name.toLowerCase(Locale.ROOT)));
        if (all.isEmpty()) {
            emptyHint("Es gibt noch keine Clans");
            return;
        }
        List<Clan> sub = slice(all);
        for (int i = 0; i < sub.size(); i++) {
            entry(i, clanItem(sub.get(i)), sub.get(i).key);
        }
        Clan sel = m.byKey(selected);
        Clan mine = m.getClan(player);
        if (sel != null && mine != null && mine != sel && leader() && !mine.allies.contains(sel.key)) {
            set(ACT_B, btn(Icon.ALLIES, "<aqua><bold>Allianz anfragen", "<gray>Schickt <gold>" + sel.name + " <gray>eine Anfrage."), t -> {
                result(m.allyInvite(player, sel.key));
            });
        }
    }

    // ------------------------------------------------------------ OVERVIEW

    private void buildOverview() {
        ClanManager m = plugin.clans();
        Clan c = clan();
        if (c == null) return;
        boolean lead = leader();
        String since = new SimpleDateFormat("dd.MM.yyyy").format(new Date(c.created));

        set(13, Items.banner(custom, ClanColor.nearest(c.c1), c.tag,
                "<gray>Leiter: <gold>" + Text.esc(c.leaderName()),
                "<gray>Mitglieder: <yellow>" + c.members.size(),
                "<gray>Allianzen: <aqua>" + c.allies.size(),
                "<gray>Gegründet: <white>" + since,
                "", lead ? "<gold>★ Du bist Clanleiter" : "<gray>Du bist Mitglied"), null);

        set(20, btn(Icon.SWORD, "<green><bold>Kills", "<white>" + c.kills), null);
        set(21, btn(Icon.SKULL, "<red><bold>Tode", "<white>" + c.deaths), null);
        set(22, btn(Icon.KD, "<yellow><bold>K/D", "<white>" + String.format(Locale.ROOT, "%.2f", c.kd())), null);
        set(23, btn(Icon.PICKAXE, "<aqua><bold>Abgebaute Blöcke", "<white>" + c.blocks), null);
        set(24, btn(Icon.MEMBERS, "<gold><bold>Mitglieder", "<white>" + c.members.size(), "", "<yellow>» Klicken für die Liste"), t -> {
            click();
            go(View.MEMBERS);
        });

        set(29, btn(Icon.CHAT, "<aqua><bold>Clan-Chat",
                "<gray>Sende eine Nachricht an alle", "<gray>Clan-Mitglieder.", "",
                "<dark_gray>Tipp: <gray>/cc [Nachricht]", "",
                "<yellow>» Klicken, dann in den Chat schreiben"), t -> {
            click();
            prompt("<gray>Schreibe deine <aqua>Clan-Nachricht <gray>in den Chat.", msg -> {
                m.chat(player, msg);
                plugin.gui().open(player, View.OVERVIEW);
            }, View.OVERVIEW);
        });

        if (lead) {
            set(31, btn(Icon.INVITE, "<green><bold>Spieler einladen",
                    "<gray>Lade einen Spieler ohne Clan ein.", "", "<yellow>» Klicken"), t -> {
                click();
                go(View.INVITE_PICK);
            });
            set(33, btn(Icon.TNT, "<red><bold>Clan auflösen",
                    "<gray>Löscht den Clan unwiderruflich.", "", "<yellow>» Klicken"), t -> {
                click();
                confirm("Willst du den Clan <gold>" + c.name + " <gray>wirklich auflösen?", View.OVERVIEW, View.HOME,
                        () -> m.disband(player));
            });
        } else {
            set(31, btn(Icon.LOCK, "<dark_gray>Spieler einladen", "<red>Nur der Clanleiter kann einladen."), null);
            set(33, btn(Icon.DOOR, "<red><bold>Clan verlassen",
                    "<gray>Du verlässt den Clan <gold>" + c.name + "<gray>.", "", "<yellow>» Klicken"), t -> {
                click();
                confirm("Willst du den Clan <gold>" + c.name + " <gray>wirklich verlassen?", View.OVERVIEW, View.HOME,
                        () -> m.leave(player));
            });
        }
    }

    // ------------------------------------------------------------ MEMBERS

    private void buildMembers() {
        ClanManager m = plugin.clans();
        Clan c = clan();
        if (c == null) return;
        List<UUID> ids = new ArrayList<>(c.members.keySet());
        ids.sort(Comparator.<UUID>comparingInt(u -> u.equals(c.owner) ? 0 : (Bukkit.getPlayer(u) != null ? 1 : 2))
                .thenComparing(u -> c.members.get(u).toLowerCase(Locale.ROOT)));
        List<UUID> sub = slice(ids);
        for (int i = 0; i < sub.size(); i++) {
            UUID u = sub.get(i);
            boolean on = Bukkit.getPlayer(u) != null;
            boolean owner = u.equals(c.owner);
            boolean me = u.equals(player.getUniqueId());
            ItemStack head = Items.head(Bukkit.getOfflinePlayer(u),
                    (on ? "<green>" : "<gray>") + Text.esc(c.members.get(u)) + (me ? " <dark_gray>(du)" : ""),
                    owner ? "<gold>★ Clanleiter" : "<gray>Mitglied",
                    on ? "<green>● Online" : "<dark_gray>● Offline");
            entry(i, head, u.toString());
        }
        UUID sel = uuid(selected);
        if (leader() && sel != null && c.members.containsKey(sel) && !sel.equals(player.getUniqueId())) {
            String sn = Text.esc(c.members.get(sel));
            set(ACT_A, btn(Icon.KICK, "<red><bold>Kicken", "<gray>Entfernt <yellow>" + sn + " <gray>aus dem Clan."), t -> {
                click();
                confirm("Willst du <yellow>" + sn + " <gray>wirklich kicken?", View.MEMBERS, View.MEMBERS,
                        () -> m.kick(player, sel));
            });
            set(ACT_B, btn(Icon.CROWN, "<gold><bold>Zum Leiter machen", "<gray>Übergibt die Leitung an <yellow>" + sn + "<gray>."), t -> {
                click();
                confirm("Willst du die Leitung wirklich an <yellow>" + sn + " <gray>übergeben?", View.MEMBERS, View.MEMBERS,
                        () -> m.transferLeader(player, sel));
            });
        }
    }

    // ------------------------------------------------------------ ALLIES

    private void buildAllies() {
        ClanManager m = plugin.clans();
        Clan me = clan();
        if (me == null) return;
        boolean lead = leader();

        List<AllyEntry> list = new ArrayList<>();
        for (String k : me.allyRequests) {
            Clan o = m.byKey(k);
            if (o != null) list.add(new AllyEntry(o, false));
        }
        for (String k : me.allies) {
            Clan o = m.byKey(k);
            if (o != null) list.add(new AllyEntry(o, true));
        }

        if (list.isEmpty()) emptyHint("Keine Allianzen oder Anfragen");
        List<AllyEntry> sub = slice(list);
        for (int i = 0; i < sub.size(); i++) {
            AllyEntry e = sub.get(i);
            ItemStack it = e.ally()
                    ? clanItem(e.clan())
                    : clanItem(e.clan(), "", "<yellow>✉ Allianzanfrage an euch");
            entry(i, it, e.clan().key);
        }

        if (!lead) return;
        set(ACT_A, btn(Icon.PLUS, "<aqua><bold>Allianz anfragen", "<gray>Wähle einen Clan aus.", "", "<yellow>» Klicken"), t -> {
            click();
            go(View.ALLY_PICK);
        });
        Clan sel = m.byKey(selected);
        if (sel == null) return;
        if (me.allies.contains(sel.key)) {
            set(ACT_B, btn(Icon.CROSS, "<red><bold>Allianz beenden", "<gray>Beendet die Allianz mit <gold>" + sel.name + "<gray>."), t -> {
                click();
                confirm("Allianz mit <gold>" + sel.name + " <gray>wirklich beenden?", View.ALLIES, View.ALLIES,
                        () -> m.allyRemove(player, sel.key));
            });
        } else if (me.allyRequests.contains(sel.key)) {
            set(ACT_B, btn(Icon.CHECK, "<green><bold>Annehmen", "<gray>Allianz mit <gold>" + sel.name + " <gray>schließen."), t -> {
                result(m.allyAccept(player, sel.key));
                selected = null;
                render();
            });
            set(ACT_C, btn(Icon.CROSS, "<red><bold>Ablehnen", "<gray>Anfrage von <gold>" + sel.name + " <gray>ablehnen."), t -> {
                result(m.allyDecline(player, sel.key));
                selected = null;
                render();
            });
        }
    }

    private void buildAllyPick() {
        ClanManager m = plugin.clans();
        Clan me = clan();
        if (me == null) return;
        List<Clan> list = new ArrayList<>();
        for (Clan c : m.all()) {
            if (c != me && !me.allies.contains(c.key)) list.add(c);
        }
        list.sort(Comparator.comparing(c -> c.name.toLowerCase(Locale.ROOT)));
        back(View.ALLIES);
        if (list.isEmpty()) {
            emptyHint("Keine Clans verfügbar");
            return;
        }
        List<Clan> sub = slice(list);
        for (int i = 0; i < sub.size(); i++) {
            Clan c = sub.get(i);
            ItemStack it = c.allyRequests.contains(me.key)
                    ? clanItem(c, "", "<yellow>✉ Anfrage bereits gesendet")
                    : clanItem(c);
            entry(i, it, c.key);
        }
        Clan sel = m.byKey(selected);
        if (sel != null) {
            set(ACT_B, btn(Icon.CHECK, "<green><bold>Anfrage senden", "<gray>An <gold>" + sel.name + "<gray>."), t -> {
                result(m.allyInvite(player, sel.key));
                render();
            });
        }
    }

    // ------------------------------------------------------------ INVITE_PICK

    private void buildInvitePick() {
        ClanManager m = plugin.clans();
        back(View.OVERVIEW);
        List<Player> list = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.equals(player) && m.getClan(p) == null) list.add(p);
        }
        list.sort(Comparator.comparing(p -> p.getName().toLowerCase(Locale.ROOT)));
        if (list.isEmpty()) {
            emptyHint("Keine Spieler ohne Clan online");
            return;
        }
        List<Player> sub = slice(list);
        Clan me = clan();
        for (int i = 0; i < sub.size(); i++) {
            Player p = sub.get(i);
            boolean invited = me != null && me.invites.containsKey(p.getUniqueId());
            ItemStack head = Items.head(p, "<green>" + Text.esc(p.getName()),
                    "<gray>Hat keinen Clan",
                    invited ? "<yellow>✉ Bereits eingeladen" : "<dark_gray>Noch nicht eingeladen");
            entry(i, head, p.getUniqueId().toString());
        }
        UUID sel = uuid(selected);
        Player target = sel == null ? null : Bukkit.getPlayer(sel);
        if (target != null) {
            set(ACT_B, btn(Icon.INVITE, "<green><bold>Einladen", "<gray>Lädt <yellow>" + Text.esc(target.getName()) + " <gray>ein."), t -> {
                result(m.invite(player, target));
                render();
            });
        }
    }

    // ------------------------------------------------------------ COLOR

    private void buildColor() {
        ClanManager m = plugin.clans();
        Clan c = clan();
        if (c == null) return;
        boolean vip = m.hasVip(player);
        boolean needsLeader = plugin.getConfig().getBoolean("color-needs-leader", true);
        boolean can = vip && (!needsLeader || leader());

        if (!can) {
            String why = !vip ? "Die Clanfarbe ist nur für <gold>VIP-Booster <gray>verfügbar."
                    : "Nur der <gold>Clanleiter <gray>kann die Farbe ändern.";
            set(22, btn(Icon.LOCK, "<red><bold>Gesperrt", "<gray>" + why), null);
            set(31, Items.named(custom, Icon.PREVIEW, c.tag, "<gray>Aktueller Clan-Tag"), null);
            return;
        }

        ClanColor[] cols = ClanColor.values();
        ClanColor sel1 = ClanColor.parse(c1);
        ClanColor sel2 = ClanColor.parse(c2);
        for (int i = 0; i < cols.length; i++) {
            ClanColor col = cols[i];
            int slot = (1 + i / 5) * 9 + 2 + (i % 5);
            ItemStack it = Items.swatch(custom, col, "<color:" + col.hex() + "><bold>" + col.german(),
                    "<dark_gray>" + col.hex(), "",
                    "<gray>Linksklick: <white>Farbe 1", "<gray>Rechtsklick: <white>Farbe 2 (Verlauf)");
            set(slot, it, t -> {
                pick();
                if (t.isRightClick()) c2 = col.id();
                else c1 = col.id();
                render();
            });
            if (col == sel1 && col == sel2) marked.put(slot, "Farbe 1 + Farbe 2");
            else if (col == sel1) marked.put(slot, "Farbe 1");
            else if (col == sel2) marked.put(slot, "Farbe 2");
        }

        set(38, btn(Icon.RESET, "<red><bold>Zurücksetzen", "<gray>Standardfarbe wiederherstellen."), t -> {
            click();
            boolean ok = m.setColor(player, "reset", null);
            if (ok) {
                c1 = "";
                c2 = "";
            }
            result(ok);
            render();
        });
        set(39, btn(Icon.HEX, "<aqua><bold>Eigene Farbe (Hex)",
                "<gray>Gib im Chat Farben ein, z.B.:", "<white>#ff8800 #ffee00", "<white>red blue", "", "<yellow>» Klicken"), t -> {
            click();
            prompt("<gray>Schreibe <aqua>Farbe 1 <gray>und optional <aqua>Farbe 2 <gray>(z.B. <white>#ff8800 #ffee00<gray>).", text -> {
                String[] parts = text.trim().split("\\s+");
                m.setColor(player, parts[0], parts.length > 1 ? parts[1] : null);
                plugin.gui().open(player, View.COLOR);
            }, View.COLOR);
        });
        set(40, Items.named(custom, Icon.PREVIEW, m.buildTag(c.name, c1, c2),
                "<gray>Vorschau deines Clan-Tags", "",
                "<gray>Farbe 1: <white>" + (c1.isEmpty() ? "Standard" : Text.esc(c1)),
                "<gray>Farbe 2: <white>" + (c2.isEmpty() ? "–" : Text.esc(c2))), null);
        if (!c2.isEmpty()) {
            set(41, btn(Icon.CROSS, "<gray><bold>Farbe 2 entfernen", "<gray>Nur eine Farbe, kein Verlauf."), t -> {
                click();
                c2 = "";
                render();
            });
        }
        set(42, btn(Icon.CHECK, "<green><bold>Übernehmen", "<gray>Speichert die gewählte Farbe.", "", "<yellow>» Klicken"), t -> {
            if (c1.isEmpty()) {
                plugin.err(player, "Wähle zuerst Farbe 1 aus.");
                result(false);
                return;
            }
            result(m.setColor(player, c1, c2.isEmpty() ? null : c2));
            render();
        });
    }

    // ------------------------------------------------------------ CONFIRM

    private void confirm(String text, View back, View next, Supplier<Boolean> act) {
        confirmText = text;
        confirmBack = back;
        confirmNext = next;
        confirmAct = act;
        view = View.CONFIRM;
        page = 0;
        selected = null;
        waveStart = frame;
        render();
    }

    private void buildConfirm() {
        set(22, btn(Icon.INFO, "<gold><bold>Bestätigung", "<gray>" + confirmText), null);
        set(30, btn(Icon.CHECK, "<green><bold>Ja", "<gray>Bestätigen"), t -> {
            Supplier<Boolean> act = confirmAct;
            confirmAct = null;
            boolean ok = act != null && act.get();
            result(ok);
            View next = ok ? confirmNext : confirmBack;
            later(() -> plugin.gui().open(player, next));
        });
        set(32, btn(Icon.CROSS, "<red><bold>Nein", "<gray>Abbrechen"), t -> {
            click();
            confirmAct = null;
            go(confirmBack);
        });
    }
}
