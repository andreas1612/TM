// ============================================================
// js/tour.js
// On-site guided tour ("coachmark") for Task Manager.
//
// A self-contained, zero-dependency overlay tour: a dimming backdrop
// spotlights one real element at a time while a tooltip explains it,
// with Back / Next / Skip. Modelled on the Treppides Hub tours.
//
//  - Included on every page; picks the right set of steps from the
//    current URL, and injects a "Tutorial" button into that page's topbar.
//  - First-time visitors to a page get a gentle prompt; a per-page,
//    versioned "seen" flag in localStorage means returning users just
//    see the button.
//  - Keyboard: <- / -> to step, Esc to exit.
//  - No build step, no CDN, no framework. Plain <script>, self-inits.
// ============================================================
(function () {
  "use strict";

  // ---- Per-page scripts --------------------------------------
  // Each step points at a real element by selector. `center: true`
  // dims the whole screen for intro/outro cards. `placement` is a hint
  // (top | bottom | right); the positioner falls back automatically.
  // Anchors verified against each page's markup.
  const PAGES = {
    "dashboard.html": [
      { title: "Welcome to Task Manager", body: "This quick tour shows you around the dashboard and where everything lives. Use Next to move on, or Skip any time.", center: true },
      { title: "Your main menu", body: "Everything is one click away here — your tasks, your team's tasks, creating work, reports and time logging. The rest of the tour walks through each.", anchor: ".side-nav", placement: "right" },
      { title: "My Tasks", body: "Everything assigned to you. You change a task's status here, and when you mark one complete it asks how long it took.", anchor: '.side-nav a[href="/my-tasks.html"]', placement: "right" },
      { title: "Team Tasks", body: "Work assigned to your direct reports, so you can see at a glance what your team is on.", anchor: '.side-nav a[href="/team-tasks.html"]', placement: "right" },
      { title: "Create Task", body: "Add new work and assign it to people, with a due date, priority and client.", anchor: '.side-nav a[href="/create-task.html"]', placement: "right" },
      { title: "Reports", body: "Turns completed work into per-employee, team and department summaries — with time-to-complete figures and a PDF export.", anchor: '.side-nav a[href="/reports.html"]', placement: "right" },
      { title: "Log Time", body: "Your end-of-day check-in: enter the hours you spent on each in-progress task. Those totals prefill completion times and feed the reports.", anchor: '.side-nav a[href="/log-time.html"]', placement: "right" },
      { title: "Your numbers at a glance", body: "These cards summarise your workload: tasks you own, your team's open tasks, what's due soon, what you've completed, and anything overdue.", anchor: ".summary-grid", placement: "bottom" },
      { title: "My Tasks preview", body: "Your most recent tasks show here — click any row to open its details, or “View all” for the full list.", anchor: ".large-panel", placement: "top" },
      { title: "Due Soon", body: "The nearest deadlines among your open tasks, so nothing sneaks up on you.", anchor: "#dueSoonList", placement: "top" },
      { title: "Overdue", body: "Anything past its due date and still open lands here — your catch-up list.", anchor: "#overdueList", placement: "top" },
      { title: "Team Tasks preview", body: "A quick look at your team's latest tasks, with “View all” for the full picture.", anchor: "#teamTasksPreview", placement: "top" },
      { title: "Status breakdown", body: "How your own tasks split across To Do, In Progress and Completed.", anchor: ".status-stack", placement: "top" },
      { title: "Quick actions", body: "Shortcuts to the things you do most — create a task, or jump straight to your tasks or your team's.", anchor: ".action-panel", placement: "top" },
      { title: "New task, anywhere", body: "Wherever you are, this button starts a new task.", anchor: ".header-actions .primary-button", placement: "bottom" },
      { title: "You're ready", body: "That's the tour. Start with My Tasks or create something new — and you can replay this any time from the Tutorial button up here.", center: true },
    ],

    "my-tasks.html": [
      { title: "My Tasks", body: "Everything assigned to you, in one list. Here's a quick tour of what you can do.", center: true },
      { title: "Filter the list", body: "Narrow your tasks by title, status, priority or due date to find exactly what you need.", anchor: ".table-filters", placement: "bottom" },
      { title: "Sort the columns", body: "Click Title, Status, Priority or Due Date to sort by that column — click again to reverse the order.", anchor: ".table thead", placement: "bottom" },
      { title: "Edit — open the full task", body: "The Edit button opens the task's own page: description, assignees, checklist, dependencies, comments and its complete history.", anchor: "#myTasksTable .btn-secondary", placement: "top" },
      { title: "Update status", body: "Change a task's status right here from the dropdown. Choosing Completed asks how long it took — prefilled with any hours you've logged.", anchor: "#myTasksTable .status-select", placement: "top" },
      { title: "Archive", body: "Archive clears a finished task from your list without deleting it — it stays in reports and history.", anchor: "#myTasksTable .archive-link", placement: "top" },
      { title: "New task", body: "Start a new task any time with this button.", anchor: ".header-actions .primary-button", placement: "bottom" },
      { title: "That's it", body: "Work through your tasks and keep their status up to date. Replay this tour any time from the Tutorial button.", center: true },
    ],

    "team-tasks.html": [
      { title: "Team Tasks", body: "Work assigned to your direct reports — grouped so you can track what the team is on. A quick tour follows.", center: true },
      { title: "Filter", body: "Narrow the view by title, assignee, status or due date across every group.", anchor: ".team-table-filters", placement: "bottom" },
      { title: "Task groups", body: "Tasks are grouped by team, department and supervised exceptions. Each group's header shows its task count and how many are overdue.", anchor: ".team-task-group-header", placement: "bottom" },
      { title: "Sort within a group", body: "Inside a group, click Title, Assigned To, Status or Due Date to sort that group's tasks.", anchor: "#teamTaskGroups .table thead", placement: "bottom" },
      { title: "Edit a task", body: "The Edit button opens any task's own page — details, checklist, comments and history.", anchor: "#teamTaskGroups .btn-secondary", placement: "top" },
      { title: "Archive", body: "Archive clears a completed task from the list without deleting it.", anchor: "#teamTaskGroups .archive-link", placement: "top" },
      { title: "That's it", body: "Use this page to keep an eye on your team's workload. Replay this tour any time from the Tutorial button.", center: true },
    ],

    "create-task.html": [
      { title: "Create Task", body: "Add a new task and assign it to one or more people. This tour walks through the form.", center: true },
      { title: "Title", body: "Give the task a short, clear title — this is what people see in their lists.", anchor: "#title", placement: "bottom" },
      { title: "Description", body: "Add any detail the assignee needs to get started.", anchor: "#description", placement: "bottom" },
      { title: "Assign to", body: "Filter people by name or email and pick them from the list. Everyone you add shows as a chip you can remove.", anchor: "#employeeFilter", placement: "bottom" },
      { title: "Client", body: "Optionally record the client this work is for — it shows on reports.", anchor: "#client", placement: "bottom" },
      { title: "Priority", body: "Set the priority: Low, Medium or High.", anchor: "#priority", placement: "bottom" },
      { title: "Due date", body: "Pick a due date. Overdue open tasks are flagged on the dashboard and reports.", anchor: "#dueDate", placement: "top" },
      { title: "Create it", body: "Create the task — it appears immediately in each assignee's My Tasks.", anchor: ".form-actions .primary-button", placement: "top" },
      { title: "That's it", body: "That's all it takes to assign work. Replay this tour any time from the Tutorial button.", center: true },
    ],

    "reports.html": [
      { title: "Reports", body: "Turn completed work into summaries by employee, team or department. Here's how it fits together.", center: true },
      { title: "Group by", body: "Choose how to group the report — by employee, team or department. The relevant filters appear for your choice.", anchor: "#viewSelect", placement: "bottom" },
      { title: "Narrow it down", body: "Pick a specific employee, team or department here. For team and department groupings, tick “Detailed (tasks by status)” to break the results down task-by-task.", anchor: ".report-filters", placement: "bottom" },
      { title: "Date range", body: "Set the From / To dates. Completed counts tasks finished within this range; Assigned, Open and Overdue are a live snapshot as of now.", anchor: "#startInput", placement: "bottom" },
      { title: "Headline totals", body: "At-a-glance totals for the selection: completed, assigned, open and overdue.", anchor: ".summary-grid", placement: "bottom" },
      { title: "The breakdown", body: "The table breaks the numbers down, sorted by tasks completed. In team and department views, click a row to drill into its people or tasks.", anchor: "#summaryWrap", placement: "top" },
      { title: "Export as PDF", body: "Download a formatted PDF of the current report to share.", anchor: "#downloadPdf", placement: "bottom" },
      { title: "Export as CSV", body: "Or download the raw numbers as a CSV for your own analysis.", anchor: "#downloadCsv", placement: "bottom" },
      { title: "That's it", body: "Pick a grouping and a date range, then read or export the results. Replay this tour any time from the Tutorial button.", center: true },
    ],

    "log-time.html": [
      { title: "Log Time", body: "Your end-of-day check-in: record the hours you spent on each in-progress task. A quick tour follows.", center: true },
      { title: "Your in-progress tasks", body: "Each task currently in progress and assigned to you is listed. The “Logged so far” column shows the running total you've already recorded against it.", anchor: ".panel .table", placement: "top" },
      { title: "The “Hours today” box", body: "This is where you enter time — one box per task. Type the number of hours you spent on that task today.", anchor: ".hours-input", placement: "left" },
      { title: "How the box works", body: "Decimals are fine — 1.5 means one and a half hours (1h 30m). The up/down arrows step in 15-minute (0.25) increments, and you can type a value directly. Leave a box blank or set it to 0 to skip a task you didn't work on — those rows are ignored.", anchor: ".hours-input", placement: "left" },
      { title: "Save", body: "Save your hours. Each entry is added to that task's running total (and recorded in its history), and the totals prefill the time-to-complete when you finish a task.", anchor: "#saveTimeButton", placement: "top" },
      { title: "That's it", body: "A quick daily habit keeps your reported time accurate. Replay this tour any time from the Tutorial button.", center: true },
    ],

    "task-details.html": [
      { title: "Task Details", body: "The full view of a single task — where you edit it and manage its checklist, dependencies, comments and history.", center: true },
      { title: "Edit the task", body: "The left panel holds the task's core fields: title, description, client and due date. Change anything here.", anchor: "#taskForm", placement: "right" },
      { title: "Status & priority", body: "Move the task along its lifecycle and set its priority as work progresses.", anchor: "#status", placement: "right" },
      { title: "Who's assigned", body: "Add people from the dropdown; each shows as a chip you can remove. A task can have several assignees.", anchor: "#employeeDropdown", placement: "right" },
      { title: "Save your changes", body: "Nothing on the left is saved until you click Save Changes.", anchor: "#taskForm .form-actions .primary-button", placement: "top" },
      { title: "Archive the task", body: "Archive hides a finished task from lists without deleting it — it stays in reports and history.", anchor: "#archiveTaskButton", placement: "top" },
      { title: "Checklist", body: "Break the task into smaller items and tick them off as you go.", anchor: "#checklistForm", placement: "top" },
      { title: "Dependencies", body: "Link tasks that must be completed first — useful for ordering the work.", anchor: "#dependencyForm", placement: "top" },
      { title: "Comments", body: "Discuss the task with anyone involved; each comment is stamped with its author and time.", anchor: "#commentForm", placement: "top" },
      { title: "History", body: "Every change — status moves, time logged, edits — is recorded here as an activity log.", anchor: ".history-panel", placement: "top" },
      { title: "That's it", body: "Everything about a task lives on this page. Replay this tour any time from the Tutorial button.", center: true },
    ],
  };

  // ---- Resolve the current page ------------------------------
  const PAGE = (location.pathname.split("/").pop() || "dashboard.html").toLowerCase();
  const STEPS = PAGES[PAGE];
  const SEEN_KEY = "treppides:taskmanager:tourSeen:" + PAGE + ":v1";

  // ---- DOM helpers -------------------------------------------

  function el(tag, props, html) {
    const node = document.createElement(tag);
    Object.assign(node, props || {});
    if (html) node.innerHTML = html;
    return node;
  }

  // ---- Tour controller ---------------------------------------

  let _state = { active: false, index: 0, onKey: null, onResize: null };
  let _overlay = null; // { backdrop, hole, tip } once built

  function buildOverlay() {
    if (_overlay) return _overlay;

    const backdrop = el("div", { id: "tmTourBackdrop", className: "tm-tour-backdrop" });
    const hole = el("div", { className: "tm-tour-hole" });
    const tip = el("div", { className: "tm-tour-tip", role: "dialog" });
    tip.setAttribute("aria-modal", "true");
    tip.setAttribute("aria-live", "polite");

    backdrop.appendChild(hole);
    backdrop.appendChild(tip);
    document.body.appendChild(backdrop);

    // Clicking the dimmed area does nothing — Skip is the explicit exit.
    backdrop.addEventListener("click", function (e) {
      if (e.target === backdrop) e.stopPropagation();
    });

    _overlay = { backdrop: backdrop, hole: hole, tip: tip };
    return _overlay;
  }

  const PAD = 6;    // spotlight padding around the element
  const MARGIN = 8; // gap between hole edge and tooltip

  // Resolve a step's anchor to the best *visible* element to spotlight.
  // Some anchors (e.g. a task list) may be empty (0-height) before data
  // loads, so climb to the nearest ancestor with a real box.
  function resolveAnchor(selector) {
    const start = document.querySelector(selector);
    if (!start) return null;
    let node = start;
    while (node) {
      const r = node.getBoundingClientRect();
      if (r.width > 1 && r.height > 1) return node;
      node = node.parentElement;
    }
    return null;
  }

  function centerTip() {
    const o = _overlay;
    o.hole.style.display = "none";
    o.backdrop.classList.add("tm-tour-dim");
    o.tip.style.left = "50%";
    o.tip.style.top = "50%";
    o.tip.style.transform = "translate(-50%, -50%)";
  }

  // Measure the anchor and place the spotlight + tooltip. Does not scroll.
  function placeAt(step, target) {
    if (!_state.active) return;
    const o = _overlay;
    o.backdrop.classList.remove("tm-tour-dim");

    const r = target.getBoundingClientRect();
    o.hole.style.display = "block";
    o.hole.style.left = (r.left - PAD) + "px";
    o.hole.style.top = (r.top - PAD) + "px";
    o.hole.style.width = (r.width + PAD * 2) + "px";
    o.hole.style.height = (r.height + PAD * 2) + "px";

    o.tip.style.transform = "none";
    const tipR = o.tip.getBoundingClientRect();
    const vw = window.innerWidth, vh = window.innerHeight;

    // Prefer the hinted side; fall back to top/bottom (with flip) if no room.
    const placeRight = step.placement === "right";
    const placeLeft = step.placement === "left";

    let top, left;
    if (placeRight && r.right + MARGIN + tipR.width <= vw) {
      // To the right of the anchor, vertically centered on it.
      left = r.right + MARGIN;
      top = r.top + r.height / 2 - tipR.height / 2;
    } else if (placeLeft && r.left - MARGIN - tipR.width >= 0) {
      // To the left of the anchor, vertically centered on it.
      left = r.left - MARGIN - tipR.width;
      top = r.top + r.height / 2 - tipR.height / 2;
    } else {
      let placeBelow = step.placement !== "top";
      if (placeBelow && r.bottom + MARGIN + tipR.height > vh) placeBelow = false;
      if (!placeBelow && r.top - MARGIN - tipR.height < 0) placeBelow = true;
      top = placeBelow ? r.bottom + MARGIN : r.top - MARGIN - tipR.height;
      left = r.left + r.width / 2 - tipR.width / 2;
    }

    left = Math.max(MARGIN, Math.min(left, vw - tipR.width - MARGIN));
    top = Math.max(MARGIN, Math.min(top, vh - tipR.height - MARGIN));

    o.tip.style.left = left + "px";
    o.tip.style.top = top + "px";
  }

  // Scroll the target into view, then place on the next frame.
  function positionFor(step) {
    if (step.center || !step.anchor) { centerTip(); return; }
    const target = resolveAnchor(step.anchor);
    if (!target) { centerTip(); return; }

    target.scrollIntoView({ behavior: "smooth", block: "center", inline: "nearest" });
    requestAnimationFrame(function () { placeAt(step, target); });
  }

  // Lightweight reposition for scroll/resize: re-measure only, never scroll.
  function reposition() {
    if (!_state.active) return;
    const step = STEPS[_state.index];
    if (step.center || !step.anchor) { centerTip(); return; }
    const target = resolveAnchor(step.anchor);
    if (!target) { centerTip(); return; }
    placeAt(step, target);
  }

  function render() {
    const step = STEPS[_state.index];
    const tip = _overlay.tip;
    const isFirst = _state.index === 0;
    const isLast = _state.index === STEPS.length - 1;

    tip.innerHTML =
      '<div class="tm-tour-progress">Step ' + (_state.index + 1) + " of " + STEPS.length + "</div>" +
      '<h3 class="tm-tour-title"></h3>' +
      '<p class="tm-tour-body"></p>' +
      '<div class="tm-tour-actions">' +
      '  <button type="button" class="tm-tour-x" data-tour="skip">Skip tour</button>' +
      '  <div class="tm-tour-nav">' +
      '    <button type="button" class="tm-tour-secondary" data-tour="back"' + (isFirst ? " disabled" : "") + ">Back</button>" +
      '    <button type="button" class="tm-tour-primary" data-tour="next">' + (isLast ? "Done" : "Next") + "</button>" +
      "  </div>" +
      "</div>";

    // textContent (not innerHTML) for the copy — keep it text-only.
    tip.querySelector(".tm-tour-title").textContent = step.title;
    tip.querySelector(".tm-tour-body").textContent = step.body;

    tip.querySelector('[data-tour="skip"]').addEventListener("click", end);
    tip.querySelector('[data-tour="back"]').addEventListener("click", function () { go(-1); });
    tip.querySelector('[data-tour="next"]').addEventListener("click", function () {
      if (isLast) end(); else go(1);
    });

    positionFor(step);
  }

  function go(delta) {
    const next = _state.index + delta;
    if (next < 0 || next >= STEPS.length) return;
    _state.index = next;
    render();
  }

  function start(fromStep) {
    if (_state.active) return;
    _state.active = true;
    _state.index = fromStep || 0;
    buildOverlay();
    _overlay.backdrop.classList.add("open");
    _overlay.backdrop.classList.remove("tm-tour-dim");

    _state.onKey = function (e) {
      if (e.key === "Escape") end();
      else if (e.key === "ArrowRight") go(1);
      else if (e.key === "ArrowLeft") go(-1);
    };
    _state.onResize = function () { reposition(); };
    document.addEventListener("keydown", _state.onKey);
    window.addEventListener("resize", _state.onResize);
    window.addEventListener("scroll", _state.onResize, true);

    render();
  }

  function end() {
    if (!_state.active) return;
    _state.active = false;
    if (_overlay) _overlay.backdrop.classList.remove("open");
    document.removeEventListener("keydown", _state.onKey);
    window.removeEventListener("resize", _state.onResize);
    window.removeEventListener("scroll", _state.onResize, true);
    try { localStorage.setItem(SEEN_KEY, "1"); } catch (_) {}
    _dismissPrompt();
  }

  // ---- First-visit prompt ------------------------------------

  let _prompt = null;

  function _dismissPrompt() {
    if (_prompt) _prompt.remove();
    _prompt = null;
  }

  function maybePrompt() {
    let seen = false;
    try { seen = localStorage.getItem(SEEN_KEY) === "1"; } catch (_) {}
    if (seen || _prompt) return;

    _prompt = el("div", { className: "tm-tour-prompt" });
    _prompt.innerHTML =
      '<span class="tm-tour-prompt-text">New to this page? Take a quick guided tour.</span>' +
      '<button type="button" class="tm-tour-primary" data-tour="prompt-start">Start tour</button>' +
      '<button type="button" class="tm-tour-secondary" data-tour="prompt-dismiss">No thanks</button>';
    document.body.appendChild(_prompt);
    _prompt.querySelector('[data-tour="prompt-start"]').addEventListener("click", function () {
      _dismissPrompt();
      start(0);
    });
    _prompt.querySelector('[data-tour="prompt-dismiss"]').addEventListener("click", function () {
      try { localStorage.setItem(SEEN_KEY, "1"); } catch (_) {}
      _dismissPrompt();
    });
  }

  // ---- Header "Tutorial" button ------------------------------

  function injectHelpButton() {
    if (document.getElementById("tmTourBtn")) return;
    // Scope to the topbar — some pages have a second .header-actions elsewhere.
    const actions = document.querySelector(".topbar .header-actions") ||
                    document.querySelector(".header-actions");
    if (!actions) return;
    const btn = el("button", {
      type: "button",
      id: "tmTourBtn",
      className: "tm-tour-btn",
      title: "Replay the guided tour",
    },
      '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor"' +
      ' stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
      '<circle cx="12" cy="12" r="10"></circle>' +
      '<path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"></path>' +
      '<line x1="12" y1="17" x2="12.01" y2="17"></line>' +
      "</svg><span>Tutorial</span>");
    btn.addEventListener("click", function () { start(0); });
    actions.insertBefore(btn, actions.firstChild);
  }

  // ---- Init --------------------------------------------------

  function init() {
    if (!STEPS || !STEPS.length) return; // no tour defined for this page
    injectHelpButton();
    // Expose for programmatic start (e.g. a future help menu).
    window.__tm_tour = { start: start, end: end };
    maybePrompt();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
