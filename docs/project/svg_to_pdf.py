"""Convert PlantUML SVG diagrams to vector PDF with proper Polish glyphs.

svglib falls back to reportlab's built-in Helvetica (no Polish letters) for the
generic "sans-serif" family used by PlantUML, which renders diacritics as boxes.
We register the full-Unicode DejaVuSans family and remap the SVG to use it.
"""
import sys
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from svglib.svglib import svg2rlg
from reportlab.graphics import renderPDF

FONT_DIR = "C:/Windows/Fonts/"
FAMILY = "DejaVuSans"
VARIANTS = {
    "DejaVuSans": "DejaVuSans.ttf",
    "DejaVuSans-Bold": "DejaVuSans-Bold.ttf",
    "DejaVuSans-Oblique": "DejaVuSans-Oblique.ttf",
    "DejaVuSans-BoldOblique": "DejaVuSans-BoldOblique.ttf",
}

for name, fname in VARIANTS.items():
    pdfmetrics.registerFont(TTFont(name, FONT_DIR + fname))
pdfmetrics.registerFontFamily(
    FAMILY,
    normal="DejaVuSans",
    bold="DejaVuSans-Bold",
    italic="DejaVuSans-Oblique",
    boldItalic="DejaVuSans-BoldOblique",
)


def convert(name):
    with open(name + ".svg", "r", encoding="utf-8") as f:
        svg = f.read()
    # Force the Unicode font everywhere PlantUML used a generic sans-serif.
    svg = svg.replace('font-family="sans-serif"', f'font-family="{FAMILY}"')
    tmp = name + ".uni.svg"
    with open(tmp, "w", encoding="utf-8") as f:
        f.write(svg)
    drawing = svg2rlg(tmp)
    renderPDF.drawToFile(drawing, name + ".pdf")
    print(f"ok {name}.pdf {round(drawing.width)}x{round(drawing.height)}")


if __name__ == "__main__":
    targets = sys.argv[1:] or [
        "architecture", "components", "use_cases",
        "classes", "seq_embed_ai", "seq_subscription_upgrade",
    ]
    for t in targets:
        convert(t)
