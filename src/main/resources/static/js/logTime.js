document.addEventListener("DOMContentLoaded", init);

let currentUser = null;
let candidates = [];

async function init() {
    try {
        currentUser = await getCurrentUser();
        document.getElementById("userName").innerText = currentUser.name;

        candidates = (await getLogTimeCandidates()) || [];
        renderCandidates();

        document
            .getElementById("saveTimeButton")
            .addEventListener("click", saveHours);
    } catch (error) {
        console.error(error);
        document.querySelector(".main-content").innerHTML =
            "<div class='error'>Failed to load your tasks.</div>";
    }
}

function renderCandidates() {
    const table = document.getElementById("logTimeTable");
    table.innerHTML = "";

    if (candidates.length === 0) {
        table.innerHTML =
            "<tr><td colspan='4' class='muted'>You have no in-progress tasks to log time for.</td></tr>";
        document.getElementById("saveTimeButton").disabled = true;
        return;
    }

    candidates.forEach(task => {
        const row = document.createElement("tr");
        row.innerHTML = `
            <td>${escapeHtml(task.title)}</td>
            <td>${task.priority || "-"}</td>
            <td class="muted">${formatDuration(task.loggedMinutes)}</td>
            <td>
                <input
                    type="number"
                    min="0"
                    step="0.25"
                    class="hours-input"
                    data-task-id="${task.taskId}"
                    placeholder="0"
                    aria-label="Hours worked today on ${escapeHtml(task.title)}">
            </td>
        `;
        table.appendChild(row);
    });
}

async function saveHours() {
    const inputs = document.querySelectorAll(".hours-input");
    const entries = [];

    for (const input of inputs) {
        const value = input.value.trim();
        if (value === "") {
            continue;
        }

        const hours = parseFloat(value);
        if (isNaN(hours) || hours < 0) {
            alert("Please enter valid, non-negative hours.");
            return;
        }

        if (hours === 0) {
            continue;
        }

        entries.push({
            taskId: parseInt(input.dataset.taskId, 10),
            minutes: Math.round(hours * 60)
        });
    }

    if (entries.length === 0) {
        setStatus("Nothing to save — enter some hours first.");
        return;
    }

    const button = document.getElementById("saveTimeButton");
    button.disabled = true;
    setStatus("Saving...");

    try {
        await logTime(entries);
        setStatus("Saved. Thank you!");
        // Refresh so the "logged so far" totals update and inputs clear.
        candidates = (await getLogTimeCandidates()) || [];
        renderCandidates();
    } catch (error) {
        console.error(error);
        setStatus("Failed to save. Please try again.");
    } finally {
        button.disabled = false;
    }
}

function setStatus(message) {
    document.getElementById("saveStatus").innerText = message;
}

function formatDuration(minutes) {
    if (minutes == null) {
        return "-";
    }

    const hours = Math.floor(minutes / 60);
    const mins = Math.round(minutes % 60);

    if (hours === 0) {
        return `${mins}m`;
    }

    if (mins === 0) {
        return `${hours}h`;
    }

    return `${hours}h ${mins}m`;
}

function escapeHtml(text) {
    const div = document.createElement("div");
    div.innerText = text == null ? "" : text;
    return div.innerHTML;
}
