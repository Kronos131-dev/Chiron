# Backend startup failures

Read the first `Caused by` in the log, not the last stack frame. Matching symptoms:

| Log shows | Cause | Resolution |
|-----------|-------|------------|
| `JWT_SECRET` rejected, or `JwtService` throws on startup | secret absent, blank, non-base64 or under 32 bytes | `chiron-back/.env`, see `manage-env-and-secrets` |
| `Port 9090 was already in use` | a previous run is still alive | stop that PID, or report it if it is not `chiron-back` |
| `Connection to 127.0.0.1:5454 refused` | `chiron_postgres` is down | `docker compose up -d db` from the repository root |
| `Schema-validation: missing column` / `wrong column type` | an entity gained a field with no migration | `add-flyway-migration` |
| `Migration checksum mismatch` | an applied migration was edited | restore the file from git; never edit an applied migration |
| `release version 25 not supported` | wrong `JAVA_HOME` | prefix the command with the JDK 25 path |
| `OPENROUTER_API_KEY` unresolved | key missing from `.env` | `manage-env-and-secrets` |
| `BUILD FAILURE` before Spring starts | compile error | run the `verify-backend-change` sequence |
