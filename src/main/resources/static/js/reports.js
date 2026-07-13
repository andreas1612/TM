const VIEWS = {
    employee: {
        title: "By Employee",
        loader: getEmployeeStats,
        columns: [
            { key: "fullName", label: "Name" },
            { key: "email", label: "Email" },
            { key: "departmentId", label: "Dept" },
            { key: "teamId", label: "Team" },
            { key: "completedCount", label: "Completed", num: true },
            { key: "assignedCount", label: "Assigned", num: true },
            { key: "openCount", label: "Open", num: true },
            { key: "overdueCount", label: "Overdue", num: true }
        ]
    },
    team: {
        title: "By Team",
        loader: getTeamStats,
        columns: [
            { key: "groupName", label: "Team", fallback: "No team" },
            { key: "completedCount", label: "Completed", num: true },
            { key: "assignedCount", label: "Assigned", num: true },
            { key: "openCount", label: "Open", num: true },
            { key: "overdueCount", label: "Overdue", num: true }
        ]
    },
    department: {
        title: "By Department",
        loader: getDepartmentStats,
        columns: [
            { key: "groupName", label: "Department", fallback: "Unknown" },
            { key: "completedCount", label: "Completed", num: true },
            { key: "assignedCount", label: "Assigned", num: true },
            { key: "openCount", label: "Open", num: true },
            { key: "overdueCount", label: "Overdue", num: true }
        ]
    }
};

let currentRows = [];
let currentView = "employee";
let currentMode = "summary";      // "summary" | "detail"
let currentDetailTasks = [];
let currentDetailName = "";
let currentSummaryColumns = [];   // columns of the currently displayed summary table (for CSV)
let currentSummaryRows = [];      // rows of the currently displayed summary table (for CSV)
let currentUserEmail = null;      // the logged-in viewer; reports are scoped to their people

document.addEventListener("DOMContentLoaded", () => {
    const now = new Date();
    const firstOfMonth = new Date(now.getFullYear(), now.getMonth(), 1);
    document.getElementById("startInput").value = toIsoDate(firstOfMonth);
    document.getElementById("endInput").value = toIsoDate(now);

    document.getElementById("viewSelect").addEventListener("change", loadReport);
    document.getElementById("employeeSelect").addEventListener("change", loadReport);
    document.getElementById("teamSelect").addEventListener("change", loadReport);
    document.getElementById("teamDetail").addEventListener("change", loadReport);
    document.getElementById("startInput").addEventListener("change", loadReport);
    document.getElementById("endInput").addEventListener("change", loadReport);
    document.getElementById("downloadCsv").addEventListener("click", downloadCsv);

    loadUser().then(loadReport);
});

async function loadUser() {
    try {
        const user = await getCurrentUser();
        currentUserEmail = user.email;
        document.getElementById("userName").innerText = user.name;
    } catch (err) {
        console.error(err);
    }
}

async function loadReport() {
    currentView = document.getElementById("viewSelect").value;
    const start = document.getElementById("startInput").value;
    const end = document.getElementById("endInput").value;

    const isEmployee = currentView === "employee";
    const isTeam = currentView === "team";
    document.getElementById("employeeFilter").hidden = !isEmployee;
    document.getElementById("teamFilter").hidden = !isTeam;
    if (!isTeam) {
        document.getElementById("teamDetailFilter").hidden = true;
    }

    if (!start || !end || start > end) {
        showDetail(false);
        document.getElementById("reportHead").innerHTML = "";
        document.getElementById("reportBody").innerHTML =
            `<tr><td><p class="muted">Pick a valid date range (From must be on or before To).</p></td></tr>`;
        return;
    }

    try {
        if (isEmployee) {
            await loadEmployeeView(start, end);
        } else if (isTeam) {
            await loadTeamView(start, end);
        } else {
            await loadGroupView(currentView, start, end);
        }
    } catch (err) {
        console.error(err);
        showDetail(false);
        document.getElementById("reportBody").innerHTML =
            `<tr><td colspan="8"><div class="error">Failed to load report.</div></td></tr>`;
    }
}

