import { existsSync, readdirSync, mkdirSync, writeFileSync } from 'fs';
import { basename, dirname, relative, resolve } from 'path';
import * as browser from './browser.mjs';

export function discoverTests(testPaths) {
  const paths = Array.isArray(testPaths) ? testPaths : [testPaths];
  const files = [];
  function walk(dir) {
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      if (entry.name.startsWith('_') || entry.name.startsWith('.')) continue;
      const full = resolve(dir, entry.name);
      if (entry.isDirectory()) walk(full);
      else if (entry.name.endsWith('.test.mjs')) files.push(full);
    }
  }
  for (const p of paths) {
    const full = resolve(p);
    if (full.endsWith('.test.mjs')) {
      if (existsSync(full)) files.push(full);
    } else if (existsSync(full)) {
      walk(full);
    }
  }
  return [...new Set(files)].sort();
}

export async function cmdTest(rawArgs, helpers) {
  const sepIdx = rawArgs.indexOf('--');
  const ownArgs = sepIdx >= 0 ? rawArgs.slice(0, sepIdx) : rawArgs;
  const hookArgs = sepIdx >= 0 ? rawArgs.slice(sepIdx + 1) : [];

  const opts = {
    bail: false,
    retry: 0,
    timeout: 30000,
    report: null,
    format: 'json',
    screenshot: null,
    reportDir: null,
    list: false,
  };
  let tags = null;
  let grep = null;
  let urlFlag = null;
  const positional = [];
  for (const a of ownArgs) {
    if (a.startsWith('--tags=')) tags = a.slice(7).split(',').map(s => s.trim()).filter(Boolean);
    else if (a.startsWith('--grep=')) grep = new RegExp(a.slice(7), 'i');
    else if (a.startsWith('--url=')) urlFlag = a.slice(6);
    else if (a === '--bail') opts.bail = true;
    else if (a.startsWith('--retry=')) opts.retry = Number.parseInt(a.slice(8), 10) || 0;
    else if (a.startsWith('--timeout=')) opts.timeout = Number.parseInt(a.slice(10), 10) || 30000;
    else if (a.startsWith('--report=')) opts.report = a.slice(9);
    else if (a.startsWith('--format=')) opts.format = a.slice(9);
    else if (a.startsWith('--screenshot=')) opts.screenshot = a.slice(13);
    else if (a.startsWith('--report-dir=')) opts.reportDir = a.slice(13);
    else if (a === '--list') opts.list = true;
    else if (!a.startsWith('--')) positional.push(a);
  }

  if (positional.length === 0) {
    helpers.die('Usage: node run.mjs test <dir|file>... [--url=URL] [--tags=...] [--grep=...] [--bail] [--retry=N] [--timeout=ms] [--report=path] [--format=json|junit]');
  }
  for (const p of positional) {
    if (existsSync(resolve(p))) continue;
    if (/^https?:\/\//i.test(p)) {
      helpers.die(`"${p}" looks like a URL — use --url=<url>; positional args are test paths.`);
    }
    helpers.die(`Test path not found: "${p}"`);
  }
  if (!['json', 'junit', 'allure'].includes(opts.format)) {
    helpers.die(`Invalid --format=${opts.format} (expected json|junit|allure)`);
  }
  if (opts.format === 'junit' && !opts.report) {
    helpers.die('--format=junit requires --report=path.xml');
  }

  const firstPath = resolve(positional[0]);
  const suiteDir = findSuiteDir(firstPath);
  const config = await loadOptionalModule(resolve(suiteDir, 'webtest.config.mjs'), {});
  const hooks = await loadOptionalModule(resolve(suiteDir, '_hooks.mjs'), {});
  let url = urlFlag || config.url;
  if (!url && config.contexts && config.defaultContext) {
    url = config.contexts[config.defaultContext]?.url;
  } else if (!url && config.contexts) {
    const first = Object.values(config.contexts)[0];
    url = first?.url;
  }

  opts.timeout = ownArgs.some(a => a.startsWith('--timeout=')) ? opts.timeout : (config.timeout || opts.timeout);
  opts.retry = ownArgs.some(a => a.startsWith('--retry=')) ? opts.retry : (config.retries || opts.retry);
  opts.screenshot = opts.screenshot || config.screenshot || 'on-failure';
  if (!['on-failure', 'every-step', 'off'].includes(opts.screenshot)) {
    helpers.die(`Invalid --screenshot=${opts.screenshot} (expected on-failure|every-step|off)`);
  }
  if (!tags && Array.isArray(config.tags)) tags = config.tags;

  const testFiles = discoverTests(positional);
  if (!testFiles.length) helpers.die(`No *.test.mjs files found in ${positional.join(', ')}`);
  const tests = await loadTests(testFiles, suiteDir, opts.timeout);
  const filtered = filterTests(tests, tags, grep);
  if (opts.list) {
    helpers.out({ ok: true, suiteDir, total: filtered.length, tests: filtered.map(t => ({ name: t.name, file: t.file, tags: t.tags })) });
    return;
  }
  if (!url) helpers.die('No URL provided and no webtest.config.mjs with url/contexts found');

  const reportToStdout = opts.report === '-';
  const reportDir = opts.reportDir
    ? resolve(opts.reportDir)
    : (opts.report && !reportToStdout ? dirname(resolve(opts.report)) : suiteDir);
  if (opts.screenshot !== 'off' || opts.format === 'allure') {
    mkdirSync(reportDir, { recursive: true });
  }

  const W = reportToStdout ? process.stderr : process.stdout;
  W.write(`\nweb-test -- ${url}\n`);
  W.write(`Running ${filtered.length} tests from ${relative(process.cwd(), suiteDir).replace(/\\/g, '/') || '.'}/\n\n`);

  const startedAt = new Date().toISOString();
  const results = [];
  let passCount = 0;
  let failCount = 0;
  let skipCount = 0;

  const hookLog = (...a) => W.write(`[hooks] ${a.map(String).join(' ')}\n`);
  const hookEnv = { hookArgs, log: hookLog, config };
  if (hooks.prepare) await hooks.prepare(hookEnv);

  let ctx = null;
  try {
    await browser.connect(url);
    ctx = buildContext();
    if (hooks.beforeAll) await hooks.beforeAll(ctx);

    let index = 0;
    for (const test of filtered) {
      index++;
      if (test.skip) {
        const reason = typeof test.skip === 'string' ? `: ${test.skip}` : '';
        W.write(`  ○ ${test.name} (skip${reason})\n`);
        results.push(skippedResult(test));
        skipCount++;
        continue;
      }

      const result = await runOneTest({ test, index, opts, reportDir, hooks, ctx });
      results.push(result);
      if (result.status === 'passed') {
        passCount++;
        W.write(`  ✓ ${test.name} (${result.duration}s)\n`);
      } else {
        failCount++;
        W.write(`  ✗ ${test.name} (${result.duration}s)\n`);
        printSteps(W, result.steps, '    ');
        if (result.error?.message) W.write(`    ${result.error.message}\n`);
        if (result.screenshot) W.write(`    screenshot: ${result.screenshot}\n`);
      }
      if (opts.bail && result.status === 'failed') break;
    }

    if (hooks.afterAll) await hooks.afterAll(ctx);
  } finally {
    try { await browser.disconnect(); } catch {}
    if (hooks.cleanup) {
      try { await hooks.cleanup(hookEnv); } catch {}
    }
  }

  const finishedAt = new Date().toISOString();
  const totalDuration = results.reduce((sum, r) => sum + r.duration, 0);
  W.write(`\n${passCount} passed, ${failCount} failed, ${skipCount} skipped (${formatDuration(totalDuration)})\n\n`);

  const report = {
    runner: 'web-test',
    url,
    startedAt,
    finishedAt,
    duration: totalDuration,
    summary: { total: results.length, passed: passCount, failed: failCount, skipped: skipCount },
    tests: results,
  };
  writeReport({ report, opts, reportToStdout, suiteDir, reportDir, helpers });
  if (failCount > 0) process.exit(1);
}

