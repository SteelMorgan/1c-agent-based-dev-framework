#!/usr/bin/env node

const fs = require('fs');
const os = require('os');
const path = require('path');

const e = '\x1b';
const c = {
  blue: `${e}[38;2;0;153;255m`,
  green: `${e}[38;2;0;160;0m`,
  cyan: `${e}[38;2;100;200;200m`,
  red: `${e}[38;2;255;85;85m`,
  yellow: `${e}[38;2;230;200;0m`,
  white: `${e}[38;2;220;220;220m`,
  gray: `${e}[38;2;180;180;180m`,
  purple: `${e}[38;2;167;107;206m`,
  dkgreen: `${e}[38;2;0;120;0m`,
  dkyellow: `${e}[38;2;80;80;0m`,
  dkred: `${e}[38;2;180;50;50m`,
  dim: `${e}[2m`,
  reset: `${e}[0m`,
};

function readStdin() {
  return new Promise((resolve) => {
    let data = '';
    process.stdin.setEncoding('utf8');
    process.stdin.on('data', (chunk) => (data += chunk));
    process.stdin.on('end', () => resolve(data));
  });
}

function stripAnsi(text) {
  return text.replace(/\x1b\[[0-9;]*m/g, '');
}

function padColumn(text, width) {
  const visible = stripAnsi(text).length;
  return visible < width ? text + ' '.repeat(width - visible) : text;
}

function buildBar(pct, width) {
  const clamped = Math.max(0, Math.min(100, Number.isFinite(pct) ? pct : 0));
  const filled = Math.round((clamped * width) / 100);
  const empty = width - filled;
  const color = clamped >= 75 ? c.red : clamped >= 50 ? c.yellow : c.green;
  return `${color}${'●'.repeat(filled)}${c.dim}${'○'.repeat(empty)}${c.reset}`;
}

function formatResetTime(iso, style = 'date') {
  if (!iso) return '';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  if (style === 'time') {
    return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false });
  }
  if (style === 'datetime') {
    const month = d.toLocaleString([], { month: 'short' }).toLowerCase();
    const day = d.getDate();
    const time = d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false });
    return `${month} ${day}, ${time}`;
  }
  return d.toLocaleDateString();
}

function readJsonSafe(file) {
  try {
    if (!fs.existsSync(file)) return null;
    return JSON.parse(fs.readFileSync(file, 'utf8'));
  } catch {
    return null;
  }
}

async function getUsageData() {
  const cacheFile = path.join(os.tmpdir(), 'claude-statusline-usage-cache.json');
  const cacheMaxAgeMs = 60_000;

  try {
    if (fs.existsSync(cacheFile)) {
      const stat = fs.statSync(cacheFile);
      if (Date.now() - stat.mtimeMs < cacheMaxAgeMs) {
        const cached = readJsonSafe(cacheFile);
        if (cached) return cached;
      }
    }
  } catch {}

  const credsPath = path.join(os.homedir(), '.claude', '.credentials.json');
  const creds = readJsonSafe(credsPath);
  const token = creds?.claudeAiOauth?.accessToken;
  if (!token || typeof fetch !== 'function') {
    return readJsonSafe(cacheFile);
  }

  try {
    const response = await fetch('https://api.anthropic.com/api/oauth/usage', {
      method: 'GET',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
        'anthropic-beta': 'oauth-2025-04-20',
        'User-Agent': 'claude-code/statusline-js',
      },
      signal: AbortSignal.timeout(5000),
    });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const data = await response.json();
    fs.writeFileSync(cacheFile, JSON.stringify(data));
    return data;
  } catch {
    return readJsonSafe(cacheFile);
  }
}

function getEffortLevel() {
  const settingsPath = path.join(os.homedir(), '.claude', 'settings.json');
  const settings = readJsonSafe(settingsPath);
  if (!settings) return '';

  const thinkingOn = settings.alwaysThinkingEnabled === true;
  if (!thinkingOn) return '';

  if (settings.model) return '';
  return settings.effortLevel || 'medium';
}

