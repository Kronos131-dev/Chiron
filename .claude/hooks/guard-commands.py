#!/usr/bin/env python3

"""PreToolUse guard for Chiron.

Refuses commands that rewrite history, discard uncommitted work, destroy data, mutate the
production server, or trigger a production deploy. Everything else passes.

Reads a Claude Code hook payload on stdin, exits 2 with a message on stderr to block.
"""

import json
import re
import shlex
import sys

SHELL_SEPARATORS = r"&&|\|\||;|\||\n"

HAND_BACK = "State what would need to happen and let the user run it."

PRODUCTION_HOSTS = ("46.224.227.209", "chiron-sanctuaire", "chiron")

GIT_PATH_OPTIONS_TAKING_A_VALUE = ("-C", "--git-dir", "--work-tree", "-c")

GIT_SUBCOMMANDS_THAT_DESTROY = {
    "filter-branch": "rewrites every commit in the repository",
    "filter-repo": "rewrites every commit in the repository",
    "fast-import": "writes objects directly into the object database",
    "update-ref": "moves a ref without a recorded operation",
}

FORCE_PUSH_FLAGS = ("--force", "-f", "--mirror")

MCP_LEADING_VERBS_THAT_READ = {
    "get", "list", "search", "read", "fetch", "view", "show", "find", "query",
    "lookup", "describe", "download", "export", "count", "history", "status",
    "log", "logs", "diff", "browse", "info", "summary", "check", "inspect",
}

MCP_WHOLE_NAMES_THAT_READ = {"who_am_i", "whoami", "me", "ping", "health",
                             "actions_list", "actions_get"}

MCP_WRITES_EXPLICITLY_ALLOWED = {"create_pull_request", "create_branch"}

REMOTE_READ_COMMANDS = {
    "uptime", "df", "free", "ls", "cat", "tail", "head", "grep", "curl", "wget",
    "sha256sum", "md5sum", "stat", "whoami", "hostname", "date", "ps", "top",
    "journalctl", "netstat", "ss", "env", "printenv", "wc", "find", "du", "id",
    "uname", "lsb_release", "nproc", "getent", "nslookup", "dig", "which",
}

REMOTE_READ_SUBCOMMANDS = {
    "docker": {"ps", "logs", "stats", "inspect", "images", "version", "info", "top", "port", "diff"},
    "docker-compose": {"ps", "logs", "config", "top", "images", "version"},
    "systemctl": {"status", "is-active", "is-enabled", "list-units", "show", "cat"},
    "git": {"status", "log", "show", "diff", "branch", "rev-parse"},
    "nginx": {"-t", "-T", "-v", "-V"},
}


def split_words_preserving_camel_case(name):
    underscored = re.sub(r"(?<=[a-z0-9])(?=[A-Z])", "_", name)
    return [word for word in re.split(r"[^A-Za-z0-9]+", underscored) if word]


def tokenize(single_command):
    try:
        return shlex.split(single_command)
    except ValueError:
        return single_command.split()


def git_subcommand_position(tokens_after_git):
    expecting_option_value = False
    for index, token in enumerate(tokens_after_git):
        if expecting_option_value:
            expecting_option_value = False
            continue
        if token in GIT_PATH_OPTIONS_TAKING_A_VALUE:
            expecting_option_value = True
            continue
        if token.startswith("-"):
            continue
        return index
    return None


def refuse_git(tokens):
    position = next(
        (i for i, token in enumerate(tokens) if token == "git" or token.endswith("/git")),
        None,
    )
    if position is None:
        return None

    after_git = tokens[position + 1:]
    subcommand_index = git_subcommand_position(after_git)
    if subcommand_index is None:
        return None

    subcommand = after_git[subcommand_index]
    arguments = after_git[subcommand_index + 1:]

    if subcommand in GIT_SUBCOMMANDS_THAT_DESTROY:
        return (
            f"Blocked: `git {subcommand}` {GIT_SUBCOMMANDS_THAT_DESTROY[subcommand]}.\n"
            f"{HAND_BACK}"
        )

    if subcommand == "push":
        if any(argument in FORCE_PUSH_FLAGS for argument in arguments):
            return (
                "Blocked: a force push overwrites remote history that the deploy pipeline "
                "has already built from.\n"
                "Push a new commit instead, or let the user force-push knowingly."
            )
        if any(argument.startswith("+") for argument in arguments):
            return (
                "Blocked: a `+refspec` is a force push in disguise.\n"
                "Push a new commit instead, or let the user force-push knowingly."
            )
        if "--delete" in arguments or "-d" in arguments:
            return f"Blocked: `git push --delete` removes a remote branch.\n{HAND_BACK}"

    if subcommand == "reset" and any(a in ("--hard", "--merge") for a in arguments):
        return (
            "Blocked: `git reset --hard` throws away uncommitted work irreversibly.\n"
            "Report the files involved and let the user decide."
        )

    if subcommand == "clean" and any(a.startswith("-") and "f" in a for a in arguments):
        return (
            "Blocked: `git clean -f` deletes untracked files, including a local .env.\n"
            "Run `git clean -n` to list them and let the user delete."
        )

    if subcommand == "restore" and not any(a in ("--staged", "--stage") for a in arguments):
        return (
            "Blocked: `git restore` overwrites files with the committed version.\n"
            "Report the files involved and let the user decide."
        )

    if subcommand in ("checkout", "switch"):
        creates_a_branch = any(a in ("-b", "-B", "-c", "-C") for a in arguments)
        discards_working_tree = "--" in arguments or any(a in (".", "*") for a in arguments)
        if discards_working_tree and not creates_a_branch:
            return (
                "Blocked: this form of checkout discards uncommitted work.\n"
                "Report the files involved and let the user decide."
            )

    if subcommand == "rm":
        return (
            "Blocked: `git rm` deletes files from the working tree and the index.\n"
            "Delete the file with the Edit tooling, or let the user run it."
        )

    if subcommand == "branch" and "-D" in arguments:
        return f"Blocked: `git branch -D` drops unmerged commits.\n{HAND_BACK}"

    return None