function findSuiteDir(firstPath) {
  const start = firstPath.endsWith('.test.mjs') ? dirname(firstPath) : firstPath;
  let current = start;
  while (true) {
    if (existsSync(resolve(current, 'webtest.config.mjs')) || existsSync(resolve(current, '_hooks.mjs'))) {
      return current;
    }
    const parent = dirname(current);
    if (parent === current || parent === process.cwd()) return start;
    current = parent;
  }
}

async function loadOptionalModule(path, fallback) {
  if (!existsSync(path)) return fallback;
  const mod = await import(pathToFileUrl(path));
  return mod.default || mod;
}

async function loadTests(files, suiteDir, defaultTimeout) {
  const tests = [];
  let hasOnly = false;
  for (const file of files) {
    const mod = await import(pathToFileUrl(file));
    if (typeof mod.default !== 'function') {
      throw new Error(`Test file has no default async function: ${file}`);
    }
    const base = {
      file: relative(suiteDir, file).replace(/\\/g, '/'),
      filePath: file,
      name: mod.name || basename(file, '.test.mjs'),
      tags: Array.isArray(mod.tags) ? mod.tags : [],
      timeout: mod.timeout || defaultTimeout,
      skip: mod.skip || false,
      only: mod.only || false,
      setup: mod.setup,
      teardown: mod.teardown,
      fn: mod.default,
      param: undefined,
      severity: typeof mod.severity === 'string' ? mod.severity : null,
    };
    if (base.only) hasOnly = true;
    if (Array.isArray(mod.params) && mod.params.length) {
      for (let i = 0; i < mod.params.length; i++) {
        const param = mod.params[i];
        tests.push({ ...base, name: interpolate(base.name, param, i), param });
      }
    } else {
      tests.push(base);
    }
  }
  return hasOnly ? tests.filter(t => t.only) : tests;
}