async function loadEmployeeView(start, end) {
    const view = VIEWS.employee;
    const summary = await getEmployeeStats(currentUserEmail, start, end) || [];
    currentRows = summary;
    populateEmployeeSelect(summary);

    const selectedEmail = document.getElementById("employeeSelect").value;

    if (!selectedEmail) {
        currentMode = "summary";
        document.getElementById("tableTitle").innerText = view.title;
        document.getElementById("tableSubtitle").innerText =
            `${summary.length} people • sorted by tasks completed`;
        renderTable(view, summary);
        renderTotals(summary);
        showDetail(false);
        return;
    }

    currentMode = "detail";
    const person = summary.find(row => row.email === selectedEmail);
    renderTotals(person ? [person] : []);

    const detail = await getEmployeeDetail(selectedEmail, start, end);
    currentDetailTasks = detail.tasks || [];
    currentDetailName = detail.fullName || detail.email || selectedEmail;

    document.getElementById("tableTitle").innerText = currentDetailName;
    document.getElementById("tableSubtitle").innerText =
        "Detailed report for the selected range";
    renderDetail(currentDetailTasks);
    showDetail(true);
}

async function loadGroupView(viewKey, start, end) {
    const view = VIEWS[viewKey];
    const rows = await view.loader(currentUserEmail, start, end) || [];
    currentRows = rows;
    currentMode = "summary";

    document.getElementById("tableTitle").innerText = view.title;
    document.getElementById("tableSubtitle").innerText =
        `${rows.length} groups • sorted by tasks completed`;
    renderTable(view, rows);
    renderTotals(rows);
    showDetail(false);
}

async function loadTeamView(start, end) {
    const summary = await getTeamStats(currentUserEmail, start, end) || [];
    populateUnitSelect(summary);

    const unit = document.getElementById("teamSelect").value;

    if (!unit) {
        currentMode = "summary";
        document.getElementById("teamDetailFilter").hidden = true;
        document.getElementById("tableTitle").innerText = VIEWS.team.title;
        document.getElementById("tableSubtitle").innerText =
            `${summary.length} groups • sorted by tasks completed`;
        renderTable(VIEWS.team, summary);
        renderTotals(summary);
        showDetail(false);
        return;
    }

    document.getElementById("teamDetailFilter").hidden = false;

    const unitRow = summary.find(row => row.groupKey === unit);
    const unitName = unitRow ? unitRow.groupName : unit;
    renderTotals(unitRow ? [unitRow] : []); // stat cards = the unit's deduped totals

    if (document.getElementById("teamDetail").checked) {
        currentMode = "detail";
        const detail = await getTeamDetail(unit, currentUserEmail, start, end);
        currentDetailTasks = detail.tasks || [];
        currentDetailName = unitName;
        document.getElementById("tableTitle").innerText = `${unitName} — all tasks`;
        document.getElementById("tableSubtitle").innerText = "Team tasks grouped by status";
        renderDetail(currentDetailTasks, true);
        showDetail(true);
        return;
    }

    // "People of the team" — per-employee summary rows scoped to this unit's members
    currentMode = "summary";
    const people = (await getEmployeeStats(currentUserEmail, start, end) || [])
        .filter(row => unitMatches(row, unit));
    document.getElementById("tableTitle").innerText = `${unitName} — people`;
    document.getElementById("tableSubtitle").innerText =
        `${people.length} people • sorted by tasks completed`;
    renderTable(VIEWS.employee, people);
    showDetail(false);
}

function unitMatches(row, unit) {
    const [type, idText] = unit.split(":");
    const id = Number(idText);
    if (type === "team") {
        return row.teamId === id;
    }
    if (type === "dept") {
        return (row.teamId === null || row.teamId === undefined) && row.departmentId === id;
    }
    return false;
}

function populateUnitSelect(rows) {
    const select = document.getElementById("teamSelect");
    const previous = select.value;

    const options = ['<option value="">All</option>'];
    rows.forEach(row => {
        options.push(`<option value="${escapeAttr(row.groupKey)}">${escapeHtml(row.groupName || row.groupKey)}</option>`);
    });
    select.innerHTML = options.join("");

    select.value = rows.some(row => row.groupKey === previous) ? previous : "";
}

function populateEmployeeSelect(rows) {
    const select = document.getElementById("employeeSelect");
    const previous = select.value;

    const options = ['<option value="">All</option>'];
    rows.forEach(row => {
        const name = row.fullName || row.email;
        options.push(`<option value="${escapeAttr(row.email)}">${escapeHtml(name)}</option>`);
    });
    select.innerHTML = options.join("");

    // keep the current selection if that person is still present
    if (rows.some(row => row.email === previous)) {
        select.value = previous;
    } else {
        select.value = "";
    }
}

