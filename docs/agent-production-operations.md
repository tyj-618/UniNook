# Agent production operations

This checklist applies to the UniNook assistant after the multi-turn, streaming, and tool-calling upgrades.
It complements `deployment-runbook.md`; it does not replace the existing database backup and rollback steps.

## Release gate

Before a production deployment, complete all of the following:

1. Run the backend test suite and the frontend production build.
2. Back up MySQL before changing the application image or any database configuration.
3. Confirm that `.env` is outside version control and has mode `600` on the server.
4. Keep internal service ports loopback-only. Only Nginx should receive public traffic.
5. Verify `/actuator/health` from the server and the public HTTPS page from an external network.
6. For an OpenAI-compatible provider, verify that the model endpoint, key, and model name are configured; otherwise keep `CAMPUSCIRCLE_AI_PROVIDER=mock`.

## Assistant controls

- Conversation history is Redis-only and expires after `CAMPUSCIRCLE_AI_CHAT_SESSION_TTL_SECONDS` (default: 1800 seconds).
- A user can have only one active generation for the same session. This prevents overlapping requests from overwriting history.
- The Redis session lock expires after `CAMPUSCIRCLE_AI_CHAT_SESSION_LOCK_TTL_SECONDS` (default: 300 seconds). Keep it longer than the stream timeout.
- Tool calls remain server-controlled: user, campus, and scope values are taken from the authenticated request context, not from model output.
- Write tools must return a pending confirmation and must not make changes before a user confirms.

## Monitoring

Use the shared `requestId` to follow one request in application logs:

```bash
docker compose logs --since=15m app | grep 'requestId=<request-id>'
```

The assistant records Micrometer metrics for model calls, model tokens, retrieval calls, retrieval latency, and session conflicts. Keep the actuator metrics endpoint on the loopback interface or behind authenticated monitoring; do not expose it through the public Nginx site by default.

Important structured log stages are `prompt`, `agent-model`, `tool`, `llm`, `retrieval`, `response`, and `stream-response`. Logs intentionally exclude authorization headers, API keys, and raw session history.

## Search and model degradation

- If Elasticsearch is unavailable while embeddings remain available, retrieval continues with the vector branch.
- If embeddings are unavailable while Elasticsearch remains available, retrieval continues with keyword search.
- SQL fallback is used only when no search branch produces candidates.
- The health monitor probes Elasticsearch and embeddings periodically. Repeated connection failures mark the dependency unavailable; a successful probe restores it.

Investigate configuration or network issues before increasing timeouts. Do not delete valid posts or indexes as a recovery shortcut.

## Safe rollback

1. Record the current commit and take a MySQL backup.
2. Switch only to a previously verified commit or tag.
3. Rebuild and restart the application containers without removing named volumes.
4. Verify the health endpoint, login, a normal assistant request, and a streamed assistant request.
5. If a database migration is not reversible, restore according to its matching migration and backup plan instead of rolling back only the image.

## Retrieval regression baseline

`src/test/resources/ai/golden-retrieval.csv` describes the expected top-k post for 30 representative queries. The independent corpus fixture is stored in `golden-retrieval-corpus.csv`; the test logs its hit rate during `mvnw.cmd test`. Update the corpus and expected mapping deliberately when search behavior or test data changes, and review any hit-rate regression before release.
