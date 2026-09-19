# 12 — Voice Visualizer

**Design System:** JARVIS Operating System
**Source of truth:** `JARVIS_VISUAL_BIBLE.md` §10 (Voice Visualization), §9 (state-color language), §14.4 (voice responsiveness)
**Status:** Permanent — frozen by the Visual Bible.

---

## 1. Governing Principle

**The waveform represents real audio, or it does not exist.** Amplitude is driven directly by real audio signal — input while Listening, output while speaking. A decorative idle animation standing in for real audio is forbidden (§10.1). This is the audio-input channel of the reactor's telemetry, rendered honestly.

---

## 2. Waveform (§10.1)

| Property | Spec |
|---|---|
| Form | A single continuous horizontal waveform |
| Color | Current state color (§01), exact |
| Treatment | Soft-glowing line with light bloom — bloom only where this is the brightest element (§4.2) |
| Amplitude | Driven directly by real audio signal (input while Listening; output while speaking) |
| Surface | Matches the reference's Voice Waveform panel treatment |

---

## 3. Cadence / Envelope (§10.2)

The waveform's response envelope makes it read "confident" rather than "nervous":

| Segment | Duration |
|---|---|
| Attack | ~40ms |
| Release | ~180ms |

**Behavior:** responds instantly to sound onset (short attack) but doesn't jitter on tiny fluctuations (long release).

---

## 4. Animation (§10.3)

- The waveform line has a very slight persistent **glow-trail**: the previous ~200ms of waveform fades out behind the current line at low opacity.
- Purpose: imply continuity/flow rather than a hard-cut oscilloscope readout.
- The trail is the only sanctioned persistent motion on the waveform surface; the line itself is real-time data.

---

## 5. Speaking Indicators (§10.4)

| Mode | Waveform color | Rationale |
|---|---|---|
| Listening (input) | `--state-listening` (`#00D9C0`) | Acquiring input |
| Speaking (output) | `--state-building` family (`#E8ECF2`) | Voice output is "producing," consistent with §9.7 |

**Ring sync:** during speech, the reactor's ring pulse syncs to the **output** waveform's amplitude — exactly as it syncs to input amplitude during Listening. The reactor and the waveform are one signal chain.

---

## 6. State Coupling

- **Listening state** is triggered the instant any voice is detected, with the **fastest transition speed in the system**: state-change duration is halved from the standard 400–600ms to ~200–300ms (§14.4). Voice responsiveness is the single most latency-sensitive visual event JARVIS has.
- The waveform sits beneath the reactor mini-render in the Assistant Overlay as the fixed Reactor + Waveform + Text trio (§17.9 → `05_COMPONENTS.md` §7).
- When no real audio is present, the waveform shows a **flat, honest baseline** — never a decorative shimmer. A visible "no signal" state is correct.

---

## 7. Rendering Contract

| Property | Spec |
|---|---|
| Material | Energy (§02 §2.4) — pure light line |
| Blend | Additive/glowing line |
| Background | Transparent Glass panel at most; never a filled plot area (§17.8 chart rule) |
| Scale | Continuous across the full panel width; amplitude normalized to real signal level |
| Bloom | Only if this is the brightest element on screen |

---

## 8. Absolute Prohibitions

- No decorative idle animation in place of real audio.
- No hard-cut oscilloscope jitter — the ~40/180ms envelope is mandatory.
- No waveform in non-state colors.
- No speaking-mode waveform in Listening teal — output is Building-family, always.
- No fake waveform when no signal exists.

---

## 9. Token Reference

Waveform timing (attack 40ms / release 180ms / trail 200ms) is behavioral; color tokens are the state palette in `01_COLORS.md` / `20_DESIGN_TOKENS.md`. Sync with `08_MOTION.md` §3 (ring pulse) and `18_ANIMATIONS.md`.
