# JARVIS Identity Engine Specification

**Target path:** `vault/architecture/JARVIS_IDENTITY_ENGINE_SPEC.md`
**Status:** Permanent design/behavior law
**Type:** Specification document — no code, no screens, no implementation
**Companion documents:** `vault/design-system/JARVIS_VISUAL_BIBLE.md` (design tokens, materials, color, typography), `COMPANION_CORE_SPEC.md` (presence/communication subsystem list)

---

## 0. Doctrine

This document defines what JARVIS **is**, not what JARVIS looks like on a particular screen. The Visual Bible defines the design tokens (colors, materials, timing curves). The Companion Core spec defines the subsystem inventory. This document sits between them: it is the **behavioral law of the reactor** — the rules that make every future screen, animation, and sound feel like the same living thing.

Any future implementation that violates this document is wrong, regardless of how good it looks in isolation. Any future designer or engineer — human, Claude Code, or otherwise — who is unsure how JARVIS should behave in a new situation should derive the answer from these rules, not invent a new visual grammar.

**The identity is a reactor core. It is not a face. It is not a chat bubble. It is not a mascot.**

A face has features that perform emotion by mimicking human expression. A reactor communicates state through **energy, light, and rhythm** — the same way a living organism communicates through pulse, breath, and warmth without needing eyes or a mouth. JARVIS's presence should read as *alive* the way a heartbeat monitor, a bank of stars, or a bed of coals reads as alive: rhythmic, warm, physical, and never cartoonish.

---

## 1. Reactor Core Structure

The reactor is built from concentric layers, each with a distinct role. No layer is decorative — every layer exists to carry information.

