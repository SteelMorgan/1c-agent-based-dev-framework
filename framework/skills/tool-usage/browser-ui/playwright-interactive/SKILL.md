---
name: "playwright-interactive"
description: "Use for итеративной отладки UI веб-приложений и Electron через персистентную `js_repl`-сессию Playwright. Helps сохранять браузерные хендлы живыми между шагами без перезапуска."
---

# Playwright Interactive Skill

Persistent `js_repl` Playwright session for iterative UI debugging of web and Electron apps. Keep the same handles alive across iterations.

## Preconditions

- `js_repl` must be enabled (`~/.codex/config.toml`: `[features] js_repl = true`, or `--enable js_repl`).
- Start with `--sandbox danger-full-access` (temporary requirement).
- Run setup from the project directory you need to debug.
- Treat `js_repl_reset` as recovery only. Resetting destroys Playwright handles.

## One-time setup

```bash
test -f package.json || npm init -y
npm install playwright
node -e "import('playwright').then(() => console.log('playwright import ok')).catch((error) => { console.error(error); process.exit(1); })"
```

For web: `npx playwright install chromium`. For Electron: `npm install --save-dev electron`.

## Core Workflow

1. Build QA inventory from: user requirements, implemented features, claims for final response.
2. Run bootstrap cell once (see `references/snippets.md` > Bootstrap).
3. Start or confirm dev server in persistent TTY.
4. Launch runtime, keep reusing Playwright handles.
5. After code change: reload (renderer-only) or relaunch (main-process/startup).
6. Run functional QA.
7. Run separate visual QA pass.
8. Verify viewport fit, capture signoff screenshots.
9. Clean up only when task is finished.

## Session Modes

All code snippets for session setup are in `references/snippets.md`.

| Mode | When | Key parameters | Snippet |
|------|------|---------------|---------|
| Desktop web | Default iteration | `viewport: { width: 1600, height: 900 }` | snippets.md > Desktop Web Context |
| Mobile web | Mobile testing | `viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true` | snippets.md > Mobile Web Context |
| Native-window web | OS-level DPI, browser chrome validation | `viewport: null` | snippets.md > Native-Window Web Pass |
| Electron | Desktop app | `electronLauncher.launch({ args: [ELECTRON_ENTRY] })` | snippets.md > Electron Session |

Binding rules:
- Use `var` for top-level Playwright handles (reused across `js_repl` cells).
- One named handle per surface: `page`, `mobilePage`, `appWindow`.
- Stale handle: set to `undefined` and rerun the cell.
- Switching modes = context reset. Close old page/context, create new.

## Reload vs Relaunch

| Change type | Action |
|-------------|--------|
| Renderer-only | `await reloadWebContexts()` or `await appWindow.reload(...)` |
| Main-process, preload, startup | Relaunch Electron (snippets.md > Electron Restart) |
| Uncertain process ownership | Relaunch |

## Screenshots

Default: CSS-normalized, JPEG quality 85. All helpers in `references/snippets.md` > Screenshot Helpers.

| Surface | Default path | Fallback (native-window) |
|---------|-------------|--------------------------|
| Web page | `await emitWebJpeg(page)` | `emitWebScreenshotCssScaled({ page })` |
| Mobile web | `await emitWebJpeg(mobilePage)` | same with `mobilePage` |
| Electron | `emitElectronScreenshotCssScaled({ electronApp })` | (always main-process path) |
| Raw (fidelity-sensitive only) | `surface.screenshot({ type: "jpeg", quality: 85 })` | |

Key rules:
- Do not assume `scale: "css"` works in native-window mode; Chromium Retina may return device pixels.
- Electron: never use `appWindow.context().newPage()` as scratch page.
- For clipped captures, add clip origin back when clicking: `clickCssPoint({ surface, clip, x, y })`.

## Viewport Fit Checks (Required)

Before signoff, verify the intended initial view. Snippet in `references/snippets.md` > Viewport Fit Check.

- Screenshots are primary evidence; numeric checks support, not overrule.
- Signoff fails if required regions are clipped, even if scroll metrics look clean.
- Fixed-shell interfaces: scrolling is not an acceptable workaround for primary interactive surface.
- Check region bounds (via `getBoundingClientRect()`), not just document bounds.
- For Electron: verify launched window size and placement before any resize.

## Dev Server

Keep the app running in a persistent TTY session. Verify port is listening before `page.goto(...)`. For Electron with a separate dev server (Vite/Next), keep that running first.

## Cleanup

Run cleanup cell from `references/snippets.md` > Cleanup when finished. Exiting Codex does not implicitly run `browser.close()` or `electronApp.close()`.

## Checklists

### Session Loop

- Bootstrap once, keep handles alive.
- Make code change.
- Reload or relaunch.
- Update QA inventory if exploration reveals new controls/states.
- Re-run functional QA.
- Re-run visual QA.
- Capture final artifacts.

### Functional QA

- Use real user controls (keyboard, mouse, click, touch, Playwright input APIs).
- Verify at least one end-to-end critical flow with visible result confirmation.
- Work through the shared QA inventory, not ad hoc.
- Cover every visible control at least once; for stateful toggles test full cycle.
- After scripted checks: 30-90 second exploratory pass with normal input.
- `page.evaluate(...)` inspects state but does not count as signoff input.

### Visual QA

- Separate from functional QA; use the same shared QA inventory.
- Restate user-visible claims and verify each in the specific state where it matters.
- Inspect initial viewport before scrolling.
- Inspect all required visible regions, not just the main surface.
- Check at least one post-interaction state and one in-transition state for animations.
- For dynamic visuals: inspect densest realistic state, not empty/loading.
- If minimum supported viewport is defined, run separate visual QA pass there.
- Distinguish presence from perceptibility: weak contrast, occlusion, clipping = visual failure.
- Look for: clipping, overflow, distortion, layout imbalance, spacing, alignment, legibility, contrast, layering, motion states.
- Judge aesthetic quality, not just correctness.
- Prefer viewport screenshots; full-page only as debugging artifacts.
- Before signoff ask: what part have I not inspected? What defect would most embarrass this result?

### Signoff

- Functional path passed with normal user input.
- Coverage explicit against QA inventory with intentional exclusions noted.
- Visual QA covered the whole relevant interface.
- Each claim has matching visual check and screenshot from the right state/viewport.
- Viewport-fit checks passed for initial view and any required minimum viewport.
- If product launches in window: as-launched size, placement, layout checked before resize.
- UI is visually coherent, not just functional.
- Functional correctness, viewport fit, and visual quality each pass independently.
- Exploratory pass completed; response mentions what it covered.
- Screenshot vs. numeric check discrepancies investigated before signoff.
- Brief negative confirmation of defect classes checked and not found.
- Cleanup executed, or session intentionally kept alive.

## Common Failure Modes

- `Cannot find module 'playwright'`: run one-time setup, verify import.
- Browser executable missing: `npx playwright install chromium`.
- `net::ERR_CONNECTION_REFUSED`: dev server running? Recheck port, prefer `http://127.0.0.1:<port>`.
- `electron.launch` hangs: verify `electron` dep, `args` target, renderer dev server running.
- `Identifier has already been declared`: reuse bindings, new name, or `{ ... }` block.
- `Not supported` on `browserContext.newPage` with Electron: use Electron-specific screenshot path.
- `js_repl` timeout: rerun bootstrap, use shorter cells.
- Network fails immediately: confirm `--sandbox danger-full-access`.
