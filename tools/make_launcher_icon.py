"""Builds the launcher icon resources from photo.webp.

The source art is a flat graphic on a white background. The outer white is removed with a
flood fill started at the border, which keeps the white bands and dots that live inside the
cylinder. The artwork is then centred and scaled onto the adaptive-icon foreground canvas
(108dp, artwork kept inside the 72dp safe zone) and onto the legacy square/round icons.

Run from anywhere:  python tools/make_launcher_icon.py
"""
import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCE = os.path.join(ROOT, "photo.webp")
RES = os.path.join(ROOT, "app", "src", "main", "res")

# density name -> scale factor against the 48dp legacy icon / 108dp adaptive canvas
DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}
WHITE = (255, 255, 255, 255)
CLEAR = (0, 0, 0, 0)

ADAPTIVE_DP = 108  # adaptive icon layer size
LEGACY_DP = 48  # legacy launcher icon size
FOREGROUND_RATIO = 0.64  # artwork size inside the adaptive safe zone
SQUARE_RATIO = 0.78  # artwork size on the legacy square icon
ROUND_RATIO = 0.68  # artwork size on the legacy round icon

ADAPTIVE_XML = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
</adaptive-icon>
"""


def cut_out_background(image):
    """Key out the white area connected to the border, keeping interior white opaque."""
    image = image.convert("RGBA")
    for corner in ((0, 0), (image.width - 1, 0), (0, image.height - 1), (image.width - 1, image.height - 1)):
        ImageDraw.floodfill(image, corner, CLEAR, thresh=40)
    return image.crop(image.getchannel("A").getbbox())


def paste_centred(canvas, art, ratio):
    """Scale the artwork so its longest side is `ratio` of the canvas and centre it."""
    side = max(1, round(canvas.width * ratio))
    scale = side / max(art.width, art.height)
    resized = art.resize((max(1, round(art.width * scale)), max(1, round(art.height * scale))), Image.LANCZOS)
    canvas.alpha_composite(resized, ((canvas.width - resized.width) // 2, (canvas.height - resized.height) // 2))


def adaptive_foreground(art, size):
    canvas = Image.new("RGBA", (size, size), CLEAR)
    paste_centred(canvas, art, FOREGROUND_RATIO)
    return canvas


def legacy_square(art, size):
    canvas = Image.new("RGBA", (size, size), WHITE)
    paste_centred(canvas, art, SQUARE_RATIO)
    return canvas


def legacy_round(art, size):
    """White disc plus artwork, drawn oversized and downsampled so the rim stays smooth."""
    scale = 4
    big = size * scale
    canvas = Image.new("RGBA", (big, big), CLEAR)
    ImageDraw.Draw(canvas).ellipse((0, 0, big - 1, big - 1), fill=WHITE)
    paste_centred(canvas, art, ROUND_RATIO)
    return canvas.resize((size, size), Image.LANCZOS)


def circle_mask(image):
    mask = Image.new("L", image.size, 0)
    ImageDraw.Draw(mask).ellipse((0, 0, image.width - 1, image.height - 1), fill=255)
    out = image.copy()
    out.putalpha(mask)
    return out


def write_preview(art, path, side=512):
    """Neutral-background preview: circular mask (as most launchers draw it) plus the legacy pair."""
    icon = Image.new("RGBA", (ADAPTIVE_DP * 4, ADAPTIVE_DP * 4), WHITE)
    paste_centred(icon, art, FOREGROUND_RATIO)
    icon = circle_mask(icon.resize((side, side), Image.LANCZOS))
    legacy = legacy_square(art, side // 2)
    rounded = legacy_round(art, side // 2)
    canvas = Image.new("RGBA", (side + side, side), (0xE8, 0xEA, 0xED, 255))
    canvas.alpha_composite(icon, (0, 0))
    canvas.alpha_composite(legacy, (side, 0))
    canvas.alpha_composite(rounded, (side + side // 4, side // 2))
    canvas.convert("RGB").save(path)


def main():
    art = cut_out_background(Image.open(SOURCE))
    print(f"artwork {art.width}x{art.height} after background removal")

    for density, factor in DENSITIES.items():
        folder = os.path.join(RES, f"mipmap-{density}")
        os.makedirs(folder, exist_ok=True)
        adaptive = round(ADAPTIVE_DP * factor)
        legacy = round(LEGACY_DP * factor)
        adaptive_foreground(art, adaptive).save(os.path.join(folder, "ic_launcher_foreground.png"))
        legacy_square(art, legacy).save(os.path.join(folder, "ic_launcher.png"))
        legacy_round(art, legacy).save(os.path.join(folder, "ic_launcher_round.png"))
        print(f"mipmap-{density}: foreground {adaptive}px, legacy {legacy}px")

    anydpi = os.path.join(RES, "mipmap-anydpi-v26")
    os.makedirs(anydpi, exist_ok=True)
    for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
        with open(os.path.join(anydpi, name), "w", encoding="utf-8", newline="\n") as handle:
            handle.write(ADAPTIVE_XML)

    preview_dir = os.path.join(ROOT, "app", "build")
    os.makedirs(preview_dir, exist_ok=True)
    write_preview(art, os.path.join(preview_dir, "icon-preview.png"))
    print("preview -> app/build/icon-preview.png")


if __name__ == "__main__":
    main()
