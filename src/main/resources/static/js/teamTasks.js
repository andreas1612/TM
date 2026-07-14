document.addEventListener("DOMContentLoaded", loadTeamTasks);

let currentTeamGroups = [];
let teamFiltersBound = false;
let teamFilters = {
    title: "",
    assignedTo: "",
    status: "",
    dueDate: ""
};
let teamSortStates = {};

async function loadTeamTasks() {
    try {

        const user = await getCurrentUser();

        document.getElementById("userName").innerText =
            user.name;

        const groups = await getTeamTaskGroups(user.email);

        currentTeamGroups = groups || [];
        bindTeamFilters();
        renderTaskGroups(currentTeamGroups);

    } catch (error) {

        console.error(error);

        document.querySelector(".main-content").innerHTML =
            "<div class='error'>Failed to load team tasks.</div>";
    }
}

function bindTeamFilters() {
    if (teamFiltersBound) {
        return;
    }

    const filterBindings = [
        ["teamTitleFilter", "title"],
        ["teamAssignedFilter", "assignedTo"],
        ["teamStatusFilter", "status"],
        ["teamDueDateFilter", "dueDate"]
    ];

    filterBindings.forEach(([elementId, filterKey]) => {
        document
            .getElementById(elementId)
            .addEventListener("input", event => {
                teamFilters[filterKey] =
                    event.target.value.trim().toLowerCase();

                renderTaskGroups(currentTeamGroups);
            });
    });

    teamFiltersBound = true;
}

function renderTaskGroups(groups) {

    const container =
        document.getElementById("teamTaskGroups");

    container.innerHTML = "";

    if (!groups || groups.length === 0) {

        container.innerHTML = `
            <p class="muted">No team tasks found.</p>
        `;

        return;
    }

    groups.forEach(group => {
        const groupSection = document.createElement("section");

        groupSection.className = "team-task-group";

        const overdueCount = (group.tasks || []).filter(isOverdue).length;

        groupSection.innerHTML = `
            <div class="team-task-group-header">
                <div>
                    <h3>${group.groupName}</h3>
                    <p>${formatGroupType(group.groupType)}</p>
                </div>
                <div>
                    <span class="badge">
                        ${group.tasks ? group.tasks.length : 0} tasks
                    </span>
                    ${overdueCount > 0 ? `<span class="badge overdue">${overdueCount} overdue</span>` : ""}
                </div>
            </div>

            <table class="table">
                <thead>
                    <tr>
                        <th><button type="button" class="sort-header" data-sort="title">Title</button></th>
                        <th><button type="button" class="sort-header" data-sort="assignedTo">Assigned To</button></th>
                        <th><button type="button" class="sort-header" data-sort="status">Status</button></th>
                        <th><button type="button" class="sort-header" data-sort="dueDate">Due Date</button></th>
                        <th>Details</th>
                        <th></th>
                    </tr>
                </thead>
                <tbody></tbody>
            </table>
        `;

        const tableBody = groupSection.querySelector("tbody");
        const sortState =
            getTeamSortState(group.groupKey);

        renderGroupRows(groupSection, tableBody, group.tasks || [], teamFilters, sortState);

        groupSection.querySelectorAll(".sort-header").forEach(header => {
            header.addEventListener("click", () => {
                handleGroupSort(
                    groupSection,
                    tableBody,
                    group.tasks || [],
                    teamFilters,
                    sortState,
                    header.dataset.sort
                );
            });
        });

        container.appendChild(groupSection);
    });
}

function getTeamSortState(groupKey) {
    if (!teamSortStates[groupKey]) {
        teamSortStates[groupKey] = {
            column: null,
            direction: "asc"
        };
    }

    return teamSortStates[groupKey];
}

function renderGroupRows(groupSection, tableBody, tasks, filters, sortState) {
    tableBody.innerHTML = "";

    const filteredTasks =
        getSortedTeamTasks(
            tasks.filter(task => teamTaskMatchesFilters(task, filters)),
            sortState
        );

    updateTeamSortHeaders(groupSection, sortState);

    if (filteredTasks.length === 0) {
        tableBody.innerHTML = `
            <tr>
                <td colspan="6" class="muted">
                    No tasks found for this group.
                </td>
            </tr>
        `;
        return;
    }

    filteredTasks.forEach(task => {
        const row = document.createElement("tr");

        const overdue = isOverdue(task);
        if (overdue) {
            row.className = "overdue-row";
        }

        row.innerHTML = `
            <td>
                <strong>${task.title}</strong>
            </td>
            <td>
                ${task.assignedTo && task.assignedTo.length > 0
                    ? task.assignedTo.join(", ")
                    : "-"}
            </td>
            <td>
                <span class="badge ${getStatusClass(task.status)}">
                    ${formatStatus(task.status)}
                </span>
            </td>
            <td>
                ${task.dueDate || "-"}
                ${overdue ? '<span class="badge overdue">Overdue</span>' : ""}
            </td>
            <td>
                <button
                    type="button"
                    class="btn-secondary"
                    aria-label="Edit ${task.title}"
                    onclick="openTask(${task.taskId})">
                    Edit
                </button>
            </td>
            <td>
                <button
                    type="button"
                    class="archive-link"
                    aria-label="Archive ${task.title}"
                    onclick="handleArchiveTask(${task.taskId})">
                    Archive
                </button>
            </td>
        `;

        tableBody.appendChild(row);
    });
}

