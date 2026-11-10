# INCIDENT: No screen can ask a half-built engine for anything: waking is a state, not a crash

## WHAT

FATAL IllegalStateException "JarvisEngine.init() must run first" from JarvisEngine.getBodyCoordinator (JarvisEngine.kt:683) called by HomeScreen.kt:55 during cold launch composition (observed in raw log 20261024-235308-JARVIS, first launch, 1.1s after process start; 10-22 and 10-23 logs showed the same).

## WHERE

- `com.jarvis.app.JarvisEngine.init` sets initialized = true synchronously, then posts async wiring to a HandlerThread.
- `com.jarvis.app.JarvisEngine.getBodyCoordinator` throws `checkNotNull` if called before handler thread finishes.
- `com.jarvis.app.ui.screens.HomeScreen.kt:55`, `ConversationScreen.kt:47`, and other UI screens asked for engine properties immediately upon Compose rendering on main thread.

## WHEN

- Raw launch logs 2026-10-24 23:53:08 (and 2026-10-22, 2026-10-23), ~1.1s after process start on first cold boot.
- Active story: LAUNCH-WAKE-ORDER.

## WHY

A cold launch race where the main thread begins Jetpack Compose composition of the root UI tree before the engine's background HandlerThread has finished wiring the object graph.
`initialized` flag meant "bootstrap started", but callers treated it as "wiring complete and ready to use".
Accessors used `checkNotNull(...)` throwing `IllegalStateException`, which crashed the application immediately on launch if composition ran first.

## MITIGATION

- PERMANENT-FIX: Introduced `EngineReadinessState` (NotStarted, Waking, Ready, FailedToWake).
- PERMANENT-FIX: Made UI accessors route through `com.jarvis.app.ui.EngineAccess` returning typed `EngineSlot<T>` answers that never throw.
- PERMANENT-FIX: Navigation host and screens display a plain `WakingSurface` during waking/failed states instead of crashing.
- PERMANENT-FIX: Handled wiring failures by reporting to `FailureSurface` and updating state to `FailedToWake`, never throwing uncaught.

## REPRODUCTION

Cold launch app when background thread execution is delayed relative to Compose initial composition pass.

## STATUS

fixed — permanent fix in LAUNCH-WAKE-ORDER.
