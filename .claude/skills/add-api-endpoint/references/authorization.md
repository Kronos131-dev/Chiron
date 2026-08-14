# Authorizing a Chiron endpoint

There is **no `@PreAuthorize` in this codebase**. Two mechanisms exist and neither is automatic.

## 1. URL rules in `security/SecurityConfig.java`

The whole coarse layer:

```java
.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
.requestMatchers(
        "/error",
        "/api/auth/**",
        "/api/images/**",
        "/images/**",
        "/api/exercices/*/image/*",
        "/actuator/health",
        // swagger paths
).permitAll()
.requestMatchers(HttpMethod.GET, "/api/fitbit/callback").permitAll()
.requestMatchers(HttpMethod.POST, "/api/exercices/import").hasRole("ADMIN")
.anyRequest().authenticated()
```

`anyRequest().authenticated()` is the default, so a new endpoint is authenticated without any change.
Add to `permitAll` only for an OAuth callback, a public image or a health probe — never to "make it
work".

Authentication itself is a stateless JWT (jjwt) validated by `JwtAuthenticationFilter`, registered
before `UsernamePasswordAuthenticationFilter`. CSRF is off; CORS carries an explicit allow-list that
includes `capacitor://localhost` for the Android build.

## 2. Ownership, written by hand

Everything finer than "is logged in" is code. Take the caller from the injected `Authentication`:

```java
@GetMapping("/historique")
public ResponseEntity<List<SeanceDto>> getHistorique(Authentication authentication,
                                                     @RequestParam String username) {
    String caller = authentication.getName();
    // …
}
```

Many existing endpoints accept a `username` query parameter and trust it. **Do not copy that.** The
parameter names the target; the principal names the caller, and the two must be compared.

### The rule the app applies

For reading another athlete's data, in the order the existing code checks it:

1. Caller is the target → allow.
2. Caller has `Role.ADMIN` → allow.
3. Target's profile is public (`targetUser.getIsPublic()` is `TRUE`) → allow reads.
4. Caller is one of the target's coaches (`targetUser.getCoaches()` contains the caller) → allow
   reads and, for programmes, writes.
5. Otherwise → `throw new SecurityException("…")`, which `GlobalExceptionHandler` maps to 403.

Reference implementations to copy:

| Concern | Where |
|---------|-------|
| Public-profile gate, returning a message | `ai/WorkoutTools.getUserProgrammes` |
| Admin bypass | `ai/WorkoutTools`, `Role.ADMIN` comparisons |
| Coach relationship | `Utilisateur.coaches` / `coachedUsers`, used by `AgoraController` and `ProgrammeController` |

Note that `getIsPublic()` returns a nullable `Boolean` — a null means private, and
`!targetUser.getIsPublic()` would throw. Check `== null || !…` as the existing code does.

## Roles

`ROLE_USER` is every athlete. `ROLE_ADMIN` reads everything. "Coach" is not a role: it is the
`coaches` self-referencing many-to-many on `Utilisateur`, granted per athlete.

## What to verify before shipping

- An endpoint returning another user's data has an explicit check against the principal.
- A write endpoint checks ownership, not just authentication.
- Nothing new was added to `permitAll` unless it is genuinely public.
- The `@WebMvcTest` for the controller excludes `SecurityAutoConfiguration`, so it proves the mapping
  and the payload — **not** the authorization. The ownership check needs its own service-level test.