function showDetail(isDetail) {
    document.getElementById("summaryWrap").hidden = isDetail;
    document.getElementById("detailContainer").hidden = !isDetail;
}

function renderTable(view, rows) {
    currentSummaryColumns = view.columns;
    currentSummaryRows = rows;

    const head = document.getElementById("reportHead");
    const body = document.getElementById("reportBody");

    head.innerHTML =
        "<tr>" + view.columns.map(c => `<th>${c.label}</th>`).join("") + "</tr>";

    if (!rows.length) {
        body.innerHTML =
            `<tr><td colspan="${view.columns.length}"><p class="muted">No data for this period.</p></td></tr>`;
        return;
    }

    body.innerHTML = rows.map(row =>
        "<tr>" + view.columns.map(c => `<td>${formatCell(row, c)}</td>`).join("") + "</tr>"
    ).join("");
}

const DETAIL_SECTIONS = [
    { key: "TO_DO", label: "To Do", kind: "open" },
    { key: "IN_PROGRESS", label: "In Progress", kind: "open" },
    { key: "ON_HOLD", label: "On Hold", kind: "open" },
    { key: "COMPLETED", label: "Completed in range", kind: "completed" },
    { key: "CANCELLED", label: "Cancelled", kind: "cancelled" }
];

function renderDetail(tasks, showAssignees = false) {
    const groups = {};
    tasks.forEach(task => {
        const key = task.status === "DONE" ? "COMPLETED" : task.status;
        (groups[key] = groups[key] || []).push(task);
    });

    const html = DETAIL_SECTIONS
        .map(section => renderDetailSection(section, groups[section.key] || [], showAssignees))
        .filter(Boolean)
        .join("");

    document.getElementById("detailContainer").innerHTML =
        html || `<p class="muted">No tasks in this range.</p>`;
}

function renderDetailSection(section, rows, showAssignees) {
    if (rows.length === 0) {
        return ""; // hide statuses with no tasks
    }

    const overdue = rows.filter(t => t.overdue).length;
    const meta = overdue > 0
        ? `<span class="muted">(${rows.length}, </span><span class="overdue-count">${overdue} overdue</span><span class="muted">)</span>`
        : `<span class="muted">(${rows.length})</span>`;

    const assigneeHead = showAssignees ? "<th>Assigned to</th>" : "";

    let table;
    if (section.kind === "completed") {
        table = `<table class="table">
                <thead><tr><th>Task</th>${assigneeHead}<th>Completed on</th><th>Time taken</th><th>Client</th></tr></thead>
                <tbody>${rows.map(task => completedRow(task, showAssignees)).join("")}</tbody>
            </table>`;
    } else if (section.kind === "cancelled") {
        table = `<table class="table">
                <thead><tr><th>Task</th>${assigneeHead}<th>Due date</th><th>Client</th></tr></thead>
                <tbody>${rows.map(task => cancelledRow(task, showAssignees)).join("")}</tbody>
            </table>`;
    } else {
        table = `<table class="table">
                <thead><tr><th>Task</th>${assigneeHead}<th>Due date</th><th>Open for</th></tr></thead>
                <tbody>${rows.map(task => openRow(task, showAssignees)).join("")}</tbody>
            </table>`;
    }

    return `<div class="detail-section">
        <h3>${section.label} ${meta}</h3>
        ${table}
    </div>`;
}

function assigneeCell(task, showAssignees) {
    if (!showAssignees) {
        return "";
    }
    const names = (task.assignedTo && task.assignedTo.length > 0)
        ? task.assignedTo.join(", ")
        : "-";
    return `<td>${escapeHtml(names)}</td>`;
}

function completedRow(task, showAssignees) {
    return `<tr class="${task.overdue ? "overdue-row" : ""}">
        <td><strong>${escapeHtml(task.title)}</strong>${task.overdue ? ' <span class="badge overdue">Late</span>' : ""}</td>
        ${assigneeCell(task, showAssignees)}
        <td>${task.completedAt || "-"}</td>
        <td>${formatDuration(task.minutesToComplete)}</td>
        <td>${escapeHtml(task.client || "-")}</td>
    </tr>`;
}

