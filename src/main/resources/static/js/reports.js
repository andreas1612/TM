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

document.addEventListener("DOMContentLoaded", () => {
    const monthInput = document.getElementById("monthInput");
    monthInput.value = currentMonthValue();

    document.getElementById("viewSelect")
        .addEventListener("change", loadReport);
    monthInput.addEventListener("change", loadReport);
    document.getElementById("downloadCsv")
        .addEventListener("click", downloadCsv);

    loadUser();
    loadReport();
});

async function loadUser() {
    try {
        const user = await getCurrentUser();
        document.getElementById("userName").innerText = user.name;
    } catch (err) {
        console.error(err);
    }
}

async function loadReport() {
    currentView = document.getElementById("viewSelect").value;
    const month = document.getElementById("monthInput").value;
    const { start, end } = monthRange(month);

    const view = VIEWS[currentView];

    document.getElementById("tableTitle").innerText = view.title;

    try {
        currentRows = await view.loader(start, end) || [];
        renderTable(view, currentRows);
        renderTotals(currentRows);
        document.getElementById("tableSubtitle").innerText =
            `${currentRows.length} ${currentView === "employee" ? "people" : "groups"} • sorted by tasks completed`;
    } catch (err) {
        console.error(err);
        document.getElementById("reportBody").innerHTML =
            `<tr><td colspan="${view.columns.length}"><div class="error">Failed to load report.</div></td></tr>`;
    }
}

function renderTable(view, rows) {
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
    const view = VIEWS[currentView];
    const header = view.columns.map(c => c.label);
    const lines = [header.map(csvCell).join(",")];

    currentRows.forEach(row => {
        lines.push(view.columns.map(c => {
            const value = row[c.key];
            if (value === null || value === undefined || value === "") {
                return csvCell(c.num ? 0 : (c.fallback || ""));
            }
            return csvCell(value);
        }).join(","));
    });

    const month = document.getElementById("monthInput").value;
    const blob = new Blob([lines.join("\r\n")], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `report-${currentView}-${month}.csv`;
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

function escapeHtml(value) {
    return String(value)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;");
}

function currentMonthValue() {
    const now = new Date();
    const month = String(now.getMonth() + 1).padStart(2, "0");
    return `${now.getFullYear()}-${month}`;
}

function monthRange(monthValue) {
    const [year, month] = monthValue.split("-").map(Number);
    const start = `${monthValue}-01`;
    const lastDay = new Date(year, month, 0).getDate();
    const end = `${monthValue}-${String(lastDay).padStart(2, "0")}`;
    return { start, end };
}
