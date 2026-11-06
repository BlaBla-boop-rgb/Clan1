#!/usr/bin/env python3
"""
Erzeugt das Resource Pack (ClansPack.zip) für das Clans-Plugin.

Alle Texturen werden hier per Code gezeichnet (16x16 Pixel-Art). Du kannst sie
danach in assets/clans/textures/item/ durch eigene Texturen ersetzen, solange die
Dateinamen gleich bleiben.

Aufruf:  python3 tools/make_pack.py  [ausgabe.zip]
"""
import io
import json
import sys
import zipfile
from PIL import Image, ImageDraw

S = 16
NS = "clans"

# --- Farben ------------------------------------------------------------
WHITE = (240, 240, 245, 255)
GREY = (150, 150, 162, 255)
DGREY = (84, 84, 96, 255)
GOLD = (255, 190, 40, 255)
GOLD2 = (205, 135, 20, 255)
RED = (232, 52, 62, 255)
GREEN = (72, 214, 92, 255)
BLUE = (84, 140, 240, 255)
LBLUE = (130, 180, 255, 255)
BROWN = (146, 92, 52, 255)
CYAN = (96, 224, 244, 255)
SKIN = (242, 204, 164, 255)
BLACK = (16, 16, 20, 255)
OUTLINE = (12, 12, 16, 255)

ORANGE = (240, 160, 32, 255)

# id, r, g, b  (gleich wie ClanColor.java)
COLORS = [
    ("red", 255, 85, 85), ("darkred", 170, 0, 0), ("orange", 255, 170, 0),
    ("yellow", 255, 255, 85), ("green", 85, 255, 85), ("darkgreen", 0, 170, 0),
    ("aqua", 85, 255, 255), ("darkaqua", 0, 170, 170), ("blue", 85, 85, 255),
    ("darkblue", 0, 0, 170), ("pink", 255, 85, 255), ("purple", 170, 0, 170),
    ("white", 255, 255, 255), ("gray", 170, 170, 170), ("darkgray", 85, 85, 85),
]

files = {}  # pfad -> bytes


def put_png(path, img):
    b = io.BytesIO()
    img.save(b, "PNG")
    files[path] = b.getvalue()


def put_json(path, obj):
    files[path] = json.dumps(obj, indent=2).encode("utf-8")


def clamp(v):
    return max(0, min(255, int(v)))


def shade(c, f):
    return (clamp(c[0] * f), clamp(c[1] * f), clamp(c[2] * f), 255)


def new():
    return Image.new("RGBA", (S, S), (0, 0, 0, 0))


def outline(img):
    """Dunkler 1px-Rand um die gezeichnete Form."""
    src = img.load()
    out = img.copy()
    o = out.load()
    for y in range(S):
        for x in range(S):
            if src[x, y][3] != 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < S and 0 <= ny < S and src[nx, ny][3] != 0:
                    o[x, y] = OUTLINE
                    break
    return out


# --- Icons (gezeichnet im Bereich 3..12) --------------------------------
def i_overview(d):
    d.polygon([(3, 3), (12, 3), (12, 8), (8, 13), (7, 13), (3, 8)], fill=BLUE)
    d.polygon([(4, 4), (7, 4), (7, 11), (4, 8)], fill=LBLUE)
    d.rectangle((7, 6, 8, 7), fill=GOLD)


def i_members(d):
    d.ellipse((3, 3, 7, 7), fill=SKIN)
    d.rectangle((2, 8, 8, 12), fill=BLUE)
    d.ellipse((9, 4, 13, 8), fill=shade(SKIN, 0.85))
    d.rectangle((9, 9, 13, 12), fill=shade(BLUE, 0.7))


def i_allies(d):
    d.ellipse((4, 3, 11, 10), fill=GOLD)
    d.rectangle((3, 9, 12, 11), fill=GOLD)
    d.rectangle((7, 2, 8, 3), fill=GOLD2)
    d.rectangle((7, 12, 8, 13), fill=GOLD2)


def i_color(d):
    d.ellipse((2, 3, 13, 12), fill=(196, 138, 78, 255))
    d.rectangle((4, 5, 5, 6), fill=RED)
    d.rectangle((7, 4, 8, 5), fill=GREEN)
    d.rectangle((10, 5, 11, 6), fill=BLUE)
    d.rectangle((10, 8, 11, 9), fill=GOLD)


