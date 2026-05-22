# Video Recording + Subtitles

## Two Paths: Vanessa (Preferred) and Playwright (Fallback)

| | Vanessa Automation | Playwright (`web-test-1c`) |
|---|---|---|
| **When to use** | The scenario is described in a `.feature` file; you need a demo video for the team with automatic subtitles from Gherkin steps | Vanessa is unavailable; you need headless mode or the scenario is written in JS; precise control over overlays is required |
| **Scenario format** | Gherkin / feature file | JS / `.test.mjs` or inline `exec` |
| **Titles/subtitles** | Generated automatically from step texts | Manually via `showCaption()` + `addNarration()` |
| **1C UI** | Full standard client | Browser launched by Playwright |
| **Dependencies** | Vanessa Automation, v8-runner | Node.js, ffmpeg, optional node-edge-tts |

---

## Path 1: Recording via Vanessa Automation (recommended)

Vanessa Automation can record scenario runs and generate subtitles from Gherkin steps out of the box, without extra code.

### Vanessa profile parameters (va-params / tests.va):

```json
{
  "МаксимальноеВремяОжидания": 60,
  "ДелатьСнимкиЭкрана": true,
  "ЗаписыватьВидео": true,
  "ПутьКВидеозаписям": "./reports/video",
  "ГенерироватьСубтитры": true
}
```

Recording is enabled through the profile - no separate code is needed in the feature file.

### Launch with video recording

```bash
v8-runner test va --profile tests.va
```

Or through command-line parameters:

```bash
v8-runner test va --params '{"ЗаписыватьВидео":true,"ПутьКВидеозаписям":"./reports/video"}'
```

### Result

- Video: `<ПутьКВидеозаписям>/<ИмяСценария>.mp4` (one file per scenario)
- Subtitles: `.srt` or `.vtt` next to the video, text is taken from Gherkin step names
- With `ДелатьСнимкиЭкрана: true` - PNG screenshots for each step in `reports/screenshots/`

### Diagnostics

If the video was not created, check:
1. `va-status.json` / `vanessa-execution.log` for errors (see the `vanessa-diagnostics` skill)
2. Write permissions in the `ПутьКВидеозаписям` directory
3. Presence of a codec / ffmpeg in the environment (Vanessa may use it for encoding)

---

## Path 2: Recording via Playwright (fallback)

Use this when Vanessa is unavailable, headless mode is needed, or the scenario is written in JS in `web-test-1c`.

### Prerequisites

**ffmpeg** - required. Installation options:

- Local in the project: `tools/ffmpeg/bin/ffmpeg` (detected automatically)
- Global in PATH
- Through `.v8-project.json`: `{ "ffmpegPath": "/opt/ffmpeg/bin/ffmpeg" }`

Search order: `opts.ffmpegPath` → `FFMPEG_PATH` → PATH → `tools/ffmpeg/bin/ffmpeg[.exe]`

**node-edge-tts** (optional, for TTS subtitles):

```bash
npm install --prefix tools/tts node-edge-tts
```

### Recording API

#### `startRecording(outputPath, opts?)`

| Parameter | Type | Default | Description |
|----------|-----|---------|----------|
| `outputPath` | string | required | Path to the output `.mp4` |
| `opts.fps` | number | 25 | Frame rate |
| `opts.quality` | number | 80 | JPEG quality (1-100) |
| `opts.ffmpegPath` | string | auto | Explicit path to ffmpeg |
| `opts.speechRate` | number | 70 | Ms/character for smart TTS waiting |

#### `stopRecording()` → `{ file, duration, size, captions }`

Stops recording and finalizes the MP4. Saves `.captions.json` next to the video.

#### `showCaption(text, opts?)`

Displays a text overlay on top of the page (visible in the recording).

| Parameter | Type | Default | Description |
|----------|-----|---------|----------|
| `text` | string | required | Subtitle text |
| `opts.position` | `'top'`\|`'bottom'` | `'bottom'` | Vertical position |
| `opts.fontSize` | number | 24 | Font size (px) |
| `opts.speech` | string\|false | — | Text for TTS (empty = displayed text, false = no narration) |
| `opts.voice` | string | — | Voice for this subtitle (overrides global) |

**Smart TTS wait**: `showCaption` automatically pauses for the estimated speaking time of the text (~70 ms/character, min 2 sec). The subsequent `wait()` accounts for this credit.

#### `addNarration(videoPath, opts?)` → `{ file, duration, size, captions }`

Generates TTS and overlays audio onto the video. Called after `stopRecording()`.

| Parameter | Type | Description |
|----------|-----|----------|
| `videoPath` | string | Path to the recorded MP4 |
| `opts.provider` | string | `'edge'` (default), `'openai'`, `'elevenlabs'` |
| `opts.voice` | string | Voice name (depends on provider) |
| `opts.apiKey` | string | API key (for openai/elevenlabs) |
| `opts.outputPath` | string | Output file (default `video-narrated.mp4`) |

#### Additional overlays

| Function | Description |
|---------|----------|
| `hideCaption()` | Remove the subtitle |
| `showTitleSlide(text, opts?)` | Full-screen title slide (intro/outro) |
| `hideTitleSlide()` | Remove the title |
| `showImage(path, opts?)` | Full-screen image overlay (styles: `blur`\|`dark`\|`light`\|`full`) |
| `hideImage()` | Remove the image |
| `setHighlight(on)` | Auto-highlight elements before each action (for video) |
| `highlight(text)` / `unhighlight()` | Manual element highlighting |
| `isRecording()` | Check whether recording is in progress |
| `getCaptions()` | Get subtitles from the last recording |

### TTS configuration in `.v8-project.json`

```json
{
  "ffmpegPath": "C:\\tools\\ffmpeg\\bin\\ffmpeg.exe",
  "tts": {
    "provider": "edge",
    "voice": "ru-RU-DmitryNeural"
  }
}
```

Edge TTS is free, with no API key required (internet needed). For OpenAI:
```json
{ "tts": { "provider": "openai", "apiKey": "sk-...", "voice": "alloy" } }
```

### Example: recording a workflow with subtitles and auto-highlighting

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

**Order: subtitle → pause → action.** `showCaption` handles the TTS pause on its own; add `wait()` only if you need to wait for the form to load.

### Re-narration without re-recording

After `stopRecording()`, a `.captions.json` file is saved next to the video. You can narrate it with a different voice without re-shooting:

```js
const result = await addNarration('recordings/demo.mp4', { voice: 'ru-RU-SvetlanaNeural' });
```

### Integration with regression tests

In `webtest.config.mjs`, you can enable recording for the entire suite:

```js
export default {
  url: '...',
  record: true,   // запись каждого теста
  screenshot: 'on-failure',
};
```

Or for individual tests via `export const tags = ['recording']` + severity mapping.

## Troubleshooting (Playwright path)

| Problem | Solution |
|---------|---------|
| "ffmpeg not found" | Install ffmpeg, check the path (see above) |
| 0-byte file | Check write permissions in the directory; ffmpeg may have crashed |
| Stuttering video | Add `wait()` between steps; reduce `quality` |
| "Already recording" | Call `stopRecording()` before starting a new recording |
| No subtitles | Was `showCaption()` used during recording? Or pass `opts.captions` to `addNarration` |
| TTS timeout | Edge TTS requires internet; check the connection |
