document.addEventListener("DOMContentLoaded", loadMyTasks);

let currentUser = null;
let allMyTasks = [];
let filtersBound = false;
let sortColumn = null;
let sortDirection = "asc";

async function loadMyTasks() {
    try {
        currentUser = await getCurrentUser();
        document.getElementById("userName").innerText = currentUser.name;

        const tasks = await getMyTasks(currentUser.email);
        allMyTasks = tasks || [];
        bindTaskFilters();
        renderMyTasks();
    } catch (error) {
        console.error(error);
        document.querySelector(".main-content").innerHTML =
            "<div class='error'>Failed to load my tasks.</div>";
    }
}

function bindTaskFilters() {
    if (filtersBound) {
        return;
    }

    [
        "titleFilter",
        "statusFilter",
        "priorityFilter",
        "dueDateFilter"
    ].forEach(filterId => {
        document
            .getElementById(filterId)
            .addEventListener("input", renderMyTasks);
    });

    document.querySelectorAll(".sort-header").forEach(header => {
        header.addEventListener("click", () => {
            handleSort(header.dataset.sort);
        });
    });

    filtersBound = true;
}

function renderMyTasks() {
    const table = document.getElementById("myTasksTable");
    table.innerHTML = "";

    const tasks = getSortedTasks(getFilteredTasks());
    updateSortHeaders();
    updateOverdueSummary();

    if (!tasks || tasks.length === 0) {
        table.innerHTML = `
            <tr>
                <td colspan="7" class="muted">
                    No tasks assigned to you.
                </td>
            </tr>
        `;
        return;
    }

    tasks.forEach(task => {
        const row = document.createElement("tr");

        const overdue = isOverdue(task);
        if (overdue) {
            row.className = "overdue-row";
        }

        row.innerHTML = `
            <td>
                <strong>${task.title}</strong>
                <p class="muted">${task.description || "No description"}</p>
            </td>
            <td>
                <span class="badge ${getStatusClass(task.status)}">
                    ${formatStatus(task.status)}
                </span>
                ${(["COMPLETED", "DONE"].includes(task.status) && task.completionMinutes != null)
                    ? `<div class="muted time-spent">Took ${formatDuration(task.completionMinutes)}${task.completionTimeEdited ? " · edited" : ""}</div>`
                    : ""}
            </td>
            <td>${task.priority || "-"}</td>
            <td>
                ${task.dueDate || "No due date"}
                ${overdue ? '<span class="badge overdue">Overdue</span>' : ""}
            </td>
             <td>
                <button
                    type="button"
                    class="btn-secondary"
                    onclick="openTask(${task.taskId})">
                    Edit
                </button>
            </td>
            <td>
                <select class="status-select"
                        data-task-id="${task.taskId}"
                        aria-label="Update status for ${task.title}">
                    <option value="TO_DO" ${task.status === "TO_DO" ? "selected" : ""}>To Do</option>
                    <option value="IN_PROGRESS" ${task.status === "IN_PROGRESS" ? "selected" : ""}>In Progress</option>
                    <option value="ON_HOLD" ${task.status === "ON_HOLD" ? "selected" : ""}>On Hold</option>
                    <option value="COMPLETED" ${task.status === "COMPLETED" ? "selected" : ""}>Completed</option>
                    <option value="CANCELLED" ${task.status === "CANCELLED" ? "selected" : ""}>Cancelled</option>
                </select>
            </td>
            <td>
                <button
                    type="button"
                    class="archive-link"
                    onclick="handleArchiveTask(${task.taskId})">
                    Archive
                </button>
            </td>
        `;

        table.appendChild(row);
    });

    document.querySelectorAll(".status-select").forEach(select => {
        select.addEventListener("change", async function () {
            const taskId = this.dataset.taskId;
            const newStatus = this.value;

            let timeSpentMinutes = null;

            if (newStatus === "COMPLETED" || newStatus === "DONE") {
                const task = allMyTasks.find(t => String(t.taskId) === String(taskId));
                const result = await askCompletionTime(taskId, task ? task.loggedMinutes : null);

                if (result.cancelled) {
                    await loadMyTasks(); // revert the dropdown to the saved value
                    return;
                }

                timeSpentMinutes = result.minutes;
            }

            await updateTaskStatus(taskId, newStatus, currentUser.email, timeSpentMinutes);
            await loadMyTasks();
        });
    });
}

function handleSort(column) {
    if (sortColumn === column) {
        sortDirection =
            sortDirection === "asc" ? "desc" : "asc";
    } else {
        sortColumn = column;
        sortDirection = "asc";
    }

    renderMyTasks();
}

