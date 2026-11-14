# Friday phone jump-start test plan

Run these in order. Record pass/fail and exact observations. Do not infer a pass from a screen merely opening.

## A. Install and first launch

- [ ] APK installs on the phone.
- [ ] App opens and stays open for 60 seconds.
- [ ] No permission or startup loop blocks manual text use.
- [ ] Restart the app; persistent data remains.
- [ ] Restart the phone; app opens again.

## B. Model setup

- [ ] Open Model Manager.
- [ ] Choose a GGUF model from local storage.
- [ ] Import reports success, with no crash or out-of-space failure.
- [ ] Provider health eventually reports a loaded model.
- [ ] Send a short test: Reply with exactly: LOCAL MODEL READY.
- [ ] Record load time, first-token delay, approximate response speed, heat and battery use.

The proposed starter is LFM2.5-1.2B-Instruct Q4_K_M, approximately 731 MB. It is a small initial language engine, not Friday's identity. Device RAM and sustained speed are not known until tested.

## C. Offline test (essential)

- [ ] Enable airplane mode and ensure Wi-Fi/mobile data are off.
- [ ] Force-stop and relaunch Friday.
- [ ] Send a fresh text question that was not in the previous exchange.
- [ ] Confirm an answer is generated rather than a canned fallback.
- [ ] Confirm saved identity/memory context remains available.
- [ ] Try speech output if its voice model was previously installed.
- [ ] Try speech input and record whether Android has an on-device recognizer installed.

## D. Continuity and development

- [ ] Tell Friday one harmless, explicit preference correction.
- [ ] Restart the app.
- [ ] Give an unrelated later prompt where that preference applies.
- [ ] Check whether the correction changes the answer.
- [ ] Record whether the app can explain the evidence it used, where applicable.

This test demonstrates a narrow adaptation mechanism only; it is not evidence of consciousness or general self-development.

## E. Stability / limits

- [ ] Send 10 consecutive short messages.
- [ ] Background the app for two minutes and return.
- [ ] Interrupt speech playback.
- [ ] Record crashes, freezes, model-unload behavior and storage growth.
- [ ] Note any network access that appears required.

## Report

Copy templates/device-review.md into reports/inbox/, fill what you observed, and upload it. Attach only relevant, redacted logs.
