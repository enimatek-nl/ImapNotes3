#!/usr/bin/env python3
# ImapNotesX - icon artwork generator.
#
# Rasterizes art/icons/imapnotesx_master.svg (a deliberately small subset of SVG:
# rect / circle / path with M L H V Q A Z commands, groups with translate/rotate,
# solid fill and stroke) into every bitmap the project needs:
#
#   ImapNotesX/src/main/res/mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher.png
#   ImapNotesX/src/main/res/mipmap-{...}/ic_launcher_round.png
#   fastlane/metadata/android/<locale>/images/icon.png
#   fastlane/metadata/android/en-US/images/featureGraphic.png
#
# The adaptive-icon layers for API 26+ are vector drawables in res/drawable and
# are NOT generated here; keep art/icons/*.svg and those vectors in sync.
#
# Usage:  uv run --with pillow python3 scripts/make_icons.py [--outdir DIR]
#         (plain `python3 scripts/make_icons.py` works if Pillow is installed)

import argparse
import math
import os
import re
import xml.etree.ElementTree as ET
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MASTER = os.path.join(ROOT, "art", "icons", "imapnotesx_master.svg")
VB = 108.0                      # master viewBox is 108x108
SS = 4                          # supersampling factor (poor man's antialiasing)

# ---------------------------------------------------------------- SVG parsing

NUM = re.compile(r"[-+]?[0-9]*\.?[0-9]+(?:[eE][-+]?[0-9]+)?")


def numbers(text):
    return [float(n) for n in NUM.findall(text)]


def mul(n, o):
    """Affine product N.O with the SVG layout [a,b,c,d,e,f]."""
    return [
        n[0] * o[0] + n[2] * o[1],
        n[1] * o[0] + n[3] * o[1],
        n[0] * o[2] + n[2] * o[3],
        n[1] * o[2] + n[3] * o[3],
        n[0] * o[4] + n[2] * o[5] + n[4],
        n[1] * o[4] + n[3] * o[5] + n[5],
    ]


def parse_transform(text):
    """translate(x[,y]) / rotate(deg) / scale(s) -> 2x3 matrix (a,b,c,d,e,f)."""
    m = [1.0, 0.0, 0.0, 1.0, 0.0, 0.0]

    for name, args in re.findall(r"(\w+)\s*\(([^)]*)\)", text or ""):
        v = numbers(args)
        if name == "translate":
            t = [1, 0, 0, 1, v[0], v[1] if len(v) > 1 else 0.0]
        elif name == "rotate":
            a = math.radians(v[0])
            c, s = math.cos(a), math.sin(a)
            t = [c, s, -s, c, 0, 0]
            if len(v) == 3:  # rotate(deg, cx, cy)
                cx, cy = v[1], v[2]
                t = mul([1, 0, 0, 1, cx, cy], mul(t, [1, 0, 0, 1, -cx, -cy]))
        elif name == "scale":
            t = [v[0], 0, 0, v[1] if len(v) > 1 else v[0], 0, 0]
        else:
            raise SystemExit("unsupported transform: " + name)
        m = mul(m, t)
    return m


def apply(m, x, y):
    return (m[0] * x + m[2] * y + m[4], m[1] * x + m[3] * y + m[5])


def arc_to_center(x1, y1, x2, y2, rx, ry, rot_deg, large, sweep):
    """Endpoint parameterisation -> centre parameterisation (SVG spec F.6.5)."""
    phi = math.radians(rot_deg)
    cp, sp = math.cos(phi), math.sin(phi)
    x1p = cp * (x1 - x2) / 2 + sp * (y1 - y2) / 2
    y1p = -sp * (x1 - x2) / 2 + cp * (y1 - y2) / 2
    rx, ry = abs(rx), abs(ry)
    lam = (x1p / rx) ** 2 + (y1p / ry) ** 2
    if lam > 1:
        rx *= math.sqrt(lam)
        ry *= math.sqrt(lam)
    num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
    den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
    co = 0.0 if den == 0 else math.sqrt(max(0.0, num / den))
    if large == sweep:
        co = -co
    cxp = co * rx * y1p / ry
    cyp = -co * ry * x1p / rx
    cx = cp * cxp - sp * cyp + (x1 + x2) / 2
    cy = sp * cxp + cp * cyp + (y1 + y2) / 2
    ang = lambda ux, uy: math.atan2(uy, ux)
    th1 = ang((x1p - cxp) / rx, (y1p - cyp) / ry)
    dtheta = ang((-x1p - cxp) / rx, (-y1p - cyp) / ry) - th1
    if not sweep and dtheta > 0:
        dtheta -= 2 * math.pi
    elif sweep and dtheta < 0:
        dtheta += 2 * math.pi
    return cx, cy, rx, ry, phi, th1, dtheta