function effortColor(level) {
  if (level === 'high') return c.dkgreen;
  if (level === 'medium') return c.dkyellow;
  if (level === 'low') return c.dkred;
  return c.gray;
}

async function main() {
  try {
    const inputText = (await readStdin()).trim();
    if (!inputText) {
      process.stdout.write('Claude');
      return;
    }

    const json = JSON.parse(inputText);
    const modelName = json?.model?.display_name || 'Claude';
    const size = Number(json?.context_window?.context_window_size) || 200000;
    const usage = json?.context_window?.current_usage || {};
    const current = Number(usage.input_tokens || 0) + Number(usage.cache_creation_input_tokens || 0) + Number(usage.cache_read_input_tokens || 0);
    const pctUsed = size > 0 ? Math.round((current / size) * 100) : 0;
    const tokensLeft = Math.max(0, size - current);

    const sep = ` ${c.dim}|${c.reset} `;
    const barWidth = 15;
    const col1w = 30;
    const modelCol = 55;

    const ctxBar = buildBar(pctUsed, barWidth);
    const l1c1 = padColumn(`${c.white}context:${c.reset} ${ctxBar} ${c.cyan}${pctUsed}%${c.reset}`, col1w);
    const l1Left = `${l1c1}${sep}${c.purple}${tokensLeft.toLocaleString()} left${c.reset}`;
    const gap1 = Math.max(1, modelCol - stripAnsi(l1Left).length);
    const line1 = `${l1Left}${' '.repeat(gap1)}${c.blue}${modelName}${c.reset}`;

    const usageData = await getUsageData();
    let line2 = '';
    let line3 = '';

    if (usageData) {
      const fiveHourPct = Math.round(Number(usageData?.five_hour?.utilization || 0));
      const fiveHourReset = formatResetTime(usageData?.five_hour?.resets_at, 'time');
      const fhBar = buildBar(fiveHourPct, barWidth);
      const fhC1 = padColumn(`${c.white}current:${c.reset} ${fhBar} ${c.cyan}${fiveHourPct}%${c.reset}`, col1w);
      const l2Left = `${fhC1}${sep}${c.gray}${fiveHourReset}${c.reset}`;
      const effort = getEffortLevel();
      const effortStr = effort ? `${effortColor(effort)}${effort} effort${c.reset}` : '';
      const gap2 = Math.max(1, modelCol - stripAnsi(l2Left).length);
      line2 = `${l2Left}${' '.repeat(gap2)}${effortStr}`;

      const sevenDayPct = Math.round(Number(usageData?.seven_day?.utilization || 0));
      const sevenDayReset = formatResetTime(usageData?.seven_day?.resets_at, 'datetime');
      const sdBar = buildBar(sevenDayPct, barWidth);
      const sdC1 = padColumn(`${c.white}weekly:${c.reset}  ${sdBar} ${c.cyan}${sevenDayPct}%${c.reset}`, col1w);

      let extraStr = '';
      if (usageData?.extra_usage?.is_enabled) {
        const extraPct = Math.round(Number(usageData.extra_usage.utilization || 0));
        const extraUsed = (Number(usageData.extra_usage.used_credits || 0) / 100).toFixed(2);
        const extraLimit = (Number(usageData.extra_usage.monthly_limit || 0) / 100).toFixed(2);
        const extraBar = buildBar(extraPct, barWidth);
        extraStr = `${sep}${c.white}extra:${c.reset} ${extraBar} ${c.cyan}$${extraUsed}/$${extraLimit}${c.reset}`;
      }

      line3 = `${sdC1}${sep}${c.gray}${sevenDayReset}${c.reset}${extraStr}`;
    }

    process.stdout.write(line1);
    if (line2) process.stdout.write(`\n${line2}`);
    if (line3) process.stdout.write(`\n${line3}`);
  } catch (err) {
    process.stdout.write(`Claude | Error: ${err?.message || err}`);
  }
}

main();
