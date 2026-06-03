document.addEventListener("DOMContentLoaded", loadTeamTasks);

async function loadTeamTasks() {
    try {

        const user = await getCurrentUser();

        document.getElementById("userName").innerText =
            user.name;

        const tasks = await getTeamTasks(user.email);

        renderTasks(tasks);

    } catch (error) {

        console.error(error);

        document.querySelector(".main-content").innerHTML =
            "<div class='error'>Failed to load team tasks.</div>";
    }
}

function renderTasks(tasks) {

    const table =
        document.getElementById("teamTasksTable");

    table.innerHTML = "";

    if (!tasks || tasks.length === 0) {

        table.innerHTML = `
            <tr>
                <td colspan="3" class="muted">
                    No team tasks found.
                </td>
            </tr>
        `;

        return;
    }

    tasks.forEach(task => {

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
        `;

        table.appendChild(row);
    });
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