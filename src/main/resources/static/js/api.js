async function apiRequest(url, options = {}) {
  const response = await fetch(url, {
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      ...(options.headers || {})
    },
    ...options
  });

  const text = await response.text();

  if (!response.ok) {
    throw new Error(text || `Request failed: ${response.status}`);
  }

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

async function getTeamTaskGroups(email) {
  return apiRequest(`/api/tasks/team/${encodeURIComponent(email)}/groups`);
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

async function getChecklistItems(taskId) {
    return apiRequest(`/api/tasks/${taskId}/checklist`);
}

async function addChecklistItem(taskId, itemText, changedBy) {
    return apiRequest(
        `/api/tasks/${taskId}/checklist?changedBy=${encodeURIComponent(changedBy)}`,
        {
            method: "POST",
            body: JSON.stringify({
                itemText
            })
        }
    );
}

async function toggleChecklistItem(checklistItemId, changedBy) {
    return apiRequest(
        `/api/tasks/checklist/${checklistItemId}/toggle?changedBy=${encodeURIComponent(changedBy)}`,
        {
            method: "PUT"
        }
    );
}

async function deleteChecklistItem(checklistItemId, changedBy) {
    return apiRequest(
        `/api/tasks/checklist/${checklistItemId}?changedBy=${encodeURIComponent(changedBy)}`,
        {
            method: "DELETE"
        }
    );
}

async function getTaskDependencies(taskId) {
    return apiRequest(`/api/tasks/${taskId}/dependencies`);
}

async function getDependencyCandidates(taskId) {
    return apiRequest(`/api/tasks/${taskId}/dependency-candidates`);
}

async function addTaskDependency(taskId, dependsOnTaskId, dependencyType = "BLOCKED_BY") {
    return apiRequest(`/api/tasks/${taskId}/dependencies`, {
        method: "POST",
        body: JSON.stringify({
            dependsOnTaskId,
            dependencyType
        })
    });
}

async function deleteTaskDependency(dependencyId) {
    return apiRequest(`/api/tasks/dependencies/${dependencyId}`, {
        method: "DELETE"
    });
}
