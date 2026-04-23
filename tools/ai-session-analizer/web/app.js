const state = {
  dataset: null,
  filtered: [],
};

const els = {
  providerFilter: document.getElementById("providerFilter"),
  workingDirectoryFilter: document.getElementById("workingDirectoryFilter"),
  taskFilter: document.getElementById("taskFilter"),
  agentFilter: document.getElementById("agentFilter"),
  stepFilter: document.getElementById("stepFilter"),
  generatedAt: document.getElementById("generatedAt"),
  reloadButton: document.getElementById("reloadButton"),
  statusText: document.getElementById("statusText"),
  metrics: document.getElementById("metrics"),
  stepChart: document.getElementById("stepChart"),
  directoryChart: document.getElementById("directoryChart"),
  taskTable: document.getElementById("taskTable"),
  agentTable: document.getElementById("agentTable"),
  recordsTable: document.getElementById("recordsTable"),
};

function formatNumber(value) {
  return new Intl.NumberFormat("ru-RU").format(value || 0);
}

function optionSelect(select, values) {
  const currentValue = select.value;
  select.innerHTML = "";
  const all = document.createElement("option");
  all.value = "";
  all.textContent = "All";
  select.appendChild(all);
  for (const value of values) {
    const option = document.createElement("option");
    option.value = value;
    option.textContent = value;
    select.appendChild(option);
  }
  if (currentValue && values.includes(currentValue)) {
    select.value = currentValue;
  }
}

function formatDate(value) {
  if (!value) return "n/a";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString("ru-RU");
}

function computeTotals(records) {
  return records.reduce(
    (acc, row) => {
      acc.records += 1;
      acc.input += row.input_tokens || 0;
      acc.output += row.output_tokens || 0;
      acc.cacheRead += row.cache_read_tokens || 0;
      acc.cacheCreation += row.cache_creation_tokens || 0;
      acc.total += row.total_tokens || 0;
      return acc;
    },
    { records: 0, input: 0, output: 0, cacheRead: 0, cacheCreation: 0, total: 0 },
  );
}

function createMetric(title, value, detail) {
  return `
    <article class="metric">
      <span>${title}</span>
      <strong>${formatNumber(value)}</strong>
      <small>${detail}</small>
    </article>
  `;
}

function renderMetrics(records) {
  const totals = computeTotals(records);
  els.metrics.innerHTML = [
    createMetric("Total Tokens", totals.total, "input + output + cache"),
    createMetric("Input Tokens", totals.input, "модельный вход"),
    createMetric("Output Tokens", totals.output, "ответы модели"),
    createMetric("Cache Read", totals.cacheRead, "повторное чтение кэша"),
    createMetric("Cache Create", totals.cacheCreation, "создание prompt cache"),
    createMetric("Records", totals.records, "нормализованные шаги"),
  ].join("");
}

function renderBars(container, items) {
  const top = items.slice(0, 12);
  if (!top.length) {
    container.innerHTML = `<div class="empty">Нет данных для выбранных фильтров.</div>`;
    return;
  }
  const max = Math.max(...top.map((item) => item.total_tokens), 1);
  container.innerHTML = top
    .map(
      (item) => `
        <div class="bar-row">
          <div class="bar-label">
            <strong>${item.key}</strong>
            <span>${formatNumber(item.total_tokens)} tokens</span>
          </div>
          <div class="bar-track">
            <div class="bar-fill" style="width:${(item.total_tokens / max) * 100}%"></div>
          </div>
        </div>
      `,
    )
    .join("");
}

function renderTable(container, columns, rows) {
  if (!rows.length) {
    container.innerHTML = `<div class="empty">Нет данных для выбранных фильтров.</div>`;
    return;
  }
  container.innerHTML = `
    <table>
      <thead>
        <tr>${columns.map((column) => `<th>${column.label}</th>`).join("")}</tr>
      </thead>
      <tbody>
        ${rows
          .map(
            (row) => `
              <tr>
                ${columns.map((column) => `<td>${column.render(row)}</td>`).join("")}
              </tr>
            `,
          )
          .join("")}
      </tbody>
    </table>
  `;
}