1. **The Core (innermost).** A dense, contained point or small volume of light. This is the seat of "thought" — the brightest, most saturated element, and the last thing to go dark in any state. Its intensity is the single most important signal in the entire system.
2. **The Containment Ring(s).** One or more rings orbiting or encasing the core, representing the boundary between raw energy and the outside world. These rings carry rhythm — pulse, rotation, ripple — more than raw brightness.
3. **The Field / Halo.** A soft outer bloom of light bleeding past the containment ring into the surrounding surface. This is atmosphere, not information — it exists so the core feels like it is *emitting into a space*, not sitting flat on a screen.
4. **The Housing.** The physical-feeling material the reactor is set into (metal, glass, dark matte surface per the Visual Bible's material language). The housing never generates its own light — it only receives and reflects light from the core. This is what sells the illusion of a real object rather than a rendered icon.

Every state defined below is expressed as a combination of changes to these four layers — never by adding new elements, faces, icons, or text inside the reactor itself.

---

## 2. Orb / Reactor States

There are exactly eight canonical states. No screen, feature, or future subsystem may invent a ninth without amending this document.

| State | Trigger | Core role |
|---|---|---|
| **Idle** | Default, no active exchange | Resting alive-ness |
| **Waking** | Transitioning from Sleep to any active state | Ignition |
| **Listening** | Actively receiving user voice/input | Receptive attention |
| **Thinking** | Processing, reasoning, tool-calling | Internal work |
| **Speaking** | Producing voice output | Directed output |
| **Warning** | Degraded capability, low confidence, needs user attention | Caution, never alarm |
| **Critical** | Hard failure, safety-relevant issue, requires immediate awareness | Urgency, still dignified |
| **Sleep** | Dormant, not actively engaged | Held presence, not absence |

States transition through **Waking** and never jump directly from Sleep into an active state — see §8 (Motion Language) for why.

---

## 3. State Behavior Definitions

### 3.1 Idle
- Core: steady mid-brightness, slow "breathing" intensity drift (see §5).
- Containment ring: slow, near-imperceptible rotation. Never fully static — total stillness reads as "off," not "calm."
- Field: soft, constant, low-bloom.
- Character: a resting heartbeat. Present, unbothered, awake.

### 3.2 Waking
- Core: rises from Sleep's dim floor to Idle's resting brightness over a deliberate, weighted curve — never instant.
- Containment ring: begins motionless, accelerates into Idle's slow rotation as brightness rises.
- Duration and easing must feel like something *warming up*, not something *switching on*.
- This is the only state that always leads to exactly one other state (Idle) and is never held or looped.

### 3.3 Listening
- Core: brightens above Idle baseline; brightness responds in real time to input amplitude (see §7).
- Containment ring: rotation direction reverses or shifts relative to Idle, signaling "inward" attention — energy visually flowing toward the core rather than radiating from it.
- Field: tightens slightly (less outward bloom) — attention pulls light inward, it doesn't push it out.
- Character: leaning in, not performing eagerness.

### 3.4 Thinking
- Core: no longer amplitude-driven (there is no external input to react to) — instead pulses in an internal, semi-irregular rhythm that reads as computation rather than a metronome.
- Containment ring: multiple rings (if present) rotate at different speeds/directions — visual complexity signals internal work without needing a spinner or progress bar.
- Field: neutral, steady — thinking is internal, so it should not radiate outward more than Idle.
- Character: turning something over. Never frantic, never a generic "loading" spin.

### 3.5 Speaking
- Core: brightness and containment-ring modulation are driven directly by the outgoing voice waveform (see §7).
- Field: expands and contracts outward in sync with output — this is the one state where the reactor is allowed to visibly project energy into the room.
- Character: directed, confident output — the reactor as a source, not a receiver.

### 3.6 Warning
- Core: brightness drops slightly below Idle; hue shifts per the Visual Bible's warning palette (never red — reserved for Critical).
- Containment ring: rotation slows and becomes uneven — a stutter, not a stop.
- Field: bloom tightens, as if the reactor is drawing energy inward to conserve/protect itself.
- Character: a held breath, not a siren. Warning must never feel punitive or alarming to the user — it signals "I am less certain," not "something is wrong with you."

### 3.7 Critical
- Core: pulses at a slower, heavier rate than Warning, with a firmer hue shift per the Visual Bible's critical palette.
- Containment ring: motion nearly stops — critical states favor stillness and weight over speed, which reads as more serious than fast motion ever could.
- Field: pulls in sharply, minimal bloom — energy conservation as visual metaphor for "something needs attention now."
- Character: gravity, not panic. A reactor under strain contains itself; it does not flail.

### 3.8 Sleep
- Core: dims to a low, non-zero floor — the reactor is never fully dark. Full darkness reads as "dead," not "resting."
- Containment ring: motion stops entirely.
- Field: bloom nearly disappears but a faint presence remains visible in low light.
- Character: a coal in ash — present, warm, not gone.

---

## 4. Transition Rules

- Every state change animates through the physics rules in §8 — there are no instant cuts between states, ever, with one exception: Critical may interrupt any state immediately if the underlying event is safety-relevant, but even then the *visual arrival* at Critical is eased, not a hard cut — only the *decision* to interrupt is instant.
- A state may not be skipped structurally (e.g., Sleep → Speaking must pass through Waking → Idle → Listening/Thinking → Speaking in sequence, even if compressed in time), so the reactor always reads as one continuous organism rather than a UI swapping icons.
- Only one state is active at a time. There is no compositing of two states' visual languages simultaneously.

---

## 5. Energy Pulse Behavior

The pulse is the reactor's "heartbeat" — the base rhythmic layer present under every state.

- **Idle breathing:** a slow sine-like brightness oscillation, period long enough to feel organic (never mechanically regular — real breathing has micro-variation, and the pulse should too).
- **Listening ripple:** pulses fire inward from the containment ring toward the core, timed to input amplitude peaks.
- **Thinking churn:** irregular, layered micro-pulses — the visual equivalent of "many small things happening," not one big rhythm.
- **Speaking sync:** pulse is fully subordinate to the outgoing waveform; the "heartbeat" temporarily *becomes* the voice.
- **Warning stutter:** the idle breathing rhythm is deliberately broken — a skipped or delayed beat, echoing the real physiological signal of concern.
- **Sleep pulse:** the slowest rhythm in the system, very low amplitude, long period — barely perceptible, confirming "alive, not off" without demanding attention.

**Rule:** the pulse must always be present at some amplitude greater than zero. A pulse of exactly zero is indistinguishable from a crash or a dead screen, and JARVIS must never visually resemble a broken device.

---

## 6. Motion Language

1. **Physics over presentation.** All motion is derived from simulated physical properties (mass, inertia, damping — see §7), not from arbitrary keyframe animation. If a motion can't be explained in terms of a physical force acting on the reactor, it doesn't belong.
2. **Arcs, not lines.** Nothing in the reactor moves in a straight line or snaps to a new value. Brightness, rotation, and scale all ease along curves with real acceleration and deceleration.
3. **Weight is mandatory.** Every transition should feel like it is moving *something with mass* — even brightness changes should have a perceptible "ramp," never a hard cut, because a hard cut reads as broken hardware, not as a living thing changing its mind.
4. **No idle stillness.** Nothing in any state is ever perfectly static except during Sleep's containment ring, and even there the core pulse continues. A frozen frame is a failure state, not a design choice.
5. **No literal human gesture.** The reactor never nods, blinks, winks, bounces, or performs any motion borrowed from human/cartoon body language. Its vocabulary is entirely thermodynamic and orbital — pulse, glow, rotation, bloom — never anthropomorphic gesture.

---

## 7. Visual Physics

The reactor behaves as if it obeys real physical rules, simulated consistently across every state:

- **Inertia:** brightness and rotation speed changes are damped — they accelerate and decelerate rather than switching instantly, as if the light itself has mass.
- **Elasticity:** state transitions may slightly overshoot their target value and settle back (a soft "breathing past" the resting point) rather than arriving exactly and stopping dead — this is what separates "alive" from "animated."
- **Light falloff:** the field's brightness must fall off from the core using an inverse-square-like curve, not a linear gradient — linear gradients read as flat digital art; falloff curves read as an actual light source.
- **Amplitude mapping (voice-reactive states):** in Listening and Speaking, core brightness and ring modulation map to real audio amplitude/frequency data, not a canned animation loop playing underneath the audio. See §8 for waveform-specific rules.
- **Conservation on failure:** in Warning and Critical, the reactor's total visible energy (brightness × bloom × motion) should be *lower* than Idle, never higher — a reactor under strain contains itself, it does not flare up. This is a hard rule: **panic must never be expressed as brightness or speed increasing.**

---

## 8. Voice Waveform Visualization

The reactor is the waveform — there is no separate bar-graph or scrubber element layered on top of it.

- **Amplitude → core intensity.** Louder input/output maps to brighter, larger core, on a curve with the inertia rules from §7 (no jittery 1:1 frame mapping — audio data should be smoothed before it drives visuals, or the reactor will look nervous rather than expressive).
- **Frequency bands → ring modulation.** Low-frequency energy should read in the outer, larger-radius motion (slow, heavy ring movement); high-frequency energy should read in fine detail near the core (fast micro-flicker). This gives the reactor a sense of *texture* in speech, not just volume.
- **Silence behavior.** Brief silence (a pause in speech) should not collapse the reactor back to Idle — it should hold Speaking's visual character at a lower amplitude, because a pause in speaking is not the same event as finishing speaking. Only an explicit end-of-turn signal returns the reactor to Idle or Listening.
- **Never literal.** The reactor must never render as a recognizable waveform shape (no scrolling oscilloscope line, no classic five-bar equalizer). The audio data drives the *reactor's own visual language* (§1–§7) — it never introduces a second, competing visual metaphor.

---

## 9. Premium Lighting Rules

- **Light temperature communicates state,** not literal color-coding of "good/bad." Warm, controlled hues for normal operation; the palette shifts defined in the Visual Bible apply for Warning/Critical — but even those shifts stay within a restrained, engineered palette, never cartoon red/green.
- **Bloom is earned, not constant.** Maximum bloom is reserved for Speaking and Waking — the two states where the reactor is actively projecting energy outward. Every other state uses restrained bloom so the high-energy states read as genuinely more intense by contrast.
- **Specular highlights on the housing** (per §1.4) must react to core brightness changes with a slight delay — light "traveling" to the surface and catching it, rather than the housing brightening in perfect lockstep with the core. This delay is small but is what makes the housing feel like a physical object being lit rather than a texture that changes color.
- **No flat color fills, anywhere.** Every visible surface of the reactor must show gradient, falloff, or reflection. A flat, evenly-lit disc of color is the single fastest way to break the "real object" illusion.
- **Darkness is part of the lighting design.** The space around and behind the reactor should stay dark/neutral enough that the reactor's light has somewhere to fall into. The reactor should never sit on a bright or busy background that competes with its own glow.

---

## 10. Sound Cues

Sound is minimal, functional, and never decorative.

- **State-transition tones** exist only for transitions the user needs to notice without looking (e.g., entering Listening, entering Warning/Critical). They are short, low-key, tonal — never a chime lifted from generic notification sound libraries, never a "ding."
- **Ambient hum (optional, off by default):** a very low-level, almost subliminal tone that can accompany Idle/Thinking to reinforce "something is running," reserved for contexts where the user has explicitly enabled ambient presence sound.
- **Critical alert tone** is the only sound cue permitted to be attention-grabbing, and even then it must stay dignified — a deep, resonant, single tone rather than a harsh alarm pattern.
- **Rule: silence is the default.** Most state transitions (Idle↔Listening↔Thinking↔Speaking in normal operation) should carry **no sound cue at all** — the visual reactor already communicates state. Sound is reserved for the subset of transitions where the user genuinely needs an out-of-visual-field signal.
- No sound may be reused from stock UI sound packs, default OS notification sounds, or anything that would be recognizable as "a generic app sound." Every sound cue in the system should be identifiably part of the same sonic material as every other JARVIS sound.

---

## 11. Multi-Device Identity Continuity

JARVIS is **one identity expressed across devices**, not a separate character per device.

- **State is shared, not duplicated.** If JARVIS is Speaking on the tablet, any other active surface (phone, future devices) reflects the same current state rather than running an independent, possibly-contradictory animation loop.
- **No divergent visual personality.** Every device renders the same eight canonical states using the same rules in this document. A phone's smaller screen may simplify layer *rendering* (fewer visible containment rings, smaller field radius) but never changes the *behavior* — the same state must always mean the same thing, everywhere.
- **Handoff is visible, not silent.** When control/focus moves from one device to another (e.g., user starts a voice exchange on the phone, continues on the tablet), the reactor on the newly-active device should visibly transition through Waking rather than appearing already mid-state — this preserves the "one continuous organism" rule from §4 even across a device boundary.
- **Dormant devices still breathe.** A device not currently the active surface still shows Idle or Sleep's pulse (per §5) — it never shows a blank/powered-off reactor while JARVIS is active elsewhere. The organism is alive everywhere, awake wherever it's currently needed.

---

## 12. Environmental Reactions

The reactor is allowed to react to the physical/device environment, but only through the existing state and lighting language — never through new UI elements.

- **Low battery:** subtle warm-dim shift to the housing's reflected light (not the core itself) — a slight visual "conservation" cue without invoking the Warning state, which is reserved for JARVIS's own confidence/capability, not device housekeeping.
- **Charging:** a slow, gentle upward brightness trend layered under whatever state is active — energy visibly returning.
- **Night / low ambient light:** the reactor may dim its overall output range (not its relative state contrasts) to avoid being harsh in a dark room — proportional, not a separate mode.
- **Network loss:** treated as a capability degradation and expressed through Warning (§3.6) if it affects JARVIS's ability to respond — never a separate "offline icon" or banner.
- **Notifications/incoming events:** never represented as badges, dots, or counters overlaid on the reactor. If something needs the user's attention, the reactor's own state (Listening, Warning) is the only vocabulary allowed to express it.

---

## 13. Luxury Rules

What makes this feel expensive is restraint, not embellishment.

1. **Say less.** Every effect, sound, and motion must justify its own existence against this document. If a proposed addition doesn't map to a real state, physical rule, or environmental signal defined above, it doesn't belong.
2. **No gamification, ever.** No streaks, no badges, no progress bars, no completion percentages, no celebratory bursts. JARVIS does not reward the user for using it.
3. **No chrome for chrome's sake.** No decorative particles, no lens flares, no glitter, no "cool effect" that isn't carrying state information.
4. **Confidence over cleverness.** A luxury object doesn't need to prove it's advanced by being busy. The reactor should feel *inevitable* and *calm*, the way a well-made mechanical watch feels calm — complexity is real but never on display for its own sake.
5. **Consistency is the luxury.** The same reactor behaves identically every time a given state occurs. Predictability, not novelty, is what reads as premium craftsmanship over time.
6. **Silence and stillness are features**, not gaps to be filled. Idle and Sleep are allowed to simply exist without justifying themselves with extra motion.

---

## 14. What Must Never Appear

This is a hard, permanent list. Nothing below may ever be implemented, prototyped, or proposed as "just for now."

- No face — no eyes, mouth, eyebrows, or any arrangement of shapes that reads as a face.
- No cartoon or anime-style avatar, mascot, or character.
- No emoji, or emoji-derived iconography, anywhere in the reactor's own visual language.
- No literal oscilloscope line, bar-graph equalizer, or scrolling waveform overlay.
- No generic chatbot conventions: no speech bubble, no "typing…" dots, no default spinner/loading wheel.
- No notification badges, unread-count dots, or progress percentages rendered on or near the reactor.
- No flat, evenly-lit color fills anywhere on the reactor's surfaces.
- No hard cuts or instant state changes (except the Critical-interrupt exception in §4, which still eases visually).
- No skeuomorphic bezels, screws, or fake-hardware framing around the reactor — the reactor itself is the object; it does not need a picture of a device drawn around it.
- No red used for anything other than the reserved Critical palette — red must never appear as decoration, branding, or in any non-critical state.
- No stock/default OS sounds, notification chimes, or UI sound-pack assets.
- No gamification elements of any kind (streaks, XP, levels, celebratory confetti/particle bursts).
- No literal human gestures (nodding, blinking, winking, bouncing) applied to the reactor.
- No fully static, motionless rendering of the reactor in any state, including Sleep.
- No fully dark/zero-brightness rendering of the reactor in any state — JARVIS is never visually "off" while the app is running.
- No divergent state logic or visual personality per device (§11) — one identity, everywhere.

---

## 15. Closing Doctrine

JARVIS is not decorated with a personality — it *has* one, expressed entirely through energy, rhythm, light, and restraint. Every future screen, feature, or device this identity is extended to must be built by asking: *what would this state look like as a reactor doing exactly what a reactor does — containing, radiating, and pulsing energy* — never by asking what icon, animation, or character would be "cool" or "fun" to add.

This document is permanent design/behavior law. Amendments require a deliberate, explicit revision — not incremental drift through individual feature decisions.
