# Aegis Backend

Enterprise AI agent governance platform. AI agents propose actions; Aegis
decides whether they are allowed, runs the approved ones, and audits everything.

## Required versions (do not change alone)
- Java 21
- Spring Boot 4.1.2
- Spring AI 2.0.1 (Gemini)
- Use the Maven wrapper: `mvnw.cmd` (Windows) or `./mvnw` (Mac/Linux)

## Build and test
    mvnw.cmd clean verify

Tests set their own JWT secret (see `pom.xml`), so no setup is needed to run them.

## Run the app locally
The app will NOT start without a JWT secret. Set these in the same terminal
window, then start it (Windows `cmd`):

    set AEGIS_JWT_SECRET=any-long-secret-at-least-32-characters-long
    set AEGIS_ADMIN_USERNAME=admin
    set AEGIS_ADMIN_PASSWORD=choose-a-password-8-chars-or-more
    mvnw.cmd spring-boot:run

- `AEGIS_JWT_SECRET`: signs login tokens. At least 32 characters. Never commit a real one.
- `AEGIS_ADMIN_USERNAME` / `AEGIS_ADMIN_PASSWORD`: optional. They create an ADMIN
  user at startup (password at least 8 characters). Users are kept in memory,
  so they disappear when the app stops.
- The default setup uses a mock LLM. No Gemini key is needed.
  To use the real Gemini later, set `GEMINI_API_KEY` and activate the `live`
  profile. Never commit a key.

## Try it (the app listens on port 8080)
1. Log in and copy the `token` from the answer:

       curl -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d "{\"username\":\"admin\",\"password\":\"your-password\"}"

2. Send a request through the security pipeline:

       curl -X POST http://localhost:8080/api/requests -H "Authorization: Bearer PASTE_TOKEN_HERE" -H "Content-Type: application/json" -d "{\"message\":\"show IT employees\"}"

3. Read the audit log (needs a REVIEWER or ADMIN token):

       curl http://localhost:8080/api/audit/logs -H "Authorization: Bearer PASTE_TOKEN_HERE"

Status codes from `/api/requests`: 200 executed, 202 waiting for review,
403 blocked, 500 failed, 400 bad input, 401 no or bad token.

## Roles
- `POST /api/auth/register` always creates a normal USER.
- An ADMIN can promote users: `PUT /api/admin/users/{username}/role` with `{"role":"REVIEWER"}`.
- `/api/audit/**` and `/api/review/**` are for REVIEWER and ADMIN only.

## The one rule that matters
The LLM proposes. Java decides and executes. Every request goes through
`SecurityPipeline`. Agents have no execute method. An architecture test
(`PipelineGuardTest`) fails the build if any code outside the pipeline calls
`ToolExecutor.execute()`.

## Branches
| Member | Branch |
|---|---|
| BB | feature/auth-database |
| Shreyoshi | feature/agent-orchestration |
| Amar | feature/policy-email |
| Aashish | feature/pipeline-audit-redteam |

Short follow-up fixes can use a short-lived branch (for example `fix/readme`).
After a squash merge, start the next change from a fresh `main`.

## Team rules
1. Never push to `main`. Use pull requests (1 review + CI green).
2. Merge to `main` at least twice a week. Keep `main` runnable.
3. `git merge main` into your branch before each work session.
4. Edit only your own package. Changes to `common/` need everyone's agreement.
5. No secrets in Git. This repo is public.
6. Replace the remaining stubs (`StubPolicyService`, `StubAgentRouter`) with real
   code, and delete the stub in the same pull request.
