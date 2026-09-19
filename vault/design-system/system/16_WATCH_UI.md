# 16 — Watch UI

**Design System:** JARVIS Operating System
**Source of truth:** `JARVIS_VISUAL_BIBLE.md` §13.2 (Smartwatch), §19.5 (Wearables), §3.3 (breathing), §11 (Sound)
**Status:** Permanent. The smartwatch render is the **floor of the system** (§13.2).

---

## 1. Governing Rule (§13.1, §13.2)

The watch is the **minimum viable JARVIS presence**: ignition point + at most 2 simplified rings, no particles, no panels. State communicated primarily through color and a single breathing pulse.

**The acceptance test:** *if it still reads as "JARVIS" at this level of reduction, the core design has succeeded.*

---

## 2. Watch Render Spec

| Property | Spec |
|---|---|
| Ignition point | Present — the single most important element that must survive reduction |
| Rings | At most **2 simplified rings** (no inner/outer multi-axis clusters) |
| Particles | **None** |
| Panels | **None** (at most a transient notification/alert overlay, below) |
| State channel | Color + a single breathing pulse (§3.3) |
| Text | Minimal — `--font-caption`/`--font-data-mono` only where a real value is shown (never decorative) |

**State signaling:** the watch's entire vocabulary is the current state color plus breathing tempo. Idle blue, Listening teal, Thinking amber, Warning red, Sleeping violet, Offline steel — all from §01.

---

## 3. Interactions on Watch

| Signal | Behavior |
|---|---|
| Voice detected | Immediate Listening — fastest transition (~200–300ms, §14.4) |
| Notification | Two-tone chime (§11.3) + a brief state-colored accent; no panel chrome |
| Warning / Critical | State-colored pulse at 1Hz / 2Hz — the watch renders the alert purely through color + tempo, relying on sound for detail (§11) |
| Device pickup (motion) | Brief gentle brightening event pulse (§14.5) |

---

## 4. Sound on Watch (§19.5)

Wearables without screens express state through the **Sound Language** (§11) and, where present, a single-LED-equivalent color cue mapped directly to §5's state palette. **The minimum viable JARVIS presence is one color and one sound, both already fully specified.**

- Reactor hum (§11.3) available at `--audio-hum-db`.
- Notification chimes pitched to state family.
- Silence in Offline.

---

## 5. Reduction Hierarchy (how to reduce further if forced)

1. Drop one ring (2 → 1).
2. Drop rings entirely — **ignition point + breathing only**.
3. If only a static point is possible (non-screen wearable), express state via **color only** — the LED-equivalent cue mapped to the state palette, plus sound.

Never drop the state color, the breathing, or the sound. Those three are the irreducible JARVIS.

---

## 6. Performance Budget

- The watch render is already at floor density — it should be the **least visually demanding render in the entire system** (§3.5 Idle is the screensaver-equivalent). There is nothing lower to simplify; state animation quality is preserved.

---

## 7. Absolute Prohibitions

- No particles on watch.
- No panels as default chrome.
- No third ring.
- No device-specific color or motion language — only reduction.
- No anthropomorphic features even at minimal scale (§15.7).

---

## 8. Token Reference

State colors and breathing tokens in `20_DESIGN_TOKENS.md`; reactor reduction in `07_REACTOR.md` §7; sound in `11_SOUND.md`.
