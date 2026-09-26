<!-- ai: {:tags [:operations :deployment :railway] :kind :runbook} -->

# Deploying to Railway

This guide walks through deploying the app to [Railway](https://railway.app) using the included `Dockerfile`.

Railway detects the `Dockerfile` automatically and handles container builds, HTTPS, and domain assignment without extra configuration.

## Prerequisites

- A Railway account and the [Railway CLI](https://docs.railway.app/develop/cli) (optional — the dashboard works too)
- Your app's GitHub repository connected to Railway
- A Google Cloud project for OAuth login and Gmail email sending (see [Gmail API setup](#gmail-api-setup-for-email))

---

## Step 1: Create a Railway Project

1. Go to [railway.app/dashboard](https://railway.app/dashboard) and click **New Project**.
2. Choose **Deploy from GitHub repo** and select your repository.
3. Railway will detect the `Dockerfile` and start the first build. It will fail until you add environment variables — that is expected.

---

## Step 2: Add a PostgreSQL Database

1. In your Railway project, click **+ New Service → Database → PostgreSQL**.
2. Railway will provision a Postgres instance and automatically inject `DATABASE_URL` into all services in the project.

No manual DB connection string is needed — `config/base.edn` reads `DATABASE_URL` in the `:prod` profile.

---

## Step 3: Set Environment Variables

In your Railway service → **Variables** tab, add the following.

### Required

| Variable | Description |
|---|---|
| `DATABASE_URL` | Auto-injected by Railway when Postgres is linked. Do not set manually. |
| `PORT` | Auto-injected by Railway. Do not set manually. |
| `BASE_URL` | Your public app URL, e.g. `https://your-app.railway.app`. No trailing slash. Used in email links and OAuth callbacks. |
| `GOOGLE_OAUTH_CLIENT_ID` | Google OAuth client ID (also used for Gmail API email sending). |
| `GOOGLE_OAUTH_CLIENT_SECRET` | Google OAuth client secret (also used for Gmail API email sending). |
| `GMAIL_REFRESH_TOKEN` | OAuth2 refresh token for sending email via Gmail REST API. See [Gmail API setup](#gmail-api-setup-for-email). |
| `SMTP_FROM_EMAIL` | The Gmail address to send from, e.g. `noreply@yourdomain.com`. |
| `GOOGLE_OAUTH_REDIRECT_URI` | Google OAuth callback URL: `https://your-app.railway.app/oauth/google/callback` |

> **Why Gmail REST API for email?** Railway blocks outbound SMTP ports 465 and 587. The app uses the Gmail REST API over HTTPS (port 443) in production to work around this. The same Google OAuth credentials used for login are reused for email sending.

### Optional — GitHub OAuth

| Variable | Description |
|---|---|
| `GITHUB_OAUTH_CLIENT_ID` | GitHub OAuth App client ID. |
| `GITHUB_OAUTH_CLIENT_SECRET` | GitHub OAuth App client secret. |
| `GITHUB_OAUTH_REDIRECT_URI` | GitHub OAuth callback URL: `https://your-app.railway.app/oauth2/github/callback` |

### Optional — Stripe

| Variable | Description |
|---|---|
| `STRIPE_LIVE_API_KEY` | Stripe live secret key (`sk_live_...`). |
| `STRIPE_LIVE_WEBHOOK_SECRET` | Stripe live webhook endpoint secret (`whsec_...`). |

### Optional — Postmark (alternative email provider)

| Variable | Description |
|---|---|
| `POSTMARK_API_KEY` | Postmark server API token. |
| `POSTMARK_FROM_EMAIL` | Verified Postmark sender address. |

---

## Step 4: Database Migrations (automatic)

Migrations run **automatically on every deploy** via Railway's `preDeployCommand` (configured in `railway.json`):

```
java -cp /app/app.jar clojure.main -m app.migrate
```

The standalone migration runner (`src/app/migrate.clj`) reads `DATABASE_URL`, applies all pending migrations through the non-deprecated automigrate execution APIs, and exits non-zero if anything fails. Railway aborts the deploy and keeps the previous revision live.

No manual intervention is needed. Committed migration files under `resources/db/migrations/` are applied automatically on the next deploy.

**Manual migrations (escape hatch):** If you need to run migrations outside a deploy (e.g. rollback, inspect status), use the Railway CLI:

```bash
railway run clj -M:nrepl
# Then in the REPL:
# (require 'app.template.backend.migrations.simple-repl :reload)
# (app.template.backend.migrations.simple-repl/migrate!)   ; or (migrate-to! :prod 42)
# (app.template.backend.migrations.simple-repl/status :prod)
```

---

## Step 5: Configure OAuth Redirect URIs

In [Google Cloud Console](https://console.cloud.google.com/apis/credentials):

1. Open your OAuth 2.0 Client ID.
2. Under **Authorized redirect URIs**, add:
   - `https://your-app.railway.app/oauth/google/callback`
3. Under **Authorized JavaScript origins**, add:
   - `https://your-app.railway.app`

For GitHub OAuth: in your [GitHub OAuth App settings](https://github.com/settings/developers), set the **Authorization callback URL** to `https://your-app.railway.app/oauth2/github/callback`.

---

## How the Dockerfile Works

The build uses two stages to keep the runtime image small:

1. **Builder** (`eclipse-temurin:21-jdk-jammy` + Node 22): installs all tooling, runs `npm ci`, downloads Clojure deps, compiles ClojureScript with shadow-cljs, and produces an AOT-compiled uberjar via `clj -T:build uber`.
2. **Runtime** (`eclipse-temurin:21-jre-jammy`): copies only the uberjar — no build tools, no source code.

Railway injects `PORT` at runtime; the app reads it from the environment and binds there. The healthcheck polls `GET /health` every 30 seconds with a 30-second startup grace period (sufficient for JVM cold start).

---

## Gmail API Setup for Email

Railway blocks outbound SMTP. The app sends email in production via the Gmail REST API using an OAuth2 refresh token.

### One-time setup

1. In [Google Cloud Console](https://console.cloud.google.com/apis/dashboard), enable the **Gmail API** for your project.
2. Your existing OAuth 2.0 client (the one used for Google login) already has the right credentials. Add `https://mail.google.com/` to its **Authorized scopes** if not already present.
3. Use the [OAuth 2.0 Playground](https://developers.google.com/oauthplayground/) to generate a refresh token:
   - Click the gear icon → check **Use your own OAuth credentials** → enter your `GOOGLE_OAUTH_CLIENT_ID` and `GOOGLE_OAUTH_CLIENT_SECRET`.
   - In step 1, find **Gmail API v1** → select `https://mail.google.com/` → **Authorize APIs**.
   - Complete the consent flow, then in step 2 click **Exchange authorization code for tokens**.
   - Copy the **Refresh token** value.
4. Set `GMAIL_REFRESH_TOKEN` in Railway to this value.
5. Set `SMTP_FROM_EMAIL` to the Gmail address you authorized.

> The refresh token does not expire as long as the app uses it at least once every 6 months and the Google account remains in good standing.

---

## Production Debugging

Use the [Railway CLI](https://docs.railway.app/develop/cli) to inspect and debug the running production service without opening the dashboard.

### Prerequisites

```bash
# Install (once)
npm install -g @railway/cli

# Authenticate and link to this project
railway login
railway link   # prompts to select project → environment → service
```

### Stream Production Logs

```bash
# Tail live logs (Ctrl-C to stop)
railway logs

# Last N lines only
railway logs --tail 200
```

All application output (Timbre logs, Ring request logs, exception traces) appears here.

### Inspect Environment Variables

```bash
railway variables
```

Verify required variables (`DATABASE_URL`, `BASE_URL`, `GOOGLE_OAUTH_CLIENT_ID`, etc.) before debugging auth or email issues.

### Run Commands with Production Environment

`railway run <cmd>` spawns a **local** process with every production variable injected — including `DATABASE_URL`. The runtime Docker image contains only the JRE and the uberjar (no Clojure tooling), so run these from your local machine (which has the full dev toolchain):

```bash
# Interactive nREPL connected to the production database
railway run clj -M:nrepl
```

> **⚠ Danger**: This nREPL session connects to the **live production database**. Any eval that writes data affects production. Prefer read-only queries; wrap mutations in explicit transactions you can roll back.

From the production nREPL you can:

- **Query the live DB** with HoneySQL / next.jdbc directly
- **Inspect config**: `(require 'app.template.backend.core :reload)` and read config maps
- **Manual migration** (escape hatch — normally automatic via `preDeployCommand`): `(require 'app.template.backend.migrations.simple-repl :reload)` → `(app.template.backend.migrations.simple-repl/status :prod)` / `(migrate! :prod)`

```bash
# Run a Babashka task against the production environment
railway run bb seed-geo-reference prod
```

### First Admin Account

Fresh databases ship **without** an admin row: the old default-admin seed
migration is a no-op since the email-privacy cutover (migration 0080 enforces
`NOT NULL` on encrypted email columns that a SQL seed cannot compute). Create
the first owner admin through the application's own write path, which stores
the email encrypted at rest:

```bash
railway run bb seed-admin prod
```

- Requires `ADMIN_PASSWORD` to be set (the script refuses to seed the default
  dev password into production) and asks for typed confirmation (`SEED ADMIN
  PROD`); pass `--yes` to skip the prompt in automation.
- Optional `ADMIN_EMAIL` overrides the default `admin@example.com`.
- The seed is an upsert: re-running resets that admin's password. Change the
  password after first login if it was shared over an insecure channel.

### Frontend Config Tasks Against Railway

Use the Railway-aware Babashka tasks when you need to inspect or promote live frontend config from production:

```bash
# Promote Railway prod runtime config back into source-controlled EDN defaults (dry-run)
bb railway-export-frontend-config-from-db

# Same export, scoped to one domain
bb railway-export-frontend-config-from-db --only expenses

# Run the full migration-adjacent frontend-config workflow against Railway prod
bb railway-migrate-and-sync-frontend-config --only expenses
```

`bb railway-frontend-config` is the generic entrypoint behind these helpers. It injects the Railway production DB URL through the public TCP proxy for DB-backed settings tasks, and forwards non-DB validation/sync tasks through the same workflow naming.

### Shell into the Running Container

```bash
railway shell
```

Opens a bash shell inside the live container. Since the runtime image (`eclipse-temurin:21-jre-jammy`) ships only the JRE and the uberjar — no Clojure CLI, no `bb`, no `npm` — use `railway shell` mainly to inspect the filesystem or verify the jar is present. For data or code work, prefer `railway run` above.

### Railway MCP Server (AI-native debugging)

Railway ships an official MCP server that exposes Railway operations as structured tools callable directly from Claude Code and other MCP-compatible AI assistants — no manual CLI commands needed.

**Install into Claude Code (once):**

```bash
claude mcp add Railway npx @railway/mcp-server
```

**Prerequisites:** Railway CLI installed and `railway login` completed.

**Available tools:**

| Tool | What it does |
|---|---|
| `check-railway-status` | Verify CLI auth |
| `list-projects` / `list-services` | Inspect project and service state |
| `get-logs` | Retrieve build/service logs (supports line limits and filtering) |
| `list-variables` / `set-variables` | Read and write environment variables |
| `deploy` / `deploy-template` | Deploy a service or Railway template |
| `create-environment` / `link-environment` | Manage environments |
| `generate-domain` | Generate a `.railway.app` domain |

Destructive operations (deletes, drops) are intentionally excluded from the MCP surface. Full docs: [docs.railway.com/ai/mcp-server](https://docs.railway.com/ai/mcp-server).

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Build fails at `npm ci` | `package-lock.json` missing | Commit `package-lock.json` and push |
| App starts but DB errors on boot | Migration failed or `DATABASE_URL` missing | Check deploy logs for `preDeployCommand` output; see [Step 4](#step-4-database-migrations-automatic) |
| OAuth login fails with redirect mismatch | Redirect URI not added to Google Console | Follow [Step 5](#step-5-configure-oauth-redirect-uris) |
| Email not sent, no error | `GMAIL_REFRESH_TOKEN` or `SMTP_FROM_EMAIL` missing | Add both Railway variables |
| Health check fails, container restarts | JVM startup > 30s or missing env vars | Check deploy logs; increase `--start-period` in Dockerfile if needed |
