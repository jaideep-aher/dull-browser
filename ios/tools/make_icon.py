"""Draws the app icon: one ink circle with one horizontal line.

Run from ios/: python3 tools/make_icon.py
Proportions follow the Android adaptive foreground (108dp canvas, 72dp visible).
"""
from pathlib import Path

from PIL import Image, ImageDraw

SIZE = 1024
SCALE = 4
PAPER = (0xF3, 0xEF, 0xE6)
INK = (0x1C, 0x19, 0x17)
OUT = Path(__file__).resolve().parent.parent / "DullBrowser/Assets.xcassets/AppIcon.appiconset"


def draw(mark, background):
    size = SIZE * SCALE
    mode = "RGB" if background else "RGBA"
    image = Image.new(mode, (size, size), background or (0, 0, 0, 0))
    pen = ImageDraw.Draw(image)
    unit = size / 80
    center = size / 2
    radius = 26 * unit
    stroke = round(3.2 * unit)
    half = stroke / 2
    pen.ellipse(
        (center - radius - half, center - radius - half, center + radius + half, center + radius + half),
        outline=mark,
        width=stroke,
    )
    pen.rectangle((center - 20 * unit, center - half, center + 20 * unit, center + half), fill=mark)
    return image.resize((SIZE, SIZE), Image.LANCZOS)


draw(INK, PAPER).save(OUT / "icon-light.png")
draw(PAPER + (255,), None).save(OUT / "icon-dark.png")
draw((255, 255, 255, 255), None).save(OUT / "icon-tinted.png")
