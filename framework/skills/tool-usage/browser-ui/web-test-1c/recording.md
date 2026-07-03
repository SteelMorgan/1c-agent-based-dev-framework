# Запись видео + субтитры

## Два пути: Vanessa и Playwright для browser-only задач

| | Vanessa Automation | Playwright (`web-test-1c`) |
|---|---|---|
| **Когда использовать** | Сценарий описан в `.feature`-файле; нужно демо-видео для команды с автоматическими субтитрами из шагов Gherkin | Сценарий browser-only, нужен JS/DOM-контроль, точный контроль браузерных оверлеев или fallback по правилам `va-visual-check` |
| **Формат сценария** | Gherkin / feature-файл | JS / `.test.mjs` или inline `exec` |
| **Заголовки/субтитры** | Генерируются из текстов шагов автоматически | Вручную через `showCaption()` + `addNarration()` |
| **UI 1С** | Полный стандартный клиент | Браузер, запущенный Playwright |
| **Зависимости** | Vanessa Automation, v8-runner | Node.js, ffmpeg, опционально node-edge-tts |

---

## Путь 1: Запись через Vanessa Automation (рекомендуемый)

Vanessa Automation умеет записывать видео прогона сценария и генерировать субтитры из шагов Gherkin — из коробки, без дополнительного кода.

### Параметры профиля Vanessa (va-params / tests.va)

```json
{
  "МаксимальноеВремяОжидания": 60,
  "ДелатьСнимкиЭкрана": true,
  "ЗаписыватьВидео": true,
  "ПутьКВидеозаписям": "./reports/video",
  "ГенерироватьСубтитры": true
}
```

Запись включается через профиль — отдельного кода в feature-файле не нужно.

### Запуск с видеозаписью

```bash
v8-runner test va --profile tests.va
```

Или через параметры командной строки:

```bash
v8-runner test va --params '{"ЗаписыватьВидео":true,"ПутьКВидеозаписям":"./reports/video"}'
```

### Результат

- Видео: `<ПутьКВидеозаписям>/<ИмяСценария>.mp4` (по одному файлу на сценарий)
- Субтитры: `.srt` или `.vtt` рядом с видео, текст берётся из имён шагов Gherkin
- При `ДелатьСнимкиЭкрана: true` — PNG-снимки по каждому шагу в `reports/screenshots/`

### Диагностика

Если видео не создалось — проверь:
1. `va-status.json` / `vanessa-execution.log` на ошибки (см. навык `vanessa-diagnostics`)
2. Права записи в каталог `ПутьКВидеозаписям`
3. Наличие кодека / ffmpeg в окружении (Vanessa может использовать его для кодирования)

---

## Путь 2: Запись через Playwright для browser-only задач

Используй, когда сценарий относится к браузерному слою, написан на JS в `web-test-1c` или выбран как fallback по правилам `va-visual-check`. Для обычного 1C UI-сценария перед Playwright зафиксируй выполненные VA-шаги, причину fallback, почему browser/web-client даёт достаточный сигнал, и остаточный риск отличий клиента.

### Предварительные требования

**ffmpeg** — обязателен. Варианты установки:

- Локально в проект: `tools/ffmpeg/bin/ffmpeg` (ищется автоматически)
- Глобально в PATH
- Через `.v8-project.json`: `{ "ffmpegPath": "/opt/ffmpeg/bin/ffmpeg" }`

Порядок поиска: `opts.ffmpegPath` → `FFMPEG_PATH` → PATH → `tools/ffmpeg/bin/ffmpeg[.exe]`

**node-edge-tts** (опционально, для TTS-субтитров):

```bash
npm install --prefix tools/tts node-edge-tts
```

### API записи

#### `startRecording(outputPath, opts?)`

| Параметр | Тип | По умолч. | Описание |
|----------|-----|-----------|----------|
| `outputPath` | string | обязателен | Путь к выходному .mp4 |
| `opts.fps` | number | 25 | Частота кадров |
| `opts.quality` | number | 80 | Качество JPEG (1-100) |
| `opts.ffmpegPath` | string | auto | Явный путь к ffmpeg |
| `opts.speechRate` | number | 70 | Мс/символ для умного TTS-ожидания |

#### `stopRecording()` → `{ file, duration, size, captions }`

Останавливает запись и финализирует MP4. Сохраняет `.captions.json` рядом с видео.

#### `showCaption(text, opts?)`

Отображает текстовый оверлей поверх страницы (виден в записи).

| Параметр | Тип | По умолч. | Описание |
|----------|-----|-----------|----------|
| `text` | string | обязателен | Текст субтитра |
| `opts.position` | `'top'`\|`'bottom'` | `'bottom'` | Вертикальная позиция |
| `opts.fontSize` | number | 24 | Размер шрифта (px) |
| `opts.speech` | string\|false | — | Текст для TTS (пусто = показанный текст, false = без озвучки) |
| `opts.voice` | string | — | Голос для этого субтитра (override глобального) |

