# Гайд по промтам для `image_generation` (через codex-image-gen wrapper)

> Базовый источник — рекомендации команды GPT-5 по работе с инструментом `image_generation`.
> Адаптация: добавлен раздел 0 «Что wrapper делает за тебя», чтобы агенты не дублировали технические инструкции.

## 0. Что wrapper делает за тебя (читай первым)

`codex_image_gen.py` сам подставляет в конец твоего промта:

1. **Hint размера:** `Use 1024x1024 unless the user explicitly asked for another size.`
2. **Команду сохранения:** `Save the resulting image as <filename> in the current working directory. Do not save under any other name and do not write outside the current working directory.`
3. **Список reference-файлов** (если передавал `--reference-image`) — они уже скопированы в CWD под именами `ref_0.png`, `ref_1.jpg` и т.п. Можно ссылаться на них в промте по короткому имени.

Поэтому в собственном промте НЕ дублируй: имя файла, путь сохранения, путь к референсам, дефолтный размер. Сосредоточься на содержимом картинки.

## 1. Общий принцип

Хороший промт описывает не идею, а наблюдаемый результат:
- что именно должно быть в кадре
- в каком стиле
- с каким ракурсом
- при каком свете
- с какими ограничениями

Плохой вариант:

```text
Сделай красиво
```

Хороший вариант:

```text
Clean editorial-style product photo of a ceramic coffee mug, centered, three-quarter view, soft studio lighting, warm neutral palette, subtle shadow, no text, no logo, no extra objects.
```

## 2. Что указывать в промпте для генерации нового изображения

Базовые оси:
- Сюжет: кто или что изображено.
- Композиция: крупный план, общий план, сверху, фронтально, centered, asymmetrical.
- Стиль: photo, illustration, 3D render, flat vector, watercolor, poster, pixel art, UI mockup.
- Свет: daylight, studio light, dramatic light, overcast, neon, golden hour.
- Цвет: muted, pastel, saturated, monochrome, warm, cold.
- Детализация: minimal, clean, highly detailed, realistic textures.
- Фон: transparent, plain white, city street, office interior, gradient backdrop.
- Ограничения: no text, no watermark, no extra hands, no background people.

Практические рекомендации:
- Сразу задавайте тип результата: `photo`, `illustration`, `3D render`, `diagram`, `mockup`.
- Пишите конкретно: не `современный офис`, а `bright open-plan office with oak desks and glass walls`.
- Если важно качество, указывайте уровень чистоты: `clean`, `polished`, `high legibility`, `minimal clutter`.
- Если нужен объект без окружения, явно пишите: `isolated on white background` или `transparent background`.
- Если нужно несколько объектов, указывайте точное количество.
- Если текст на изображении не нужен, явно пишите `no text`.
- Не перегружайте промпт десятками равноправных требований. Лучше 5-8 приоритетных характеристик.

## 3. Что указывать в промпте для редактирования существующего изображения

Для редактирования важно формулировать не заново всю картинку, а дельту изменений.

Хорошая структура:
- Что сохранить: композицию, позу, стиль, освещение, фон, пропорции.
- Что изменить: цвет, объект, фон, одежду, выражение лица, размер, материал.
- Что удалить: лишние люди, текст, логотип, шум, артефакты.
- Что добавить: новый объект, надпись, аксессуар, тень, фон, реквизит.
- Насколько бережно менять: `keep everything else unchanged`.

Рекомендации для редактирования:
- Начинайте с фразы вида `Keep the original composition and lighting`.
- Меняйте один смысловой блок за раз, если нужен предсказуемый результат.
- Если правка локальная, указывайте точную область: `change only the background`, `replace the shirt color only`.
- Если исходный стиль нужно сохранить, пишите это явно: `preserve the original illustration style`.
- Если удаляете объект, полезно указать чем заполнить место: `remove the person and fill the area with the same wall texture`.
- Если меняете фон, уточняйте, надо ли сохранить тени и направление света.
- Если нужен ретушный результат, добавляйте: `natural look`, `seamless edit`, `realistic blending`.
- Если нельзя трогать лицо, пропорции или позу, пишите это отдельно.

При работе через wrapper передавай оригинал через `--reference-image <path>`. В промте ссылайся на него по имени `ref_0.<ext>` (см. раздел 0).

## 4. Удобные шаблоны

### 4.1. Шаблон для генерации нового изображения

```text
[Image type] showing [main subject], [composition/view], in [style], with [lighting], [color palette], [detail level]. Background: [background]. Constraints: [what to avoid].
```

Пример:

```text
Product photo showing a matte black wireless mouse, top-down view, in a premium commercial style, with soft studio lighting, neutral gray palette, high detail. Background: clean light gray seamless backdrop. Constraints: no text, no logo, no extra objects.
```

### 4.2. Шаблон для редактирования существующего изображения

```text
Use ref_0.<ext> as the source. Keep [what must stay unchanged]. Change [specific change]. Remove [what to remove]. Add [what to add if needed]. Preserve [style/lighting/proportions/background if important]. Make the edit look [natural / seamless / realistic / clean].
```

Пример:

```text
Use ref_0.png as the source. Keep the original composition, pose, and lighting. Change the jacket color from blue to dark green. Remove the background person on the left. Preserve the original photo realism and skin tones. Make the edit look seamless and natural.
```