def ssh_target_and_remote_command(tokens):
    index = 1
    options_taking_a_value = ("-i", "-p", "-o", "-l", "-F", "-b", "-c", "-E", "-J", "-L", "-R", "-D", "-W")
    while index < len(tokens):
        token = tokens[index]
        if token in options_taking_a_value:
            index += 2
            continue
        if token.startswith("-"):
            index += 1
            continue
        return token, tokens[index + 1:]
    return None, []


def is_production_target(token):
    if token is None:
        return False
    host = token.split("@")[-1].split(":")[0]
    return host in PRODUCTION_HOSTS


def refuse_remote_command(remote_tokens):
    if len(remote_tokens) == 1:
        remote_tokens = tokenize(remote_tokens[0])

    # A quoted remote command containing a shell operator was split upstream, leaving an unbalanced
    # quote glued to the first token. Strip it so the verb is still recognised — the remaining
    # segments are analysed on their own pass.
    remote_tokens = [token.strip("\"'") for token in remote_tokens]

    if not remote_tokens:
        return (
            "Blocked: an interactive ssh session cannot be driven from here and would hang.\n"
            "Pass the command explicitly, for example "
            '`ssh chiron "docker logs --tail 100 chiron_backend"`.'
        )

    leading = remote_tokens[0].rsplit("/", 1)[-1]

    if leading in ("sudo", "env", "sh", "bash"):
        return (
            f"Blocked: `{leading}` on the production server hides what actually runs.\n"
            "Name the read command directly, for example `docker ps -a`."
        )

    if leading == "docker" and "exec" in remote_tokens[:2]:
        after_exec = remote_tokens[remote_tokens.index("exec") + 1:]
        inner = [t for t in after_exec if not t.startswith("-")]
        if len(inner) >= 2 and inner[1].rsplit("/", 1)[-1] in REMOTE_READ_COMMANDS:
            return None
        return (
            "Blocked: `docker exec` may only run a command that is itself on the production read "
            "allowlist.\n"
            f"This agent reads production, it never mutates it. {HAND_BACK}"
        )

    if leading in REMOTE_READ_SUBCOMMANDS:
        subcommand = next((t for t in remote_tokens[1:] if not t.startswith("-")), None)
        if leading == "nginx":
            subcommand = remote_tokens[1] if len(remote_tokens) > 1 else None
        if subcommand in REMOTE_READ_SUBCOMMANDS[leading]:
            return None
        shown = f"{leading} {subcommand}" if subcommand else leading
        return (
            f"Blocked: `{shown}` is not on the production read allowlist.\n"
            f"This agent reads production, it never mutates it. {HAND_BACK}"
        )

    if leading in REMOTE_READ_COMMANDS:
        return None

    return (
        f"Blocked: `{leading}` is not on the production read allowlist, so it is refused by "
        "default.\n"
        f"This agent reads production, it never mutates it. {HAND_BACK}\n"
        "If the command really only reads, add it to REMOTE_READ_COMMANDS in "
        ".claude/hooks/guard-commands.py."
    )


