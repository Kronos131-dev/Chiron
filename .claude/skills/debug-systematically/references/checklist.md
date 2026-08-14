# Debugging checklist

## Reproduction
* [ ] The exact input, observed output and expected output are known.
* [ ] Where it reproduces is known: locally, in production, or both.
* [ ] A production-only symptom was routed to `inspect-production` or `manage-env-and-secrets`.
* [ ] Nothing was fixed speculatively without a reproduction.

## Ruling out the silent causes
* [ ] `references/silent-causes.md` was checked for the affected area.
* [ ] It was confirmed that the code being read is the code being run — no stale container, no
      service worker serving an old bundle.

## The loop
* [ ] The failure was reduced to the smallest thing that still shows it.
* [ ] A failing test was written at that level before editing production code.
* [ ] Each iteration stated one hypothesis and changed one thing.
* [ ] What was ruled out was recorded, so no hypothesis was tested twice.
* [ ] After three failed hypotheses, the path was re-read rather than iterated on further.

## The fix
* [ ] The cause was fixed where the wrong value originates, not where it surfaces.
* [ ] An existing convention was applied rather than worked around.
* [ ] Every diagnostic was removed: no `console.log`, no `System.out.println`, no leftover `DEBUG`
      logging.

## Closing
* [ ] The regression test is kept, correctly placed and named.
* [ ] It was confirmed to fail against the old code.
* [ ] `verify-backend-change` or `verify-frontend-change` passes.
* [ ] A symptom that disappeared without an identified cause was reported as unresolved, not fixed.
