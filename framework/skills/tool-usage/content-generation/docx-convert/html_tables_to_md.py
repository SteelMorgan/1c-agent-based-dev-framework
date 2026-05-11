#!/usr/bin/env python3
# html_tables_to_md.py — постобработка MD-файла после pandoc:
#   - HTML-таблицы → компактный Markdown pipe-формат
#   - <img ...> → ![alt](src)
#   - <br /> → пробел
#   - <strong>text</strong> → **text**
#   - убирает мусорные HTML-теги

import re
import sys
from pathlib import Path


def strip_tag(text, tag):
    """Убрать открывающий и закрывающий тег, оставив содержимое."""
    text = re.sub(rf'<{tag}[^>]*>', '', text)
    text = re.sub(rf'</{tag}>', '', text)
    return text


def cell_text(html):
    """Извлечь читаемый текст из HTML ячейки таблицы."""
    # <br> → пробел
    html = re.sub(r'<br\s*/?\s*>', ' ', html)
    # <strong>/<b> → **
    html = re.sub(r'<strong>(.*?)</strong>', r'**\1**', html, flags=re.DOTALL)
    html = re.sub(r'<b>(.*?)</b>', r'**\1**', html, flags=re.DOTALL)
    # <em>/<i> → *
    html = re.sub(r'<em>(.*?)</em>', r'*\1*', html, flags=re.DOTALL)
    html = re.sub(r'<i>(.*?)</i>', r'*\1*', html, flags=re.DOTALL)
    # убрать все оставшиеся теги
    html = re.sub(r'<[^>]+>', '', html)
    # схлопнуть пробелы, убрать переносы строк внутри ячейки
    html = re.sub(r'\s+', ' ', html).strip()
    return html


def html_table_to_md(table_html):
    """Конвертировать HTML-таблицу в pipe Markdown."""
    rows = re.findall(r'<tr[^>]*>(.*?)</tr>', table_html, re.DOTALL)
    if not rows:
        return table_html

    parsed = []
    header_row = None

    for i, row in enumerate(rows):
        # th — заголовок, td — обычная ячейка
        cells = re.findall(r'<t[hd][^>]*>(.*?)</t[hd]>', row, re.DOTALL)
        is_header = bool(re.search(r'<th[^>]*>', row))
        texts = [cell_text(c) for c in cells]
        if not texts:
            continue
        if is_header and header_row is None:
            header_row = texts
        else:
            parsed.append((is_header, texts))

    if not parsed and header_row is None:
        return table_html

    lines = []

    # если заголовка нет — берём первую строку как заголовок
    if header_row is None:
        header_row = parsed.pop(0)[1] if parsed else []

    if header_row:
        lines.append('| ' + ' | '.join(header_row) + ' |')
        lines.append('| ' + ' | '.join(['---'] * len(header_row)) + ' |')

    for _, cells in parsed:
        # выровнять количество ячеек по заголовку
        while len(cells) < len(header_row):
            cells.append('')
        lines.append('| ' + ' | '.join(cells[:len(header_row)]) + ' |')

    return '\n'.join(lines)


def img_to_md(match):
    """<img src="..." alt="..."> → ![alt](src)"""
    src = re.search(r'src="([^"]+)"', match.group(0))
    alt = re.search(r'alt="([^"]+)"', match.group(0))
    src_val = Path(src.group(1)).name if src else 'image'
    alt_val = alt.group(1) if alt else 'image'
    # alt от pandoc для скриншотов длинный и бесполезный — укорачиваем
    if len(alt_val) > 60:
        alt_val = alt_val[:60] + '…'
    return f'![{alt_val}](images/{src_val})'


def process(text):
    # 1. HTML-таблицы → MD
    text = re.sub(
        r'<table[^>]*>.*?</table>',
        lambda m: html_table_to_md(m.group(0)),
        text,
        flags=re.DOTALL
    )
    # 2. <img ...> → ![alt](src)
    text = re.sub(r'<img[^>]+/?\s*>', img_to_md, text)
    # 3. оставшиеся <br>
    text = re.sub(r'<br\s*/?\s*>', ' ', text)
    # 4. оставшиеся HTML-теги (colgroup, col, thead, tbody и т.п.)
    text = re.sub(r'<[^>]+>', '', text)
    # 5. схлопнуть тройные+ пустые строки
    text = re.sub(r'\n{3,}', '\n\n', text)
    return text.strip() + '\n'


if __name__ == '__main__':
    path = Path(sys.argv[1])
    original = path.read_text(encoding='utf-8')
    result = process(original)
    path.write_text(result, encoding='utf-8')
    print(f'Обработано: {path}')
