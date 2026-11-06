# Aegis Backend

Enterprise AI agent governance platform. AI agents propose actions; Aegis
decides whether they are allowed, runs the approved ones, and audits everything.

## Required versions (do not change alone)
- Java 21
- Spring Boot 4.1.2
- Spring AI 2.0.1 (Gemini)
- Use the Maven wrapper: `mvnw.cmd` (Windows) or `./mvnw` (Mac/Linux)

## Run and test
    mvnw.cmd clean verify
    mvnw.cmd spring-boot:run

The default setup uses a mock LLM. No API key is needed.
To use the real Gemini later, set `GEMINI_API_KEY` and activate the `live`
profile. Never commit a key.

## The one rule that matters
The LLM proposes. Java decides and executes. Every request goes through
`SecurityPipeline`. Agents have no execute method.

## Branches
| Member | Branch |
|---|---|
| BB | feature/auth-database |
| Shreyoshi | feature/agent-orchestration |
| Amar | feature/policy-email |
| Aashish | feature/pipeline-audit-redteam |

## Team rules
1. Never push to `main`. Use pull requests (1 review + CI green).
2. Merge to `main` at least twice a week. Keep `main` runnable.
3. `git merge main` into your branch before each work session.
4. Edit only your own package. Changes to `common/` need everyone's agreement.
5. No secrets in Git.
6. Replace stubs (`StubPolicyService`, `StubAgentRouter`, `TemporarySecurityConfig`) with real code, then delete the stub.
