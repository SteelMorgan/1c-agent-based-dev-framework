# Video recording + captions

## Two paths: Vanessa and Playwright for browser-only tasks

| | Vanessa Automation | Playwright (`web-test-1c`) |
|---|---|---|
| **When to use** | The scenario is described in a `.feature` file; you need a demo video for the team with automatic captions generated from Gherkin steps | The scenario is browser-only, you need JS/DOM control, precise control over browser overlays, or a fallback according to the `va-visual-check` rules |
| **Scenario format** | Gherkin / feature file | JS / `.test.mjs` or inline `exec` |
| **Titles/captions** | Generated automatically from step texts | Manually via `showCaption()` + `addNarration()` |
| **1C UI** | Full standard client | Browser launched by Playwright |
| **Dependencies** | Vanessa Automation, v8-runner | Node.js, ffmpeg, optionally node-edge-tts |

---

## Path 1: Recording through Vanessa Automation (recommended)

Vanessa Automation can record a scenario run video and generate captions from Gherkin steps out of the box, without any additional code.

### Vanessa profile parameters (va-params / tests.va)

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

### Run with video recording

```bash
v8-runner test va --profile tests.va
```

Or via command-line parameters:

```bash
v8-runner test va --params '{"ЗаписыватьВидео":true,"ПутьКВидеозаписям":"./reports/video"}'
```

### Result

- Video: `<ПутьКВидеозаписям>/<ИмяСценария>.mp4` (one file per scenario)
- Captions: `.srt` or `.vtt` next to the video, text is taken from Gherkin step names
- With `ДелатьСнимкиЭкрана: true` - PNG screenshots for each step in `reports/screenshots/`

### Diagnostics

If the video was not created, check:
1. `va-status.json` / `vanessa-execution.log` for errors (see the `vanessa-diagnostics` skill)
2. Write permissions for the `ПутьКВидеозаписям` directory
3. The presence of a codec / ffmpeg in the environment (Vanessa may use it for encoding)

---

## Path 2: Recording through Playwright for browser-only tasks

Use this when the scenario belongs to the browser layer, is written in JS in `web-test-1c`, or is chosen as a fallback according to the `va-visual-check` rules. For a regular 1C UI scenario, before Playwright, record the completed VA steps, the reason for the fallback, why the browser/web client provides a sufficient signal, and the residual risk of client differences.

### Prerequisites

**ffmpeg** - required. Installation options:

- Locally in the project: `tools/ffmpeg/bin/ffmpeg` (detected automatically)
- Globally in PATH
- Through `.v8-project.json`: `{ "ffmpegPath": "/opt/ffmpeg/bin/ffmpeg" }`

Search order: `opts.ffmpegPath` → `FFMPEG_PATH` → PATH → `tools/ffmpeg/bin/ffmpeg[.exe]`

**node-edge-tts** (optional, for TTS captions):

```bash
npm install --prefix tools/tts node-edge-tts
```

### Recording API

#### `startRecording(outputPath, opts?)`

| Parameter | Type | Default | Description |
|----------|-----|-----------|----------|
| `outputPath` | string | required | Path to the output .mp4 |
| `opts.fps` | number | 25 | Frame rate |
| `opts.quality` | number | 80 | JPEG quality (1-100) |
| `opts.ffmpegPath` | string | auto | Explicit path to ffmpeg |
| `opts.speechRate` | number | 70 | ms/char for smart TTS waiting |

#### `stopRecording()` → `{ file, duration, size, captions }`

Stops recording and finalizes the MP4. Saves `.captions.json` next to the video.

#### `showCaption(text, opts?)`

Displays a text overlay over the page (visible in the recording).

| Parameter | Type | Default | Description |
|----------|-----|-----------|----------|
| `text` | string | required | Caption text |
| `opts.position` | `'top'`\|`'bottom'` | `'bottom'` | Vertical position |
| `opts.fontSize` | number | 24 | Font size (px) |
| `opts.speech` | string\|false | — | Text for TTS (empty = displayed text, false = no narration) |
| `opts.voice` | string | — | Voice for this caption (global override) |

**Smart TTS wait**: `showCaption` automatically pauses for the estimated speaking time of the text (~70 ms/char, min 2 sec). The following `wait()` takes this credit into account.

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
| `hideCaption()` | Hide the caption |
| `showTitleSlide(text, opts?)` | Full-screen title slide (intro/outro) |
| `hideTitleSlide()` | Hide the title |
| `showImage(path, opts?)` | Full-screen image overlay (styles: `blur`\|`dark`\|`light`\|`full`) |
| `hideImage()` | Hide the image |
| `setHighlight(on)` | Auto-highlight elements before each action (for video) |
| `highlight(text)` / `unhighlight()` | Manual element highlighting |
| `isRecording()` | Check whether recording is active |
| `getCaptions()` | Get captions from the last recording |

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

Edge TTS is free, with no API key required (internet access is needed). For OpenAI:
```json
{ "tts": { "provider": "openai", "apiKey": "sk-...", "voice": "alloy" } }
```

### Example: recording a workflow with captions and auto-highlight

```js
await startRecording('recordings/create-order.mp4');

await showTitleSlide('Создание заказа клиента', {
  subtitle: 'Демонстрация',
  speech: 'Создание заказа клиента. Демонстрация.'
});
await wait(1);
await hideTitleSlide();

setHighlight(true);  // auto-highlight before each action

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

**Order: caption → pause → action.** `showCaption` handles the TTS pause itself; add `wait()` only if you need to wait for the form to load.

### Re-narration without re-recording

After `stopRecording()`, a `.captions.json` file is saved next to the video. You can narrate it again with a different voice without re-shooting:

```js
const result = await addNarration('recordings/demo.mp4', { voice: 'ru-RU-SvetlanaNeural' });
```

### Integration with regression tests

In `webtest.config.mjs`, you can enable recording for the entire suite:

```js
export default {
  url: '...',
  record: true,   // record every test
  screenshot: 'on-failure',
};
```

Or for individual tests via `export const tags = ['recording']` + severity mapping.

## Troubleshooting (Playwright path)

| Problem | Solution |
|---------|---------|
| "ffmpeg not found" | Install ffmpeg, check the path (see above) |
| 0-byte file | Check write permissions in the directory; ffmpeg may have crashed |
| Video stutter | Add `wait()` between steps; reduce `quality` |
| "Already recording" | Call `stopRecording()` before starting a new recording |
| No captions | Was `showCaption()` used during recording? Or pass `opts.captions` to `addNarration` |
| TTS timeout | Edge TTS requires internet access; check the connection |
