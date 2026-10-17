from PIL import Image, ImageDraw, ImageFont, ImageFilter
from pathlib import Path

src = Path(r'C:\Users\xl__p\Desktop\招聘资料\e13bd00bfce765898c1733915f859fc7.jpg')
out_dir = Path('output/images')
out_dir.mkdir(parents=True, exist_ok=True)

SCALE = 4
W, H = 344*SCALE, 248*SCALE
font_path = r'C:\Windows\Fonts\simsun.ttc'
font_bold = r'C:\Windows\Fonts\simhei.ttf'

def f(path, size):
    return ImageFont.truetype(path, size, index=0)

def centered(draw, text, y, font, fill=(60, 55, 48, 255)):
    box = draw.textbbox((0, 0), text, font=font)
    x = (W - (box[2]-box[0])) // 2
    draw.text((x, y), text, font=font, fill=fill)

def make(year, award, filename):
    base = Image.open(src).convert('RGBA').resize((W, H), Image.Resampling.LANCZOS)
    # Clear all original text inside the ornamental frame. A clean paper-colored
    # panel avoids leaving fragments of the reference certificate underneath.
    paper = Image.new('RGBA', (1200, 880), (239, 239, 234, 255))
    # Add very subtle paper grain so the panel blends into the scanned template.
    grain = Image.effect_noise((1200, 880), 2).convert('L')
    grain_rgba = Image.new('RGBA', (1200, 880), (245, 245, 240, 0))
    grain_rgba.putalpha(grain.point(lambda v: int(v * 0.10)))
    paper = Image.alpha_composite(paper, grain_rgba)
    base.alpha_composite(paper, (72, 27))

    draw = ImageDraw.Draw(base)
    title_font = f(font_bold, 29*SCALE)
    body_font = f(font_path, 13*SCALE)
    seal_font = f(font_bold, 8*SCALE)
    centered(draw, '奖 学 金 证 书', 42*SCALE, title_font, fill=(133, 76, 55, 255))
    draw.line((112*SCALE, 71*SCALE, 232*SCALE, 71*SCALE), fill=(170, 155, 135, 255), width=1*SCALE)
    draw.ellipse((166*SCALE, 67*SCALE, 174*SCALE, 75*SCALE), outline=(170, 130, 105, 255), width=1*SCALE)
    centered(draw, f'孙明珠同学获得{year}年度', 88*SCALE, body_font)
    centered(draw, f'学业{award}。', 119*SCALE, body_font)
    centered(draw, '特发此证，以资鼓励。', 151*SCALE, body_font)

    # Generic example seal, intentionally marked as a non-official sample.
    cx, cy, r = 268*SCALE, 190*SCALE, 28*SCALE
    red = (157, 42, 41, 210)
    draw.ellipse((cx-r, cy-r, cx+r, cy+r), outline=red, width=2*SCALE)
    draw.ellipse((cx-r+5*SCALE, cy-r+5*SCALE, cx+r-5*SCALE, cy+r-5*SCALE), outline=red, width=1*SCALE)
    box = draw.textbbox((0,0), '样式示例', font=seal_font)
    draw.text((cx-(box[2]-box[0])/2, cy-7*SCALE), '样式示例', font=seal_font, fill=red)
    box2 = draw.textbbox((0,0), '奖学金', font=seal_font)
    draw.text((cx-(box2[2]-box2[0])/2, cy+5*SCALE), '奖学金', font=seal_font, fill=red)
    draw.arc((cx-r+8*SCALE, cy-r+8*SCALE, cx+r-8*SCALE, cy+r-8*SCALE), 210, 330, fill=red, width=1*SCALE)
    draw.arc((cx-r+8*SCALE, cy-r+8*SCALE, cx+r-8*SCALE, cy+r-8*SCALE), 30, 150, fill=red, width=1*SCALE)

    # Light disclosure mark.
    draw.text((18*SCALE, 218*SCALE), '样式示例 · 非正式证明', font=f(font_bold, 10*SCALE), fill=(130, 130, 125, 145))
    base.convert('RGB').save(out_dir / filename, quality=95, dpi=(200,200))

make('二〇二四', '三等奖学金', '孙明珠-2024年三等奖学金-样式示例.jpg')
make('二〇二五', '二等奖学金', '孙明珠-2025年二等奖学金-样式示例.jpg')
print('\n'.join(str(p) for p in sorted(out_dir.glob('孙明珠-*.jpg'))))
