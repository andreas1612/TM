document.addEventListener("DOMContentLoaded", initTaskDetailsPage);

let currentUser = null;
let taskId = null;
let selectedEmployees = [];
let availableEmployees = [];

async function initTaskDetailsPage() {
    try {
        const params = new URLSearchParams(window.location.search);
        taskId = params.get("id");

        if (!taskId) {
            document.querySelector(".main-content").innerHTML =
                "<div class='error'>No task selected.</div>";
            return;
        }

        currentUser = await getCurrentUser();
        document.getElementById("userName").innerText = currentUser.name;

        await loadAvailableEmployees();
        await loadTask();
        await loadChecklist();
        await loadComments();
        await loadHistory();

        document
            .getElementById("employeeDropdown")
            .addEventListener("change", handleEmployeeDropdownChange);

        document
            .getElementById("taskForm")
            .addEventListener("submit", handleSaveTask);
        
        document
            .getElementById("checklistForm")
            .addEventListener("submit", handleAddChecklistItem);
                
        document
            .getElementById("commentForm")
            .addEventListener("submit", handleAddComment);

    } catch (error) {
        console.error(error);
        document.querySelector(".main-content").innerHTML =
            "<div class='error'>Failed to load task details.</div>";
    }
}

async function loadAvailableEmployees() {
    const employees = await getDirectReports(currentUser.email);

    availableEmployees = [
        {
            email: currentUser.email,
            fullName: `${currentUser.name} (You)`
        },
        ...employees.filter(employee =>
            employee.email !== currentUser.email
        )
    ];
}

async function loadTask() {
    const task = await getTaskById(taskId);

    document.getElementById("title").value =
        task.title || "";

    document.getElementById("description").value =
        task.description || "";

    selectedEmployees =
        task.assignedTo && task.assignedTo.length > 0
            ? [...task.assignedTo]
            : [];

    renderSelectedEmployees();
    renderEmployeeDropdown();

    document.getElementById("client").value =
        task.client || "";

    document.getElementById("status").value =
        task.status || "TO_DO";

    document.getElementById("priority").value =
        task.priority || "MEDIUM";

    document.getElementById("dueDate").value =
        task.dueDate || "";
}

function renderEmployeeDropdown() {
    const dropdown =
        document.getElementById("employeeDropdown");

    dropdown.innerHTML =
        "<option value=''>Add employee...</option>";

    const remainingEmployees =
        availableEmployees.filter(employee =>
            !selectedEmployees.includes(employee.email)
        );

    remainingEmployees.forEach(employee => {
        const option =
            document.createElement("option");

        option.value =
            employee.email;

        option.textContent =
            employee.fullName;

        dropdown.appendChild(option);
    });
}

function handleEmployeeDropdownChange(event) {
    const email =
        event.target.value;

    if (!email) {
        return;
    }

    if (!selectedEmployees.includes(email)) {
        selectedEmployees.push(email);
    }

    event.target.value = "";

    renderSelectedEmployees();
    renderEmployeeDropdown();
}

function renderSelectedEmployees() {
    const container =
        document.getElementById("selectedEmployees");

    container.innerHTML = "";

    if (selectedEmployees.length === 0) {
        container.innerHTML =
            "<p class='muted'>No employees selected.</p>";
        return;
    }

    selectedEmployees.forEach(email => {
        const tag =
            document.createElement("div");

        tag.className =
            "employee-tag";

        tag.innerHTML = `
            <span>${getEmployeeName(email)}</span>
            <button type="button"
                    onclick="removeEmployee('${email}')">
                ×
            </button>
        `;

        container.appendChild(tag);
    });
}

function removeEmployee(email) {
    selectedEmployees =
        selectedEmployees.filter(selectedEmail =>
            selectedEmail !== email
        );

    renderSelectedEmployees();
    renderEmployeeDropdown();
}

function getEmployeeName(email) {
    const employee =
        availableEmployees.find(employee =>
            employee.email === email
        );

    return employee
        ? employee.fullName
        : email;
}