function filterTests(tests, tags, grep) {
  return tests.filter(t => {
    if (tags && !tags.some(tag => t.tags.includes(tag))) return false;
    if (grep && !grep.test(t.name)) return false;
    return true;
  });
}

function buildContext() {
  const ctx = {};
  for (const [key, value] of Object.entries(browser)) {
    if (key !== 'default') ctx[key] = value;
  }
  ctx.assert = createAssertions();
  ctx.log = () => {};
  ctx.step = async (_name, fn) => await fn();
  return ctx;
}

async function runOneTest({ test, index, opts, reportDir, hooks, ctx }) {
  let last = null;
  const maxAttempts = 1 + opts.retry;
  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    const output = [];
    const steps = [];
    let currentSteps = steps;
    let stepIndex = 0;
    const started = Date.now();
    ctx.testInfo = {
      name: test.name,
      file: basename(test.file),
      filePath: test.file,
      tags: test.tags,
      timeout: test.timeout,
      attempt,
      maxAttempts,
      param: test.param,
    };
    ctx.testResult = null;
    ctx.log = (...a) => output.push(a.map(String).join(' '));
    ctx.step = async (name, fn) => {
      const step = { name, start: Date.now(), status: 'passed', steps: [] };
      currentSteps.push(step);
      const prev = currentSteps;
      currentSteps = step.steps;
      stepIndex++;
      const myIndex = stepIndex;
      try {
        await fn();
      } catch (e) {
        step.status = 'failed';
        step.error = e.message;
        throw e;
      } finally {
        step.stop = Date.now();
        currentSteps = prev;
        if (opts.screenshot === 'every-step' && step.status === 'passed') {
          try {
            const file = resolve(reportDir, `${index}-${myIndex}-${slugify(name)}.png`);
            writeFileSync(file, await browser.screenshot());
            step.screenshot = file;
          } catch {}
        }
      }
    };

    try {
      if (hooks.beforeEach) await hooks.beforeEach(ctx);
      if (test.setup) await test.setup(ctx);
      await withTimeout(test.fn(ctx, test.param), test.timeout);
      if (test.teardown) {
        try { await test.teardown(ctx); } catch {}
      }
      const duration = elapsed(started);
      ctx.testResult = { status: 'passed', duration, attempts: attempt, error: null, steps };
      if (hooks.afterEach) {
        try { await hooks.afterEach(ctx); } catch {}
      }
      await resetState(ctx);
      return {
        name: test.name,
        file: test.file,
        tags: test.tags,
        severity: test.severity,
        status: 'passed',
        duration,
        attempts: attempt,
        start: started,
        stop: Date.now(),
        steps,
        output: output.join('\n'),
        error: null,
        screenshot: null,
      };
    } catch (e) {
      const screenshot = await screenshotOnFailure(opts, reportDir, index, test);
      if (test.teardown) {
        try { await test.teardown(ctx); } catch {}
      }
      const duration = elapsed(started);
      const error = { message: e.message, screenshot };
      ctx.testResult = { status: 'failed', duration, attempts: attempt, error, steps };
      if (hooks.afterEach) {
        try { await hooks.afterEach(ctx); } catch {}
      }
      await resetState(ctx);
      last = {
        name: test.name,
        file: test.file,
        tags: test.tags,
        severity: test.severity,
        status: 'failed',
        duration,
        attempts: attempt,
        start: started,
        stop: Date.now(),
        steps,
        output: output.join('\n'),
        error,
        screenshot,
      };
    }
  }
  return last;
}

