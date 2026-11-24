"""Synthetic OCR test pages with known text (all text written for this test).

- syn_ja_vertical.png: manga-like page, screentone background, white bubbles
  with vertical Japanese columns (right to left), one bubble with small text.
- syn_zh_vertical.png: the same with traditional Chinese.
- syn_zh_novel.png: phone screenshot of a Chinese novel (horizontal).
- syn_en_book.png: phone screenshot of an English book.
Writes truth.json: {image: [lines of text that should be recognized]}.
"""
import json
import os
import sys

from PIL import Image, ImageDraw, ImageFont

OUT = sys.argv[1]
FONTS = 'C:/Windows/Fonts/'


def font(name, size, index=0):
    return ImageFont.truetype(FONTS + name, size, index=index)


def screentone(w, h):
    img = Image.new('L', (w, h), 235)
    d = ImageDraw.Draw(img)
    for y in range(0, h, 9):
        for x in range((y // 9) % 2 * 4, w, 9):
            d.ellipse((x, y, x + 3, y + 3), fill=150)
    # panel borders
    d.rectangle((20, 20, w - 20, h // 2 - 10), outline=0, width=6)
    d.rectangle((20, h // 2 + 10, w - 20, h - 20), outline=0, width=6)
    return img


def vertical_bubble(img, cx, cy, columns, f):
    """Columns are drawn right to left, characters top to bottom."""
    size = f.size
    gap = int(size * 0.35)
    n_rows = max(len(c) for c in columns)
    w = len(columns) * (size + gap) + size
    h = n_rows * (size + 4) + size
    d = ImageDraw.Draw(img)
    d.ellipse((cx - w * 0.8, cy - h * 0.7, cx + w * 0.8, cy + h * 0.7), fill=255, outline=0, width=4)
    x = cx + w / 2 - size
    for col in columns:
        y = cy - h / 2 + size / 2
        for ch in col:
            box = d.textbbox((0, 0), ch, font=f)
            d.text((x + (size - (box[2] - box[0])) / 2 - box[0], y), ch, font=f, fill=0)
            y += size + 4
        x -= size + gap


def manga_page(name, bubbles, fontname):
    img = screentone(1080, 1500)
    truth = []
    for cx, cy, columns, size in bubbles:
        vertical_bubble(img, cx, cy, columns, font(fontname, size))
        truth.append(''.join(columns))
    img.convert('RGB').save(os.path.join(OUT, name))
    return truth


def text_page(name, paragraphs, f, width=1080, margin=60, dark=False):
    bg, fg = ((24, 24, 24), (220, 220, 220)) if dark else ((250, 248, 240), (30, 30, 30))
    lines = []
    probe = ImageDraw.Draw(Image.new('RGB', (10, 10)))
    for p in paragraphs:
        words = p.split(' ') if ' ' in p else list(p)
        sep = ' ' if ' ' in p else ''
        line = ''
        for wd in words:
            cand = (line + sep + wd) if line else wd
            if probe.textlength(cand, font=f) > width - 2 * margin:
                lines.append(line)
                line = wd
            else:
                line = cand
        lines.append(line)
    lh = int(f.size * 1.7)
    img = Image.new('RGB', (width, margin * 2 + lh * len(lines) + 120), bg)
    d = ImageDraw.Draw(img)
    d.text((margin, 30), 'Chapter 3', font=font('arial.ttf', 30), fill=fg)
    for i, line in enumerate(lines):
        d.text((margin, margin + 80 + i * lh), line, font=f, fill=fg)
    img.save(os.path.join(OUT, name))
    return paragraphs


truth = {
    'syn_ja_vertical.png': manga_page('syn_ja_vertical.png', [
        (820, 330, ['今日は', 'いい天気', 'ですね'], 46),
        (330, 300, ['どこへ', '行くの？'], 46),
        (700, 1050, ['魔法の薬を', '作っている', 'ところだよ'], 42),
        (260, 1150, ['本当に', '大丈夫かな'], 30),
    ], 'YuGothM.ttc'),
    'syn_zh_vertical.png': manga_page('syn_zh_vertical.png', [
        (820, 330, ['我們', '走吧！'], 46),
        (330, 330, ['這是', '什麼東西？'], 46),
        (700, 1050, ['不要害怕', '我會保護你'], 42),
        (260, 1150, ['真的', '可以嗎'], 30),
    ], 'msjh.ttc' if os.path.exists(FONTS + 'msjh.ttc') else 'msyh.ttc'),
    'syn_zh_novel.png': text_page('syn_zh_novel.png', [
        '夜色渐深，山间的风带着几分凉意。林远站在破旧的庙门前，望着远处若隐若现的灯火，心中隐隐有些不安。',
        '他想起师父临走前说过的话：修行之路漫长，切不可急于求成。可如今宗门危在旦夕，他又怎能袖手旁观？',
    ], font('msyh.ttc', 40), dark=True),
    'syn_en_book.png': text_page('syn_en_book.png', [
        'The innkeeper wiped the same glass for the third time, pretending not to listen. Outside, the rain had turned the road into a river of mud.',
        'Nobody leaves tonight, the old woman said. Not with the bridge gone and the wolves this close to the village.',
    ], font('georgia.ttf', 40)),
}
json.dump(truth, open(os.path.join(OUT, 'syn_truth.json'), 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
print('ok')
