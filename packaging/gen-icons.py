"""Regenerates every icon asset from logo.png (run from the repo root: python packaging/gen-icons.py)."""
from PIL import Image, ImageDraw
import os
src = Image.open('logo.png').convert('RGBA')
BG = (7, 22, 74, 255)

def fit(img, size, pad=0.0):
    inner = int(size * (1 - 2 * pad)); im = img.resize((inner, inner), Image.LANCZOS)
    out = Image.new('RGBA', (size, size), (0, 0, 0, 0)); out.paste(im, ((size - inner) // 2, (size - inner) // 2), im); return out

for d, s in {'mdpi': 1, 'hdpi': 1.5, 'xhdpi': 2, 'xxhdpi': 3, 'xxxhdpi': 4}.items():
    folder = f'androidApp/src/main/res/mipmap-{d}'; os.makedirs(folder, exist_ok=True)
    fit(src, int(108 * s), pad=0.18).save(f'{folder}/ic_launcher_foreground.png')
    legacy = int(48 * s); bg = Image.new('RGBA', (legacy, legacy), BG)
    mask = Image.new('L', (legacy, legacy), 0); ImageDraw.Draw(mask).rounded_rectangle([0, 0, legacy - 1, legacy - 1], radius=int(legacy * 0.2), fill=255)
    logo = fit(src, legacy, pad=0.06); bg.paste(logo, (0, 0), logo)
    rounded = Image.new('RGBA', (legacy, legacy), (0, 0, 0, 0)); rounded.paste(bg, (0, 0), mask)
    rounded.save(f'{folder}/ic_launcher.png'); rounded.save(f'{folder}/ic_launcher_round.png')

sizes = [256, 128, 64, 48, 32, 16]; imgs = [fit(src, s) for s in sizes]
imgs[0].save('desktopApp/src/main/resources/icons/dropnest.ico', format='ICO', sizes=[(s, s) for s in sizes], append_images=imgs[1:])
fit(src, 256).save('desktopApp/src/main/resources/icons/logo.png')

os.makedirs('docs/store', exist_ok=True)
icon = Image.new('RGBA', (512, 512), BG); l = fit(src, 512, pad=0.02); icon.paste(l, (0, 0), l); icon.convert('RGB').save('docs/store/play-icon-512.png')
feat = Image.new('RGB', (1024, 500), BG[:3]); l = fit(src, 440); feat.paste(l, (292, 30), l); feat.save('docs/store/play-feature-1024x500.png')
src.resize((300, 300), Image.LANCZOS).save('docs/store/msstore-logo-300.png')

os.makedirs('packaging/windows/assets', exist_ok=True)
for name, (w, h) in {'StoreLogo': (50, 50), 'Square150x150Logo': (150, 150), 'Square44x44Logo': (44, 44), 'Wide310x150Logo': (310, 150)}.items():
    t = Image.new('RGBA', (w, h), BG); s = min(w, h); l = fit(src, s, pad=0.08); t.paste(l, ((w - s) // 2, (h - s) // 2), l); t.save(f'packaging/windows/assets/{name}.png')
print('icons regenerated')
