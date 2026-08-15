#!/usr/bin/env python3
"""Drive every Chiron hook through its blocked and allowed paths."""

import json
import os
import subprocess
import sys

REPO = os.environ.get("CLAUDE_PROJECT_DIR") or os.getcwd()
MIGRATIONS = os.path.join(REPO, "chiron-back/src/main/resources/db/migration")

GUARD = [
    (2, "force push", "git push --force origin main"),
    (2, "push -f", "git push -f"),
    (2, "push +refspec", "git push origin +main"),
    (2, "reset --hard", "git reset --hard HEAD~1"),
    (2, "clean -fd", "git clean -fd"),
    (2, "restore", "git restore chiron-back/pom.xml"),
    (2, "checkout -- .", "git checkout -- ."),
    (2, "git rm", "git rm chiron-back/pom.xml"),
    (2, "branch -D", "git branch -D main"),
    (2, "compose down -v", "docker compose down -v"),
    (2, "flyway:clean", "mvn flyway:clean"),
    (2, "docker volume rm", "docker volume rm chiron_data"),
    (2, "rm -rf /", "rm -rf /"),
    (2, "scp to prod", "scp app.jar chiron:/opt/chiron/"),
    (2, "rsync to prod", "rsync -az dist/ chiron:/opt/chiron/frontend/"),
    (2, "interactive ssh", "ssh root@46.224.227.209"),
    (2, "ssh docker rm", 'ssh chiron "docker rm chiron_backend"'),
    (2, "ssh chained mutation", 'ssh chiron "docker ps && docker rm chiron_backend"'),
    (2, "ssh chained rm -rf", 'ssh chiron "cat /etc/passwd; rm -rf /opt/chiron"'),
    (2, "ssh sudo", 'ssh chiron "sudo systemctl restart docker"'),
    (2, "ssh compose up", 'ssh chiron "docker compose up -d --force-recreate backend"'),
    (2, "ssh exec bash", 'ssh chiron "docker exec -it chiron_backend bash"'),
    (2, "ssh exec rm", 'ssh chiron "docker exec chiron_backend rm -rf /app"'),
    (2, "local mutation after ssh read", 'ssh chiron "docker ps" && git reset --hard'),
    (0, "commit", 'git commit -m "feat: add tempo"'),
    (0, "plain push", "git push origin main"),
    (0, "restore --staged", "git restore --staged chiron-back/pom.xml"),
    (0, "checkout -b", "git checkout -b feat/tempo"),
    (0, "reset path", "git reset chiron-back/pom.xml"),
    (0, "fetch", "git fetch origin"),
    (0, "mvn verify", "cd chiron-back && mvn verify"),
    (0, "npm build", "cd chiron-front && npm run build"),
    (0, "compose up", "docker compose up -d db"),
    (0, "rm -rf target", "rm -rf chiron-back/target"),
    (0, "ssh docker logs", 'ssh chiron "docker logs --tail 50 chiron_backend"'),
    (0, "ssh docker ps", "ssh chiron docker ps -a"),
    (0, "ssh df", 'ssh chiron "df -h"'),
    (0, "ssh piped read", 'ssh chiron "ss -ltnp | grep 9090"'),
    (0, "ssh logs piped", 'ssh chiron "docker logs chiron_backend 2>&1 | head -80"'),
    (0, "ssh journalctl piped", 'ssh chiron "journalctl -u docker --since -1h --no-pager | tail -50"'),
    (0, "ssh exec sha256sum", 'ssh chiron "docker exec chiron_backend sha256sum /app/app.jar"'),
    (0, "ssh non-prod", "ssh git@github.com"),
    (0, "curl public health", 'curl -s https://chiron-sanctuaire.fr/actuator/health'),
]

GUARD_MCP = [
    (2, "run_workflow", "mcp__github__run_workflow"),
    (2, "rerun_workflow_run", "mcp__github__rerun_workflow_run"),
    (2, "delete_file", "mcp__github__delete_file"),
    (2, "merge_pull_request", "mcp__github__merge_pull_request"),
    (2, "push_files", "mcp__github__push_files"),
    (0, "list_workflow_runs", "mcp__github__list_workflow_runs"),
    (0, "get_job_logs", "mcp__github__get_job_logs"),
    (0, "search_code", "mcp__github__search_code"),
    (0, "create_pull_request", "mcp__github__create_pull_request"),
    (0, "other server untouched", "mcp__wiremock__create_mock_api"),
]

COMMIT = [
    (2, "bare commit", "git commit"),
    (2, "no prefix", 'git commit -m "ajoute un truc"'),
    (2, "claude trailer", 'git commit -m "feat: x" -m "Co-Authored-By: Claude"'),
    (2, "generated with", 'git commit -m "feat: x" -m "Generated with Claude Code"'),
    (2, "anthropic address", 'git commit -m "feat: x" -m "a@noreply@anthropic.com"'),
    (2, "--author", 'git commit --author="Bot <b@c.d>" -m "feat: x"'),
    (2, "-c user.name", 'git -c user.name=Bot commit -m "feat: x"'),
    (2, "subject too long", 'git commit -m "feat: this subject rambles well past the seventy-two character limit for sure"'),
    (0, "valid", 'git commit -m "fix: correct tonnage rounding"'),
    (0, "amend --no-edit", "git commit --amend --no-edit"),
    (0, "grep mentioning it", 'grep -rn "git commit" .claude/'),
    (0, "not a commit", "git status"),
]

