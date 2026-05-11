#!/bin/bash
# docx2md.sh — конвертация Word (.docx) в Markdown + извлечение картинок
# Использование: docx2md.sh <input.docx> [output_dir]
#
# Результат:
#   <output_dir>/document.md   — текст в Markdown
#   <output_dir>/images/       — все изображения из документа

set -e

INPUT="$1"
OUTPUT_DIR="${2:-$(dirname "$INPUT")/$(basename "$INPUT" .docx)}"

if [ -z "$INPUT" ]; then
    echo "Использование: $0 <input.docx> [output_dir]"
    exit 1
fi

if [ ! -f "$INPUT" ]; then
    echo "Файл не найден: $INPUT"
    exit 1
fi

mkdir -p "$OUTPUT_DIR/images"

echo "Конвертация: $INPUT -> $OUTPUT_DIR"

# pandoc: docx -> markdown с извлечением медиафайлов
pandoc "$INPUT" \
    --from=docx \
    --to=gfm \
    --wrap=none \
    --extract-media="$OUTPUT_DIR/images" \
    -o "$OUTPUT_DIR/document.md"

# Если pandoc положил картинки во вложенную папку media — перемещаем наверх
if [ -d "$OUTPUT_DIR/images/media" ]; then
    mv "$OUTPUT_DIR/images/media"/* "$OUTPUT_DIR/images/" 2>/dev/null || true
    rmdir "$OUTPUT_DIR/images/media" 2>/dev/null || true
fi

# Правим пути к картинкам в md-файле
sed -i "s|images/media/|images/|g" "$OUTPUT_DIR/document.md"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Постобработка: HTML-таблицы → MD pipe, <img> → ![]()
python3 "$SCRIPT_DIR/html_tables_to_md.py" "$OUTPUT_DIR/document.md"

IMG_COUNT=$(ls "$OUTPUT_DIR/images/" 2>/dev/null | wc -l)

echo "Готово:"
echo "  Markdown: $OUTPUT_DIR/document.md"
echo "  Картинок: $IMG_COUNT шт. -> $OUTPUT_DIR/images/"
