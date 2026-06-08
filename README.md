# Task Manager

Task Manager is a web application developed for K. Treppides & Co Ltd to support task assignment, tracking, prioritization, and team collaboration.

## Overview

The application allows team leaders and employees to manage tasks efficiently by providing functionality for:

- Creating and assigning tasks
- Tracking task progress
- Managing task priorities
- Setting deadlines
- Receiving task notifications
- Monitoring team workload

## Current Features

### Task Management
- Create tasks
- Edit tasks
- Delete tasks
- Assign tasks to employees
- Add task descriptions
- Set due dates

### Status Tracking
- To Do
- In Progress
- Completed
- On Hold

### Priority Levels
- Low
- Medium
- High

### Employee Management
- Assign tasks to direct reports
- Track assigned tasks
- View task ownership

## Technology Stack

### Backend
- Java
- Spring Boot
- Spring Security
- JPA / Hibernate

### Database
- Microsoft SQL Server

### Frontend
- HTML
- CSS
- JavaScript
- Thymeleaf

### Authentication
- Azure AD / Microsoft Authentication

## Future Enhancements

- Client management integration
- Advanced reporting
- Dashboard analytics
- Email notifications
- Task dependencies
- Calendar integration

## Project Structure

```text
src/
├── main/
│   ├── java/
│   ├── resources/
│   └── webapp/
└── test/
```

## Setup

1. Clone the repository
2. Configure database connection settings
3. Configure Azure AD credentials
4. Run the Spring Boot application

## Authors

**Lygia Pampaka** — original project  
Technology Team, K. Treppides & Co Ltd.

---

## Treppides Deployment Fork (`andreas1612/TM`)

This repository is the **Treppides production deployment** of the Task Manager.
The upstream/mother project is **https://github.com/lygia-p/TaskManager** (Lygia's repo — do not push there).

### What this fork adds on top of upstream

- **Custom login page** (`login.html`) — dark-themed Microsoft SSO screen, captures `?returnTo` for hub redirect
- **Hub auth integration** (`SecurityConfig.java`) — CORS for `hub.treppides.com`, 401 for `/api/**` so hub JS can detect unauthenticated state
- **Root redirect** (`HomeController.java`) — `/` → `/dashboard.html`
- **Hub return flow** (`dashboard.html`) — after Azure AD login, bounces user back to hub if triggered from there

### Pulling upstream changes from Lygia

```bash
git fetch upstream
git merge upstream/main
```

### Credentials

`application.properties` is **gitignored — never commit it**.
Copy `application.properties.example` → `application.properties` and fill in real values.
On the server the real file lives at `~/taskmanager/src/main/resources/application.properties`.

### Production

Deployed on `192.168.0.221` via nginx at `https://hub.treppides.com/projects`.
See `DEPLOY_CHECKLIST.md` for full deployment steps.
