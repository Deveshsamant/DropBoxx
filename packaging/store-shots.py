"""Compose store screenshots from raw device captures.

Play needs 9:16 phone shots whose long side is at most twice the short side (a bare 1080x2400
capture is rejected), so each raw capture is placed in a rounded frame on a Nocturne gradient
with a one-line caption -> 1080x1920 PNG. Desktop captures get the same treatment at 1920x1080
for the Microsoft Store (min 1366x768). Also refreshes the Play feature graphic (1024x500).

Usage: python packaging/store-shots.py <raw-dir>
  raw-dir must contain phone-*.png (portrait captures) and desktop-*.png (window captures);
  captions come from CAPTIONS below (file stem -> text).
"""
import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "docs" / "store" / "screenshots"
BG, BG2, ACCENT, TEXT, MUTED = (22, 24, 38), (27, 29, 44), (141, 132, 240), (236, 238, 251), (150, 154, 184)
FONT = "C:/Windows/Fonts/seguisb.ttf" if Path("C:/Windows/Fonts/seguisb.ttf").exists() else "C:/Windows/Fonts/segoeuib.ttf"
FONT_REG = "C:/Windows/Fonts/segoeui.ttf"

CAPTIONS = {
    "phone-mybox": ("Drop anything into your box", "Files, photos, text and links - no other device needed"),
    "phone-mybox-share": ("Share from any app", "WhatsApp, Instagram, Gallery, browser - straight into the nest"),
    "phone-devices": ("Your devices find each other", "Same Wi-Fi or your phone's hotspot, no accounts"),
    "phone-peer-box": ("Pick what you want, fetch it", "Browse another device's box and pull selected items"),
    "phone-transfers": ("Fast, encrypted, resumable", "Everything moves device-to-device over TLS"),
    "phone-settings": ("You decide who gets in", "Ask me, trusted only, or anyone nearby - plus a PIN"),
    "desktop-mybox": ("Your box, on every device", "Drag anything in - your phone picks it up in one tap"),
    "desktop-devices": ("Phones and PCs, one network", "Discovery works with no setup at all"),
    "desktop-settings": ("Dark or light, your call", "Keep it running in the tray, start with Windows"),
}


def font(size, bold=True):
    return ImageFont.truetype(FONT if bold else FONT_REG, size)


def gradient(w, h):
    img = Image.new("RGB", (w, h), BG)
    glow = Image.new("RGB", (w, h), BG)
    d = ImageDraw.Draw(glow)
    d.ellipse((-w * 0.2, -h * 0.25, w * 0.8, h * 0.45), fill=(46, 44, 92))
    d.ellipse((w * 0.45, h * 0.55, w * 1.3, h * 1.25), fill=(38, 40, 78))
    glow = glow.filter(ImageFilter.GaussianBlur(w * 0.18))
    return Image.blend(img, glow, 0.9)


def rounded(img, radius):
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, img.width - 1, img.height - 1), radius, fill=255)
    out = img.convert("RGBA")
    out.putalpha(mask)
    return out


def caption(canvas, title, sub, y, w, scale=1.0):
    d = ImageDraw.Draw(canvas)
    f1, f2 = font(int(w * 0.058 * scale)), font(int(w * 0.03 * scale), bold=False)
    tw = d.textlength(title, font=f1)
    d.text(((w - tw) / 2, y), title, fill=TEXT, font=f1)
    sw = d.textlength(sub, font=f2)
    d.text(((w - sw) / 2, y + f1.size + int(w * 0.012)), sub, fill=MUTED, font=f2)


def phone(raw: Path, title, sub):
    W, H = 1080, 1920
    canvas = gradient(W, H)
    shot = Image.open(raw).convert("RGB")
    target_h = 1440
    shot = shot.resize((int(shot.width * target_h / shot.height), target_h), Image.LANCZOS)
    frame = rounded(shot, 64)
    border = Image.new("RGBA", (frame.width + 16, frame.height + 16), (0, 0, 0, 0))
    ImageDraw.Draw(border).rounded_rectangle((0, 0, border.width - 1, border.height - 1), 72, fill=(44, 46, 70, 255), outline=(90, 88, 140, 255), width=3)
    shadow = Image.new("RGBA", (border.width + 120, border.height + 120), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle((60, 80, shadow.width - 60, shadow.height - 40), 80, fill=(0, 0, 0, 150))
    shadow = shadow.filter(ImageFilter.GaussianBlur(40))
    x = (W - border.width) // 2
    y = H - border.height - 40
    canvas.paste(shadow, (x - 60, y - 60), shadow)
    canvas.paste(border, (x, y), border)
    canvas.paste(frame, (x + 8, y + 8), frame)
    caption(canvas, title, sub, 130, W)
    return canvas


def desktop(raw: Path, title, sub):
    W, H = 1920, 1080
    canvas = gradient(W, H)
    shot = Image.open(raw).convert("RGB")
    target_w = 1380
    shot = shot.resize((target_w, int(shot.height * target_w / shot.width)), Image.LANCZOS)
    frame = rounded(shot, 22)
    shadow = Image.new("RGBA", (frame.width + 160, frame.height + 160), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle((80, 100, shadow.width - 80, shadow.height - 60), 40, fill=(0, 0, 0, 160))
    shadow = shadow.filter(ImageFilter.GaussianBlur(50))
    x = (W - frame.width) // 2
    y = H - frame.height + 70
    canvas.paste(shadow, (x - 80, y - 80), shadow)
    canvas.paste(frame, (x, y), frame)
    caption(canvas, title, sub, 44, W, scale=0.6)
    return canvas


def feature_graphic(logo: Path):
    W, H = 1024, 500
    canvas = gradient(W, H)
    lg = Image.open(logo).convert("RGBA").resize((270, 270), Image.LANCZOS)
    canvas.paste(lg, (70, 115), lg)
    d = ImageDraw.Draw(canvas)
    d.text((385, 140), "DropNest", fill=TEXT, font=font(80))
    d.text((389, 246), "Your box, on every device.", fill=ACCENT, font=font(32, bold=False))
    d.text((389, 296), "Drop files, photos, text and links. Your other devices\npick them up over Wi-Fi. No cloud, no accounts.", fill=MUTED, font=font(22, bold=False), spacing=6)
    return canvas


def main(raw_dir: Path):
    (OUT / "phone").mkdir(parents=True, exist_ok=True)
    (OUT / "desktop").mkdir(parents=True, exist_ok=True)
    n = 0
    for stem, (title, sub) in CAPTIONS.items():
        src = raw_dir / f"{stem}.png"
        if not src.exists():
            print("skip", src)
            continue
        if stem.startswith("phone"):
            phone(src, title, sub).save(OUT / "phone" / f"{n:02d}-{stem}.png", optimize=True)
        else:
            desktop(src, title, sub).save(OUT / "desktop" / f"{n:02d}-{stem}.png", optimize=True)
        n += 1
    feature_graphic(ROOT / "logo.png").save(ROOT / "docs" / "store" / "play-feature-1024x500.png", optimize=True)
    print(f"wrote {n} screenshots to {OUT}")


if __name__ == "__main__":
    main(Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "docs" / "design" / "nocturne")
