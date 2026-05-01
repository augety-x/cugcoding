# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

CUGCoding Forum — a campus learning exchange forum (校园学习交流论坛), built as a graduation design project. Currently a minimal working skeleton with core auth/post/comment features. The `paicoding/` folder contains a mature reference forum codebase (技术派) to borrow patterns from.

## Architecture

### Current Project Structure

```
backend/   → Spring Boot 2.7.18 + Java 8 + JDBC Template + MySQL
frontend/  → Vue 3 + Vite 6 + Vue Router 4 + Axios + md-editor-v3
paicoding/ → Reference project: Spring Boot 2.7.1 + MyBatis-Plus + Redis + ES + RabbitMQ + MongoDB
```

### Backend Package Layout (flat, single-module)

```
com.cugcoding.forum
├── model/       → POJOs: User, Post, PostDetail, Comment, CommentView
├── repo/        → ForumRepository (JDBC Template, raw SQL)
├── service/     → ForumService (business logic + init seed data)
├── web/         → ApiController (REST endpoints), GlobalExceptionHandler
└── config/      → AppConfig (DataSource bean), StartupRunner (CommandLineRunner)
```

- **Session-based auth**: userId/username stored in HttpSession after login. No JWT, no Redis.
- **Database**: Auto-creates schema on startup via `initSchema()` in code. No Liquibase/migrations.
- **Frontend** served separately via Vite dev server (port 5173), backend on 8080. CORS configured for localhost:5173.

### paicoding Reference Project (multi-module, layered)

```
paicoding-api     → Entities, DTOs/VOs, enums (base module)
paicoding-core    → Utils, cache, search, async, dynamic config
paicoding-service → Business logic, MyBatis-Plus DAOs, converters
paicoding-ui      → Thymeleaf templates, CSS, JS static resources
paicoding-web     → Controllers, interceptors, QuickForumApplication entry point
```

Key patterns worth borrowing: layered module separation, JWT+Redis dual-auth login, MyBatis-Plus ORM, Liquibase schema management, global interceptor for auth, Cookie-based token (`f-session`).

## Common Commands

### Backend
```bash
# Start (from backend/ directory)
cd backend && mvn spring-boot:run

# Run tests
cd backend && mvn test

# Skip tests on build
mvn clean install -DskipTests=true
```

### Frontend
```bash
# Install dependencies (first time or after package.json changes)
cd frontend && npm install

# Dev server (port 5173)
cd frontend && npm run dev

# Production build
cd frontend && npm run build
```

### One-click startup (PowerShell)
```powershell
.\run-all.ps1        # Opens backend + frontend in separate windows
.\run-backend.ps1    # Backend only
.\run-frontend.ps1   # Frontend only (auto npm install if needed)
```

### paicoding Reference
```bash
cd paicoding && mvn clean install -DskipTests=true
```

## Configuration

- Backend config: `backend/src/main/resources/application.properties` — MySQL connection at `localhost:3306/cugcoding` with credentials `root/root123`
- Test config: `backend/src/main/resources/application-test.properties`
- Frontend `axios.defaults.baseURL` hardcoded to `http://localhost:8080` in `frontend/src/main.js`
- Vite dev server configured to port 5173 in `vite.config.js`

## Key Constraints

- **Java 8** target. Do not use Java 9+ APIs.
- Database `cugcoding` must exist in MySQL before first run. Tables are auto-created.
- Default admin account: `admin` / `123456`, created automatically on first startup.
- No database migrations — schema changes require editing `ForumRepository.initSchema()` directly.
- Frontend and backend are separate processes. CORS is narrowly scoped to `localhost:5173`.

## paicoding Integration Notes

When borrowing patterns from paicoding, note these differences:
- paicoding uses **MyBatis-Plus** (your project uses raw JDBC) — consider upgrading for larger features
- paicoding auth is **JWT + Redis cookie-based** (your project uses plain HttpSession) — see `paicoding_login_report.md` for detailed analysis
- paicoding uses **Druid** connection pool (your project uses the default HikariCP via Spring Boot)
- paicoding frontend is **Thymeleaf SSR** (your project uses Vue SPA) — UI patterns won't directly port
- paicoding has **Liquibase** for schema versioning — worth adopting as your schema grows

The `paicoding_login_report.md` file contains a thorough analysis of paicoding's login system with recommendations for simplifying it for your project.
