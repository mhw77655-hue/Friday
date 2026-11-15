# Nano Liquid Symbiosis and phone-wide voice

Friday's Android app is the only production host. This work adds a live adaptation layer and the Android system-assistant/accessibility entry points.

## Nano Liquid Symbiosis

`LiquidSymbiosisEngine` receives real host observations from `NanoSymbioticController` and real reasoning/speech outcomes from `BodyCoordinator`. It keeps per-organ strategies for reasoning, speech input/output, memory, vision and background work.

The learned strategy changes future context budgets, concurrency, speech chunk sizes and background-work weight. Sustained host pressure reduces the footprint; healthy conditions can restore capacity gradually. Reasoning context grows only after repeated successful reasoning outcomes, not merely because the phone is idle.

The state is stored in app-private files and restored after process restart. Persistence is throttled for host samples and forced after organ outcomes. It stores aggregate metrics and strategy values, not prompts, transcripts or screen contents.

## JEPA-inspired predictive core

`JepaLatentPredictor` learns a compact next-state predictor over six resource/workload features, conditioned on the previous operating action. It updates an EMA target representation, tracks prediction error and persists predictor parameters. It never trains on raw conversation text, audio or pixels.

This is a lightweight online latent predictor inspired by JEPA, not a pretrained or full neural JEPA model. Its value is to establish a persistent predictive-learning substrate on the phone; richer encoders and multimodal predictive models can be introduced as organs later.

## Phone-wide voice control

- `FridayVoiceInteractionService` registers Friday as a selectable Android system assistant.
- A system assistant invocation opens Friday and sends a wake/listen event into the existing `BodyCoordinator` state machine.
- `FridayAccessibilityService` provides user-enabled semantic phone controls: Back, Home, Recents, Notifications, Quick Settings, scroll, launch app, tap accessible text and type into a focused field.
- `PhoneCommandInterpreter` handles only clear, deterministic commands. Ambiguous or complex requests remain in Friday's cognitive path.
- Android requires the user to select Friday as the default assistant and explicitly enable Friday's accessibility service.

## Boundaries that remain real

A custom always-listening "Friday" wake phrase still depends on the device's SoundTrigger/hotword support and enrollment; this commit does not claim that capability is available on every Realme firmware. System assistant invocation is supported through Android's assistant entry point.

Accessibility cannot bypass secure windows, protected system surfaces, app permissions or app-specific restrictions. It is not unrestricted root control. The service's window-content access is disclosed and must be enabled by the user.

Nano Liquid Symbiosis changes Friday's operating strategy, not Android's kernel, clocks, other apps' code or Friday's signed APK. Capability creation and safe self-replacement remain separate future organs.
