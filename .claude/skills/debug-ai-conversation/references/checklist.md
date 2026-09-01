# Coach debugging checklist

## Establishing the facts
* [ ] The literal message and the literal reply were obtained, not a summary.
* [ ] The conversation and the provider (`Utilisateur.aiProvider`) are known.
* [ ] It was established whether the symptom reproduces in a fresh conversation.
* [ ] The backend log was read for the exchange, and it is known whether a tool was called at all.

## Ruling out the silent causes first
* [ ] The tool is named in brackets in the matching `ChironAgent` `@SystemMessage` block.
* [ ] `OPENROUTER_API_KEY` was confirmed non-blank, and the model id in `CHIRON_AI_MODEL` still
      exists on OpenRouter and still advertises `tools`.
* [ ] The 20-message window was considered before calling it a memory bug.

## Fixing
* [ ] The fix targets the layer the symptom actually belongs to.
* [ ] A tool bug is covered by a unit test against the tool class, with no model involved.
* [ ] A router or memory bug is covered by a test with a mocked agent.
* [ ] A prompt change was verified by conversation against the deployed model.
* [ ] A neighbouring question was confirmed **not** to trigger the tool.
* [ ] No deliberate behaviour from the "should not be fixed" table was changed without saying why.

## Closing
* [ ] `mvn verify` passes.
* [ ] If the prompt changed, the commit body names it — it is the main lever on coach behaviour.
