#!/usr/bin/env python3
"""Package the approved artwork; requires Python 3 and Pillow (pip install Pillow)."""

from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]


def main():
    # Android's 108 dp layer reveals its central 72 dp. Keep the entire approved
    # square in that viewport; extend only edge pixels into the parallax margin.
    image = Image.open(HERE / "little-leaf.png").convert("RGB")
    size, inset, art_size = 432, 72, 288
    art = image.resize((art_size, art_size), Image.Resampling.LANCZOS)
    layer = Image.new("RGB", (size, size))
    layer.paste(art, (inset, inset))
    for box, position, dimensions in (
        ((0, 0, art_size, 1), (inset, 0), (art_size, inset)),
        ((0, art_size - 1, art_size, art_size), (inset, size - inset), (art_size, inset)),
        ((0, 0, 1, art_size), (0, inset), (inset, art_size)),
        ((art_size - 1, 0, art_size, art_size), (size - inset, inset), (inset, art_size)),
    ):
        layer.paste(art.crop(box).resize(dimensions), position)
    for x in (0, size - inset):
        for y in (0, size - inset):
            pixel = art.getpixel((0 if x == 0 else art_size - 1, 0 if y == 0 else art_size - 1))
            layer.paste(pixel, (x, y, x + inset, y + inset))
    for module in ("app", "wear"):
        target = ROOT / module / "src/main/res/drawable-nodpi/ic_launcher_foreground.webp"
        target.parent.mkdir(parents=True, exist_ok=True)
        layer.save(target, lossless=True, method=6)

    # Review the actual visible viewport under common launcher masks and at 48px.
    sheet = Image.new("RGB", (720, 300), "#F8F7EF")
    draw = ImageDraw.Draw(sheet)
    for index, name in enumerate(("Circle", "Rounded square", "Squircle")):
        mask = Image.new("L", (288, 288))
        pen = ImageDraw.Draw(mask)
        if index == 0:
            pen.ellipse((0, 0, 287, 287), fill=255)
        elif index == 1:
            pen.rounded_rectangle((0, 0, 287, 287), radius=64, fill=255)
        else:
            # A fourth-order superellipse, rendered at source resolution.
            for y in range(288):
                for x in range(288):
                    if abs((x - 143.5) / 144) ** 4 + abs((y - 143.5) / 144) ** 4 <= 1:
                        mask.putpixel((x, y), 255)
        icon = art.convert("RGBA")
        icon.putalpha(mask)
        origin = index * 240
        sheet.paste(icon.resize((192, 192), Image.Resampling.LANCZOS), (origin + 24, 16),
                    icon.resize((192, 192), Image.Resampling.LANCZOS))
        small = icon.resize((48, 48), Image.Resampling.LANCZOS)
        sheet.paste(small, (origin + 96, 220), small)
        draw.text((origin + 60, 280), name, fill="#22302B")
    sheet.save(HERE / "mask-preview.png")


if __name__ == "__main__":
    main()