HEREDOC_DIRTY = """git commit -F - <<'EOF'
feat: add gemini fallback

Ajoute un repli automatique sur Mistral.

Co-Authored-By: Claude <noreply@anthropic.com>
EOF"""

HEREDOC_CLEAN = """git commit -F - <<'EOF'
feat: add gemini fallback

Ajoute un repli automatique sur Mistral quand Gemini renvoie un 503.
EOF"""

NO_COMMENTS_JAVA = os.path.join(
    REPO, "chiron-back/src/main/java/com/kronos/chiron/__hook_probe__.java")

NO_COMMENTS_TS = os.path.join(REPO, "chiron-front/src/app/__hook_probe__.ts")

NO_COMMENTS_OUT_OF_SCOPE = os.path.join(
    REPO, "chiron-back/src/test/java/com/kronos/chiron/__hook_probe__.java")

NO_COMMENTS = [
    (2, "bare line comment", NO_COMMENTS_JAVA, "// remet le compteur à zéro\nint total = 0;\n"),
    (2, "trailing comment", NO_COMMENTS_JAVA, "int total = 0; // remet à zéro\n"),
    (2, "javadoc", NO_COMMENTS_JAVA, "/**\n * Additionne les séries.\n */\nint total = 0;\n"),
    (2, "second block unmarked", NO_COMMENTS_JAVA,
     "// WHY: Google renvoie 403 quand l'API est désactivée.\nint a = 0;\n// simple paraphrase\nint b = 0;\n"),
    (0, "marked rationale", NO_COMMENTS_JAVA,
     "// WHY: Google renvoie 403 quand l'API est désactivée.\nint total = 0;\n"),
    (0, "marked rationale spanning lines", NO_COMMENTS_JAVA,
     "// WHY: Boditrax sépare l'heure de AM/PM par une espace fine insécable\n"
     "// (U+202F) que java \\s ne couvre pas.\nint total = 0;\n"),
    (0, "pragma", NO_COMMENTS_JAVA, "int total = 0; // NOSONAR\n"),
    (0, "no comment at all", NO_COMMENTS_JAVA, "int total = 0;\n"),
    (0, "url in a string", NO_COMMENTS_JAVA, 'String u = "https://chiron-sanctuaire.fr";\n'),
    (2, "frontend comment", NO_COMMENTS_TS, "// recharge la liste\nconst x = 1;\n"),
    (0, "frontend marked", NO_COMMENTS_TS, "// WHY: Safari ignore le focus programmatique.\nconst x = 1;\n"),
    (0, "out of scope", NO_COMMENTS_OUT_OF_SCOPE, "// Given un athlète\nint total = 0;\n"),
]

MIGRATION = [
    (2, "committed migration", os.path.join(MIGRATIONS, "V0__baseline.sql")),
    (2, "bad name", os.path.join(MIGRATIONS, "44-add-tempo.sql")),
    (0, "new migration", os.path.join(MIGRATIONS, "V44__add_serie_tempo.sql")),
    (0, "not a migration", os.path.join(REPO, "chiron-back/pom.xml")),
]


def drive(hook, payload):
    result = subprocess.run(
        [os.path.join(REPO, ".claude/hooks", hook)],
        input=json.dumps(payload),
        capture_output=True,
        text=True,
    )
    return result.returncode


def drive_no_comments(path, content):
    """The hook reads the file through `git diff HEAD`, so the probe must exist on disk."""
    with open(path, "w", encoding="utf-8") as handle:
        handle.write(content)
    try:
        return drive("check-no-comments.py", {"tool_name": "Write", "tool_input": {"file_path": path}})
    finally:
        os.remove(path)


def report(title, rows):
    print(f"\n=== {title} ===")
    failures = 0
    for expected, label, actual in rows:
        ok = actual == expected
        failures += not ok
        print(f"  {'OK  ' if ok else 'FAIL'} exit={actual} (want {expected})  {label}")
    return failures


def main():
    failures = 0

    failures += report("guard-commands.py — Bash", [
        (e, l, drive("guard-commands.py", {"tool_name": "Bash", "tool_input": {"command": c}}))
        for e, l, c in GUARD
    ])

    failures += report("guard-commands.py — MCP", [
        (e, l, drive("guard-commands.py", {"tool_name": t, "tool_input": {}}))
        for e, l, t in GUARD_MCP
    ])

    failures += report("check-commit-message.py", [
        (e, l, drive("check-commit-message.py", {"tool_name": "Bash", "tool_input": {"command": c}}))
        for e, l, c in COMMIT
    ] + [
        (2, "heredoc with trailer",
         drive("check-commit-message.py", {"tool_name": "Bash", "tool_input": {"command": HEREDOC_DIRTY}})),
        (0, "heredoc clean",
         drive("check-commit-message.py", {"tool_name": "Bash", "tool_input": {"command": HEREDOC_CLEAN}})),
    ])

    failures += report("check-no-comments.py", [
        (e, l, drive_no_comments(p, c)) for e, l, p, c in NO_COMMENTS
    ])

    failures += report("check-migration-immutable.py", [
        (e, l, drive("check-migration-immutable.py", {"tool_name": "Edit", "tool_input": {"file_path": p}}))
        for e, l, p in MIGRATION
    ])

    print(f"\n{'ALL PASS' if failures == 0 else f'{failures} FAILURE(S)'}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
