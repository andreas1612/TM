document.addEventListener("DOMContentLoaded", loadMyTasks);

let currentUser = null;

async function loadMyTasks() {
    try {
        currentUser = await getCurrentUser();
        document.getElementById("userName").innerText = currentUser.name;

        const tasks = await getMyTasks(currentUser.email);
        renderMyTasks(tasks);
    } catch (error) {
        console.error(error);
        document.querySelector(".main-content").innerHTML =
            "<div class='error'>Failed to load my tasks.</div>";
    }
}

function renderMyTasks(tasks) {
    const table = document.getElementById("myTasksTable");
    table.innerHTML = "";

    if (!tasks || tasks.length === 0) {
        table.innerHTML = `
            <tr>
                <td colspan="6" class="muted">
                    No tasks assigned to you.
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
                <p class="muted">${task.description || "No description"}</p>
            </td>
            <td>
                <span class="badge ${getStatusClass(task.status)}">
                    ${formatStatus(task.status)}
                </span>
            </td>
            <td>${task.priority || "-"}</td>
            <td>${task.dueDate || "No due date"}</td>
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
        `;

        table.appendChild(row);
    });

    document.querySelectorAll(".status-select").forEach(select => {
        select.addEventListener("change", async function () {
            const taskId = this.dataset.taskId;
            const newStatus = this.value;

            await updateTaskStatus(taskId, newStatus, currentUser.email);
            await loadMyTasks();
        });
    });
}

function openTask(taskId) {
    window.location.href =
        `/task-details.html?id=${taskId}`;
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