def i_clans(d):
    d.rectangle((3, 3, 12, 12), fill=(158, 62, 52, 255))
    d.rectangle((5, 4, 11, 11), fill=WHITE)
    d.line((6, 6, 10, 6), fill=GREY)
    d.line((6, 8, 10, 8), fill=GREY)
    d.line((6, 10, 9, 10), fill=GREY)


def i_invites(d):
    d.rectangle((2, 4, 13, 11), fill=WHITE)
    d.line((2, 4, 7, 8), fill=GREY)
    d.line((13, 4, 8, 8), fill=GREY)
    d.rectangle((11, 2, 12, 5), fill=GREEN)
    d.rectangle((10, 3, 13, 4), fill=GREEN)


def i_home(d):
    d.rectangle((4, 3, 5, 13), fill=BROWN)
    d.polygon([(6, 3), (13, 5), (6, 8)], fill=RED)


def i_plus(d):
    d.rectangle((7, 3, 8, 12), fill=GREEN)
    d.rectangle((3, 7, 12, 8), fill=GREEN)


def i_check(d):
    d.line((3, 8, 6, 11), fill=GREEN, width=2)
    d.line((6, 11, 12, 4), fill=GREEN, width=2)


def i_cross(d):
    d.line((4, 4, 11, 11), fill=RED, width=2)
    d.line((11, 4, 4, 11), fill=RED, width=2)


def i_kick(d):
    d.ellipse((3, 3, 8, 8), fill=SKIN)
    d.rectangle((2, 9, 8, 12), fill=BLUE)
    d.line((10, 9, 13, 12), fill=RED, width=2)
    d.line((13, 9, 10, 12), fill=RED, width=2)


def i_crown(d):
    d.polygon([(3, 10), (3, 5), (6, 8), (8, 4), (10, 8), (13, 5), (13, 10)], fill=GOLD)
    d.rectangle((3, 10, 13, 12), fill=GOLD2)
    d.point((8, 7), fill=RED)


def i_chat(d):
    d.rectangle((3, 3, 12, 9), fill=WHITE)
    d.polygon([(5, 9), (5, 12), (8, 9)], fill=WHITE)
    for x in (5, 8, 10):
        d.point((x, 6), fill=DGREY)


def i_invite(d):
    d.ellipse((3, 3, 7, 7), fill=SKIN)
    d.rectangle((2, 8, 8, 12), fill=BLUE)
    d.rectangle((11, 6, 12, 12), fill=GREEN)
    d.rectangle((9, 8, 14, 9), fill=GREEN)


def i_tnt(d):
    d.rectangle((3, 4, 12, 12), fill=RED)
    d.rectangle((3, 7, 12, 9), fill=WHITE)
    d.rectangle((7, 2, 8, 4), fill=BROWN)


def i_door(d):
    d.rectangle((3, 3, 9, 13), fill=BROWN)
    d.point((8, 8), fill=GOLD)
    d.line((9, 8, 12, 8), fill=GOLD)
    d.polygon([(11, 6), (14, 8), (11, 10)], fill=GOLD)


def i_up(d):
    d.polygon([(8, 3), (13, 9), (10, 9), (10, 13), (6, 13), (6, 9), (3, 9)], fill=WHITE)


def i_down(d):
    d.polygon([(8, 13), (13, 7), (10, 7), (10, 3), (6, 3), (6, 7), (3, 7)], fill=WHITE)


def i_left(d):
    d.polygon([(3, 8), (9, 3), (9, 6), (13, 6), (13, 10), (9, 10), (9, 13)], fill=WHITE)


def i_reset(d):
    d.ellipse((3, 3, 12, 12), outline=RED, width=2)
    d.line((4, 4, 11, 11), fill=RED, width=2)


def i_hex(d):
    d.line((6, 3, 5, 12), fill=WHITE, width=1)
    d.line((10, 3, 9, 12), fill=WHITE, width=1)
    d.line((3, 6, 12, 6), fill=WHITE, width=1)
    d.line((3, 9, 12, 9), fill=WHITE, width=1)


def i_info(d):
    d.ellipse((3, 3, 12, 12), fill=BLUE)
    d.rectangle((7, 7, 8, 11), fill=WHITE)
    d.rectangle((7, 4, 8, 5), fill=WHITE)


def i_sword(d):
    d.line((5, 11, 12, 4), fill=CYAN, width=2)
    d.line((3, 9, 7, 13), fill=GOLD2, width=2)
    d.line((3, 13, 5, 11), fill=BROWN, width=2)


