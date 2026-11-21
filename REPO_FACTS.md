# Friday repository facts

- Product: Friday, one continuing Android-hosted individual.
- Canonical repository: mhw77655-hue/Friday.
- Canonical development branch: main.
- Android module: mobile/app.
- Android application ID: ai.friday.assistant.
- Android namespace and Kotlin base package: ai.friday.assistant.
- Sole active delivery authority: .ralph/FRIDAY_DELIVERY_PLAN.md.
- Owner vision: .ralph/VISION.md and .ralph/OWNER_WORDS.md.
- Build: ./gradlew :mobile:app:assembleDebug.
- Unit tests: ./gradlew :mobile:app:testDebugUnitTest.
- Emulator/instrumentation and physical-device evidence are separate from JVM unit-test evidence.
- Live Ollama tests are opt-in diagnostics; a localhost model server is not a production dependency or proof of offline operation.
- CI artifacts and temporary signing keys are not the permanent production signing identity.
- Do not infer current phone firmware, installed state, permissions or physical-device success from source code or emulator tests.