function openRow(task, showAssignees) {
    return `<tr class="${task.overdue ? "overdue-row" : ""}">
        <td><strong>${escapeHtml(task.title)}</strong>${task.overdue ? ' <span class="badge overdue">Overdue</span>' : ""}</td>
        ${assigneeCell(task, showAssignees)}
        <td>${task.dueDate || "No due date"}</td>
        <td>${formatDuration(task.minutesOpen)}</td>
    </tr>`;
}

function cancelledRow(task, showAssignees) {
    return `<tr>
        <td><strong>${escapeHtml(task.title)}</strong></td>
        ${assigneeCell(task, showAssignees)}
        <td>${task.dueDate || "No due date"}</td>
        <td>${escapeHtml(task.client || "-")}</td>
    </tr>`;
}

function renderTotals(rows) {
    const sum = key => rows.reduce((acc, r) => acc + (r[key] || 0), 0);
    document.getElementById("totalCompleted").innerText = sum("completedCount");
    document.getElementById("totalAssigned").innerText = sum("assignedCount");
    document.getElementById("totalOpen").innerText = sum("openCount");
    document.getElementById("totalOverdue").innerText = sum("overdueCount");
}

function formatCell(row, column) {
    const value = row[column.key];
    if (value === null || value === undefined || value === "") {
        return escapeHtml(column.fallback || "—");
    }
    return escapeHtml(value);
}

function downloadCsv() {
    const range = `${document.getElementById("startInput").value}_${document.getElementById("endInput").value}`;

    if (currentMode === "detail") {
        downloadDetailCsv(range);
        return;
    }

    const columns = currentSummaryColumns;
    const header = columns.map(c => c.label);
    const lines = [header.map(csvCell).join(",")];

    currentSummaryRows.forEach(row => {
        lines.push(columns.map(c => {
            const value = row[c.key];
            if (value === null || value === undefined || value === "") {
                return csvCell(c.num ? 0 : (c.fallback || ""));
            }
            return csvCell(value);
        }).join(","));
    });

    triggerCsvDownload(lines, `report-${currentView}-${range}.csv`);
}

function downloadDetailCsv(range) {
    const header = ["Task", "Assigned to", "Status", "Due date", "Completed on", "Time taken (h)", "Open for (h)", "Overdue"];
    const lines = [header.map(csvCell).join(",")];

    currentDetailTasks.forEach(task => {
        lines.push([
            task.title,
            (task.assignedTo && task.assignedTo.length > 0) ? task.assignedTo.join(", ") : "",
            formatStatusLabel(task.status),
            task.dueDate || "",
            task.completedAt || "",
            task.minutesToComplete != null ? (task.minutesToComplete / 60).toFixed(1) : "",
            task.minutesOpen != null ? (task.minutesOpen / 60).toFixed(1) : "",
            task.overdue ? "Yes" : "No"
        ].map(csvCell).join(","));
    });

    const safeName = (currentDetailName || "employee").replace(/[^a-z0-9]+/gi, "-").toLowerCase();
    triggerCsvDownload(lines, `report-${safeName}-${range}.csv`);
}

function triggerCsvDownload(lines, filename) {
    const blob = new Blob([lines.join("\r\n")], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
}

function csvCell(value) {
    const text = String(value);
    if (/[",\r\n]/.test(text)) {
        return `"${text.replace(/"/g, '""')}"`;
    }
    return text;
}

function formatDuration(minutes) {
    if (minutes == null) {
        return "-";
    }

    const totalHours = minutes / 60;

    if (totalHours >= 24) {
        const days = Math.floor(totalHours / 24);
        const remainingHours = Math.round(totalHours % 24);
        return remainingHours > 0 ? `${days}d ${remainingHours}h` : `${days}d`;
    }

    if (totalHours >= 1) {
        return `${Math.round(totalHours * 10) / 10}h`;
    }

    return `${minutes}m`;
}

function formatStatusLabel(status) {
    if (status === "TO_DO") return "To Do";
    if (status === "IN_PROGRESS") return "In Progress";
    if (status === "ON_HOLD") return "On Hold";
    if (status === "COMPLETED" || status === "DONE") return "Completed";
    if (status === "CANCELLED") return "Cancelled";
    return status || "-";
}

function escapeHtml(value) {
    return String(value)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;");
}

function escapeAttr(value) {
    return escapeHtml(value).replace(/"/g, "&quot;");
}

function toIsoDate(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const day = String(date.getDate()).padStart(2, "0");
    return `${year}-${month}-${day}`;
}
