document.addEventListener("DOMContentLoaded", () => {
    loadDashboard();
});

async function loadDashboard() {
    try {
        const user = await getCurrentUser();

        document.getElementById("userName").innerText =
            user.name;

        const myTasks =
            await getMyTasks(user.email);

        const teamTasks =
            await getTeamTasks(user.email);

        const todo =
            myTasks.filter(t => t.status === "TO_DO").length;

        const inProgress =
            myTasks.filter(t => t.status === "IN_PROGRESS").length;

        const onHold =
            myTasks.filter(t => t.status === "ON_HOLD").length;

        const completed =
            myTasks.filter(t => isCompletedStatus(t.status)).length;

        const cancelled =
            myTasks.filter(t => isCancelledStatus(t.status)).length;

        const activeTasks = myTasks
            .filter(t => !isInactiveStatus(t.status))
            .sort(compareByDueDate);

        const activeTeamTasks = teamTasks
            .filter(t => !isInactiveStatus(t.status))
            .sort(compareByDueDate);

        const dueSoon = activeTasks
            .filter(t => t.dueDate)
            .slice(0, 5);

        document.getElementById("myCount").innerText =
            activeTasks.length;

        document.getElementById("teamCount").innerText =
            activeTeamTasks.length;

        document.getElementById("dueSoonCount").innerText =
            dueSoon.length;

        document.getElementById("doneCount").innerText =
            completed;

        document.getElementById("myStats").innerText =
            `${todo} To Do • ${inProgress} In Progress • ${onHold} On Hold`;

        document.getElementById("todoBreakdown").innerText =
            todo;

        document.getElementById("progressBreakdown").innerText =
            inProgress;

        document.getElementById("doneBreakdown").innerText =
            completed;

        const holdBreakdown =
            document.getElementById("holdBreakdown");

        if (holdBreakdown) {
            holdBreakdown.innerText = onHold;
        }

        const cancelledBreakdown =
            document.getElementById("cancelledBreakdown");

        if (cancelledBreakdown) {
            cancelledBreakdown.innerText = cancelled;
        }

        renderTaskPreview(
            "myTasksPreview",
            activeTasks.slice(0, 5)
        );

        renderTaskPreview(
            "dueSoonList",
            dueSoon,
            true
        );

        renderTaskPreview(
            "teamTasksPreview",
            activeTeamTasks.slice(0, 5),
            true
        );

    } catch (err) {
        console.error(err);

        document.querySelector(".main-content").innerHTML =
            "<div class='error'>Failed to load dashboard.</div>";
    }
}

function renderTaskPreview(containerId, tasks, compact = false) {
    const container =
        document.getElementById(containerId);

    if (!container) {
        return;
    }

    container.innerHTML = "";

    if (!tasks || tasks.length === 0) {
        container.innerHTML =
            "<p class='muted'>No tasks</p>";

        return;
    }

    tasks.forEach(task => {
        const div =
            document.createElement("div");

        div.className = compact
            ? "task-row compact-row clickable-task"
            : "task-row clickable-task";

        div.addEventListener("click", () => {
            window.location.href =
                `/task-details.html?id=${task.taskId}`;
        });

        div.innerHTML = `
            <div>
                <strong>${task.title}</strong>
                <p>${task.description || "No description"}</p>
            </div>

            <div class="task-meta">
                <span class="badge ${getStatusClass(task.status)}">
                    ${formatStatus(task.status)}
                </span>

                <span>
                    ${task.dueDate || "No due date"}
                </span>
            </div>
        `;

        container.appendChild(div);
    });
}

function formatStatus(status) {
    if (status === "TO_DO")
        return "To Do";

    if (status === "IN_PROGRESS")
        return "In Progress";

    if (status === "ON_HOLD")
        return "On Hold";

    if (isCompletedStatus(status))
        return "Completed";

    if (isCancelledStatus(status))
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

    if (isCompletedStatus(status))
        return "done";

    if (isCancelledStatus(status))
        return "cancelled";

    return "";
}

function isCompletedStatus(status) {
    return status === "DONE"
        || status === "COMPLETED";
}

function isCancelledStatus(status) {
    return status === "CANCELLED";
}

function isInactiveStatus(status) {
    return isCompletedStatus(status)
        || isCancelledStatus(status);
}

function compareByDueDate(a, b) {
    if (!a.dueDate && !b.dueDate)
        return 0;

    if (!a.dueDate)
        return 1;

    if (!b.dueDate)
        return -1;

    return new Date(a.dueDate)
        - new Date(b.dueDate);
}