def refuse_production_access(whole_command):
    """Inspect production access on the *whole* command, before any shell splitting.

    The remote command is quoted, so splitting on shell operators first would tear it into
    fragments and let `ssh host "docker ps && docker rm x"` through on the harmless half.
    """
    tokens = tokenize(whole_command)

    for index, token in enumerate(tokens):
        leading = token.rsplit("/", 1)[-1]

        if leading in ("scp", "rsync", "sftp"):
            if any(is_production_target(t) for t in tokens[index + 1:]):
                return (
                    f"Blocked: `{leading}` moves files onto or off the production server.\n"
                    "Deploying is the pipeline's job — push to main and let "
                    ".github/workflows/deploy.yml do it. To read a file, use "
                    '`ssh chiron "cat <path>"`.'
                )

        if leading == "ssh":
            target, remote_tokens = ssh_target_and_remote_command(tokens[index:])
            if not is_production_target(target):
                continue
            if not remote_tokens:
                return refuse_remote_command([])
            for sub_command in re.split(SHELL_SEPARATORS, " ".join(remote_tokens)):
                refusal = refuse_remote_command(tokenize(sub_command.strip()))
                if refusal:
                    return refusal

    return None


def refuse_data_loss(tokens, single_command):
    joined = " ".join(tokens)

    if "flyway:clean" in single_command:
        return (
            "Blocked: `flyway:clean` drops every table in the schema.\n"
            "To rebuild the local database, run `docker compose down -v && docker compose up -d db` "
            "yourself."
        )

    if re.search(r"\bdocker(-|\s+)compose\b.*\bdown\b.*(-v|--volumes)", joined):
        return (
            "Blocked: `docker compose down -v` deletes the local PostgreSQL volume and every "
            "row in it.\n"
            f"{HAND_BACK}"
        )

    if re.search(r"\bdocker\s+volume\s+rm\b", joined) or re.search(r"\bdocker\s+system\s+prune\b", joined):
        return f"Blocked: this deletes Docker volumes, including the local database.\n{HAND_BACK}"

    if re.search(r"\bpsql\b", joined) and re.search(r"\b(DROP|TRUNCATE)\s+(TABLE|SCHEMA|DATABASE)\b", single_command, re.I):
        return (
            "Blocked: this drops or truncates a database object.\n"
            "Schema changes go through a Flyway migration — apply the add-flyway-migration skill."
        )

    if tokens[0] == "rm" and any(a.startswith("-") and "r" in a and "f" in a for a in tokens):
        targets = [t for t in tokens[1:] if not t.startswith("-")]
        if any(t in ("/", "~", "$HOME", "*", ".", "..") or t.rstrip("/") in ("/home", "/opt") for t in targets):
            return f"Blocked: `rm -rf {' '.join(targets)}` is unrecoverable.\n{HAND_BACK}"

    return None


def refuse_bash(command):
    refusal = refuse_production_access(command)
    if refusal:
        return refusal

    for single_command in re.split(SHELL_SEPARATORS, command):
        single_command = single_command.strip()
        if not single_command:
            continue

        tokens = tokenize(single_command)
        if not tokens:
            continue

        for refusal in (refuse_data_loss(tokens, single_command), refuse_git(tokens)):
            if refusal:
                return refusal

    return None


def refuse_mcp(tool_name):
    if not tool_name.lower().startswith("mcp__"):
        return None

    name_parts = tool_name.split("__")
    server = (name_parts[1] if len(name_parts) > 1 else "").lower()
    if "github" not in server:
        return None

    action = "__".join(name_parts[2:]) if len(name_parts) > 2 else ""
    if action.lower() in MCP_WHOLE_NAMES_THAT_READ:
        return None

    words = [w.lower() for w in split_words_preserving_camel_case(action)]
    if not words:
        return None

    normalised = "_".join(words)
    if normalised in MCP_WRITES_EXPLICITLY_ALLOWED:
        return None

    if words[0] in MCP_LEADING_VERBS_THAT_READ:
        return None

    if any(w in ("run", "rerun", "trigger", "dispatch") for w in words) and "workflow" in words:
        return (
            f"Blocked: `{tool_name}` starts or replays the deploy workflow, which pushes to the "
            "production server.\n"
            "Deploying is the user's decision — report that the run is ready to be triggered."
        )

    return (
        f"Blocked: `{tool_name}` does not start with a recognised read verb, so it is refused by "
        "default on GitHub.\n"
        f"{HAND_BACK}\n"
        "If this tool really only reads, add its leading verb to MCP_LEADING_VERBS_THAT_READ in "
        ".claude/hooks/guard-commands.py."
    )


def main():
    try:
        payload = json.load(sys.stdin)
    except (json.JSONDecodeError, ValueError):
        return 0

    tool_name = payload.get("tool_name") or ""
    tool_input = payload.get("tool_input") or {}

    if tool_name == "Bash":
        refusal = refuse_bash(tool_input.get("command") or "")
    else:
        refusal = refuse_mcp(tool_name)

    if refusal:
        print(refusal, file=sys.stderr)
        return 2

    return 0


if __name__ == "__main__":
    sys.exit(main())