async function handleSaveTask(event) {
    event.preventDefault();

    const message = document.getElementById("saveMessage");
    message.innerHTML = "";

    if (selectedEmployees.length === 0) {
        message.innerHTML =
            "<div class='error'>Please select at least one employee.</div>";
        return;
    }

    const updatedTask = {
        title: document.getElementById("title").value.trim(),
        description: document.getElementById("description").value.trim(),
        assignedTo: selectedEmployees,
        client: document.getElementById("client").value.trim() || null,
        status: document.getElementById("status").value,
        priority: document.getElementById("priority").value,
        dueDate: document.getElementById("dueDate").value || null
    };

    try {
        await updateTask(taskId, updatedTask, currentUser.email);

        message.innerHTML =
            "<div class='success'>Task updated successfully.</div>";

        await loadTask();
        await loadHistory();

        setTimeout(() => {
            message.innerHTML = "";
        }, 2500);

    } catch (error) {
        console.error(error);
        message.innerHTML =
            "<div class='error'>Failed to update task.</div>";
    }
}

async function loadComments() {
    const comments = await getTaskComments(taskId);
    renderComments(comments);
}

function renderComments(comments) {
    const list = document.getElementById("commentsList");
    list.innerHTML = "";

    if (!comments || comments.length === 0) {
        list.innerHTML = "<p class='muted'>No comments yet.</p>";
        return;
    }

    comments.forEach(comment => {
        const item = document.createElement("div");
        item.className = "comment-item";

        item.innerHTML = `
            <div class="comment-header">
                <strong>${comment.createdBy?.fullName || comment.createdBy?.email || "Unknown"}</strong>
                <span>${formatDateTime(comment.createdAt)}</span>
            </div>
            <p>${comment.commentText}</p>
        `;

        list.appendChild(item);
    });
}

async function handleAddComment(event) {
    event.preventDefault();

    const textarea = document.getElementById("commentText");
    const text = textarea.value.trim();

    if (!text) return;

    try {
        await addComment(taskId, text, currentUser.email);

        textarea.value = "";
        await loadComments();

    } catch (error) {
        console.error(error);
        alert("Failed to add comment.");
    }
}

async function loadHistory() {
    const history = await getTaskHistory(taskId);
    renderHistory(history);
}

function renderHistory(history) {
    const container =
        document.getElementById("historyList");

    container.innerHTML = "";

    if (!history || history.length === 0) {
        container.innerHTML =
            "<p class='muted'>No history yet.</p>";
        return;
    }

    history.forEach(item => {
        const row = document.createElement("div");

        row.className = "history-item";

        row.innerHTML = `
            <div class="history-header">
                <strong>
                    ${item.changedBy?.fullName || item.changedBy?.email || "Unknown"}
                </strong>

                <span>
                    ${formatDateTime(item.changedAt)}
                </span>
            </div>

            <p>
                Changed
                <strong>${item.fieldChanged}</strong>
            </p>

            <div class="history-values">
                <div>
                    <label>Old</label>
                    <span>${item.oldValue || "-"}</span>
                </div>

                <div>
                    <label>New</label>
                    <span>${item.newValue || "-"}</span>
                </div>
            </div>
        `;

        container.appendChild(row);
    });
}

function formatDateTime(value) {
    if (!value) return "";

    const date = new Date(value);

    if (Number.isNaN(date.getTime())) {
        return value;
    }

    return date.toLocaleString();
}

async function loadChecklist() {
    const items = await getChecklistItems(taskId);
    renderChecklist(items);
}

function renderChecklist(items) {
    const container = document.getElementById("checklistItems");
    container.innerHTML = "";

    if (!items || items.length === 0) {
        container.innerHTML = "<p class='muted'>No checklist items yet.</p>";
        return;
    }

    items.forEach(item => {
        const row = document.createElement("div");
        row.className = item.isCompleted
            ? "checklist-item completed"
            : "checklist-item";

        row.innerHTML = `
            <label>
                <input type="checkbox"
                       ${item.isCompleted ? "checked" : ""}
                       onchange="handleToggleChecklistItem(${item.checklistItemId})">

                <span>${item.itemText}</span>
            </label>

            <button type="button"
                    class="icon-button"
                    onclick="handleDeleteChecklistItem(${item.checklistItemId})">
                ×
            </button>
        `;

        container.appendChild(row);
    });
}

async function handleAddChecklistItem(event) {
    event.preventDefault();

    const input = document.getElementById("checklistText");
    const text = input.value.trim();

    if (!text) {
        return;
    }

    await addChecklistItem(taskId, text, currentUser.email);

    input.value = "";
    await loadChecklist();
}

async function handleToggleChecklistItem(checklistItemId) {
    await toggleChecklistItem(checklistItemId, currentUser.email);    await loadChecklist();
}

async function handleDeleteChecklistItem(checklistItemId) {
    await deleteChecklistItem(checklistItemId, currentUser.email);
    await loadChecklist();
}