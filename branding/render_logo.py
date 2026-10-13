"""Растеризует логотип PLNA Smart Map (геометрия совпадает с plna-smart-map-logo.svg)."""
import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw

SS = 4  # суперсэмплинг


def cubic(p0, p1, p2, p3, n=40):
    pts = []
    for i in range(n + 1):
        t = i / n
        u = 1 - t
        pts.append((
            u**3 * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t**3 * p3[0],
            u**3 * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t**3 * p3[1],
        ))
    return pts


def pin_polygon():
    pts = []
    pts += cubic((54, 24), (65, 24), (74, 33), (74, 44))
    pts += cubic((74, 44), (74, 58), (54, 78), (54, 78))
    pts += cubic((54, 78), (54, 78), (34, 58), (34, 44))
    pts += cubic((34, 44), (34, 33), (43, 24), (54, 24))
    return pts


def gradient(size, c0, c1, box):
    """Линейный градиент вдоль вектора box=(x1,y1,x2,y2) в координатах 108."""
    w = h = size
    x1, y1, x2, y2 = (v * size / 108 for v in box)
    dx, dy = x2 - x1, y2 - y1
    ln = dx * dx + dy * dy
    img = Image.new("RGBA", (w, h))
    px = img.load()
    for y in range(h):
        for x in range(w):
            t = max(0.0, min(1.0, ((x - x1) * dx + (y - y1) * dy) / ln))
            px[x, y] = tuple(int(c0[i] + (c1[i] - c0[i]) * t) for i in range(3)) + (255,)
    return img


def render(size, with_bg=True, mono=False, scale=1.0):
    S = size * SS
    k = S / 108

    def tr(p):
        cx = cy = 54
        return ((cx + (p[0] - cx) * scale) * k, (cy + (p[1] - cy) * scale) * k)

    out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    if with_bg:
        bg = gradient(S, (0x8B, 0x5C, 0xF6), (0x2E, 0xC4, 0xF3), (0, 0, 108, 108))
        mask = Image.new("L", (S, S), 0)
        ImageDraw.Draw(mask).rounded_rectangle((0, 0, S - 1, S - 1), radius=24 * k, fill=255)
        out.paste(bg, (0, 0), mask)

    layer = Image.new("L", (S, S), 0)
    d = ImageDraw.Draw(layer)
    r = 1.9 * scale * k
    for cx, cy in [(28, 84), (34.5, 83), (41, 82)]:
        x, y = tr((cx, cy))
        d.ellipse((x - r, y - r, x + r, y + r), fill=int(255 * 0.7))
    ex, ey = tr((54, 80.5))
    d.ellipse((ex - 9 * scale * k, ey - 2.6 * scale * k, ex + 9 * scale * k, ey + 2.6 * scale * k),
              fill=int(255 * 0.35))
    d.polygon([tr(p) for p in pin_polygon()], fill=255)

    check = Image.new("L", (S, S), 0)
    cd = ImageDraw.Draw(check)
    pts = [tr(p) for p in [(46, 44), (51.5, 49.5), (62, 39)]]
    wdt = 4.6 * scale * k
    cd.line(pts, fill=255, width=int(wdt), joint="curve")
    for x, y in (pts[0], pts[-1]):
        cd.ellipse((x - wdt / 2, y - wdt / 2, x + wdt / 2, y + wdt / 2), fill=255)

    white = Image.new("RGBA", (S, S), (255, 255, 255, 255))
    if mono:
        out.paste(white, (0, 0), ImageChops.subtract(layer, check))
    else:
        out.paste(white, (0, 0), layer)
        cg = gradient(S, (0x7C, 0x5C, 0xF6), (0x3A, 0xA8, 0xF0),
                      (46 * scale + 54 * (1 - scale), 38, 64, 52))
        out.paste(cg, (0, 0), check)
    return out.resize((size, size), Image.LANCZOS)


if __name__ == "__main__":
    here = Path(__file__).parent
    render(512).save(here / "plna-smart-map-logo.png")
    res = Path(sys.argv[1]) if len(sys.argv) > 1 else None
    if res:
        for name, px in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
            d = res / f"mipmap-{name}"
            icon = render(px, scale=1.25)
            icon.save(d / "ic_launcher.webp", "WEBP", lossless=True)
            round_mask = Image.new("L", (px * SS, px * SS), 0)
            ImageDraw.Draw(round_mask).ellipse((0, 0, px * SS - 1, px * SS - 1), fill=255)
            round_mask = round_mask.resize((px, px), Image.LANCZOS)
            rnd = Image.new("RGBA", (px, px), (0, 0, 0, 0))
            rnd.paste(render(px, scale=1.25), (0, 0), round_mask)
            rnd.save(d / "ic_launcher_round.webp", "WEBP", lossless=True)
