async function apiRequest(url, options = {}) {
  const response = await fetch(url, {
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      ...(options.headers || {})
    },
    ...options
  });

  if (!response.ok) {
    throw new Error(`Request failed: ${response.status}`);
  }

  const text = await response.text();

  if (!text) {
    return null;
  }

  return JSON.parse(text);
}

async function getCurrentUser() {
  return apiRequest("/api/me");
}

async function getMyTasks(email) {
  return apiRequest(`/api/tasks/employee/${encodeURIComponent(email)}`);
}

async function getTeamTasks(email) {
  return apiRequest(`/api/tasks/team/${encodeURIComponent(email)}`);
}

async function createTask(task) {
  return apiRequest("/api/tasks", {
    method: "POST",
    body: JSON.stringify(task)
  });
}

async function updateTaskStatus(taskId, status, changedBy) {
  return apiRequest(`/api/tasks/${taskId}/status`, {
    method: "PUT",
    body: JSON.stringify({ status, changedBy })
  });
}

async function getTaskById(taskId) {
    return apiRequest(`/api/tasks/${taskId}`);
}

async function updateTask(taskId, task, changedBy) {
    return apiRequest(
        `/api/tasks/${taskId}?changedBy=${encodeURIComponent(changedBy)}`,
        {
            method: "PUT",
            body: JSON.stringify(task)
        }
    );
}

async function getTaskComments(taskId) {
    return apiRequest(`/api/tasks/${taskId}/comments`);
}

async function addComment(taskId, commentText, createdBy) {
    return apiRequest(`/api/tasks/${taskId}/comments`, {
        method: "POST",
        body: JSON.stringify({
            commentText,
            createdBy
        })
    });
}

async function getTaskHistory(taskId) {
    return apiRequest(`/api/tasks/${taskId}/history`);
}

async function getDirectReports(email) {
    return apiRequest(
        `/api/employees/direct-reports/${encodeURIComponent(email)}`
    );
}