function renderRecords(records) {
  const rows = records
    .slice()
    .sort((a, b) => (b.total_tokens || 0) - (a.total_tokens || 0))
    .slice(0, 120);
  renderTable(
    els.recordsTable,
    [
      { label: "Provider", render: (row) => row.provider },
      { label: "Working Directory", render: (row) => `<code>${row.working_directory}</code>` },
      { label: "Task", render: (row) => row.task_id || "<unknown>" },
      { label: "Agent", render: (row) => `${row.agent_type}<br><small>${row.agent_label || ""}</small>` },
      { label: "Step", render: (row) => `${row.step_type}<br><small>${row.step_reason || ""}</small>` },
      { label: "Tokens", render: (row) => formatNumber(row.total_tokens) },
      { label: "Signal", render: (row) => `<small>${row.signal_preview || ""}</small>` },
    ],
    rows,
  );
}

function currentParams() {
  const params = new URLSearchParams();
  if (els.providerFilter.value) params.append("provider", els.providerFilter.value);
  if (els.workingDirectoryFilter.value) params.append("working_directory", els.workingDirectoryFilter.value);
  if (els.taskFilter.value) params.append("task_id", els.taskFilter.value);
  if (els.agentFilter.value) params.append("agent_type", els.agentFilter.value);
  if (els.stepFilter.value) params.append("step_type", els.stepFilter.value);
  return params;
}

async function loadFilteredRecords() {
  els.statusText.textContent = "Loading records…";
  const response = await fetch(`/api/records?${currentParams().toString()}`);
  const payload = await response.json();
  state.filtered = payload.records;

  optionSelect(els.providerFilter, payload.summary.filters.providers || []);
  optionSelect(els.workingDirectoryFilter, payload.summary.filters.working_directories || []);
  optionSelect(els.taskFilter, payload.summary.filters.task_ids || []);
  optionSelect(els.agentFilter, payload.summary.filters.agent_types || []);
  optionSelect(els.stepFilter, payload.summary.filters.step_types || []);

  renderMetrics(state.filtered);
  renderBars(els.stepChart, payload.summary.aggregates.by_step_type || []);
  renderBars(els.directoryChart, payload.summary.aggregates.by_working_directory || []);

  renderTable(
    els.taskTable,
    [
      { label: "Task ID", render: (row) => row.key },
      { label: "Tokens", render: (row) => formatNumber(row.total_tokens) },
      { label: "Records", render: (row) => formatNumber(row.records) },
    ],
    (payload.summary.aggregates.by_task_id || []).slice(0, 30),
  );

  renderTable(
    els.agentTable,
    [
      { label: "Agent Type", render: (row) => row.key },
      { label: "Tokens", render: (row) => formatNumber(row.total_tokens) },
      { label: "Records", render: (row) => formatNumber(row.records) },
    ],
    (payload.summary.aggregates.by_agent_type || []).slice(0, 30),
  );

  renderRecords(state.filtered);
  els.statusText.textContent = `Loaded ${formatNumber(payload.summary.totals.records || 0)} records`;
}

function attachFilters() {
  for (const element of [
    els.providerFilter,
    els.workingDirectoryFilter,
    els.taskFilter,
    els.agentFilter,
    els.stepFilter,
  ]) {
    element.addEventListener("change", loadFilteredRecords);
  }
  els.reloadButton.addEventListener("click", loadFilteredRecords);
}

async function boot() {
  const response = await fetch("/api/data");
  state.dataset = await response.json();
  optionSelect(els.providerFilter, state.dataset.summary.filters.providers);
  optionSelect(els.workingDirectoryFilter, state.dataset.summary.filters.working_directories);
  optionSelect(els.taskFilter, state.dataset.summary.filters.task_ids);
  optionSelect(els.agentFilter, state.dataset.summary.filters.agent_types);
  optionSelect(els.stepFilter, state.dataset.summary.filters.step_types);
  els.generatedAt.textContent = formatDate(state.dataset.generated_at);
  attachFilters();
  await loadFilteredRecords();
}

boot().catch((error) => {
  console.error(error);
  els.statusText.textContent = `Load failed: ${error.message}`;
});
