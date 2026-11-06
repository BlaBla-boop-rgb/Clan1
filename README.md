# Clans 2.0 (Paper 1.21.11)

Clan-System, das komplett über ein Menü (`/clan`) bedient wird. Mit Custom-Icons aus einem Resource Pack.

## Bauen
Java 21 + Maven: `mvn clean package` -> `target/Clans-2.0.0.jar` in `plugins/`.
Oder einfach das Projekt auf GitHub hochladen: die Action in `.github/workflows/build.yml` baut die JAR.

## Resource Pack (Custom-Icons)
Das Pack steckt in der JAR und liegt nach dem ersten Start unter `plugins/Clans/ClansPack.zip`.
Es muss einmal öffentlich erreichbar sein. Zwei Möglichkeiten (config.yml -> `resource-pack`):

1. **Eigener Link**: ClansPack.zip irgendwo hochladen (GitHub-Release, Webspace) und den Direktlink bei `url` eintragen.
2. **Plugin hostet selbst**: `builtin-host.enabled: true`, `address` = öffentliche IP/Domain, `port` freigeben.

Danach `/clan reload` bzw. Server neustarten. Spieler bekommen das Pack beim Join.
Wer das Pack ablehnt, sieht dasselbe Menü mit normalen Minecraft-Items (mode `auto`).

## Eigene Icons
`python3 tools/make_pack.py src/main/resources/ClansPack.zip` erzeugt das Pack neu.
Alle Texturen sind 16x16 und liegen in `assets/clans/textures/item/` (Namen siehe `make_pack.py`).
Du kannst sie in der Zip durch eigene PNGs ersetzen, solange die Dateinamen gleich bleiben.

## Befehle
- `/clan` - Menü (alles andere läuft darüber)
- `/cc <Nachricht>` - Clan-Chat
- `/clan pack` - Icon-Pack erneut senden
- `/clan reload` - Config neu laden (`clans.admin`)

PlaceholderAPI: `%clan_name%`, `%clan_tab%`, `%clan_tag%`, `%clan_kills%`, `%clan_deaths%`, `%clan_blocks%`, `%clan_members%`, `%clan_leader%`
