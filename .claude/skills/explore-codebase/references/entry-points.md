# Entry points by question

## The four traversals

### "What happens when the API is called at X?"

```
security/SecurityConfig.java   is the path public, or authenticated?
controller/XController.java    the mapping, the DTO, the Authentication parameter
service/XService.java          the rules, the transaction, the ownership check
repository/XRepository.java    the query
entity/X.java                  the fields
```

### "Why does this screen show that?"

```
app.routes.ts                          which component the path renders
components/<feature>/<feature>.html    the template and its | t keys
components/<feature>/<feature>.ts       the signals and the computed values
service/chiron-api.ts                  the HTTP call — every call goes through here
```
then continue into the backend traversal above.

### "Why did the coach answer that?"

```
ai/ChironAgent.java              the @SystemMessage — find the block, find [toolName]
ai/<X>Tools.java                 the method with that name
                                 its @Tool description is what the model read
ai/ChironAgentRouter.java        which provider ran it, retries, fallback
ai/ConversationMemoryManager.java what history it had
controller/ChatController.java   what context was prepended to the message
```

### "Where does this stored value come from?"

```
entity/X.java                      the field and its Lombok annotations
db/migration/V*__*.sql             grep the column name for the migration that created it
dto/XDto.java                      whether it is exposed
service/chiron-api.ts              whether the frontend receives it
```

## Where things live

| Looking for | Start at |
|-------------|----------|
| An endpoint's URL | `controller/` — every class carries `@RequestMapping("/api/…")` |
| Whether a path is public | `security/SecurityConfig.java` |
| The JWT handling | `security/JwtService.java`, `security/JwtAuthenticationFilter.java` |
| What the coach can do | `ai/ChironAgent.java`, then the `ai/*Tools` components |
| The coach's personality and rules | the `@SystemMessage` in `ai/ChironAgent.java` |
| The provider choice and quota | `ai/ChironAgentRouter.java`, `service/AiUsageService.java` |
| A statistics computation | `stats/` — controller, service and DTOs together |
| Fitbit, Olympus, Visbody, Boditrax | their own vertical packages |
| A column's origin | `grep -rn '<column>' chiron-back/src/main/resources/db/migration/` |
| Every backend call the frontend makes | `chiron-front/src/app/service/chiron-api.ts` |
| A user-visible string | `chiron-front/src/app/i18n/fr.ts` |
| A route and its guard | `chiron-front/src/app/app.routes.ts` |
| Colour tokens | the `@theme` block in `chiron-front/src/styles.css` |
| What the deploy does | `.github/workflows/deploy.yml` |
| What runs on the server | `docker-compose.yml`, `nginx.conf` |

## Useful scoped searches

```bash
grep -rn "getUserProgrammes" chiron-back/src/main/java
grep -rn "@Tool(" chiron-back/src/main/java/com/kronos/chiron/ai
grep -rn "tempo" chiron-back/src/main/resources/db/migration
grep -rn "journal\." chiron-front/src/app/i18n/fr.ts
grep -rn "chironApi\." chiron-front/src/app/components
```

## Never search

`chiron-back/target/` · `chiron-front/node_modules/` · `chiron-front/dist/` ·
`chiron-front/android/` · `.idea/` · `.git/`

`chiron-front/android/` is the generated Capacitor wrapper and contains a full copy of the web build.

## Scale, so expectations are calibrated

151 Java files in `chiron-back/src/main/java`, 32 test classes, 41 Flyway migrations. 55 TypeScript
files in `chiron-front/src/app`, 11 specs, 567 i18n keys. `ai/WorkoutTools.java` is the largest file
at about 1050 lines and holds 41 of the 84 tools; `service/chiron-api.ts` is about 730 lines and is
the whole frontend API surface. Both are worth reading in sections rather than whole.