function getSortedTasks(tasks) {
    if (!sortColumn) {
        return tasks;
    }

    return [...tasks].sort((firstTask, secondTask) => {
        const firstValue =
            getSortValue(firstTask, sortColumn);

        const secondValue =
            getSortValue(secondTask, sortColumn);

        const comparison =
            firstValue.localeCompare(secondValue, undefined, {
                numeric: true,
                sensitivity: "base"
            });

        return sortDirection === "asc"
            ? comparison
            : comparison * -1;
    });
}

function getSortValue(task, column) {
    if (column === "title") {
        return task.title || "";
    }

    if (column === "status") {
        return formatStatus(task.status);
    }

    if (column === "priority") {
        return task.priority || "";
    }

    if (column === "dueDate") {
        return task.dueDate || "";
    }

    return "";
}

function updateSortHeaders() {
    document.querySelectorAll(".sort-header").forEach(header => {
        const isActive =
            header.dataset.sort === sortColumn;

        header.classList.toggle("active", isActive);
        header.dataset.direction =
            isActive ? sortDirection : "";
    });
}

function getFilteredTasks() {
    const titleFilter =
        document.getElementById("titleFilter").value.trim().toLowerCase();

    const statusFilter =
        document.getElementById("statusFilter").value;

    const priorityFilter =
        document.getElementById("priorityFilter").value;

    const dueDateFilter =
        document.getElementById("dueDateFilter").value.trim().toLowerCase();

    return allMyTasks.filter(task => {
        const title =
            `${task.title || ""} ${task.description || ""}`.toLowerCase();

        const dueDate =
            (task.dueDate || "No due date").toLowerCase();

        return (!titleFilter || title.includes(titleFilter))
            && (!statusFilter || task.status === statusFilter)
            && (!priorityFilter || task.priority === priorityFilter)
            && (!dueDateFilter || dueDate.includes(dueDateFilter));
    });
}

function openTask(taskId) {
    window.location.href =
        `/task-details.html?id=${taskId}`;
}

async function handleArchiveTask(taskId) {
    const confirmed =
        window.confirm("Archive this task? It will be hidden from task lists.");

    if (!confirmed) {
        return;
    }

    await archiveTask(taskId, currentUser.email);
    await loadMyTasks();
}

function formatStatus(status) {
    if (status === "TO_DO") return "To Do";
    if (status === "IN_PROGRESS") return "In Progress";
    if (status === "ON_HOLD") return "On Hold";
    if (status === "COMPLETED") return "Completed";
    if (status === "CANCELLED") return "Cancelled";
    return status || "-";
}

function getStatusClass(status) {
    if (status === "TO_DO") return "todo";
    if (status === "IN_PROGRESS") return "progress";
    if (status === "ON_HOLD") return "hold";
    if (status === "COMPLETED") return "done";
    if (status === "CANCELLED") return "cancelled";
    return "";
}

async function askCompletionTime(taskId, loggedMinutes = null) {
    let calculatedMinutes = 0;

    try {
        const estimate = await getCompletionEstimate(taskId);
        if (estimate && estimate.calculatedMinutes != null) {
            calculatedMinutes = estimate.calculatedMinutes;
        }
    } catch (error) {
        console.error(error);
    }

    // Prefer the time the user has logged day-by-day; fall back to the
    // system-calculated elapsed time when nothing has been logged.
    const hasLogged = loggedMinutes != null;
    const defaultMinutes = hasLogged ? loggedMinutes : calculatedMinutes;
    const defaultHours = (defaultMinutes / 60).toFixed(1);

    const promptMessage = hasLogged
        ? "How long did this task take to complete? (hours)\n" +
          "Pre-filled with the hours you logged. Adjust if needed."
        : "How long did this task take to complete? (hours)\n" +
          "Leave as-is to accept the calculated time.";

    const input = window.prompt(promptMessage, defaultHours);

    if (input === null) {
        return { cancelled: true };
    }

    const trimmed = input.trim();

    if (trimmed === "") {
        return { minutes: defaultMinutes };
    }

    const hours = parseFloat(trimmed);

    if (isNaN(hours) || hours < 0) {
        alert("Please enter a valid, non-negative number of hours.");
        return { cancelled: true };
    }

    return { minutes: Math.round(hours * 60) };
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

function isOverdue(task) {
    if (!task.dueDate) return false;
    if (["COMPLETED", "DONE", "CANCELLED"].includes(task.status)) return false;
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    return new Date(`${task.dueDate}T00:00:00`) < today;
}

function updateOverdueSummary() {
    const subtitle = document.getElementById("myTasksSubtitle");
    if (!subtitle) return;

    const overdueCount = allMyTasks.filter(isOverdue).length;
    subtitle.innerHTML = overdueCount > 0
        ? `Tasks assigned to your account • <span class="overdue-count">${overdueCount} overdue</span>`
        : "Tasks assigned to your account";
}
