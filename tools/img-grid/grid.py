"""Overlay a numbered grid on an image to help determine column/row proportions.

Usage: python grid.py <image> [--cell-size 50] [--cols N] [--rows N] [-o OUTPUT]

Renders a labelled grid with a margin band outside the image content so numbers
never overlap the form. Useful for reverse-engineering MXL printed-form layouts.
"""
import argparse
import os
import sys

from PIL import Image, ImageDraw, ImageFont

MARGIN_TOP = 20
MARGIN_LEFT = 24


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")

    parser = argparse.ArgumentParser(description="Overlay numbered grid on image")
    parser.add_argument("image", help="Input image path (PNG, JPG)")
    parser.add_argument("--cell-size", type=int, default=50,
                        help="Cell size in pixels — used to derive cols/rows (default: 50)")
    parser.add_argument("-c", "--cols", type=int, default=0,
                        help="Number of vertical divisions (overrides --cell-size)")
    parser.add_argument("-r", "--rows", type=int, default=0,
                        help="Number of horizontal divisions (0 = auto, square cells)")
    parser.add_argument("-o", "--output", help="Output path (default: <name>-grid.<ext>)")
    args = parser.parse_args()

    src = Image.open(args.image).convert("RGBA")
    sw, sh = src.size

    cols = args.cols if args.cols > 0 else max(1, round(sw / args.cell_size))
    step_x = sw / cols
    rows = args.rows if args.rows > 0 else max(1, round(sh / step_x))
    step_y = sh / rows

    # Canvas with margins for labels
    cw = MARGIN_LEFT + sw
    ch = MARGIN_TOP + sh
    canvas = Image.new("RGBA", (cw, ch), (255, 255, 255, 255))
    canvas.paste(src, (MARGIN_LEFT, MARGIN_TOP))

    overlay = Image.new("RGBA", (cw, ch), (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay)

    label_font_size = 12
    try:
        label_font = ImageFont.truetype("arial.ttf", label_font_size)
    except Exception:
        label_font = ImageFont.load_default()

    # Vertical lines + numbers in top margin
    for i in range(cols + 1):
        x = MARGIN_LEFT + round(i * step_x)
        major = i % 10 == 0
        mid = i % 5 == 0
        alpha = 160 if major else (110 if mid else 40)
        lw = 2 if major else 1
        draw.line([(x, MARGIN_TOP), (x, ch)], fill=(255, 0, 0, alpha), width=lw)
        if major or mid or step_x >= 20:
            label = str(i)
            bbox = label_font.getbbox(label)
            tw = bbox[2] - bbox[0]
            color = (200, 0, 0, 255) if (major or mid) else (200, 0, 0, 180)
            draw.text((x - tw // 2, 2), label, fill=color, font=label_font)

    # Horizontal lines + numbers in left margin
    for j in range(rows + 1):
        y = MARGIN_TOP + round(j * step_y)
        major = j % 10 == 0
        mid = j % 5 == 0
        alpha = 160 if major else (110 if mid else 20)
        lw = 2 if major else 1
        draw.line([(MARGIN_LEFT, y), (cw, y)], fill=(0, 0, 200, alpha), width=lw)
        if major or mid or step_y >= 20:
            label = str(j)
            bbox = label_font.getbbox(label)
            tw = bbox[2] - bbox[0]
            color = (0, 0, 200, 255) if (major or mid) else (0, 0, 200, 180)
            draw.text((MARGIN_LEFT - tw - 3, y - label_font_size // 2),
                      label, fill=color, font=label_font)

    result = Image.alpha_composite(canvas, overlay).convert("RGB")

    if args.output:
        out = args.output
    else:
        name, ext = os.path.splitext(args.image)
        out = f"{name}-grid{ext}"

    result.save(out)
    print(f"Grid:      {cols} x {rows} cells")
    print(f"Cell size: {step_x:.1f} x {step_y:.1f} px")
    print(f"Image:     {sw} x {sh} px")
    print(f"Saved:     {out}")


if __name__ == "__main__":
    main()
