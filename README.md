# clojure-template

This repository is **clojure-template**, a single-tenant starter derived from the Hosting multi-tenant app. Use it as a clean starting point for building your own product.

## Quick Start

### Prerequisites

Before running the application, ensure you have all required software installed. See the [**Complete Software Requirements**](#complete-software-requirements) section below (and the canonical runbook at `docs/general/operations/README.md`). The default dev DB setup uses Docker Compose (`docker-compose.yml`).

### 1. Secrets & Database Setup

This template expects a secrets file for DB credentials:

- Create `config/.secrets.edn` (or `~/.secrets.edn`) with at least:
  - `:db {:dev-password "...", :test-password "..."}`

By default, `bb run-app` uses `docker compose` to start two Postgres containers:
- Dev DB: `multi_tenant_pos` on `localhost:55432`
- Test DB: `multi_tenant_pos_test` on `localhost:55433`

### 2. Project Setup

```bash
# Clone the repository
git clone <repository-url>
cd <repo-dir>

# Install Node.js dependencies
npm install

```

### 3. Start Your Application Stack

```bash
# Terminal 1: Start your development environment
bb run-app
```

First-time DB setup (new database): in another terminal, run:
```bash
bb migrate-and-sync-frontend-config
```

Optional (recommended for the Expenses domain): seed reference geo data:
```bash
bb seed-geo-reference dev

# or run individually:
bb seed-countries dev
bb seed-bh-cities dev
```

The application will be available at:
- **Main App**: http://localhost:8085
- **Admin Panel**: http://localhost:8085/admin/users (admin auth is simplified for this template)

## Complete Software Requirements

### Core Runtime Requirements

| Software | Version | Purpose | Installation |
|----------|---------|---------|--------------|
| **OpenJDK** | 17+ | Clojure JVM runtime | `brew install openjdk@17` |
| **Clojure CLI** | Latest | Clojure application runtime | `brew install clojure/tools/clojure` |
| **Babashka** | 1.12+ | Fast Clojure scripting | `brew install borkdave/brew/babashka` |

### Database Requirements

| Software | Version | Purpose | Installation |
|----------|---------|---------|--------------|
| **PostgreSQL** | 13+ | Primary database | Via `docker compose` (recommended) or `brew install postgresql@14` |
| **psql** | Latest | Database administration | Comes with PostgreSQL |

> **Note**: Two database instances are required:
> - Development: `localhost:55432`
> - Test: `localhost:55433`

### Frontend & Build Tools

| Software | Version | Purpose | Installation |
|----------|---------|---------|--------------|
| **Node.js** | 22.0.0+ | Frontend build tooling | `brew install node@22` |
| **Shadow-CLJS** | 2.28.18+ | ClojureScript compiler | Via npm (`npx shadow-cljs ...`) |
| **PostCSS** | 8.5.6+ | CSS processing | Via npm (auto-installed) |
| **Tailwind CSS** | 4.0.0+ | CSS framework | Via npm (auto-installed) |

### Development Tools

| Software | Version | Purpose | Installation |
|----------|---------|---------|--------------|
| **Clojure-LSP** | Latest | Code intelligence & refactoring | `brew install clojure-lsp/brew/clojure-lsp` |
| **clj-kondo** | 2025.06.05+ | Clojure linter | `brew install borkdave/brew/clj-kondo` |
| **cljfmt** | 0.9.2+ | Code formatter | Included as dependency |
| **neil** | Latest | Dependency management | `brew install borkdave/brew/neil` |

### Testing Frameworks

| Software | Version | Purpose | Installation |
|----------|---------|---------|--------------|
| **Kaocha** | 1.91.1392+ | Backend test runner | Included as dependency |
| **Karma** | 6.4.3+ | Frontend test runner | Via npm (auto-installed) |
| **Chrome/Chromium** | Latest | Browser testing | Install Chrome browser |

### Optional Tools

| Software | Purpose | Installation |
|----------|---------|--------------|
| **Git** | Version control | `brew install git` |
| **pandoc** | Documentation generation | `brew install pandoc` |
| **Fish/Zsh** | Enhanced shell experience | Install your preferred shell |

## One-Command Installation (macOS)

```bash
# Core requirements
brew install openjdk@17 clojure/tools/clojure babashka node@22

# Development tools
brew install clojure-lsp/brew/clojure-lsp borkdave/brew/clj-kondo borkdave/brew/neil

# Optional tools
brew install git pandoc
```

## Development Workflow

### Available Babashka Tasks

The project includes comprehensive task automation via Babashka. Key commands:

```bash
# Application management
bb run-app              # Start the application

# Testing
bb be-test              # Run backend tests
bb fe-test-node         # Run frontend tests

# 🚨 IMPORTANT: Always save test output before analysis:
bb be-test 2>&1 | tee /tmp/be-test.txt && grep "FAIL" /tmp/be-test.txt
npm run test:cljs 2>&1 | tee /tmp/fe-test.txt && grep "FAIL" /tmp/fe-test.txt
# NEVER re-run tests just to grep differently!

# Code quality
bb lint                 # Run clj-kondo linting
bb cljfmt-check         # Check code formatting
bb cljfmt-fix           # Fix code formatting
bb fix-lint             # Auto-fix lint warnings

# Database management
bb backup-db --dev      # Backup development database
bb restore-db --dev <file>  # Restore from backup
bb clean-db --dev       # Clean database tables

# Project utilities
bb tasks-pretty         # Show available tasks nicely formatted
bb clean-cache          # Clear compilation caches
bb upgrade-deps         # Upgrade all dependencies
```

### Application Architecture (Template Scope)

- **Backend**: Clojure with Ring HTTP server and PostgreSQL; DI via `app.template.di.config`
- **Frontend**: ClojureScript with Shadow-CLJS and re-frame/uix
- **Admin UI**: `/admin/users` and related template pages are included; add your own admin pages under `app.admin.frontend.pages`
- **Database**: Starts from `resources/db/models.edn`; no Hosting/Financial/Integration domain tables
- **Authentication**: Simplified admin auth for single-tenant local use (nil treated as allowed in `auth-guard`)
- **Build/Tooling**: Babashka tasks, Shadow-CLJS, nREPL/Calva-friendly dev loop

### Troubleshooting

#### Common Issues

1. **Cache issues**: `bb clean-cache` to clear compilation caches
2. **Database connection**: Ensure PostgreSQL is running on ports 55432 and 55433
3. **Missing dependencies**: Run `npm install` and check Java/Clojure installation

#### Database Reset

```bash
# Completely reset development database
bb clean-restore-db --dev <backup-file.sql>

# Or clean and reinitialize
bb clean-db --dev
# Then run migrations via REPL: (mig/migrate!)
```

### Configuration

Key configuration files:
- `deps.edn` - Clojure dependencies and aliases
- `shadow-cljs.edn` - ClojureScript build configuration
- `package.json` - Node.js dependencies and scripts
- `bb.edn` - Babashka task definitions
- `resources/db/models.edn` - Database schema definitions

### Documentation

- `docs/index.md` — clojure-template documentation entry point
- `docs/general/index.md` — General overview, ops, and architecture
- `docs/template/backend/single-tenant-template.md` — What this template includes and how to extend it
- `docs/general/operations/README.md` — Dev/startup/testing commands
- `docs/general/operations/railway-deployment.md` — **Deploying to Railway** (env vars, DB, Gmail API, OAuth setup)
- `docs/general/migrations/migration-overview.md` — Models/migrations workflow for this template
- `docs/template/frontend/app-shell.md` — Frontend/app shell overview (with admin bootstrap notes)
- `docs/general/reference/hosting/` — Hosting/Financial/Integration reference docs

## License

[Add your license information here]
