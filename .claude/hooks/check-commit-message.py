#!/usr/bin/env python3

"""PreToolUse guard on `git commit` for Chiron.

Chiron commits carry no trace of any AI tooling and are authored by the repository owner
alone. The subject is English with a conventional prefix; the body is French. This hook
enforces the mechanical half of that contract — the commit-changes skill carries the rest.

Reads a Claude Code hook payload on stdin, exits 2 with a message on stderr to block.
"""

import json
import re
import shlex
import sys

SHELL_SEPARATORS = r"&&|\|\||;|\n"

FORBIDDEN_IN_MESSAGE = (
    ("co-authored-by", "a Co-Authored-By trailer"),
    ("claude", "the word “Claude”"),
    ("anthropic", "the word “Anthropic”"),
    ("generated with", "a “Generated with” line"),
    ("\N{ROBOT FACE}", "a robot emoji"),
    ("noreply@anthropic.com", "an Anthropic address"),
)

CONVENTIONAL_SUBJECT = re.compile(
    r"^(feat|fix|refacto|tech|chore|docs|test|perf|style|build|ci)(\([a-z0-9._-]+\))?!?: .+"
)

MAX_SUBJECT_LENGTH = 72

HEREDOC = re.compile(r"<<-?\s*['\"]?([A-Za-z_][A-Za-z0-9_]*)['\"]?\r?\n(.*?)\r?\n[ \t]*\1", re.S)

AUTHORSHIP_OVERRIDES = ("--author", "user.name=", "user.email=")


GIT_PATH_OPTIONS_TAKING_A_VALUE = ("-C", "--git-dir", "--work-tree", "-c")


def git_subcommand(tokens):
    position = next(
        (i for i, token in enumerate(tokens) if token == "git" or token.endswith("/git")),
        None,
    )
    if position is None:
        return None

    expecting_option_value = False
    for token in tokens[position + 1:]:
        if expecting_option_value:
            expecting_option_value = False
            continue
        if token in GIT_PATH_OPTIONS_TAKING_A_VALUE:
            expecting_option_value = True
            continue
        if token.startswith("-"):
            continue
        return token
    return None


def commit_segments(command):
    """Yield only segments that actually invoke `git commit`.

    Matching the words anywhere in the text would fire on a command that merely mentions them —
    a grep over the documentation, or a message being echoed.
    """
    for segment in re.split(SHELL_SEPARATORS, command):
        segment = segment.strip()
        if not segment:
            continue
        try:
            tokens = shlex.split(segment)
        except ValueError:
            tokens = segment.split()
        if git_subcommand(tokens) == "commit":
            yield segment


def messages_in(segment, whole_command):
    messages = []

    for body in HEREDOC.findall(whole_command):
        messages.append(body[1])

    try:
        tokens = shlex.split(segment)
    except ValueError:
        tokens = segment.split()

    index = 0
    while index < len(tokens):
        token = tokens[index]
        if token in ("-m", "--message", "-F", "--file") and index + 1 < len(tokens):
            messages.append(tokens[index + 1])
            index += 2
            continue
        if token.startswith("--message=") or token.startswith("--file="):
            messages.append(token.split("=", 1)[1])
            index += 1
            continue
        if token.startswith("-m") and len(token) > 2:
            messages.append(token[2:])
            index += 1
            continue
        index += 1

    return [m for m in messages if m and m != "-"], tokens


def refuse_authorship_override(tokens):
    for token in tokens:
        for override in AUTHORSHIP_OVERRIDES:
            if token.startswith(override) or token == override:
                return (
                    f"Blocked: `{token}` rewrites the commit identity.\n"
                    "Chiron commits are authored by the repository owner, using the identity "
                    "already configured in git. Drop the flag."
                )
    return None


def refuse_forbidden_content(message):
    lowered = message.lower()
    for needle, description in FORBIDDEN_IN_MESSAGE:
        if needle in lowered:
            return (
                f"Blocked: the commit message contains {description}.\n"
                "Chiron commits carry no trace of any AI tooling — no Co-Authored-By, no "
                "“Generated with”, no robot emoji, no mention of Claude or Anthropic, in the "
                "subject, the body or the trailers. The author is the repository owner alone.\n"
                "Rewrite the message without it."
            )
    return None


def refuse_bad_subject(message):
    subject = message.strip().splitlines()[0] if message.strip() else ""
    if not subject:
        return None

    if not CONVENTIONAL_SUBJECT.match(subject):
        return (
            f"Blocked: `{subject}` is not a valid Chiron subject line.\n"
            "The subject is written in English with a conventional prefix: "
            "feat|fix|refacto|tech|chore|docs|test|perf|style|build|ci, then `: `, then an "
            "imperative summary. The body underneath is written in French."
        )

    if len(subject) > MAX_SUBJECT_LENGTH:
        return (
            f"Blocked: the subject line is {len(subject)} characters, over the "
            f"{MAX_SUBJECT_LENGTH} limit.\n"
            "Shorten it and move the detail into the French body."
        )

    return None


def refuse_interactive_editor(tokens):
    opens_an_editor = not any(
        token in ("-m", "--message", "-F", "--file", "-C", "--reuse-message", "--no-edit")
        or token.startswith(("-m", "--message=", "--file="))
        for token in tokens
    )
    if opens_an_editor:
        return (
            "Blocked: `git commit` without a message opens an interactive editor, which cannot "
            "be answered from here and would hang the session.\n"
            "Pass the message with a heredoc: `git commit -F - <<'EOF' … EOF`."
        )
    return None


def refuse(command):
    for segment in commit_segments(command):
        messages, tokens = messages_in(segment, command)

        for refusal in (refuse_authorship_override(tokens), refuse_interactive_editor(tokens)):
            if refusal:
                return refusal

        for message in messages:
            for refusal in (refuse_forbidden_content(message), refuse_bad_subject(message)):
                if refusal:
                    return refusal

    return None


def main():
    try:
        payload = json.load(sys.stdin)
    except (json.JSONDecodeError, ValueError):
        return 0

    if (payload.get("tool_name") or "") != "Bash":
        return 0

    refusal = refuse((payload.get("tool_input") or {}).get("command") or "")
    if refusal:
        print(refusal, file=sys.stderr)
        return 2

    return 0


if __name__ == "__main__":
    sys.exit(main())
