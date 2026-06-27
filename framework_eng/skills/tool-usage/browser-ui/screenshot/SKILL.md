---
name: "screenshot"
description: "OS screenshots of the desktop, a window, or a screen region"
---

# Taking Screenshots

Save rules:
1. User specified a path → save there.
2. User asked without a path → use the OS default location.
3. Codex self-check → temporary directory.

Prefer specialized image-capture tools (Figma MCP, Playwright, agent-browser, VA MCP) when the target domain has such a tool. Use this skill for explicit requests to capture the desktop/window, full-screen captures, or domains where no specialized tool exists.

For 1C:Enterprise UI/forms, first use the specialized `va-visual-check` skill. Use this OS-screenshot skill for 1C only as a fallback according to the `va-visual-check` rules, recording the completed VA steps, the reason for the fallback, and the residual risk.

## macOS permission preflight

Run once before window/app capture. Combine preflight + capture to reduce sandbox prompts:

```bash
bash <path-to-skill>/scripts/ensure_macos_permissions.sh && \
python3 <path-to-skill>/scripts/take_screenshot.py --app "<App>" --mode temp
```

## Python helper (macOS and Linux)

```bash
python3 <path-to-skill>/scripts/take_screenshot.py [OPTIONS]
```

| Option | Example | Note |
|--------|---------|------|
| (none) | | OS default location |
| `--mode temp` | | Codex visual check |
| `--path <path>` | `--path output/screen.png` | Explicit location |
| `--app "<Name>"` | `--app "Codex"` | macOS only, substring match |
| `--app "<Name>" --window-name "<Title>"` | | macOS only |
| `--list-windows --app "<Name>"` | | macOS only, discover window ids |
| `--region x,y,w,h` | `--region 100,200,800,600` | |
| `--active-window` | | Frontmost window |
| `--window-id <id>` | `--window-id 12345` | |

The script prints one path per capture. Multiple windows/displays produce multiple paths with `-w<id>` or `-d<display>` suffixes.

### Linux tool selection (automatic)

Priority: `scrot` → `gnome-screenshot` → ImageMagick `import` → `ffmpeg` (x11grab). For X11 virtual displays, `ffmpeg` is often the most practical option in containers:

```bash
ffmpeg -y -f x11grab -video_size 1920x1080 -i :99 -frames:v 1 /tmp/screen.png
```

Display dimensions: `DISPLAY=:99 xdpyinfo | grep dimensions`

`--app`, `--window-name`, `--list-windows` are macOS-only. On Linux use `--active-window` or `--window-id`.

For 1C screenshots in Xvfb, see `va-visual-check`: it describes the VA flow, exposing the window before the VA capture, and the fallback conditions.

## PowerShell helper (Windows)

```powershell
powershell -ExecutionPolicy Bypass -File <path-to-skill>/scripts/take_screenshot.ps1 [OPTIONS]
```

| Option | Example |
|--------|---------|
| (none) | Default location |
| `-Mode temp` | Codex visual check |
| `-Path "<path>"` | Explicit |
| `-Region x,y,w,h` | Pixel region |
| `-ActiveWindow` | Ask user to focus first |
| `-WindowHandle <id>` | Specific window |

## Direct OS screenshots

### macOS

```bash
screencapture -x output/screen.png                    # full screen
screencapture -x -R100,200,800,600 output/region.png  # region
screencapture -x -l12345 output/window.png            # window id
```

### Linux

```bash
scrot output/screen.png                               # full
scrot -a 100,200,800,600 output/region.png            # region
scrot -u output/window.png                            # active window
import -window root output/screen.png                 # ImageMagick full
ffmpeg -y -f x11grab -video_size 800x600 -i :99+100,200 -frames:v 1 output/region.png
```

## Error handling

- macOS sandbox errors ("screen capture blocked", `ModuleCache`) → rerun with escalated permissions
- macOS no matches → `--list-windows --app "Name"` → retry with `--window-id`
- Linux tool missing → `command -v scrot`, `command -v import`
- 1C VA PNG is black/solid-color → follow `va-visual-check`
- Always report saved file path
