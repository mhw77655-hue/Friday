# 11 — Sound

**Design System:** JARVIS Operating System
**Source of truth:** `JARVIS_VISUAL_BIBLE.md` §11 (Sound Language), §5.3/§15 (state-color emotional consistency), §18 (no skeuomorphic samples)
**Status:** Permanent — frozen by the Visual Bible.

---

## 1. Governing Principle

Sound is the audio equivalent of the visual system: **the reactor hum is the breathing pulse; UI sounds are micro event pulses; state family defines pitch.** Every state in §9 has a corresponding one-shot or looping sound design (the reference's Sound Signature panel: Wake, Listening, Thinking, Success, Warning, Shutdown).

Three laws:
1. **All sound is synthesized and tonal** — sine/triangle-derived. **No skeuomorphic UI sound samples** (mechanical clicks, camera shutters) — ever (§18).
2. **Sound and color stay emotionally consistent** — pitch and timbre follow the current state's color family conceptually, even though they are different senses.
3. **Sound and visual ignition are frame-accurate** — startup and shutdown are the system's most important audio sync points.

---

## 2. Level Budget

| Signal | Level | Token |
|---|---|---|
| Baseline ambient reactor hum | −36dB | `--audio-hum-db` |
| Maximum UI sound above hum | +6dB headroom | `--audio-ui-headroom` |

**Rule:** no UI sound ever exceeds ambient hum by more than 6dB. Volume discipline mirrors visual restraint.

---

## 3. The Reactor Hum (§11.2)

- **When:** continuous whenever the system is Idle through Critical (i.e., "on").
- **Character:** very low-level ambient drone — sub-bass + a faint harmonic shimmer.
- **Behavior:** rises subtly in presence-of-mind states; **absent entirely in Offline** (the audio equivalent of no breathing).
- **Sync:** the hum is the audio twin of the breathing pulse — always present, subtle, rhythmic.

---

## 4. UI Sounds (§11.1)

- **Duration:** short, `<150ms`.
- **Character:** pitched, non-musical clicks/chimes for discrete interactions (button press, panel open/close).
- **Source:** clean sine- or triangle-wave-derived tone — never a sampled "click."
- **Level:** never exceeds hum +6dB.

| Interaction | Character |
|---|---|
| Button press | Micro tonal click (sine-derived) |
| Panel open / close | Low chime, panel-in/out matched to `--dur-transition` |
| Input focus | Subtle brightening tone |
| Notification | Two-tone rising chime (§11.3) |

---

## 5. Notifications (§11.3)

- **Structure:** two-tone rising chime for standard notifications.
- **State-family pitch mapping:** pitched to match the current state's color family conceptually — cooler/higher pitch for Listening-family events, warmer/lower for Thinking-family events. Sound and color remain emotionally consistent.
- Dismissal follows the visual auto-dismiss (see `05_COMPONENTS.md` §5.4).

---

## 6. Startup (Wake) (§11.4)

- A rising swell from silence to the Idle reactor hum, **1.5–2.5s**, timed to sync with the reactor's ignition point brightening from zero to baseline.
- **Frame-accurate sync:** sound and visual ignition must land together. This is the single most important sync point in the whole audio system.
- Duration token: `--dur-startup-shutdown` (2200ms nominal).

---

## 7. Shutdown (§11.5)

- **The exact reverse of Startup:** hum descends to silence over 2–3s, timed to the visual Shutdown sequence (§3.9).
- The final ember-point of light and the final fade of sound land together.
- Offline is silent.

---

## 8. State Sound Signatures

| State | Sound |
|---|---|
| Wake / Startup | Rising swell → idle hum (frame-accurate with ignition) |
| Listening | Hum rises subtly; attention presence |
| Thinking / Analysis | Hum steady; warm family tones for discrete events |
| Building / Executing | Hum at output energy |
| Success | Single clean bright chime (matches the `success` event pulse rarity, §15.4) |
| Warning | Sharp tonal alert (matches 1Hz visual pulse, sharp ease-out) |
| Critical | Higher-intensity tonal alert (matches 2Hz visual pulse) |
| Shutdown | Hum descends → silence (frame-accurate with ember fade) |
| Offline | Silence — no hum, no UI sounds |

---

## 9. Sound-Color Consistency Map (§5.3/§15)

| Color family | Pitch/timbre family |
|---|---|
| Blue / Nominal (Idle) | Neutral-low, steady |
| Teal / Input (Listening) | Cooler, higher |
| Amber / Thinking | Warmer, lower |
| Near-white / Building | Bright, clear |
| Red / Alert | Sharp, attention-drawing |
| Violet / Sleep | Deep, quiet |
| Offline | Silence |

---

## 10. Absolute Prohibitions

- No skeuomorphic samples (clicks, shutters, mechanical sounds).
- No UI sound above hum +6dB.
- No music/loops as UI decoration — the hum is the only continuous audio.
- No Offline sounds of any kind.
- No sound without a visual cause mapping to §3, §9, or §15 (mirror of the animation rule).

---

## 11. Token Reference

Audio tokens (`--audio-hum-db`, `--audio-ui-headroom`) declared in `20_DESIGN_TOKENS.md`. Sync points cross-reference `08_MOTION.md` §10 and `18_ANIMATIONS.md`.