**Smart TTS wait**: `showCaption` автоматически делает паузу на расчётное время произнесения текста (~70 мс/символ, мин 2 сек). Последующий `wait()` учитывает этот кредит.

#### `addNarration(videoPath, opts?)` → `{ file, duration, size, captions }`

Генерирует TTS и накладывает аудио на видео. Вызывается после `stopRecording()`.

| Параметр | Тип | Описание |
|----------|-----|----------|
| `videoPath` | string | Путь к записанному MP4 |
| `opts.provider` | string | `'edge'` (default), `'openai'`, `'elevenlabs'` |
| `opts.voice` | string | Имя голоса (зависит от провайдера) |
| `opts.apiKey` | string | API-ключ (для openai/elevenlabs) |
| `opts.outputPath` | string | Выходной файл (по умолч. `video-narrated.mp4`) |

#### Дополнительные оверлеи

| Функция | Описание |
|---------|----------|
| `hideCaption()` | Убрать субтитр |
| `showTitleSlide(text, opts?)` | Полноэкранный титульный слайд (intro/outro) |
| `hideTitleSlide()` | Убрать титул |
| `showImage(path, opts?)` | Полноэкранный оверлей картинки (стили: `blur`\|`dark`\|`light`\|`full`) |
| `hideImage()` | Убрать картинку |
| `setHighlight(on)` | Авто-подсветка элементов перед каждым действием (для видео) |
| `highlight(text)` / `unhighlight()` | Ручная подсветка элемента |
| `isRecording()` | Проверить, идёт ли запись |
| `getCaptions()` | Получить субтитры из последней записи |

### Конфигурация TTS в `.v8-project.json`

```json
{
  "ffmpegPath": "C:\\tools\\ffmpeg\\bin\\ffmpeg.exe",
  "tts": {
    "provider": "edge",
    "voice": "ru-RU-DmitryNeural"
  }
}
```

Edge TTS — бесплатно, без API-ключа (нужен интернет). Для OpenAI:
```json
{ "tts": { "provider": "openai", "apiKey": "sk-...", "voice": "alloy" } }
```

### Пример: запись воркфлоу с субтитрами и авто-подсветкой

```js
await startRecording('recordings/create-order.mp4');

await showTitleSlide('Создание заказа клиента', {
  subtitle: 'Демонстрация',
  speech: 'Создание заказа клиента. Демонстрация.'
});
await wait(1);
await hideTitleSlide();

setHighlight(true);  // авто-подсветка перед каждым действием

await showCaption('Шаг 1. Переходим в раздел «Продажи»');
await wait(1.5);
await navigateSection('Продажи');

await showCaption('Шаг 2. Открываем заказы клиентов');
await wait(1.5);
await openCommand('Заказы клиентов');

await showCaption('Шаг 3. Создаём новый заказ');
await wait(1.5);
await clickElement('Создать');
await wait(2);

await showCaption('Шаг 4. Заполняем шапку');
await wait(1.5);
await fillFields({ 'Организация': 'Конфетпром', 'Контрагент': 'Альфа' });

await hideCaption();
setHighlight(false);
const video = await stopRecording();
console.log(`Записано ${video.duration}с, ${(video.size/1024/1024).toFixed(1)} МБ`);

// Добавляем озвучку
const narrated = await addNarration(video.file, { voice: 'ru-RU-DmitryNeural' });
console.log(`С озвучкой: ${narrated.file}`);
```

**Порядок: субтитр → пауза → действие.** `showCaption` делает паузу под TTS сам; `wait()` добавляй только если нужно дождаться загрузки формы.

### Повторная озвучка без перезаписи

После `stopRecording()` рядом с видео сохраняется `.captions.json`. Можно озвучить другим голосом не переснимая:

```js
const result = await addNarration('recordings/demo.mp4', { voice: 'ru-RU-SvetlanaNeural' });
```

### Интеграция с регресс-тестами

Чтобы включить запись для всего регресс-сьюта, задай `record: true` в `webtest.config.mjs`
(по умолчанию `record: false`). Полная схема конфига и `severity`-маппинг — [regress.md](regress.md)
§ webtest.config.mjs. Для отдельных тестов помечай их `export const tags = ['recording']` —
в дефолтном `severity` они попадают в `minor` (см. regress.md § Тяжесть тестов).

## Устранение неполадок (Playwright-путь)

| Проблема | Решение |
|---------|---------|
| "ffmpeg not found" | Установи ffmpeg, проверь путь (см. выше) |
| Файл 0 байт | Проверь права записи в каталог; ffmpeg мог упасть |
| Рывки в видео | Добавь `wait()` между шагами; уменьши `quality` |
| "Already recording" | Вызови `stopRecording()` перед новой записью |
| Нет субтитров | Использовался `showCaption()` во время записи? Или передай `opts.captions` в `addNarration` |
| TTS timeout | Edge TTS требует интернет; проверь соединение |
