"""Contact sheets made from actual Android GPU outputs, never simulated filter previews."""
import pathlib
import sys
from PIL import Image, ImageDraw, ImageOps

root = pathlib.Path(sys.argv[1])
styles = ["Simple", "Grainy", "Gritty", "Lores", "Handheld", "Zoomblur", "Flow-Blur", "Shutter-Drag", "Sensor-Raw", "Frame-Echo", "Clean", "Warm-Film", "Soft-Fade", "Mono", "Cool-Night"]
for scene in ("nature", "sea", "portrait", "night", "cafe"):
    sheet = Image.new("RGB", (3 * 216, len(styles) * 414), "#18181c")
    draw = ImageDraw.Draw(sheet)
    for row, style in enumerate(styles):
        for level, label in enumerate(("0%", "25% default", "100%")):
            image = Image.open(root / f"{scene}-{style}-{level}.png").convert("RGB").resize((216, 384))
            sheet.paste(image, (level * 216, row * 414))
            draw.text((level * 216 + 6, row * 414 + 390), style + " / " + label, fill="white")
    sheet.save(root / f"comparison-{scene}.jpg", quality=88)

sheet = Image.new("RGB", (432, 7 * 414), "#18181c")
draw = ImageDraw.Draw(sheet)
for row, style in enumerate(styles[:7]):
    for column, path in enumerate((root / f"before-portrait-{style}.png", root / f"portrait-{style}-1.png")):
        sheet.paste(Image.open(path).convert("RGB").resize((216, 384)), (column * 216, row * 414))
        draw.text((column * 216 + 6, row * 414 + 390), style + (" baseline bf6f868" if column == 0 else " improved"), fill="white")
sheet.save(root / "comparison-before-after.jpg", quality=90)

paths = sorted(root.glob("card-*.png")) + sorted(root.glob("text-*.png"))
sheet = Image.new("RGB", (2 * 480, ((len(paths) + 1) // 2) * 260), "#45454c")
draw = ImageDraw.Draw(sheet)
for index, path in enumerate(paths):
    tile = ImageOps.contain(Image.open(path).convert("RGBA"), (456, 218))
    x = index % 2 * 480
    y = index // 2 * 260
    sheet.paste(tile, (x + (480 - tile.width) // 2, y + (218 - tile.height) // 2), tile)
    draw.text((x + 12, y + 232), path.stem, fill="white")
sheet.save(root / "comparison-cards-text.jpg", quality=90)
print("Created 7 actual-render comparison sheets.")