## 5. 10 готовых шаблонов для генерации нового изображения

### 1. Product photo

```text
Commercial product photo of [object], centered, three-quarter view, soft studio lighting, premium minimal aesthetic, realistic materials, subtle shadow, plain light background, no text, no extra objects.
```

### 2. Portrait

```text
Realistic portrait of [person description], chest-up framing, looking at camera, soft natural light, shallow depth of field, clean background, natural skin texture, calm expression, no text, no watermark.
```

### 3. Lifestyle scene

```text
Lifestyle photo of [subject] in [environment], candid moment, natural daylight, balanced composition, realistic details, warm and inviting atmosphere, no text, no extra visual clutter.
```

### 4. Office / business illustration

```text
Clean modern illustration of [business scene], isometric or front-facing composition, flat vector style, limited corporate color palette, high clarity, minimal clutter, white or very light background, no text.
```

### 5. UI / app mockup

```text
High-fidelity UI mockup for [app/screen], clean layout, excellent legibility, structured spacing, modern visual hierarchy, realistic device frame or flat presentation, no branding, no watermark.
```

### 6. Icon / sticker

```text
Simple icon-style illustration of [object], centered, bold silhouette, clean edges, flat colors, transparent background, no text, no shadow unless specified.
```

### 7. Poster / cover art

```text
Bold poster design featuring [subject], dramatic composition, striking typography area left empty, high contrast palette, cinematic lighting, polished graphic style, no actual text rendered.
```

### 8. Architectural interior

```text
Photorealistic interior rendering of [room], wide-angle view, balanced composition, natural daylight from windows, realistic materials, tasteful styling, clean and uncluttered, no people.
```

### 9. Concept art / fantasy scene

```text
Atmospheric concept art of [scene], wide cinematic shot, dramatic lighting, rich environmental details, cohesive color palette, epic mood, highly detailed, no text, no watermark.
```

### 10. Diagram / explainer visual

```text
Minimal explainer-style visual of [concept], clean composition, simple shapes, clear separation of elements, restrained palette, white background, high readability, no decorative clutter, no extra text unless explicitly required.
```

## 6. 10 готовых шаблонов для редактирования существующего изображения

### 1. Change color

```text
Use ref_0.<ext> as the source. Keep the original composition, lighting, and materials. Change the color of [object] to [new color]. Preserve everything else unchanged. Make the edit seamless and realistic.
```

### 2. Remove object

```text
Use ref_0.<ext> as the source. Remove [object/person] from the image. Fill the area naturally using the surrounding background and textures. Keep the original lighting, perspective, and overall scene unchanged.
```

### 3. Replace background

```text
Use ref_0.<ext> as the source. Keep the main subject unchanged. Replace the background with [new background]. Match the original lighting direction and perspective. Make the result look natural and well integrated.
```

### 4. Add object

```text
Use ref_0.<ext> as the source. Keep the original image style and composition. Add [object] in [location]. Match scale, lighting, perspective, and shadows so the new element blends naturally into the scene.
```

### 5. Retouch portrait

```text
Use ref_0.<ext> as the source. Keep the person recognizable and preserve natural skin texture. Remove minor blemishes, reduce under-eye shadows slightly, and clean up stray hairs. Do not over-smooth the face. Keep lighting and expression unchanged.
```

### 6. Change clothing

```text
Use ref_0.<ext> as the source. Keep the person’s pose, face, body proportions, and background unchanged. Replace the current clothing with [new clothing description]. Preserve realistic folds, lighting, and fabric texture.
```

### 7. Extend / uncrop image

```text
Use ref_0.<ext> as the source. Extend the image beyond its current borders in the same style and perspective. Continue the background naturally and keep the subject unchanged. Make the expansion seamless.
```

### 8. Convert style

```text
Use ref_0.<ext> as the source. Preserve the original composition and subject. Transform the image into [target style: watercolor / comic / 3D render / flat illustration]. Keep the scene recognizable and visually coherent.
```

### 9. Clean up image

```text
Use ref_0.<ext> as the source. Remove distracting elements, visual noise, artifacts, and unwanted small objects. Keep the main subject, framing, and lighting unchanged. Make the image cleaner without changing its meaning.
```

### 10. Add text area without rendering text

```text
Use ref_0.<ext> as the source. Keep the main subject and overall style. Recompose or simplify [top/bottom/left/right] area to create clean negative space for future text placement. Do not render any actual text.
```

## 7. Частые ошибки

- Слишком абстрактно: `сделай стильно`.
- Нет указания стиля.
- Нет указания ракурса.
- Нет ограничений, из-за чего появляются лишние объекты.
- Для редактирования не сказано, что именно нужно сохранить.
- Попытка внести слишком много разноплановых изменений за один шаг.
- Дублирование wrapper-инструкций (имя файла, путь сохранения) — wrapper подставит их сам, см. раздел 0.

## 8. Практический совет по итерациям

Если результат почти верный, следующую правку лучше формулировать коротко:

```text
Use ref_0.png as the source. Keep everything else unchanged. Make the background lighter and remove the extra object on the right.
```

(где `ref_0.png` — предыдущий результат, переданный в новый вызов wrapper через `--reference-image tasks/<id>/assets/<previous>.png`)

Если результат ушёл слишком далеко от цели, лучше пересобрать промпт целиком, а не накапливать много мелких исправлений.
