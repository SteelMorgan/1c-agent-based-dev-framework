const state = {
  dataset: null,
  filtered: [],
  timelineRefreshHandle: null,
  summaryCache: new Map(),
  timelineCache: new Map(),
  selectedProvider: "",
  selectedDirectory: "",
  selectedTask: "",
  selectedAgent: "",
  selectedStep: "",
};

const els = {
  providerFilter: document.getElementById("providerFilter"),
  reloadButton: document.getElementById("reloadButton"),
  clearSelectionButton: document.getElementById("clearSelectionButton"),
  statusText: document.getElementById("statusText"),
  metrics: document.getElementById("metrics"),
  agentBreakdown: document.getElementById("agentBreakdown"),
  stepChart: document.getElementById("stepChart"),
  directoryChart: document.getElementById("directoryChart"),
  taskOverview: document.getElementById("taskOverview"),
  timelineSummary: document.getElementById("timelineSummary"),
  workflowTimeline: document.getElementById("workflowTimeline"),
  workflowDetail: document.getElementById("workflowDetail"),
  timelineChart: document.getElementById("timelineChart"),
  timelineLaneDetail: document.getElementById("timelineLaneDetail"),
  recordsTable: document.getElementById("recordsTable"),
};

let selectedTimelineTask = "";
let selectedTimelineLane = "";
let selectedWorkflowItem = "";

