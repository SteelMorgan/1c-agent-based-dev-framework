---
name: docx-convert
description: "Use for конвертации Word-документа (.docx) в Markdown с извлечением изображений (ТЗ, спека, инструкция, документация поставщика). Helps получить GFM-текст через pandoc с постобработкой HTML-таблиц и путей к картинкам."
capabilities: content-generation,document-conversion
---

# Конвертация Word → Markdown

Тонкая обёртка над `pandoc` для конвертации `.docx` в GitHub-Flavored Markdown с извлечением встроенных изображений. Дополнительно постобрабатывает результат: чинит пути к картинкам и превращает HTML-таблицы (которые pandoc оставляет в исходном виде для сложных случаев) в pipe-таблицы Markdown.

## Когда применять

| Ситуация | Действие |
|----------|----------|
| Заказчик прислал ТЗ в `.docx`, нужно положить в репозиторий как md | `docx2md.sh input.docx` |
| Документация поставщика в Word, нужно скормить агенту | `docx2md.sh input.docx output_dir` |
| Документ со сложными таблицами и стилями — pandoc их пропускает | mammoth (см. ниже) |
| Нужна только текстовая часть без картинок | `pandoc input.docx --to=gfm -o out.md` напрямую |

## Когда НЕ применять

- Нужен HTML — `pandoc --to=html`, скрипт не требуется.
- Нужен PDF — `pandoc --to=pdf` (нужен LaTeX), скрипт не требуется.
- Документ создан через WordArt/SmartArt/фигуры — они теряются при конвертации, это ограничение pandoc.

## Зависимости

- `pandoc` ≥ 3.x — основной конвертер
- `python3` — постобработка (`html_tables_to_md.py`)
- `mammoth` (python) — опциональная альтернатива для сложных таблиц

## Быстрый старт

```bash
# Результат рядом с файлом, в каталоге с тем же именем без расширения
bash framework/skills/tool-usage/content-generation/docx-convert/docx2md.sh "/path/to/file.docx"

# С указанием выходного каталога
bash framework/skills/tool-usage/content-generation/docx-convert/docx2md.sh "/path/to/file.docx" "/path/to/output"
```

Результат:
- `output/document.md` — текст в GFM с pipe-таблицами
- `output/images/` — все картинки из документа (png/jpeg/emf/wmf)

> При использовании из проекта, куда фреймворк установлен симлинками, путь к скрипту: `.claude/skills/docx-convert/docx2md.sh`.

## Ручные команды (без скрипта)

### Только текст
```bash
pandoc input.docx --from=docx --to=gfm --wrap=none -o output.md
```

### Текст + картинки
```bash
pandoc input.docx --from=docx --to=gfm --wrap=none \
    --extract-media=./images \
    -o output.md
```

### Через mammoth (для сложных таблиц/стилей)
```bash
python3 -c "
import mammoth, pathlib
result = mammoth.convert_to_markdown(open('input.docx', 'rb'))
pathlib.Path('output.md').write_text(result.value)
"
```

## Анти-паттерны

- **Конвертировать `.doc` (старый формат)** — pandoc принимает только `.docx`. Сначала пересохранить через LibreOffice/Word.
- **Ожидать сохранение формул** — OMML конвертируется в LaTeX частично, сложные формулы лучше пересобрать вручную.
- **Применять к сканам/PDF** — это не задача docx-convert; для OCR использовать другие инструменты.

## Примечания

- Сложные элементы Word (WordArt, SmartArt, фигуры) теряются — нормально для pandoc-конвертации.
- Встроенные картинки извлекаются корректно в форматах png, jpeg, emf, wmf.
- Постобработка (`html_tables_to_md.py`) обрабатывает только HTML-таблицы и `<img>`-теги, оставленные pandoc; остальной HTML сохраняется как есть.