function handleGroupSort(groupSection, tableBody, tasks, filters, sortState, column) {
    if (sortState.column === column) {
        sortState.direction =
            sortState.direction === "asc" ? "desc" : "asc";
    } else {
        sortState.column = column;
        sortState.direction = "asc";
    }

    renderGroupRows(groupSection, tableBody, tasks, filters, sortState);
}

function getSortedTeamTasks(tasks, sortState) {
    if (!sortState.column) {
        return tasks;
    }

    return [...tasks].sort((firstTask, secondTask) => {
        const firstValue =
            getTeamSortValue(firstTask, sortState.column);

        const secondValue =
            getTeamSortValue(secondTask, sortState.column);

        const comparison =
            firstValue.localeCompare(secondValue, undefined, {
                numeric: true,
                sensitivity: "base"
            });

        return sortState.direction === "asc"
            ? comparison
            : comparison * -1;
    });
}

function getTeamSortValue(task, column) {
    if (column === "title") {
        return task.title || "";
    }

    if (column === "assignedTo") {
        return task.assignedTo && task.assignedTo.length > 0
            ? task.assignedTo.join(", ")
            : "";
    }

    if (column === "status") {
        return formatStatus(task.status);
    }

    if (column === "dueDate") {
        return task.dueDate || "";
    }

    return "";
}

function updateTeamSortHeaders(groupSection, sortState) {
    groupSection.querySelectorAll(".sort-header").forEach(header => {
        const isActive =
            header.dataset.sort === sortState.column;

        header.classList.toggle("active", isActive);
        header.dataset.direction =
            isActive ? sortState.direction : "";
    });
}

function teamTaskMatchesFilters(task, filters) {
    const title =
        (task.title || "").toLowerCase();

    const assignedTo =
        task.assignedTo && task.assignedTo.length > 0
            ? task.assignedTo.join(", ").toLowerCase()
            : "-";

    const dueDate =
        (task.dueDate || "-").toLowerCase();

    return (!filters.title || title.includes(filters.title))
        && (!filters.assignedTo || assignedTo.includes(filters.assignedTo))
        && (!filters.status || task.status.toLowerCase() === filters.status)
        && (!filters.dueDate || dueDate.includes(filters.dueDate));
}

function formatGroupType(groupType) {
    if (groupType === "TEAM")
        return "Same team";
    if (groupType === "DEPARTMENT")
        return "Department fallback";
    if (groupType === "SUPERVISED_TEAM")
        return "Direct reports from another team";
    if (groupType === "SUPERVISED_DEPARTMENT")
        return "Direct reports without a team";
    return "Team tasks";
}

function formatStatus(status) {
    if (status === "TO_DO") 
        return "To Do";
    if (status === "IN_PROGRESS")
        return "In Progress";
    if (status === "ON_HOLD") 
        return "On Hold";
    if (status === "COMPLETED")
        return "Done";
    if (status === "CANCELLED")
        return "Cancelled";
    return status || "-";
}

function getStatusClass(status) {
    if (status === "TO_DO")
        return "todo";
    if (status === "IN_PROGRESS")
        return "progress";
    if (status === "ON_HOLD")
        return "hold";
    if (status === "COMPLETED")
        return "done";
    if (status === "CANCELLED")
        return "cancelled";
    return "";
}

function isOverdue(task) {
    if (!task.dueDate) return false;
    if (["COMPLETED", "DONE", "CANCELLED"].includes(task.status)) return false;
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    return new Date(`${task.dueDate}T00:00:00`) < today;
}

function openTask(taskId) {

    window.location.href =
        `/task-details.html?id=${taskId}`;
}

async function handleArchiveTask(taskId) {
    const confirmed =
        window.confirm("Archive this task? It will be hidden from task lists but kept in the database.");

    if (!confirmed) {
        return;
    }

    const user =
        await getCurrentUser();

    await archiveTask(taskId, user.email);
    await loadTeamTasks();
}