def path_subpaths(d):
    """Flatten a path 'd' into subpaths of points in local user units."""
    subs, cur = [], []
    x = y = 0.0
    sx = sy = 0.0
    px = py = 0.0  # last control point for smooth curves (unused: S/T not used)
    tokens = re.findall(r"[MmLlHhVvQqAaZz]|[-+]?[0-9]*\.?[0-9]+(?:[eE][-+]?[0-9]+)?", d)
    i = 0
    cmd = None
    while i < len(tokens):
        t = tokens[i]
        if not re.match(r"[-+0-9.]", t):
            cmd, i = t, i + 1
            if cmd in "Zz":
                if cur:
                    subs.append(cur)
                cur = []
                x, y = sx, sy
            continue
        def num():
            nonlocal i
            v = float(tokens[i])
            i += 1
            return v
        if cmd in "Mm":
            nx, ny = num(), num()
            if cur:
                subs.append(cur)
            x += nx if cmd == "m" else 0
            y += ny if cmd == "m" else 0
            if cmd == "M":
                x, y = nx, ny
            cur = [(x, y)]
            sx, sy = x, y
            cmd = "l" if cmd == "m" else "L"
        elif cmd in "Ll":
            nx, ny = num(), num()
            x += nx if cmd == "l" else 0
            y += ny if cmd == "l" else 0
            if cmd == "L":
                x, y = nx, ny
            cur.append((x, y))
        elif cmd in "Hh":
            n = num()
            x += n if cmd == "h" else 0
            if cmd == "H":
                x = n
            cur.append((x, y))
        elif cmd in "Vv":
            n = num()
            y += n if cmd == "v" else 0
            if cmd == "V":
                y = n
            cur.append((x, y))
        elif cmd in "Qq":
            cx1, cy1, nx, ny = num(), num(), num(), num()
            if cmd == "q":
                cx1 += x
                cy1 += y
                nx += x
                ny += y
            steps = 24
            for s in range(1, steps + 1):
                u = s / steps
                xu = (1 - u) ** 2 * x + 2 * (1 - u) * u * cx1 + u * u * nx
                yu = (1 - u) ** 2 * y + 2 * (1 - u) * u * cy1 + u * u * ny
                cur.append((xu, yu))
            x, y = nx, ny
        elif cmd in "Aa":
            rx, ry, rot, large, sweep = num(), num(), num(), int(num()), int(num())
            nx, ny = num(), num()
            if cmd == "a":
                nx += x
                ny += y
            cx, cy, rx, ry, phi, th1, dth = arc_to_center(
                x, y, nx, ny, rx, ry, rot, large, sweep
            )
            steps = max(8, int(abs(math.degrees(dth)) / 4) + 1)
            for s in range(1, steps + 1):
                th = th1 + dth * s / steps
                ex = rx * math.cos(th)
                ey = ry * math.sin(th)
                cur.append((cx + math.cos(phi) * ex - math.sin(phi) * ey,
                            cy + math.sin(phi) * ex + math.cos(phi) * ey))
            x, y = nx, ny
        else:
            raise SystemExit("unsupported path command " + str(cmd))
        px = py = 0.0
    if cur:
        subs.append(cur)
    return subs


def collect(node, matrix, out):
    tag = node.tag.split("}")[-1]
    if tag == "g":
        m = parse_transform(node.get("transform", ""))
        mm = mul(matrix, m)
        for child in node:
            collect(child, mm, out)
        return
    if tag not in ("rect", "circle", "path"):
        return
    fill = node.get("fill", "#000000")
    stroke = node.get("stroke")
    sw = float(node.get("stroke-width", 1))
    subs = []
    if tag == "rect":
        x, y = float(node.get("x", 0)), float(node.get("y", 0))
        w, h = float(node.get("width")), float(node.get("height"))
        subs = [[(x, y), (x + w, y), (x + w, y + h), (x, y + h)]]
    elif tag == "circle":
        cx, cy, r = (float(node.get(k)) for k in ("cx", "cy", "r"))
        subs = [[(cx + r * math.cos(a / 60 * 2 * math.pi),
                  cy + r * math.sin(a / 60 * 2 * math.pi)) for a in range(61)]]
    else:
        subs = path_subpaths(node.get("d", ""))
    out.append(([[apply(matrix, px, py) for px, py in s] for s in subs], fill, stroke, sw))


def load_layers(path=MASTER):
    root = ET.parse(path).getroot()
    layers = []
    for child in root:
        tag = child.tag.split("}")[-1]
        if tag == "rect" and child.get("id") == "background":
            subs = [[(0, 0), (VB, 0), (VB, VB), (0, VB)]]
            layers.append(("background", subs, child.get("fill")))
        elif tag == "g" and child.get("id") == "foreground":
            shapes = []
            collect(child, [1, 0, 0, 1, 0, 0], shapes)
            layers.append(("foreground", shapes, None))
    return layers