async function withTimeout(promise, timeoutMs) {
  let timer;
  try {
    await Promise.race([
      promise,
      new Promise((_, reject) => {
        timer = setTimeout(() => reject(new Error(`Timeout (${timeoutMs}ms)`)), timeoutMs);
      }),
    ]);
  } finally {
    clearTimeout(timer);
  }
}

async function resetState(ctx) {
  for (let i = 0; i < 10; i++) {
    try {
      const state = await ctx.getFormState();
      if (state.form == null) break;
      await ctx.closeForm({ save: false });
    } catch {
      break;
    }
  }
}

async function screenshotOnFailure(opts, reportDir, index, test) {
  if (opts.screenshot === 'off') return null;
  try {
    const file = resolve(reportDir, `error-${index}-${slugify(test.file.replace(/\.test\.mjs$/, ''))}.png`);
    writeFileSync(file, await browser.screenshot());
    return file;
  } catch {
    return null;
  }
}

function skippedResult(test) {
  return {
    name: test.name,
    file: test.file,
    tags: test.tags,
    severity: test.severity,
    status: 'skipped',
    duration: 0,
    attempts: 0,
    steps: [],
    output: '',
    error: null,
    screenshot: null,
  };
}

function writeReport({ report, opts, reportToStdout, suiteDir, reportDir, helpers }) {
  if (opts.format === 'junit') {
    const xml = buildJUnit(report, suiteDir);
    if (reportToStdout) process.stdout.write(xml + '\n');
    else writeFileSync(resolve(opts.report), xml);
    return;
  }
  if (opts.format === 'allure') {
    writeAllure(report.tests, reportDir);
    return;
  }
  if (reportToStdout) {
    helpers.out(report);
  } else if (opts.report) {
    writeFileSync(resolve(opts.report), JSON.stringify(report, null, 2));
  }
}

function createAssertions() {
  class AssertionError extends Error {
    constructor(message, actual, expected) {
      super(message);
      this.name = 'AssertionError';
      this.actual = actual;
      this.expected = expected;
    }
  }
  return {
    ok(value, msg) {
      if (!value) throw new AssertionError(msg || `Expected truthy, got ${JSON.stringify(value)}`, value, true);
    },
    equal(actual, expected, msg) {
      if (actual !== expected) throw new AssertionError(msg || `Expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`, actual, expected);
    },
    notEqual(actual, expected, msg) {
      if (actual === expected) throw new AssertionError(msg || `Expected not ${JSON.stringify(expected)}`, actual, expected);
    },
    deepEqual(actual, expected, msg) {
      const a = JSON.stringify(actual);
      const b = JSON.stringify(expected);
      if (a !== b) throw new AssertionError(msg || `Deep equal failed:\n  actual:   ${a}\n  expected: ${b}`, actual, expected);
    },
    includes(haystack, needle, msg) {
      const h = Array.isArray(haystack) ? haystack : String(haystack);
      if (!h.includes(needle)) throw new AssertionError(msg || `Expected ${JSON.stringify(h)} to include ${JSON.stringify(needle)}`, haystack, needle);
    },
    match(string, regex, msg) {
      if (!regex.test(string)) throw new AssertionError(msg || `Expected ${JSON.stringify(string)} to match ${regex}`, string, regex);
    },
    async throws(fn, msg) {
      try { await fn(); } catch { return; }
      throw new AssertionError(msg || 'Expected function to throw');
    },
    formHasField(state, fieldName, msg) {
      if (!state?.fields?.[fieldName]) throw new AssertionError(msg || `Field "${fieldName}" not found`, null, fieldName);
    },
    formTitle(state, expected, msg) {
      if (!state?.title?.includes(expected)) throw new AssertionError(msg || `Form title "${state?.title}" does not contain "${expected}"`, state?.title, expected);
    },
    tableHasRow(table, predicate, msg) {
      const rows = table?.rows || [];
      const found = typeof predicate === 'function'
        ? rows.some(predicate)
        : rows.some(r => Object.entries(predicate).every(([k, v]) => r[k] === v));
      if (!found) throw new AssertionError(msg || `No row matching predicate in table (${rows.length} rows)`, null, predicate);
    },
    tableRowCount(table, expected, msg) {
      const actual = table?.rows?.length ?? 0;
      if (actual !== expected) throw new AssertionError(msg || `Expected ${expected} rows, got ${actual}`, actual, expected);
    },
    noErrors(state, msg) {
      if (state?.errors) throw new AssertionError(msg || `Form has errors: ${JSON.stringify(state.errors)}`, state.errors, null);
    },
  };
}

