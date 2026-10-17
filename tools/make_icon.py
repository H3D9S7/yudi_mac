import os
from PIL import Image, ImageDraw, ImageFont

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "res")
S = 192
img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
d = ImageDraw.Draw(img)

# rounded background
d.rounded_rectangle([0, 0, S - 1, S - 1], radius=42, fill=(16, 21, 31, 255))

# subtle blue disc
d.ellipse([26, 18, 166, 158], fill=(24, 38, 63, 255))

# wifi arcs
cx, cy = 96, 118
for r in (30, 55, 80):
    d.arc([cx - r, cy - r, cx + r, cy + r], start=205, end=335,
          fill=(76, 141, 255, 255), width=11)
# dot
d.ellipse([cx - 9, cy - 9, cx + 9, cy + 9], fill=(76, 141, 255, 255))

# MAC text
try:
    font = ImageFont.truetype("C:/Windows/Fonts/segoeuib.ttf", 34)
except Exception:
    font = ImageFont.load_default()
text = "MAC"
bbox = d.textbbox((0, 0), text, font=font)
tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
d.text(((S - tw) / 2 - bbox[0], 148 - bbox[1]), text, font=font, fill=(237, 239, 242, 255))

sizes = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
for name, px in sizes.items():
    folder = os.path.join(OUT, "mipmap-" + name)
    os.makedirs(folder, exist_ok=True)
    img.resize((px, px), Image.LANCZOS).save(os.path.join(folder, "ic_launcher.png"))

print("icons written to", os.path.abspath(OUT))
