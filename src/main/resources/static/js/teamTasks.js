document.addEventListener("DOMContentLoaded", loadTeamTasks);

async function loadTeamTasks() {
    try {

        const user = await getCurrentUser();

        document.getElementById("userName").innerText =
            user.name;

        const groups = await getTeamTaskGroups(user.email);

        renderTaskGroups(groups);

    } catch (error) {

        console.error(error);

        document.querySelector(".main-content").innerHTML =
            "<div class='error'>Failed to load team tasks.</div>";
    }
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

        groupSection.innerHTML = `
            <div class="team-task-group-header">
                <div>
                    <h3>${group.groupName}</h3>
                    <p>${formatGroupType(group.groupType)}</p>
                </div>
                <span class="badge">
                    ${group.tasks ? group.tasks.length : 0} tasks
                </span>
            </div>

            <table class="table">
                <thead>
                    <tr>
                        <th>Title</th>
                        <th>Assigned To</th>
                        <th>Status</th>
                        <th>Due Date</th>
                        <th>Details</th>
                    </tr>
                </thead>
                <tbody></tbody>
            </table>
        `;

        const tableBody = groupSection.querySelector("tbody");

        if (!group.tasks || group.tasks.length === 0) {
            tableBody.innerHTML = `
                <tr>
                    <td colspan="5" class="muted">
                        No tasks found for this group.
                    </td>
                </tr>
            `;
        } else {
            group.tasks.forEach(task => {
                const row = document.createElement("tr");

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
                    </td>
                    <td>
                        <button
                            type="button"
                            class="btn-secondary"
                            onclick="openTask(${task.taskId})">
                            Edit
                        </button>
                    </td>
                `;

                tableBody.appendChild(row);
            });
        }

        container.appendChild(groupSection);
    });
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

function openTask(taskId) {

    window.location.href =
        `/task-details.html?id=${taskId}`;
}