function buildJUnit(report, suiteDir) {
  const { summary, duration, tests } = report;
  const suiteName = relative(process.cwd(), suiteDir).replace(/\\/g, '/') || '.';
  const lines = ['<?xml version="1.0" encoding="UTF-8"?>'];
  lines.push(`<testsuites name="web-test" tests="${summary.total}" failures="${summary.failed}" skipped="${summary.skipped}" time="${duration.toFixed(3)}">`);
  lines.push(`  <testsuite name="${xmlEscape(suiteName)}" tests="${summary.total}" failures="${summary.failed}" skipped="${summary.skipped}" time="${duration.toFixed(3)}">`);
  for (const t of tests) {
    const attrs = `name="${xmlEscape(t.name)}" classname="${xmlEscape(t.file)}" time="${(t.duration || 0).toFixed(3)}"`;
    if (t.status === 'passed') {
      lines.push(`    <testcase ${attrs}/>`);
    } else if (t.status === 'skipped') {
      lines.push(`    <testcase ${attrs}><skipped/></testcase>`);
    } else {
      lines.push(`    <testcase ${attrs}>`);
      lines.push(`      <failure message="${xmlEscape(t.error?.message || '')}">${xmlEscape(t.output || '')}</failure>`);
      if (t.screenshot) lines.push(`      <system-out>screenshot: ${xmlEscape(t.screenshot)}</system-out>`);
      lines.push('    </testcase>');
    }
  }
  lines.push('  </testsuite>');
  lines.push('</testsuites>');
  return lines.join('\n');
}

function writeAllure(tests, reportDir) {
  mkdirSync(reportDir, { recursive: true });
  for (const t of tests) {
    if (t.status === 'skipped') continue;
    const uuid = randomId();
    const out = {
      uuid,
      name: t.name,
      fullName: t.file,
      status: t.status,
      stage: 'finished',
      start: t.start,
      stop: t.stop,
      labels: (t.tags || []).map(tag => ({ name: 'tag', value: tag })),
      steps: (t.steps || []).map(allureStep),
      attachments: t.screenshot ? [{ name: 'Screenshot on failure', source: basename(t.screenshot), type: 'image/png' }] : [],
    };
    if (t.status === 'failed' && t.error) {
      out.statusDetails = { message: t.error.message || '', trace: t.output || '' };
    }
    writeFileSync(resolve(reportDir, `${uuid}-result.json`), JSON.stringify(out, null, 2));
  }
}

function allureStep(step) {
  return {
    name: step.name,
    status: step.status,
    stage: 'finished',
    start: step.start,
    stop: step.stop,
    steps: (step.steps || []).map(allureStep),
    attachments: step.screenshot ? [{ name: 'Screenshot', source: basename(step.screenshot), type: 'image/png' }] : [],
  };
}

function printSteps(out, steps, indent) {
  for (const step of steps || []) {
    out.write(`${indent}${step.status === 'failed' ? '✗' : '•'} ${step.name}\n`);
    if (step.error) out.write(`${indent}  ${step.error}\n`);
    printSteps(out, step.steps, indent + '  ');
  }
}

function interpolate(template, param, index) {
  if (!template.includes('{')) return `${template}[${index}]`;
  return template.replace(/\{([^}]+)\}/g, (_m, key) => param?.[key] ?? '');
}

function elapsed(t0) {
  return Math.round((Date.now() - t0) / 100) / 10;
}

function formatDuration(seconds) {
  if (seconds < 60) return `${seconds}s`;
  const mins = Math.floor(seconds / 60);
  const rest = Math.round(seconds % 60);
  return `${mins}m ${rest}s`;
}

function slugify(value) {
  return String(value).trim().replace(/[^\p{L}\p{N}._-]+/gu, '-').replace(/^-+|-+$/g, '').slice(0, 80) || 'item';
}

function xmlEscape(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}

function pathToFileUrl(path) {
  return 'file:///' + resolve(path).replace(/\\/g, '/').replace(/^\/+/, '');
}

function randomId() {
  return `${Date.now().toString(16)}-${Math.random().toString(16).slice(2)}`;
}
