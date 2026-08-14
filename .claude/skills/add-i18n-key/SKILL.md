---
name: add-i18n-key
description: Adds or changes a translated string in the chiron-front home-made i18n system. Use when a screen needs new user-visible text, when a label must change, or when a screen renders a raw key like nav.coach instead of a word. Covers the flat namespace.cle convention, fr.ts as the source of truth with en.ts mirroring it, the t pipe and the localize pipe, {{param}} interpolation through I18nService, and the i18n-diff.py script that reports keys present in only one dictionary. Do not use for building the screen itself (see add-angular-feature), for backend messages returned by the API, or for the French text inside the ChironAgent system prompt (see add-ai-tool).
---

# Add a translated string

There is no i18n library. `i18n/fr.ts` and `i18n/en.ts` are two `Record<string, string>` objects that
must hold the same keys, and nothing checks that they do. A key added to one only renders as the raw
key — `nav.coach` printed in the interface — for every user on the other language. That silent
divergence is the whole risk in this task.

`fr.ts` is the source of truth, as its own header states.

## Procedures

**Step 1: Choose the key**
1. Use a flat `namespace.cle` string. The namespace is the screen or the shared area:
   `nav.`, `chat.`, `journal.`, `programme.`, `session.`, `profile.`, `agora.`, `settings.`,
   `statistics.`, `common.`.
2. Read the neighbouring keys in `fr.ts` before inventing a namespace — the file is grouped by
   section with `// ──` separators.
3. Keep the key in French-neutral vocabulary describing the *role* of the string, not its content:
   `journal.volume_hebdo`, not `journal.tonnage_de_la_semaine_en_kg`.

**Step 2: Add it to `fr.ts` first**
1. Add the entry in the matching section, keeping the surrounding style: single quotes, trailing
   comma.
2. Write the French exactly as it must appear, including accents and the app's voice — the product
   speaks like a demanding mentor, not a chat assistant.
3. Use `{{param}}` for anything interpolated. `I18nService.t(key, params)` substitutes it.

**Step 3: Mirror it in `en.ts`**
1. Add the same key, in the same section, with the English text.
2. Never leave it out "for now". A missing key is visible to the user immediately.
3. Keep the same `{{param}}` names — the substitution is by name.

**Step 4: Use it in the template**
1. `{{ 'journal.volume_hebdo' | t }}` for a plain string.
2. `{{ 'journal.volume_hebdo' | t: { kg: total() } }}` when it interpolates.
3. `| localize` (`LocalizePipe`) is for a *value* that already carries a French and an English form —
   an `ExerciceDefinition` with `nomFr` and `nomEn` — not for a dictionary key.
4. Confirm the component imports the pipe: both are standalone and must be in the component's
   `imports` array.

**Step 5: Check the two dictionaries agree**
1. Run `python3 .claude/skills/add-i18n-key/scripts/i18n-diff.py`.
2. It exits non-zero and lists any key present in one dictionary only. Fix every one it reports, not
   just the key just added — a pre-existing divergence is a bug already visible to users.

**Step 6: Verify in the browser**
1. Run `npm start`, open the screen, and switch the language in the settings.
2. Confirm the string renders in both languages and that no raw key appears anywhere on the screen.
3. Check the narrow viewport: French is routinely 20% longer than English and overflows buttons.
4. Confirm every item in `references/checklist.md`.

## Error Handling

* If the screen shows the raw key, it is missing from the dictionary for the active language, or the
  key is misspelled at the call site. `i18n-diff.py` catches the first case, not the second.
* If the script reports keys missing in `en.ts` that were never touched here, they are pre-existing
  divergences — report them, and fix them in their own commit.
* If `{{param}}` renders literally, the parameter object was not passed to the pipe, or the name does
  not match.
* If the template rejects the pipe with "could not be found", the standalone `TranslatePipe` is not in
  the component's `imports`.
* If the string is right but the accents are broken, the file was written with the wrong encoding —
  both dictionaries are UTF-8.
* If a translated string must contain markup, it must not. Split it into several keys and compose
  them in the template; the dictionaries hold text only.
* If the script cannot parse a dictionary, an entry uses a computed key or a template literal. The
  dictionaries must stay flat literals so they remain statically checkable.