function formatNumber(value) {
  return new Intl.NumberFormat("ru-RU").format(value || 0);
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function optionSelect(select, values, currentValue = "") {
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
  select.value = values.includes(currentValue) ? currentValue : "";
}

function clipText(value, max = 140) {
  const text = String(value ?? "").trim();
  if (text.length <= max) return text;
  return `${text.slice(0, max - 1)}…`;
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

function shortenDirectoryLabel(value) {
  if (!value || value === "<unknown>") return value;
  const match = value.match(/\/1C Projects\/([^/]+)/);
  if (match) return match[1];
  const frameworkMatch = value.match(/\/1C Framework\/([^/]+)/);
  if (frameworkMatch) return `Framework / ${frameworkMatch[1]}`;
  const parts = value.split("/").filter(Boolean);
  return parts.slice(-2).join(" / ");
}

async function fetchSummary(params) {
  const key = params.toString();
  if (state.summaryCache.has(key)) {
    return state.summaryCache.get(key);
  }
  const response = await fetch(`/api/records?${key}`);
  const payload = await response.json();
  state.summaryCache.set(key, payload);
  return payload;
}

function renderMetrics(totals) {
  els.metrics.innerHTML = [
    createMetric("Total Tokens", totals.total_tokens, "input + output + cache"),
    createMetric("Input Tokens", totals.input_tokens, "модельный вход"),
    createMetric("Output Tokens", totals.output_tokens, "ответы модели"),
    createMetric("Cache Read", totals.cache_read_tokens, "повторное чтение кэша"),
    createMetric("Cache Create", totals.cache_creation_tokens, "создание prompt cache"),
    createMetric("Records", totals.records, "вся выборка, не только preview"),
  ].join("");
}

function renderBars(container, items, options = {}) {
  const clickable = options.clickable || false;
  const selectedKey = options.selectedKey || "";
  const onClick = options.onClick;
  const top = items.slice(0, 12);
  if (!top.length) {
    container.innerHTML = `<div class="empty">Нет данных для выбранных фильтров.</div>`;
    return;
  }
  const max = Math.max(...top.map((item) => item.total_tokens), 1);
  container.innerHTML = top
    .map(
      (item) => `
        <div class="bar-row ${clickable ? "clickable-bar" : ""} ${selectedKey === item.key ? "selected" : ""}" data-key="${escapeHtml(item.key)}">
          <div class="bar-label">
            <strong>${escapeHtml(options.labelFormatter ? options.labelFormatter(item.key, item) : item.key)}</strong>
            <span>${formatNumber(item.total_tokens)} tokens</span>
          </div>
          <div class="bar-track">
            <div class="bar-fill ${options.fillClass || ""}" style="width:${(item.total_tokens / max) * 100}%;${options.colorFn ? `background:${options.colorFn(item.key)};` : ""}"></div>
          </div>
        </div>
      `,
    )
    .join("");
  if (clickable && onClick) {
    container.querySelectorAll(".clickable-bar").forEach((element) => {
      element.addEventListener("click", () => onClick(element.dataset.key || "", element));
    });
  }
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

function phaseLabel(phase) {
  const labels = {
    orchestrator: "Orchestrator",
    phase0_explorer: "Phase 0 · Explorer",
    phase1_spec: "Phase 1 · Spec",
    phase2_arch: "Phase 2 · Architecture",
    phase3a_bdd_author: "Phase 3a · BDD Author",
    phase3b_tests: "Phase 3b · Tests",
    phase3c_bdd_steps: "Phase 3c · BDD Steps",
    phase3d_code: "Phase 3d · Code",
    phase4_regression: "Phase 4 · Regression",
    review: "Review",
    delegated: "Delegated",
    unknown: "Unknown",
  };
  return labels[phase] || phase;
}

function workKindColor(workKind) {
  const palette = {
    vanessa_log_analysis: "#48e0ff",
    screenshot_analysis: "#9c6cff",
    code_reading: "#4cff9a",
    code_writing: "#ff8a5b",
    test_execution: "#ffd166",
    review: "#ff5db1",
    planning: "#8ea4ff",
    other: "#7b8aa6",
    unclassified: "#5c6578",
  };
  return palette[workKind] || "#7b8aa6";
}

function visibleTimelineLanes(timeline) {
  if (selectedTimelineTask) {
    return timeline.lanes;
  }
  return timeline.lanes.slice(0, 12);
}

function workflowStageLabel(stage) {
  const labels = {
    start: "Start",
    classification: "Classification",
    phase_start: "Phase Start",
    phase_done: "Phase Done",
    review: "Review",
    cross_review: "Cross Review",
    approval_gate: "Approval Gate",
    clarification: "Clarification",
    wait: "Wait",
    diagnostics: "Diagnostics",
    failure: "Failure",
    rework: "Rework",
    finalization: "Finalization",
    escalation: "Escalation",
    note: "Note",
  };
  return labels[stage] || stage;
}

function workflowStageColor(stage) {
  const palette = {
    start: "#8ea4ff",
    classification: "#4cff9a",
    phase_start: "#48e0ff",
    phase_done: "#8ea4ff",
    review: "#ff5db1",
    cross_review: "#ff79c6",
    approval_gate: "#ffd166",
    clarification: "#a855f7",
    wait: "#6b7280",
    diagnostics: "#4cd6b0",
    failure: "#ff7b72",
    rework: "#ff8a5b",
    finalization: "#5df2ff",
    escalation: "#ff4d6d",
    note: "#7b8aa6",
  };
  return palette[stage] || "#7b8aa6";
}

function renderSignalCards(container, items, options = {}) {
  const top = items.slice(0, options.limit || 10);
  if (!top.length) {
    container.innerHTML = `<div class="empty">Нет данных для выбранных фильтров.</div>`;
    return;
  }
  const max = Math.max(...top.map((item) => item.total_tokens), 1);
  container.innerHTML = top
    .map((item, index) => {
      const ratio = item.total_tokens / max;
      const accent = options.colorFn ? options.colorFn(item.key, index) : workKindColor(item.key);
      const label = options.labelFormatter ? options.labelFormatter(item.key, item) : item.key;
      return `
        <article class="signal-card ${options.cardClass || ""} ${options.onClick ? "clickable-card" : ""} ${options.selectedKey === item.key ? "selected" : ""}" data-key="${escapeHtml(item.key)}" style="--signal-accent:${accent};--signal-ratio:${ratio}">
          <div class="signal-card-head">
            <strong>${escapeHtml(label)}</strong>
            <span>${formatNumber(item.total_tokens)}</span>
          </div>
          ${options.hideMeter ? "" : `
            <div class="signal-card-meter">
              <div class="signal-card-fill" style="width:${Math.max(12, ratio * 100)}%"></div>
            </div>
          `}
          ${options.hideFooter ? "" : `<small>${formatNumber(item.records)} records</small>`}
        </article>
      `;
    })
    .join("");
  if (options.onClick) {
    container.querySelectorAll(".signal-card").forEach((element) => {
      element.addEventListener("click", () => options.onClick(element.dataset.key || "", element));
    });
  }
}

function applyImmediateSelection(container, element, willSelect) {
  if (!container || !element) return;
  container.querySelectorAll(".selected").forEach((node) => node.classList.remove("selected"));
  if (willSelect) {
    element.classList.add("selected");
  }
  document.body.classList.add("loading");
}

function renderTimelineSummary(timeline, summary) {
  if (!state.selectedTask) {
    els.workflowTimeline.closest(".workflow-layout").style.display = "none";
    els.timelineChart.closest(".timeline-layout").style.display = "none";
    els.timelineSummary.innerHTML = `<div class="empty">Выбери задачу, чтобы увидеть workflow и таймлайн.</div>`;
    els.workflowTimeline.innerHTML = "";
    els.workflowDetail.innerHTML = "";
    els.timelineChart.innerHTML = "";
    els.timelineLaneDetail.innerHTML = "";
    return;
  }
  els.workflowTimeline.closest(".workflow-layout").style.display = "grid";
  els.timelineChart.closest(".timeline-layout").style.display = "grid";
  if (!timeline || !timeline.range) {
    els.timelineSummary.innerHTML = `<div class="empty">Таймлайн появится для текущего набора фильтров.</div>`;
    els.timelineLaneDetail.innerHTML = `<div class="empty">Выбери дорожку, чтобы увидеть периоды и пики расхода.</div>`;
    els.workflowTimeline.innerHTML = `<div class="empty">Workflow-слой появится для выбранной задачи с orchestrator-context.</div>`;
    els.workflowDetail.innerHTML = `<div class="empty">Выбери фазу или этап, чтобы увидеть детализацию.</div>`;
    return;
  }
  const durationHours = (timeline.range.duration_s / 3600).toFixed(2);
  els.timelineSummary.innerHTML = `
    <div class="timeline-summary-grid">
      <article class="timeline-stat">
        <span>Scope</span>
        <strong>${timeline.scope_label || timeline.task_id}</strong>
      </article>
      <article class="timeline-stat">
        <span>Duration</span>
        <strong>${durationHours}h</strong>
      </article>
      <article class="timeline-stat">
        <span>Lanes</span>
        <strong>${formatNumber(timeline.lanes.length)}</strong>
      </article>
      <article class="timeline-stat">
        <span>Total Tokens</span>
        <strong>${formatNumber(summary.totals.total_tokens)}</strong>
      </article>
    </div>
    <div class="timeline-help">
      <span><strong>Top → Bottom:</strong> chronological time</span>
      <span><strong>Left → Right:</strong> lanes ordered by first activity</span>
      <span><strong>Lane Width:</strong> total tokens</span>
      <span><strong>Color:</strong> work kind</span>
    </div>
  `;
}

function ensureWorkflowSelection(timeline) {
  const workflow = timeline?.workflow;
  if (!workflow) {
    selectedWorkflowItem = "";
    return;
  }
  const phases = workflow.phase_spans || [];
  const events = workflow.events || [];
  if (!selectedWorkflowItem && phases.length) {
    selectedWorkflowItem = `phase:0`;
    return;
  }
  if (!selectedWorkflowItem && events.length) {
    selectedWorkflowItem = `event:0`;
    return;
  }
  const [kind, indexText] = selectedWorkflowItem.split(":");
  const index = Number(indexText || 0);
  if (kind === "phase" && !phases[index]) {
    selectedWorkflowItem = phases.length ? "phase:0" : events.length ? "event:0" : "";
  }
  if (kind === "event" && !events[index]) {
    selectedWorkflowItem = phases.length ? "phase:0" : events.length ? "event:0" : "";
  }
}

function renderWorkflowPanel(timeline) {
  const workflow = timeline?.workflow;
  if (!workflow || (!workflow.events?.length && !workflow.phase_spans?.length)) {
    els.workflowTimeline.innerHTML = `<div class="empty">Для этой задачи не найден orchestrator-context.</div>`;
    els.workflowDetail.innerHTML = `<div class="empty">Выбери фазу или этап, чтобы увидеть детализацию.</div>`;
    return;
  }
  ensureWorkflowSelection(timeline);
  const phases = (workflow.phase_spans || []).slice(0, 12);
  const events = (workflow.events || []).slice(0, 16);
  els.workflowTimeline.innerHTML = `
    <div class="workflow-groups">
      <div class="workflow-group">
        <div class="section-head compact">
          <h2>Фазы</h2>
        </div>
        <div class="workflow-phase-list">
          ${phases.map((span, index) => `
            <button class="workflow-item ${selectedWorkflowItem === `phase:${index}` ? "selected" : ""}" data-kind="phase" data-index="${index}">
              <strong>${escapeHtml(phaseLabel(span.phase))}</strong>
              <small>${escapeHtml(clipText(span.label, 72))}</small>
            </button>
          `).join("") || `<div class="empty">Нет фаз.</div>`}
        </div>
      </div>
      <div class="workflow-group">
        <div class="section-head compact">
          <h2>Этапы</h2>
        </div>
        <div class="workflow-events">
          ${events.map((event, index) => `
            <button class="workflow-item workflow-event-item ${selectedWorkflowItem === `event:${index}` ? "selected" : ""}" data-kind="event" data-index="${index}">
              <span class="workflow-dot" style="background:${workflowStageColor(event.stage_type)}"></span>
              <div>
                <strong>${workflowStageLabel(event.stage_type)}</strong>
                <small>${escapeHtml(formatDate(event.timestamp))}</small>
              </div>
            </button>
          `).join("") || `<div class="empty">Нет этапов.</div>`}
        </div>
      </div>
    </div>
  `;
  els.workflowTimeline.querySelectorAll(".workflow-item").forEach((element) => {
    element.addEventListener("click", () => {
      selectedWorkflowItem = `${element.dataset.kind}:${element.dataset.index}`;
      renderWorkflowPanel(timeline);
    });
  });
  renderWorkflowDetail(timeline);
}

function renderWorkflowDetail(timeline) {
  const workflow = timeline?.workflow;
  if (!workflow) {
    els.workflowDetail.innerHTML = `<div class="empty">Нет workflow-данных.</div>`;
    return;
  }
  ensureWorkflowSelection(timeline);
  const [kind, indexText] = selectedWorkflowItem.split(":");
  const index = Number(indexText || 0);
  if (kind === "phase") {
    const span = workflow.phase_spans?.[index];
    if (!span) {
      els.workflowDetail.innerHTML = `<div class="empty">Нет данных по выбранной фазе.</div>`;
      return;
    }
    els.workflowDetail.innerHTML = `
      <div class="lane-card">
        <strong>${escapeHtml(phaseLabel(span.phase))}</strong>
        <span>${escapeHtml(span.label || "Phase")}</span>
        <small>${escapeHtml(formatDate(span.start || span.timestamp || ""))}</small>
      </div>
      <div class="lane-periods">
        <div class="lane-period workflow-detail-card">
          <div>
            <strong>Описание</strong>
            <small>${escapeHtml(clipText(span.label || "Нет описания.", 320))}</small>
          </div>
        </div>
      </div>
    `;
    return;
  }
  const event = workflow.events?.[index];
  if (!event) {
    els.workflowDetail.innerHTML = `<div class="empty">Нет данных по выбранному этапу.</div>`;
    return;
  }
  els.workflowDetail.innerHTML = `
    <div class="lane-card">
      <strong>${workflowStageLabel(event.stage_type)}</strong>
      <span>${escapeHtml(event.tag || event.stage_type)}</span>
      <small>${escapeHtml(formatDate(event.timestamp))}</small>
    </div>
    <div class="lane-periods">
      <div class="lane-period workflow-detail-card">
        <div>
          <strong>Сообщение</strong>
          <small>${escapeHtml(clipText(event.message || "Нет сообщения.", 420))}</small>
        </div>
      </div>
    </div>
  `;
}

function renderTimelineLaneDetail(timeline) {
  const lanes = visibleTimelineLanes(timeline || { lanes: [] });
  if (!timeline || !lanes.length) {
    els.timelineLaneDetail.innerHTML = `<div class="empty">Нет дорожек для выбранного фильтра.</div>`;
    return;
  }
  const lane =
    lanes.find((item) => item.lane === selectedTimelineLane) ||
    lanes[0];
  selectedTimelineLane = lane.lane;
  const spans = timeline.spans
    .filter((span) => span.lane === lane.lane)
    .sort((a, b) => b.total_tokens - a.total_tokens);
  const topSpans = spans.slice(0, 8);
  els.timelineLaneDetail.innerHTML = `
    <div class="lane-card">
      <strong>${lane.agent_label || lane.lane}</strong>
      <span>${escapeHtml(lane.agent_type)}</span>
      <small>${formatNumber(lane.total_tokens)} tokens · ${formatNumber(lane.records)} records</small>
    </div>
    <div class="lane-periods">
      ${topSpans
        .map(
          (span) => `
            <button class="lane-period">
              <span class="lane-period-swatch" style="background:${workKindColor(span.work_kind)}"></span>
              <div>
                <strong>${escapeHtml(phaseLabel(span.phase))}</strong>
                <small>${escapeHtml(span.work_kind)}</small>
              </div>
              <em>${formatNumber(span.total_tokens)} tok</em>
            </button>
          `,
        )
        .join("")}
    </div>
  `;
}

function renderTimelineChart(timeline) {
  if (!timeline || !timeline.range || !timeline.spans.length) {
    els.timelineChart.innerHTML = `<div class="empty">Для задачи нет данных таймлайна.</div>`;
    els.timelineLaneDetail.innerHTML = `<div class="empty">Выбери дорожку, чтобы увидеть периоды и пики расхода.</div>`;
    return;
  }
  const duration = Math.max(1, timeline.range.duration_s);
  const maxTokens = Math.max(...visibleTimelineLanes(timeline).map((lane) => lane.total_tokens || 0), 1);
  const sortedLanes = visibleTimelineLanes(timeline)
    .map((lane) => {
      const spans = timeline.spans.filter((span) => span.lane === lane.lane);
      const firstStart = spans.length ? Math.min(...spans.map((span) => span.start_offset_s || 0)) : Number.MAX_SAFE_INTEGER;
      return { lane, spans, firstStart };
    })
    .sort((a, b) => a.firstStart - b.firstStart);
  const axisMarks = [0, 0.25, 0.5, 0.75, 1]
    .map((ratio) => {
      const seconds = Math.round(duration * ratio);
      const hours = (seconds / 3600).toFixed(1);
      return `
        <div class="timeline-axis-mark" style="top:${ratio * 100}%">
          <span>${hours}h</span>
        </div>
      `;
    })
    .join("");
  const lanes = sortedLanes.map(({ lane, spans }) => {
    const width = 92 + Math.round(((lane.total_tokens || 0) / maxTokens) * 156);
    return `
      <div class="timeline-column ${lane.lane === selectedTimelineLane ? "active" : ""}" data-lane="${lane.lane}" style="width:${width}px">
        <div class="timeline-column-label">
          <strong>${escapeHtml(lane.agent_label || lane.lane)}</strong>
          <span>${formatNumber(lane.total_tokens)} tok</span>
        </div>
        <div class="timeline-column-track">
          ${spans
            .map((span) => {
              const top = (span.start_offset_s / duration) * 100;
              const height = Math.max(1.4, ((span.end_offset_s - span.start_offset_s) / duration) * 100);
              return `
                <div
                  class="timeline-span"
                  style="top:${top}%;height:${height}%;background:${workKindColor(span.work_kind)}"
                  title="${phaseLabel(span.phase)} · ${span.work_kind} · ${formatNumber(span.total_tokens)} tok"
                ></div>
              `;
            })
            .join("")}
        </div>
      </div>
    `;
  });
  els.timelineChart.innerHTML = `
    <div class="timeline-vertical">
      <div class="timeline-axis">${axisMarks}</div>
      <div class="timeline-columns">${lanes.join("")}</div>
    </div>
  `;
  els.timelineChart.querySelectorAll(".timeline-column").forEach((element) => {
    element.addEventListener("click", () => {
      selectedTimelineLane = element.dataset.lane || "";
      renderTimelineChart(timeline);
      renderTimelineLaneDetail(timeline);
    });
  });
  renderTimelineLaneDetail(timeline);
}

async function loadTimeline(taskId) {
  selectedTimelineTask = taskId || state.selectedTask || "";
  if (!selectedTimelineTask) {
    renderTimelineSummary(null, null);
    return;
  }
  const params = currentParams();
  if (selectedTimelineTask && selectedTimelineTask !== "<unknown>") {
    params.set("task_id", selectedTimelineTask);
  }
  const key = params.toString();
  let payload = state.timelineCache.get(key);
  if (!payload) {
    const response = await fetch(`/api/timeline?${key}`);
    payload = await response.json();
    state.timelineCache.set(key, payload);
  }
  const timeline = payload.timeline;
  if (!selectedTimelineTask || selectedTimelineTask === "<unknown>") {
    timeline.scope_label = "Current Filter";
  } else {
    timeline.scope_label = `Task ${selectedTimelineTask}`;
  }
  const lanes = visibleTimelineLanes(timeline);
  if (!lanes.find((lane) => lane.lane === selectedTimelineLane)) {
    selectedTimelineLane = lanes[0]?.lane || "";
  }

  renderTimelineSummary(timeline, payload.summary);
  renderTimelineChart(timeline);
  renderWorkflowPanel(timeline);
}

function renderOverviewDirectories(container, items) {
  renderBars(container, items, {
    clickable: true,
    selectedKey: state.selectedDirectory,
    labelFormatter: shortenDirectoryLabel,
    onClick: async (key, element) => {
      const willSelect = state.selectedDirectory !== key;
      applyImmediateSelection(container, element, willSelect);
      state.selectedDirectory = willSelect ? key : "";
      if (state.selectedTask) {
        state.selectedTask = "";
      }
      state.selectedAgent = "";
      state.selectedStep = "";
      selectedWorkflowItem = "";
      selectedTimelineLane = "";
      await loadFilteredRecords();
    },
  });
}

function renderOverviewAgentTypes(container, items) {
  renderBars(container, items, {
    clickable: true,
    selectedKey: state.selectedAgent,
    onClick: async (key, element) => {
      const willSelect = state.selectedAgent !== key;
      applyImmediateSelection(container, element, willSelect);
      state.selectedAgent = willSelect ? key : "";
      state.selectedStep = "";
      await loadFilteredRecords();
    },
  });
}

function renderOverviewStepTypes(container, items) {
  renderBars(container, items, {
    selectedKey: state.selectedStep,
    clickable: true,
    colorFn: (key) => {
      const colors = {
        vanessa_log_analysis: "#59e1ff",
        screenshot_analysis: "#9c6cff",
        code_reading: "#47f08a",
        code_writing: "#ff8a5b",
        test_execution: "#ff5db1",
        review: "#ffd166",
        planning: "#8ea4ff",
        other: "#7b8aa6",
      };
      return colors[key] || workKindColor(key);
    },
    onClick: async (key, element) => {
      const willSelect = state.selectedStep !== key;
      applyImmediateSelection(container, element, willSelect);
      state.selectedStep = willSelect ? key : "";
      await loadFilteredRecords();
    },
  });
}

function currentParams() {
  const params = new URLSearchParams();
  if (state.selectedProvider) params.append("provider", state.selectedProvider);
  if (state.selectedDirectory) params.append("working_directory", state.selectedDirectory);
  if (state.selectedTask) params.append("task_id", state.selectedTask);
  if (state.selectedAgent) params.append("agent_type", state.selectedAgent);
  if (state.selectedStep) params.append("step_type", state.selectedStep);
  return params;
}

async function loadFilteredRecords() {
  els.statusText.textContent = "Loading records…";
  try {
    const providerParams = new URLSearchParams();
    if (state.selectedProvider) providerParams.append("provider", state.selectedProvider);

    const taskParams = new URLSearchParams(providerParams);
    if (state.selectedDirectory) taskParams.append("working_directory", state.selectedDirectory);

    const agentParams = new URLSearchParams(taskParams);
    if (state.selectedTask) agentParams.append("task_id", state.selectedTask);

    const detailParams = new URLSearchParams(agentParams);
    if (state.selectedAgent) detailParams.append("agent_type", state.selectedAgent);
    if (state.selectedStep) detailParams.append("step_type", state.selectedStep);

    const [providerPayload, taskPayload, agentPayload, detailPayload] = await Promise.all([
      fetchSummary(providerParams),
      fetchSummary(taskParams),
      fetchSummary(agentParams),
      fetchSummary(detailParams),
    ]);

    state.filtered = detailPayload.records;
    optionSelect(els.providerFilter, state.dataset.summary.filters.providers || [], state.selectedProvider);
    state.selectedProvider = els.providerFilter.value;
    const availableTaskIds = taskPayload.summary.filters.task_ids || [];
    if (state.selectedTask && !availableTaskIds.includes(state.selectedTask)) {
      state.selectedTask = "";
    }
    const availableAgentTypes = agentPayload.summary.filters.agent_types || [];
    if (state.selectedAgent && !availableAgentTypes.includes(state.selectedAgent)) {
      state.selectedAgent = "";
    }
    const availableStepTypes = detailPayload.summary.filters.step_types || [];
    if (state.selectedStep && !availableStepTypes.includes(state.selectedStep)) {
      state.selectedStep = "";
    }

    renderMetrics(detailPayload.summary.totals);
    renderOverviewDirectories(els.directoryChart, providerPayload.summary.aggregates.by_working_directory || []);
    renderOverviewAgentTypes(els.agentBreakdown, agentPayload.summary.aggregates.by_agent_type || []);
    renderOverviewStepTypes(els.stepChart, detailPayload.summary.aggregates.by_step_type || []);
    renderBars(els.taskOverview, taskPayload.summary.aggregates.by_task_id || [], {
      clickable: true,
      selectedKey: state.selectedTask,
      labelFormatter: (key) => key || "<unknown>",
      onClick: async (key, element) => {
        const willSelect = state.selectedTask !== key;
        applyImmediateSelection(els.taskOverview, element, willSelect);
        state.selectedTask = willSelect ? key : "";
      state.selectedAgent = "";
      state.selectedStep = "";
      selectedWorkflowItem = "";
      selectedTimelineLane = "";
      await loadFilteredRecords();
    },
  });

    renderRecords(state.filtered);
    els.statusText.textContent = `Loaded ${formatNumber(detailPayload.summary.totals.records || 0)} records`;
    await loadTimeline(state.selectedTask || "");
    if (state.timelineRefreshHandle) {
      window.clearTimeout(state.timelineRefreshHandle);
    }
    state.timelineRefreshHandle = window.setTimeout(() => {
      loadTimeline(state.selectedTask || "");
    }, 120);
  } finally {
    document.body.classList.remove("loading");
  }
}

function attachFilters() {
  els.providerFilter.addEventListener("change", async () => {
    state.selectedProvider = els.providerFilter.value;
    state.selectedDirectory = "";
    state.selectedTask = "";
    state.selectedAgent = "";
    state.selectedStep = "";
    selectedWorkflowItem = "";
    selectedTimelineLane = "";
    state.summaryCache.clear();
    state.timelineCache.clear();
    await loadFilteredRecords();
  });
  els.reloadButton.addEventListener("click", async () => {
    state.summaryCache.clear();
    state.timelineCache.clear();
    document.body.classList.add("loading");
    await loadFilteredRecords();
  });
  els.clearSelectionButton.addEventListener("click", async () => {
    state.selectedProvider = "";
    state.selectedDirectory = "";
    state.selectedTask = "";
    state.selectedAgent = "";
    state.selectedStep = "";
    selectedWorkflowItem = "";
    selectedTimelineLane = "";
    state.summaryCache.clear();
    state.timelineCache.clear();
    els.providerFilter.value = "";
    await loadFilteredRecords();
  });
}

async function boot() {
  const response = await fetch("/api/data");
  state.dataset = await response.json();
  optionSelect(els.providerFilter, state.dataset.summary.filters.providers || [], state.selectedProvider);
  attachFilters();
  await loadFilteredRecords();
}

boot().catch((error) => {
  console.error(error);
  els.statusText.textContent = `Load failed: ${error.message}`;
});