def i_skull(d):
    d.ellipse((3, 3, 12, 10), fill=WHITE)
    d.rectangle((5, 10, 10, 12), fill=WHITE)
    d.rectangle((5, 6, 6, 7), fill=BLACK)
    d.rectangle((9, 6, 10, 7), fill=BLACK)


def i_kd(d):
    d.rectangle((3, 9, 5, 12), fill=GOLD)
    d.rectangle((7, 6, 9, 12), fill=GREEN)
    d.rectangle((11, 3, 13, 12), fill=CYAN)


def i_pickaxe(d):
    d.line((4, 12, 11, 5), fill=BROWN, width=2)
    d.line((3, 5, 8, 3), fill=GREY, width=2)
    d.line((8, 3, 12, 7), fill=GREY, width=2)


def i_close(d):
    d.line((4, 4, 11, 11), fill=WHITE, width=2)
    d.line((11, 4, 4, 11), fill=WHITE, width=2)


def i_lock(d):
    d.rectangle((4, 7, 11, 12), fill=GREY)
    d.arc((5, 2, 10, 9), 180, 360, fill=GREY, width=2)
    d.point((7, 9), fill=BLACK)


def i_preview(d):
    d.polygon([(3, 5), (10, 5), (13, 8), (10, 11), (3, 11)], fill=WHITE)
    d.rectangle((4, 7, 5, 8), fill=GREY)


def i_logo(d):
    i_crown(d)


# name -> (zeichenfunktion, stil)
ICONS = {
    "overview": (i_overview, "card"), "members": (i_members, "card"), "allies": (i_allies, "card"),
    "color": (i_color, "card"), "clans": (i_clans, "card"), "invites": (i_invites, "card"),
    "home": (i_home, "card"), "plus": (i_plus, "card"), "check": (i_check, "card"),
    "cross": (i_cross, "card"), "kick": (i_kick, "card"), "crown": (i_crown, "card"),
    "chat": (i_chat, "card"), "invite": (i_invite, "card"), "tnt": (i_tnt, "card"),
    "door": (i_door, "card"), "arrow_up": (i_up, "card"), "arrow_down": (i_down, "card"),
    "arrow_left": (i_left, "card"), "reset": (i_reset, "card"), "hex": (i_hex, "card"),
    "info": (i_info, "card"), "sword": (i_sword, "card"), "skull": (i_skull, "card"),
    "kd": (i_kd, "card"), "pickaxe": (i_pickaxe, "card"), "close": (i_close, "red"),
    "lock": (i_lock, "card"), "preview": (i_preview, "card"), "logo": (i_logo, "header"),
}
TAB_ICONS = ["overview", "members", "allies", "color", "clans", "invites", "home"]


# --- Kacheln -----------------------------------------------------------
def solid(c):
    return Image.new("RGBA", (S, S), c)


def base_style(style, sel):
    img = Image.new("RGBA", (S, S))
    d = ImageDraw.Draw(img)
    if style == "card":
        bg, bd, w = ((44, 44, 54, 255), (255, 255, 255, 255), 2) if sel else ((30, 30, 36, 255), (62, 62, 74, 255), 1)
    elif style == "tab":
        bg, bd, w = ((92, 60, 12, 255), (255, 232, 160, 255), 2) if sel else ((28, 28, 34, 255), ORANGE, 1)
    elif style == "red":
        bg, bd, w = ((150, 20, 34, 255), (255, 120, 130, 255), 2) if sel else ((196, 28, 44, 255), (110, 12, 24, 255), 1)
    else:  # header
        bg, bd, w = ((226, 30, 45, 255), (226, 30, 45, 255), 0)
    d.rectangle((0, 0, S - 1, S - 1), fill=bd)
    d.rectangle((w, w, S - 1 - w, S - 1 - w), fill=bg)
    if style == "header":
        d.rectangle((0, 0, S - 1, 0), fill=(250, 96, 108, 255))
        d.rectangle((0, S - 2, S - 1, S - 1), fill=(140, 14, 26, 255))
    return img


def compose(draw_fn, style, sel):
    img = base_style(style, sel)
    art = new()
    draw_fn(ImageDraw.Draw(art))
    art = outline(art)
    img.alpha_composite(art)
    return img


def banner_art(col):
    def f(d):
        c = (col[0], col[1], col[2], 255)
        d.rectangle((3, 2, 4, 13), fill=BROWN)
        d.rectangle((5, 3, 12, 9), fill=c)
        d.polygon([(5, 10), (12, 10), (12, 12), (8, 10)], fill=shade(c, 0.8))
        d.rectangle((5, 3, 12, 4), fill=shade(c, 1.2))
        d.rectangle((5, 8, 12, 9), fill=shade(c, 0.85))
    return f


