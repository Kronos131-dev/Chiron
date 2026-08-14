# i18n checklist

## The key
* [ ] Flat `namespace.cle`, in an existing namespace where one fits.
* [ ] Placed in the matching `// ──` section of the file, not appended at the end.
* [ ] Names the role of the string, not its current wording.
* [ ] Style matches the neighbours: single quotes, trailing comma.

## Both dictionaries
* [ ] Added to `fr.ts` first — it is the source of truth.
* [ ] Added to `en.ts` with the same key, in the same section.
* [ ] `{{param}}` names are identical in both.
* [ ] Both files are UTF-8 and the accents render correctly.
* [ ] The string contains no markup.

## Use
* [ ] The template uses `| t`, with a parameter object when it interpolates.
* [ ] `| localize` was used only for a value carrying `nomFr`/`nomEn`, not for a dictionary key.
* [ ] The standalone pipe is in the component's `imports` array.

## Verification
* [ ] `python3 .claude/skills/add-i18n-key/scripts/i18n-diff.py` exits 0.
* [ ] Any pre-existing divergence it reported was named to the user, for its own commit.
* [ ] The screen was opened in both languages and shows no raw key.
* [ ] The longer of the two strings was checked on a narrow viewport.
