document.addEventListener("DOMContentLoaded", initCreateTaskPage);

let currentUser = null;
let selectedEmployees = [];
let availableEmployees = [];
let employeeFilter = "";

async function initCreateTaskPage() {
    try {
        currentUser = await getCurrentUser();

        document.getElementById("userName").innerText =
            currentUser.name;

        await loadAssignableEmployees();

        document
            .getElementById("employeeFilter")
            .addEventListener("input", handleEmployeeFilterInput);

        document
            .getElementById("employeeDropdown")
            .addEventListener("change", handleEmployeeDropdownChange);

        document
            .getElementById("createTaskForm")
            .addEventListener("submit", handleCreateTask);

    } catch (error) {
        console.error(error);

        document.querySelector(".main-content").innerHTML =
            "<div class='error'>Failed to load create task page.</div>";
    }
}

async function loadAssignableEmployees() {
    const employees =
        await getAssignableEmployees(currentUser.email);

    availableEmployees = [
        {
            email: currentUser.email,
            fullName: `${currentUser.name} (You)`
        },
        ...employees.filter(employee =>
            employee.email !== currentUser.email
        )
    ];

    selectedEmployees = [];

    renderSelectedEmployees();
    renderEmployeeDropdown();
}

function renderEmployeeDropdown() {
    const dropdown =
        document.getElementById("employeeDropdown");

    dropdown.innerHTML =
        "<option value=''>Add employee...</option>";

    const normalizedFilter =
        employeeFilter.trim().toLowerCase();

    const remainingEmployees =
        availableEmployees.filter(employee =>
            !selectedEmployees.includes(employee.email)
            && employeeMatchesFilter(employee, normalizedFilter)
        );

    if (remainingEmployees.length === 0) {
        const option =
            document.createElement("option");

        option.value = "";
        option.textContent = normalizedFilter
            ? "No matching employees"
            : "No employees available";
        option.disabled = true;

        dropdown.appendChild(option);
        return;
    }

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

function handleEmployeeFilterInput(event) {
    employeeFilter =
        event.target.value;

    renderEmployeeDropdown();
}

function employeeMatchesFilter(employee, normalizedFilter) {
    if (!normalizedFilter) {
        return true;
    }

    return employee.fullName.toLowerCase().includes(normalizedFilter)
        || employee.email.toLowerCase().includes(normalizedFilter);
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

async function handleCreateTask(event) {
    event.preventDefault();

    const message =
        document.getElementById("message");

    message.innerHTML = "";

    if (selectedEmployees.length === 0) {
        message.innerHTML =
            "<div class='error'>Please select at least one employee.</div>";
        return;
    }

    const task = {
        title: document.getElementById("title").value.trim(),
        description: document.getElementById("description").value.trim(),
        createdBy: currentUser.email,
        status: "TO_DO",
        priority: document.getElementById("priority").value,
        dueDate: document.getElementById("dueDate").value || null,
        client: document.getElementById("client").value.trim() || null,
        assignedTo: selectedEmployees
    };

    try {
        await createTask(task);

        message.innerHTML =
            "<div class='success'>Task created successfully.</div>";

        document.getElementById("createTaskForm").reset();

        selectedEmployees = [];

        renderSelectedEmployees();
        renderEmployeeDropdown();

    } catch (error) {
        console.error(error);

        message.innerHTML =
            "<div class='error'>Failed to create task.</div>";
    }
}