# ------------------------------------------------------------------ rendering

def render(shape_list, scale, canvas, bg=None):
    """Draw parsed shapes onto an RGBA image (already sized/scaled)."""
    img = Image.new("RGBA", (int(VB * scale * SS), int(VB * scale * SS)), bg)
    draw = ImageDraw.Draw(img)
    for subs, fill, stroke, sw in shape_list:
        pts = [[(p[0] * scale * SS, p[1] * scale * SS) for p in s] for s in subs]
        if fill and fill != "none":
            for p in pts:
                draw.polygon(p, fill=fill)
        if stroke and stroke != "none" and sw > 0:
            for p in pts:
                draw.line(p + [p[0]], fill=stroke, width=max(1, int(round(sw * scale * SS))))
    if SS > 1:
        img = img.resize((int(VB * scale), int(VB * scale)), Image.LANCZOS)
    return img


def rounded_square_mask(size, radius_frac):
    r = int(size * radius_frac)
    m = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(m)
    d.rounded_rectangle([0, 0, size - 1, size - 1], radius=r, fill=255)
    return m


def circle_mask(size):
    m = Image.new("L", (size, size), 0)
    ImageDraw.Draw(m).ellipse([0, 0, size - 1, size - 1], fill=255)
    return m


def find_font(size):
    for path in ("/System/Library/Fonts/Helvetica.ttc",
                 "/System/Library/Fonts/Supplemental/Helvetica.ttc",
                 "/System/Library/Fonts/SFNSText.ttf",
                 "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"):
        if os.path.exists(path):
            try:
                return ImageFont.truetype(path, size)
            except Exception:
                pass
    return ImageFont.load_default()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--outdir", default=ROOT, help="write into a copy of the tree (default: repo)")
    args = ap.parse_args()
    out = args.outdir

    layers = load_layers()
    bg_color = None
    fg_shapes = None
    for name, data, color in layers:
        if name == "background":
            bg_color, bg_shape = color, data
        else:
            fg_shapes = data

    res = os.path.join(out, "ImapNotesX", "src", "main", "res")
    densities = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for dpi, px in densities.items():
        scale = px / VB
        square = render(fg_shapes, scale, None, bg=bg_color).convert("RGBA")
        bg_img = Image.new("RGBA", square.size, bg_color)
        full = Image.alpha_composite(bg_img, square)
        for fname, mask in (("ic_launcher.png", rounded_square_mask(px, 0.21)),
                            ("ic_launcher_round.png", circle_mask(px))):
            img = full.copy()
            img.putalpha(mask)
            d = os.path.join(res, "mipmap-%s" % dpi)
            os.makedirs(d, exist_ok=True)
            img.save(os.path.join(d, fname))
        print("mipmap-%s %spx" % (dpi, px))

    # ---- store artwork
    icon = render(fg_shapes, 512 / VB, None, bg=bg_color)
    bg_img = Image.new("RGBA", (512, 512), bg_color)
    icon = Image.alpha_composite(bg_img, icon)
    icon.putalpha(rounded_square_mask(512, 0.21))
    for loc in sorted(os.listdir(os.path.join(ROOT, "fastlane", "metadata", "android"))):
        if loc in ("en-US", "de", "es-ES", "fr-FR", "it-IT", "ru-RU", "uk", "zh-SIMPLIFIED"):
            d = os.path.join(out, "fastlane", "metadata", "android", loc, "images")
            os.makedirs(d, exist_ok=True)
            icon.save(os.path.join(d, "icon.png"))
            print("fastlane icon.png (%s)" % loc)

    # feature graphic 1024x500
    W, H = 1024, 500
    fg = Image.new("RGBA", (W, H), bg_color)
    d = ImageDraw.Draw(fg)
    mark = render(fg_shapes, 400 / VB, None, bg=None)
    bgm = Image.new("RGBA", (400, 400), bg_color)
    mark = Image.alpha_composite(bgm, mark)
    fg.alpha_composite(mark, (52, 50))

    tx, maxw = 500, W - 500 - 40

    def fit(text, start):
        size = start
        while size > 10:
            f = find_font(size)
            if d.textlength(text, font=f) <= maxw:
                return f
            size -= 2
        return find_font(10)

    title = "ImapNotesX"
    tagline = "notes in your IMAP mailbox"
    f1 = fit(title, 96)
    f2 = fit(tagline, 44)
    d.text((tx, 165), title, font=f1, fill="#2B2118")
    d.text((tx, 275), tagline, font=f2, fill="#5C4A28")
    fg.save(os.path.join(out, "fastlane", "metadata", "android", "en-US", "images",
                         "featureGraphic.png"))
    print("featureGraphic.png")


if __name__ == "__main__":
    main()