def swatch_art(col):
    def f(d):
        c = (col[0], col[1], col[2], 255)
        d.rectangle((3, 3, 12, 12), fill=c)
        d.rectangle((3, 3, 12, 4), fill=shade(c, 1.25))
        d.rectangle((3, 11, 12, 12), fill=shade(c, 0.75))
    return f


def tile_png(name, img):
    put_png(f"assets/{NS}/textures/item/{name}.png", img)


def model(name, scale=True):
    m = {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}}
    if scale:
        # 16px -> 18px: Kachel füllt die komplette Slot-Zelle, keine grauen Fugen
        m["display"] = {"gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1.125, 1.125, 1.125]}}
    put_json(f"assets/{NS}/models/item/{name}.json", m)


def item_def(name, conditional):
    if conditional:
        d = {"model": {
            "type": "minecraft:condition",
            "property": "minecraft:custom_model_data",
            "index": 0,
            "on_true": {"type": "minecraft:model", "model": f"{NS}:item/{name}_sel"},
            "on_false": {"type": "minecraft:model", "model": f"{NS}:item/{name}"},
        }}
    else:
        d = {"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}}
    put_json(f"assets/{NS}/items/{name}.json", d)


def emit_selectable(name, draw_fn, style):
    tile_png(name, compose(draw_fn, style, False))
    tile_png(name + "_sel", compose(draw_fn, style, True))
    model(name)
    model(name + "_sel")
    item_def(name, True)


def emit_plain(name, img):
    tile_png(name, img)
    model(name)
    item_def(name, False)


def build():
    # Icon-Karten
    for name, (fn, style) in ICONS.items():
        emit_selectable(name, fn, style)
    # Tab-Buttons (orange Rahmen)
    for name in TAB_ICONS:
        emit_selectable("tab_" + name, ICONS[name][0], "tab")
    # Clanfarben: Banner + Farbfelder
    for cid, r, g, b in COLORS:
        emit_selectable("banner_" + cid, banner_art((r, g, b)), "card")
        emit_selectable("swatch_" + cid, swatch_art((r, g, b)), "card")

    # Hintergrund-Kacheln
    emit_plain("bg", solid((26, 26, 31, 255)))
    emit_plain("foot", solid((14, 14, 18, 255)))
    emit_plain("foot_dim", solid((52, 52, 62, 255)))
    emit_plain("glow_white", solid((240, 240, 245, 255)))
    for cid, r, g, b in COLORS:
        img = solid((r, g, b, 255))
        d = ImageDraw.Draw(img)
        d.rectangle((0, 0, S - 1, 1), fill=shade((r, g, b), 1.25))
        d.rectangle((0, S - 2, S - 1, S - 1), fill=shade((r, g, b), 0.8))
        emit_plain("glow_" + cid, img)

    hdr = base_style("header", False)
    emit_plain("header", hdr)

    track = solid((14, 14, 18, 255))
    ImageDraw.Draw(track).rectangle((6, 0, 9, S - 1), fill=(48, 48, 58, 255))
    emit_plain("scroll_track", track)
    handle = solid((14, 14, 18, 255))
    hd = ImageDraw.Draw(handle)
    hd.rectangle((4, 1, 11, S - 2), fill=ORANGE)
    hd.rectangle((4, 1, 11, 2), fill=(255, 214, 120, 255))
    hd.rectangle((4, S - 3, 11, S - 2), fill=GOLD2)
    emit_plain("scroll_handle", handle)

    # pack.mcmeta (alt: pack_format, ab 1.21.9: min_format/max_format)
    put_json("pack.mcmeta", {"pack": {
        "description": "Clans GUI",
        "pack_format": 46,
        "supported_formats": {"min_inclusive": 46, "max_inclusive": 99},
        "min_format": 46,
        "max_format": 99,
    }})

    logo = Image.new("RGBA", (64, 64), (226, 30, 45, 255))
    big = compose(i_crown, "header", False).resize((48, 48), Image.NEAREST)
    logo.alpha_composite(big, (8, 8))
    put_png("pack.png", logo)


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "ClansPack.zip"
    build()
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for path in sorted(files):
            z.writestr(path, files[path])
    print(f"{len(files)} Dateien -> {out}")


if __name__ == "__main__":
    main()
