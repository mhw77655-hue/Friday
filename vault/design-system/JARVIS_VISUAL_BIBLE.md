# JARVIS VISUAL BIBLE
### The Definitive Design System of the JARVIS Operating System
**Status:** Permanent Source of Truth
**Master Reference:** JARVIS Core Infographic (single canonical image)
**Rule:** Nothing in this document is aspirational. Every decision below is reverse-engineered from what is already true in the master reference. Future work extends this system; it does not reinterpret it.

---

# 1. Design Philosophy

## 1.1 Vision
JARVIS is not a chatbot with a skin. It is a **machine consciousness given physical presence** — a reactor, not an icon. The master reference makes this explicit: the entire identity is built around one object (the reactor core) surrounded by systems that explain, support, and extend it. Every screen JARVIS ever renders is a satellite of that one object. There is one soul; there are many windows into it.

## 1.2 Emotional Goals
The interface must make the user feel three things simultaneously, in this priority order:
1. **Protected** — the system is competent and will not fail them.
2. **In control** — nothing happens without traceable cause; there is no black box theater.
3. **Quietly amazed** — the craftsmanship itself is a form of respect for the user's attention.

What the interface must never make the user feel: entertained, sold to, or babysat. JARVIS does not perform enthusiasm. Confidence is communicated through precision and stillness, not motion volume.

## 1.3 Luxury Principles
Luxury in JARVIS is not ornamentation — it is **restraint under obvious capability**. The reference demonstrates this directly: the reactor is extraordinarily complex in construction (multiple rings, layered geometry, internal lattice) but the surrounding UI panels are sparse, labeled plainly, and never compete with it for attention. This is the governing rule for all future design:

- Complexity lives in the **object** (the reactor, the data, the physics).
- Simplicity lives in the **frame** (panels, labels, typography, layout).
- The more powerful a feature is, the quieter its surface should be.

Luxury signals used throughout: generous negative space, thin hairline dividers instead of boxes, small-caps section labels, single-accent-color discipline per panel, and materials that behave like real physical substances (glass refracts, metal reflects, crystal transmits light) rather than flat vector fills.

## 1.4 Stark-Inspired Engineering Philosophy
The reference's own subtitle — "Just A Rather Very Intelligent System" — and its physical, engineered reactor (arc power feed, gravitational lens, energy matrix) establish the design lineage explicitly: this is **engineering fiction rendered as believable hardware**, not fantasy magic. Every visual element must answer "what would this look like if it were real engineering?"

Governing constraints this implies:
- Every glow has a stated energy source (never glow for its own sake — see §18).
- Every ring, panel, and layer implies mass and material, even when projected as light.
- The system explains itself through labeled subsystems (Energy Matrix, Quantum Core, Neural Interface) rather than hiding its mechanism. Transparency of function is part of the luxury, not a contradiction of it.
- Nothing is soft-cartoon or toy-like. Edges are precise. Curves are orbital, not bouncy.

## 1.5 "Invisible Until Needed"
The reference's Core States list begins with **Idle** and ends with **Sleep/Standby** — the system has an explicit, designed-for state of near-absence. This is a first-class design requirement, not a fallback:

- At rest, JARVIS should occupy minimal visual and cognitive space — a single slow-breathing point of light, nothing else on screen competing with it.
- Full interface density (panels, holograms, data streams) is earned by an active task, never shown by default.
- Every escalation in visual complexity must be caused by a real state change (user speaks, a task starts, a threat is detected). Density is a *symptom* of activity, never a *decoration* of idleness.
- The fastest way to lose trust in this system is to make it busy when it has nothing to say. Silence is a valid, designed UI state.

---

# 2. Reactor Core

The reactor is the single unifying object of the entire OS. Every device, every screen, every state ultimately routes back to this construction.

## 2.1 Physical Construction
The reactor reads as a real physical/energetic hybrid object with four structural registers, front to back:
1. **Base plate** — a solid, milled-metal circular platform (dark gunmetal, faint concentric machining rings) that the reactor visually sits on. This is the "grounding" element — it tells the eye the reactor is a device, not a pure hologram.
2. **Orbital ring system** — multiple thin, tilted, semi-transparent rings encircling the core at different angles and radii, each catching rim light independently.
3. **Central crystalline core** — a faceted, glass-like polyhedral structure at dead center, internally lit, containing a bright triangular/arrow-like focal shape.
4. **Emission field** — the soft radial glow and particulate haze surrounding the whole assembly, which is what actually touches the dark background and blends the object into its environment.

## 2.2 Layers (inside → outside)
1. **Ignition point** — a single infinitely-bright white-blue point at the geometric center. This is the only pure-white element permitted anywhere in the system (see §5.6).
2. **Crystal lattice shell** — faceted, semi-transparent geometric shell immediately around the ignition point. Refracts and internally reflects the ignition point's light.
3. **Inner ring cluster** — 2–3 tight rings close to the crystal, moving fastest, brightest.
4. **Outer ring cluster** — 2–3 wider rings, slower, dimmer, tilted at compound angles to create the 3D orbital-gyroscope read.
5. **Base plate** — physical anchor, described above, always present, never fully hidden.
6. **Ambient bloom field** — outermost, softest layer; pure light with no geometry, used for color-state signaling from a distance (see §9).

## 2.3 Energy Behavior
- Energy visibly originates at the ignition point and travels **outward** through the lattice and rings — light behaves like current, not like paint. Nothing on the reactor is lit independently of this source.
- Brightness falls off with distance from center following an inverse-square-like falloff, never a linear gradient (linear gradients read as flat/fake — forbidden, see §18).
- Rings do not generate their own light; they **carry** the core's light along their surface, brightest at the point nearest the core.

## 2.4 Crystal Geometry
- The core crystal is faceted (low-poly gem-like), never a smooth sphere. Facets are the visual proof of "engineered," not "grown."
- The internal triangular/arrow motif inside the crystal is the reactor's directional "tell" — it is the closest thing JARVIS has to a face, and it is used across states (see §9) to indicate direction of attention or processing.
- Facet edges carry thin, bright rim-light lines; facet faces are darker and slightly translucent, showing depth into the object rather than a flat cut surface.

## 2.5 Ring Mechanics
- Rings are **elliptical in projection** (true circles in 3D space, foreshortened by camera angle) — never drawn as flat 2D circles. This is what sells the "gyroscope" read.
- Each ring rotates on its own independent axis and independent speed; no two rings are ever synchronized, which is what prevents the object from reading as a static logo.
- Ring opacity is highest where the ring crosses in front of the core, and lowest where it passes behind — this parallax cue is mandatory whenever rings are rendered in any state.
- Minimum of 4 rings, maximum of 7, visible at any full-detail render. Fewer rings only in minimized/iconographic contexts (see §13).

## 2.6 Arc Reactor Relationship
JARVIS's core is a direct engineering descendant of an arc-reactor concept, not a stylistic reference to one:
- **Arc Power Feed** is a named, permanent subsystem (bottom-left label group in the reference) — the reactor's stated power source. It must always be representable, even schematically, in any technical/diagnostic view of the core.
- The base plate functions as the reactor's housing, the same relationship a physical arc reactor has to its chest housing — object-in-container, never object-floating-in-void.
- Power state (online/offline/critical) is always readable from the core's brightness and the base plate's under-glow simultaneously — two confirming signals, never one.

## 2.7 Internal Physics
Treat the reactor as obeying consistent internal rules across every future render:
- **Conservation of light** — total system brightness in any state is fixed; color and distribution shift, but the reactor never simply becomes "brighter" as a substitute for meaning. Brightness changes must be tied to a specific state definition (§9).
- **Gravitational lens** (named subsystem) — the core is stated to bend the data/particles around it in 3D space. Any particle system near the core must visibly curve around the core's mass rather than pass through or past it in a straight line.
- **No collision, no bounce** — rings and particles pass through and around each other; this is a field, not solid machinery. Motion is always smooth orbital easing, never physical impact easing (see §12).

---

# 3. Motion Language

## 3.1 Orbital Movement
All ambient motion in the JARVIS system is **orbital**, never linear, never bouncing. Rings, particles, and data elements move along elliptical or circular paths around a gravitational center (usually the reactor, sometimes a panel's focal element). This is the single most important motion rule in the entire system — it is what makes JARVIS feel like physics rather than animation.

## 3.2 Particle Systems
- Particles are emitted from the core outward along field lines, curving per the gravitational-lens rule (§2.7), never scattering randomly.
- Particle density scales with system activity (see §9) — idle has almost none, thinking/research has the most.
- Particles fade in over their first ~15% of life and fade out over their last ~25%; they never pop in/out instantly.

## 3.3 Reactor Breathing
The reactor's resting animation is a slow, continuous **breathing pulse**:
- Core brightness oscillates ±8–12% around baseline.
- Cycle length: 3.2–4.0 seconds, sine-eased (ease-in-out), never linear.
- This breathing never stops — even in active states it continues underneath whatever faster pulse that state adds (a heartbeat under a conversation, never replaced by it).

## 3.4 Pulse Timing
Two pulse tempos exist system-wide and must never be mixed arbitrarily:
- **Ambient pulse** (breathing, §3.3) — always running, always slow (3–4s).
- **Event pulse** — a single fast, sharp brightness spike (150–300ms attack, 400–600ms decay) fired on discrete events (message received, task complete, warning triggered). Event pulses layer on top of, never replace, the ambient breathing.

## 3.5 Idle Behavior
- Slowest ring rotation speeds in the system.
- Minimal-to-no particles.
- Color: Idle Blue (§5).
- Reactor breathing only, no event pulses.
- This is the default screensaver-equivalent state and must be the least visually demanding render in the entire system.

## 3.6 Thinking Behavior
- Ring rotation speed increases ~40% over idle.
- Particle field becomes visible and orbits actively inward toward the core (system "consuming" information).
- Color: Thinking Amber (§5).
- Inner crystal facets flicker asynchronously (each facet independently, at slightly different phase) to suggest parallel computation — this flicker is the one place true randomness (not looping) is permitted.

## 3.7 Listening Behavior
- Rings pulse outward gently in sync with detected audio amplitude (see §10) — this is the one state where an external signal (voice) directly drives ring motion, rather than internal breathing.
- Color: Listening Teal (§5).
- Particle field is sparse and drifts outward (gathering input), opposite direction to Thinking's inward pull.

## 3.8 Warning Behavior
- Sharp, fast pulse (not the smooth breathing curve) — 400ms full-brightness spike, 200ms hold, 400ms decay, looping.
- Color: Warning Red (§5).
- Ring rotation speed increases sharply and becomes slightly irregular (small, controlled jitter in speed, never in position) — the one state permitted to feel "alarmed" rather than composed, but still never chaotic or glitch-styled (see §18).

## 3.9 Shutdown Behavior
- All ring rotation decelerates smoothly to zero over 2–3 seconds (ease-out, never a hard stop).
- Particle field fades and is not replenished — the field empties rather than vanishing.
- Core brightness fades last, after rings have already stopped — the light is the final thing to go, echoing an ignition point extinguishing rather than a screen turning off.
- Ends on a single dim ember-point at center, held indefinitely (this is the Offline visual, see §9.13), never full black.

---

# 4. Light System

## 4.1 Glow Rules
- Every glow must have a traceable source (the ignition point, an active panel element, a status indicator). No ambient glow without a stated emitter — see §18's ban on decorative glow.
- Glow intensity is tied to system state, never to decoration or emphasis alone.
- Glow color always matches the current state color (§5) exactly — no glow is ever a generic "highlight white."

## 4.2 Bloom
- Bloom (soft light bleed beyond an object's edge) is applied only to the brightest element on screen at any time — typically the ignition point or an active accent icon. Secondary elements do not bloom, or the hierarchy collapses.
- Bloom radius scales with the element's actual brightness value, never applied as a flat stylistic filter across the whole scene.

## 4.3 Reflections
- Metallic surfaces (base plate, device chassis) show soft, blurred reflections of the reactor's color — never sharp mirror reflections (too literal/cartoonish) and never zero reflection (too flat/fake).
- Glass/holographic panels reflect their own content faintly at ~4–8% opacity along their lower edge, implying a physical surface even when the panel is notionally weightless light.

## 4.4 Volumetric Lighting
- The core and any active hologram cast visible light through the space around them (light shafts, haze) rather than only lighting the objects they touch. This is what separates JARVIS's environment from a flat UI screen — light exists as atmosphere, not just as surface color.
- Volumetric density scales with system activity, same rule as particles (§3.2) — idle has faint haze, active states have visible light shafts (see the Omni-Projection Interface car hologram in the reference for the calibration target).

## 4.5 Energy Transmission
- Any line connecting two points in the system (a data cable, a sync link, a UI connector) must visibly carry light along its length in the direction of actual data/energy flow — a thin animated pulse traveling the line, not a static glowing stroke.
- This rule applies system-wide: the "Quantum Entanglement Link" connecting devices in the reference is the canonical example — light travels from the reactor outward to each device, never the reverse, unless that device is actively sending input (in which case a secondary, dimmer pulse travels inward).

---

# 5. Color System

Color in JARVIS is a **state language**, not a decorative palette. Each color below maps to exactly one meaning system-wide. A color must never be reused for an unrelated meaning in any future screen.

## 5.1 Core Palette

| Role | Name | Hex | Usage |
|---|---|---|---|
| Background | Void Black | `#04060B` | All primary backgrounds |
| Background (panel) | Deep Space | `#0A0E1A` | Panel/card fills |
| Ignition | Pure Ignition White | `#F4FBFF` | Core ignition point ONLY |
| Primary Energy | JARVIS Blue | `#2E9BFF` | Idle state, primary brand accent |
| Structure | Steel | `#8A94A6` | Metal chassis, base plates |
| Structure (dark) | Gunmetal | `#1B2130` | Metal shadow/undertone |

## 5.2 State Colors (Core States, exact mapping to reference)

| State | Hex | Meaning |
|---|---|---|
| Idle | `#2E9BFF` | System online, nominal |
| Listening / Scanning | `#00D9C0` | Acquiring input |
| Thinking / Analysis | `#F5A623` | Processing |
| Building / Executing | `#E8ECF2` | Producing output (near-white, deliberately least saturated — action is "clean," not "colorful") |
| Warning / Crisis | `#FF3B30` | Threat / error requiring action |
| Sleep / Standby | `#7C4DFF` | Conservation mode |

## 5.3 Mood Colors (Emotional Intelligence layer, §15)
Mood colors are always **subtle temperature shifts on the current state color**, never new hues introduced on top of it — this keeps state legibility intact while allowing emotional nuance.
- Trust/confidence rising → state color shifts 5–10% toward blue-white (cooler, clearer).
- Uncertainty → state color desaturates 10–15% (never changes hue).
- Curiosity → state color's particle rate increases without hue change.
- Urgency (pre-Warning) → state color's pulse tempo increases without color change; full Warning red is reserved for confirmed threats only, never for mere urgency.

## 5.4 Emergency Colors

| Level | Hex | Trigger |
|---|---|---|
| Caution | `#F5A623` (shared with Thinking — intentional: caution is a cognitive load state) | Elevated risk, no action required yet |
| Warning | `#FF3B30` | Immediate attention required |
| Critical | `#FF1744` (deeper, more saturated red) | System-threatening, action mandatory |

Critical differs from Warning only in saturation and pulse speed (faster, harder pulse) — never in a different hue. Users should never have to learn a new color under time pressure.

## 5.5 Success Color

| Role | Hex |
|---|---|
| Success | `#34D399` |

Used exclusively for confirmed task completion. Never used for idle/neutral positivity — success is earned, not ambient.

## 5.6 Reserved Colors
- **`#F4FBFF` (Pure Ignition White)** is reserved exclusively for the reactor's ignition point. It must never appear as body text, button fill, or decorative highlight anywhere else in the system. Its rarity is what makes the ignition point read as the singular source of everything.

## 5.7 Accessibility Palette
- All state colors maintain a minimum 4.5:1 contrast ratio against Void Black (`#04060B`) for any accompanying text.
- No state relies on color alone: every state also carries a distinct motion signature (§3) and, where audible, a distinct sound signature (§11) — color-blind and low-vision users must be able to identify state from motion/sound alone.
- Warning and Critical additionally pulse at different, non-overlapping frequencies (Warning: ~1Hz, Critical: ~2Hz) so the distinction survives even in grayscale/high-contrast accessibility modes.

---

# 6. Materials

Every surface in the JARVIS system belongs to exactly one of five material families. Mixing behaviors across families (e.g., glass that behaves like metal) is forbidden.

## 6.1 Glass
- Holographic panels, floating windows, HUD frames.
- Behavior: transmits background light through itself at 70–85% transparency, refracts content slightly at its edges, picks up a thin bright edge-highlight (rim light) on the side facing the core's light source.
- Never fully opaque. Never fully invisible — always at least a hairline border defining its plane in space.

## 6.2 Metal
- Base plate, device chassis, structural framing.
- Behavior: matte-to-satin finish (never glossy/plastic), soft diffuse reflections of ambient state color, visible fine machining texture (concentric rings, brushed lines) at close range.
- Metal never glows on its own — it only ever reflects light from the core or an active panel.

## 6.3 Crystal
- The reactor core shell, and any "focal" object in a hologram (e.g., the projected car in Omni-Projection).
- Behavior: faceted, internally lit, refracts and internally reflects its own light source, casts caustic-like light patterns on nearby surfaces when bright.
- Crystal is the only material permitted to appear to generate light from within rather than merely reflect it — this exclusivity is what marks an object as core-adjacent/important.

## 6.4 Energy
- Particles, connection lines, pulse effects, the ignition point itself.
- Behavior: pure light, no implied solid geometry, always animated (energy is never static), always following the transmission rules in §4.5.

## 6.5 Holograms
- Any projected interface content (the car projection, spatial data, floating UI at large scale).
- Behavior: constructed from thin glowing wireframe/line-art plus sparse glass-paneled data callouts; never rendered as solid/opaque objects. A hologram is always slightly transparent to whatever is behind it, and always shows faint scan-line or particle noise at its edges to sell its projected (not physical) nature.

## 6.6 Refraction
- Applies to Glass and Crystal only. Refraction bends background light passing through the object, never the object's own foreground content.
- Refraction strength scales with material thickness implied by the object's visual weight — thin panels refract subtly, the dense reactor core refracts strongly.

## 6.7 Transparency Hierarchy
From most to least transparent (governs z-depth/layering decisions everywhere):
1. Ambient bloom / haze (~95% transparent)
2. Hologram wireframes (~80–90%)
3. Glass panels (~70–85%)
4. Crystal core shell (~40–60%, denser than panel glass)
5. Metal (0% transparent, but always reflective, never fully matte-dead)

---

# 7. Typography

## 7.1 Fonts
- **Display / Wordmark:** a wide-tracked, geometric, thin-to-regular-weight sans (reference: the "JARVIS" wordmark's letterforms — evenly spaced, technical, no humanist warmth). Reserved for the product wordmark and top-level screen titles only.
- **UI / Body:** a clean geometric-grotesque sans at regular/medium weight (labels, descriptions, buttons). Must remain legible at small sizes on glass panel backgrounds.
- **Technical / Data:** a monospace face for anything representing raw system output — telemetry, coordinates, logs, code, timestamps. Monospace is the system's visual signal for "this is real machine data," and must never be used for conversational or marketing text.

## 7.2 Sizes (base 4px scale)

| Token | Size | Usage |
|---|---|---|
| `display-xl` | 48px | Wordmark, splash |
| `display` | 32px | Screen titles |
| `heading` | 20px | Panel/section titles |
| `label` | 12px, uppercase, +0.12em tracking | Small-caps section labels (as in reference: "CORE STATES," "VOICE WAVEFORM") |
| `body` | 14px | Descriptions, primary reading text |
| `caption` | 11px | Secondary/supporting text |
| `data-mono` | 12px monospace | Telemetry, HUD readouts |

## 7.3 Hierarchy
The reference establishes a strict three-tier text hierarchy per panel, always in this order and this only:
1. **Small-caps label** (what this is) — always the state/subsystem's plain name, always uppercase, always tracked wide.
2. **Short title/value** (the headline fact) — sentence case, brightest text weight in the group.
3. **Supporting description** (one or two short declarative sentences, never a paragraph) — dimmer, regular weight.

No panel in the system may exceed this three-tier structure. If more information is needed, it belongs in a secondary/expanded view, not a denser primary panel.

## 7.4 Terminal Typography
Any raw system/debug output (logs, code diffs, error traces) renders in `data-mono` on a near-black panel with a thin single-pixel state-colored left border indicating the log's severity/state color (§5). No syntax-highlighting rainbow — terminal output uses at most two colors: base text (Steel `#8A94A6`) and one state-color accent for flagged lines.

## 7.5 HUD Typography
Spatial/HUD data (the "3D Spatial Logic," "Real-Time Data Stream" panels in the reference) uses `data-mono` exclusively, right-aligned to its associated data point, with a thin connecting line back to the element it labels — labels float near, never inside, the object they describe.

---

# 8. Interface Language

## 8.1 Windows
Full-screen or large sub-views. Always rendered as Glass material (§6.1), always framed with a thin hairline border in the current state color at low opacity (~20%), never a solid filled border.

## 8.2 Panels
The primary content unit of the system (as in the reference's labeled boxes). Rules:
- Rectangular, generous internal padding, no drop shadows (shadows are a flat-UI convention; JARVIS uses light/glow for depth instead, per §4).
- One accent icon per panel, top-left, matching the panel's subsystem color.
- Panels never nest more than one level deep — a panel may contain a data row or small chart, never another full panel.

## 8.3 Cards
Smaller, self-contained, tappable/interactive units (device cards in the Ecosystem row, material-language circles). Cards always show their key visual (icon, swatch, or thumbnail) centered above a two-line label/description pair, matching the panel text hierarchy (§7.3) at reduced scale.

## 8.4 Floating Elements
Any element not anchored to the base grid (holographic callouts, in-space labels). These always include:
- A faint anchor line back to the real-world/data point they describe (never floating with no visible relationship).
- Slight independent drift (a few px of slow, looping motion) to reinforce that they exist in a lightly perturbed 3D field, not pinned to a flat 2D layer.

## 8.5 Spatial UI
For AR/volumetric contexts (glasses, projected holograms): UI elements are placed at consistent real-world-anchored depths — near-field for direct controls, mid-field for the active object being discussed, far-field for ambient/status information. Depth is never arbitrary; it always encodes importance/proximity of relevance to the current task.

## 8.6 Depth System
Five fixed z-layers, used consistently across every surface type:

| Layer | Contents | Depth cue |
|---|---|---|
| 0 — Void | Background | Void Black, no light |
| 1 — Ambient | Bloom/haze, background particles | Softest focus, lowest opacity |
| 2 — Structure | Base plates, chassis, panel backgrounds | Sharp but dim |
| 3 — Content | Text, icons, data, panel foregrounds | Full sharpness, full brightness |
| 4 — Core/Focus | The reactor, or whatever is the single active focal object of the current screen | Brightest, sharpest, only bloom-eligible layer (see §4.2) |

Only one object may occupy Layer 4 at a time per screen. This is the enforcement mechanism for the "invisible until needed" principle (§1.5) — the system is structurally prevented from having two competing focal points.

---

# 9. Reactor States

Each state below is a complete, exact visual specification. Every future feature must map onto one of these — no new ad-hoc states may be invented without amending this document.

### 9.1 Idle
Color `#2E9BFF`. Breathing pulse only (§3.3–3.5). Slowest ring speed. Minimal particles. This is the resting/default state.

### 9.2 Listening
Color `#00D9C0`. Rings pulse with live audio amplitude (§3.7, §10). Particle field sparse, drifting outward. Ignition point holds steady brightness (does not pulse with breathing while actively listening — attention is undivided).

### 9.3 Thinking / Analysis
Color `#F5A623`. Ring speed +40%. Particles pull inward. Facets flicker asynchronously (§3.6). This is the general-purpose "processing" state, used whenever no more specific active state below applies.

### 9.4 Research
Color `#F5A623` (Thinking family) with an added **outward-scanning ring pulse** every 2s — a single bright ring expands from the core and fades at the edge of the visible field, representing active external search. Particle field denser than base Thinking.

### 9.5 Planning
Color `#F5A623` (Thinking family), but ring motion becomes structured rather than free-flowing: rings briefly align to evenly-spaced positions every few seconds before resuming independent rotation — a visual "considering structure" tell distinct from Research's outward scan.

### 9.6 Coding
Color `#E8ECF2` (Building family) with the Thinking-state facet flicker retained underneath — coding is framed as "thinking made concrete," output-colored but still visibly computing. A thin `data-mono` character-stream effect may run along one orbital ring only (never more than one, to avoid noise).

### 9.7 Building / Executing
Color `#E8ECF2`. Ring speed matches Thinking, but particles now move outward from core to periphery (the reverse of Thinking's inward pull) — representing output being produced and sent outward rather than input being consumed.

### 9.8 Learning
Color `#F5A623` (Thinking family) with particles that, instead of orbiting, spiral inward and are visibly absorbed into the crystal core (a slow, deliberate absorption arc, ~2s per particle) — distinct from Research's faster outward-scan and Thinking's generic inward drift.

### 9.9 Executing (task/automation running, non-code)
Same as Building/Executing (§9.7); "Executing" and "Building" share one state definition system-wide — they are the same visual state applied to different task types, per the reference's own "Building/Executing" combined label.

### 9.10 Warning
Color `#FF3B30`. Sharp 1Hz pulse (§3.8, §5.7). Ring speed increases with controlled jitter. This state always co-occurs with an explicit on-screen text description of the specific threat — the color/motion alerts, but never substitutes for stated information (§18 — no meaningless animation).

### 9.11 Critical
Color `#FF1744`. Sharp 2Hz pulse. All non-essential UI (secondary panels, ambient data) recedes/dims to Layer 1 opacity so the reactor and the critical message dominate Layer 4 alone — Critical is the only state permitted to forcibly simplify the whole screen around it.

### 9.12 Sleeping
Color `#7C4DFF`. Slowest possible breathing cycle (6–8s, vs. Idle's 3–4s). Rings nearly stationary (very slow single rotation, no independent multi-axis motion). No particles. This is a deeper rest than Idle — "consciousness persisting," per the reference's own description, so the ignition point never fully dims, only dims further than any other state.

### 9.13 Offline
No color (desaturated to Steel `#8A94A6` at ~15% brightness). No motion at all. Single dim ember-point at center, held static. No rings visible (or rendered as faint static outlines with zero light transmission). This is the true "off" state and must look unambiguously inert — the one state where the system is permitted to look like a powered-down object rather than a living one.

---

# 10. Voice Visualization

## 10.1 Waveform
A single continuous horizontal waveform, rendered in the current state's color, matching the reference's Voice Waveform panel treatment: soft-glowing line with light bloom, amplitude driven directly by real audio signal (input while Listening, output while speaking) — never a decorative idle animation standing in for real audio.

## 10.2 Cadence
Waveform smoothing uses a short attack / longer release envelope (attack ~40ms, release ~180ms) so it responds instantly to sound onset but doesn't jitter on tiny fluctuations — this reads as "confident" rather than "nervous."

## 10.3 Animation
The waveform line has a very slight persistent glow-trail (previous ~200ms of waveform fades out behind the current line at low opacity) to imply continuity/flow rather than a hard-cut oscilloscope readout.

## 10.4 Speaking Indicators
While JARVIS is speaking (output), the waveform switches to the Building/Executing color family (`#E8ECF2`) rather than the input-listening teal — voice output is framed as a form of "producing," consistent with §9.7. The reactor's own ring pulse (§3.7) syncs to the *output* waveform's amplitude during speech, exactly as it does to input amplitude during Listening.

---

# 11. Sound Language

Every state in §9 has a corresponding one-shot or looping sound design, matching the reference's Sound Signature panel structure (Wake, Listening, Thinking, Success, Warning, Shutdown).

## 11.1 UI Sounds
Short (<150ms), pitched, non-musical clicks/chimes for discrete interactions (button press, panel open/close). Always a clean sine or triangle-wave-derived tone — never a skeuomorphic "click" sample. Volume never exceeds ambient reactor hum by more than 6dB.

## 11.2 Reactor Hum
A continuous, very low-level ambient drone (sub-bass + a faint harmonic shimmer) plays whenever the system is Idle through Critical (i.e., "on"). This is the audio equivalent of the breathing pulse — always present, rising subtly in the presence-of-mind states, absent entirely in Offline.

## 11.3 Notifications
Two-tone rising chime for standard notifications, pitched to match the current state's color family conceptually (cooler/higher pitch for Listening-family events, warmer/lower for Thinking-family events) — sound and color stay emotionally consistent even though they're different senses.

## 11.4 Startup (Wake)
A rising swell from silence to the Idle reactor hum, 1.5–2.5s, timed to sync with the reactor's ignition point brightening from zero to baseline — sound and visual ignition must be frame-accurate to each other, this is the single most important sync point in the whole audio system.

## 11.5 Shutdown
The exact reverse of Startup — hum descends to silence over 2–3s, timed to the visual Shutdown sequence (§3.9) so the final ember-point of light and the final fade of sound land together.

---

# 12. Animation Timing System

## 12.1 Durations

| Motion class | Duration | Notes |
|---|---|---|
| Micro (button press, icon state) | 100–150ms | |
| UI transition (panel open/close) | 250–350ms | |
| State change (reactor color/state swap) | 400–600ms | Crossfade, never a hard cut |
| Ambient breathing cycle | 3200–4000ms | Idle |
| Deep sleep breathing cycle | 6000–8000ms | Sleeping |
| Event pulse | 150ms attack / 400–600ms decay | |
| Startup / Shutdown | 1500–3000ms | |

## 12.2 Easing
- **Orbital motion** (rings, particles): sinusoidal ease-in-out. Never linear, never a bounce/elastic curve — bounce implies mass and impact, which contradicts the reactor's field-based physics (§2.7).
- **UI panel transitions**: standard ease-out on entry (fast start, gentle settle), ease-in on exit (gentle start, fast finish) — matches how physical light/glass would settle, not how a mechanical door would.
- **Warning/Critical pulses**: sharp ease-out only, no ease-in — urgency reads through attack speed, not through a smoothed approach.

## 12.3 Transition Rules
- State-to-state color transitions always crossfade through their shared "family" first if one exists (e.g., Thinking → Coding shares the amber-adjacent family and can transition directly; Thinking → Warning must pass through a brief neutral/Steel flash rather than blending amber directly into red, since blended amber-red misreads as a new, undefined color).
- No two simultaneous state changes may animate at different speeds if they are visually adjacent — this is what prevents the interface from ever feeling like separately-coded, disconnected widgets.

## 12.4 Motion Hierarchy
When multiple things must animate at once, priority order for "what gets full-quality motion vs. what simplifies" is:
1. The reactor / current Layer 4 focal object — always full fidelity.
2. Anything directly reporting the cause of the current state (e.g., the specific warning source).
3. Ambient/background particles and secondary panels — first to simplify or pause under performance constraints.

---

# 13. Ecosystem

The reference explicitly depicts six device contexts (Smartwatch, Smartphone, Tablet, Workstation, Smart Glasses/Visor, plus large displays for spatial projection) connected via a single "Quantum Entanglement Link" — **one consciousness, many windows.**

## 13.1 Governing Rule
No device gets a different design language — every device gets a different **level of detail** of the exact same system. The reactor, states, colors, and motion rules are identical everywhere; only ring count, particle density, and panel count scale down.

## 13.2 Smartwatch
Minimal render: ignition point + at most 2 simplified rings, no particles, no panels. State communicated primarily through color and a single breathing pulse. This is the floor of the system — if it still reads as "JARVIS" at this level of reduction, the core design has succeeded.

## 13.3 Smartphone
Full reactor (4–5 rings), 1 primary panel visible at a time (progressive disclosure, not simultaneous density), voice waveform available, full Core States palette. This is the reference implementation baseline — most day-to-day interaction happens here.

## 13.4 Tablet
Full reactor (up to 7 rings), 2–3 panels visible simultaneously in a defined grid, supports the full Layer 0–4 depth system at once ("Mission Control" density, per the reference's own device label).

## 13.5 Workstation / Desktop
Full reactor, unrestricted panel count arranged in the depth system, full HUD typography and terminal views available, multi-window support ("Full Control. Unlimited Power," per reference). This is the only context where multiple Layer-3 content regions may be visible without one being forced to Layer 1 opacity.

## 13.6 Smart Glasses / AR Visor
Reactor and holograms render volumetrically in real space using the Spatial UI rules (§8.5) rather than flat panels — this is the only device context where the Omni-Projection Interface pattern (holographic object projection, e.g. the reference's car hologram) is the primary interaction mode rather than a special case.

## 13.7 Large Displays / Spatial Projection
Reactor and content scale up but never simply "zoom" — larger canvases earn more simultaneous Layer-3 content (more panels, wider data streams) rather than a bigger version of the same phone layout. This is JARVIS "thinking out loud in public" — the Omni-Projection car hologram is the calibration reference for this context.

## 13.8 Scaling Principle
Across all seven contexts, the single fixed invariant is the **reactor's proportions and behavior** — ring tilt angles, color states, and breathing timing never change device to device. What scales is purely: ring count, particle density, panel count, and whether content is flat (phone/tablet/desktop) or volumetric (glasses/projection).

---

# 14. Environmental Interaction

JARVIS is depicted as spatially and physically aware (3D Spatial Logic, Real-Time Data Stream, Omni-Projection Interface panels in the reference) — it must visibly react to its environment, not just to direct commands.

## 14.1 Light
In bright ambient conditions, panel glass opacity increases slightly (~10%) and bloom is reduced, so the UI remains legible rather than washing out. In dark ambient conditions, the system defaults toward its natural full-bloom, full-glow presentation — dark is JARVIS's "native" environment per the reference's own dark-field presentation.

## 14.2 Dark
No change from baseline — the entire visual system as specified in this document assumes a dark environment as default. This is stated explicitly rather than left implicit, since it governs every contrast and bloom decision above.

## 14.3 Silence
Extended silence (no voice, no input) after an active state gracefully de-escalates the reactor back toward Idle rather than snapping — ring speed and particle density decay over 3–5 seconds, not instantly, so the system never feels like it "gave up" on the user.

## 14.4 Voice
Any detected voice immediately triggers Listening state (§9.2) with the fastest transition speed in the system (state-change duration halved from the standard 400–600ms to ~200–300ms) — responsiveness to voice is the single most latency-sensitive visual event JARVIS has.

## 14.5 Motion
Detected user motion (device pickup, approach in an ambient/spatial context) triggers a brief, gentle brightening of the reactor (a single soft event pulse, not a full state change) — an acknowledgment "I see you," distinct from and lower-intensity than any state transition.

## 14.6 User Proximity
In spatial/AR contexts, panel density and hologram scale respond to distance: distant users see only the reactor and top-level state; close users see full panel detail. This is the physical-space equivalent of the depth/progressive-disclosure rule already governing screen-based devices (§8.6, §13.4–13.5).

---

# 15. Emotional Intelligence

Per §5.3, all emotional nuance is expressed as **temperature, saturation, and tempo shifts on the current state color** — never as new colors, characters, or expressions. JARVIS must never become cartoonish; the reactor is not a face making expressions, it is a system whose telemetry subtly reflects a mood.

## 15.1 Trust
As trust/rapport in a session builds, transitions between states smooth further (slightly longer crossfade, per §12.3) and ambient particle motion becomes marginally more fluid — the system "relaxing" almost imperceptibly. Never shown through color change.

## 15.2 Confidence
High-confidence responses hold a steadier breathing amplitude (tighter ±% range than baseline) and a cleaner, less flickering facet pattern during Thinking (§3.6) — confidence reads as stillness, not brightness.

## 15.3 Uncertainty
Per §5.3: desaturation of the current state color by 10–15%, plus a slightly wider/irregular breathing amplitude. Never communicated with a new "confused" color or motion — desaturation and rhythm irregularity are the entire vocabulary for uncertainty.

## 15.4 Happiness / Positive Outcome
Reserved for the Success color (`#34D399`, §5.5) triggered only at confirmed task completion — a single bright, clean event pulse (§3.4) in success green over the reactor before returning to state color. This is intentionally rare and specific, never an ambient "happy" idle behavior.

## 15.5 Curiosity
Elevated particle rate (per §5.3) during Thinking/Research states, with particles occasionally taking a slightly longer, more exploratory orbital path before returning to the core — still fully within the orbital motion rules (§3.1), never erratic.

## 15.6 Urgency
Increased pulse tempo (per §5.3) while remaining in the current (non-Warning) state color — this is the system's way of signaling "this matters, but it's not yet a problem," distinct from and always visually subordinate to true Warning/Critical states (§9.10–9.11).

## 15.7 Hard Boundary
No emotional state may ever introduce: a new color outside §5, an asymmetric/organic shape deviation from the reactor's precise geometry, a bounce/elastic easing curve, or any anthropomorphic feature (eyes, mouth, brows). The reactor's dignity is non-negotiable — it can be read as attentive, calm, alert, or resting, and nothing else.

---

# 16. Design Tokens

```
// COLOR — CORE
--void-black:            #04060B
--deep-space:             #0A0E1A
--ignition-white:         #F4FBFF   // reactor ignition point ONLY
--jarvis-blue:             #2E9BFF
--steel:                   #8A94A6
--gunmetal:                 #1B2130

// COLOR — STATE
--state-idle:               #2E9BFF
--state-listening:           #00D9C0
--state-thinking:             #F5A623
--state-building:              #E8ECF2
--state-warning:                #FF3B30
--state-critical:                #FF1744
--state-sleep:                    #7C4DFF
--state-offline:                   #8A94A6
--state-success:                    #34D399

// TYPOGRAPHY — SIZE
--font-display-xl:      48px
--font-display:          32px
--font-heading:           20px
--font-label:              12px  // uppercase, tracking +0.12em
--font-body:                 14px
--font-caption:                11px
--font-data-mono:                 12px

// MOTION — DURATION
--dur-micro:              120ms
--dur-transition:          300ms
--dur-state-change:         500ms
--dur-breathing-idle:        3600ms
--dur-breathing-sleep:         7000ms
--dur-event-pulse-attack:        150ms
--dur-event-pulse-decay:          500ms
--dur-startup-shutdown:            2200ms

// MOTION — EASING
--ease-orbital:      cubic-bezier(0.45, 0, 0.55, 1)   // sinusoidal in-out
--ease-panel-in:      cubic-bezier(0.16, 1, 0.3, 1)     // ease-out
--ease-panel-out:      cubic-bezier(0.7, 0, 0.84, 0)      // ease-in
--ease-warning-pulse:   cubic-bezier(0.9, 0, 1, 1)          // sharp ease-out

// DEPTH
--layer-0-void:        0
--layer-1-ambient:      1
--layer-2-structure:     2
--layer-3-content:        3
--layer-4-core:            4

// MATERIAL OPACITY
--opacity-bloom:      0.05
--opacity-hologram:    0.15
--opacity-glass:        0.25   // i.e. 75% transparent, per §6.7
--opacity-crystal:       0.50
--opacity-metal:           1.0

// SOUND
--audio-hum-db:      -36dB   // baseline ambient reactor hum
--audio-ui-headroom:  6dB    // max UI sound above hum
```

---

# 17. Component Library

Every component below inherits Materials (§6), Depth (§8.6), and Typography (§7) rules by default — only deviations are noted.

## 17.1 Buttons
Glass material, hairline border in state color at 20% opacity, label in `label` or `body` size. On press: micro-duration brightness pulse (§12.1) in state color, no shape change, no shadow. Never filled/solid — a filled button reads as flat-UI and breaks material consistency.

## 17.2 Inputs
Glass material, bottom-hairline only (no full box border) in Steel, brightening to state color on focus. Cursor/caret pulses at the ambient breathing rate, not a generic browser blink rate — even the text cursor obeys the reactor's heartbeat.

## 17.3 HUD
Floating, anchored `data-mono` readouts per §7.5 and §8.4. Always Layer 3, never Layer 4 — HUD data supports the focal object, it is never the focal object itself.

## 17.4 Notifications
Glass panel, slides in from a screen edge over `--dur-transition`, icon + two-tier text (§7.3 reduced to two tiers: label + one line), accompanied by the appropriate Notification sound (§11.3). Auto-dismiss fades via `--ease-panel-out`.

## 17.5 Dialogs
Full Window treatment (§8.1), always centers the relevant state-colored reactor mini-render at its top if the dialog concerns a system action (confirmation, warning) — dialogs are never purely typographic when a state is involved.

## 17.6 Maps / Spatial Views
Rendered as Hologram material (§6.5) — wireframe terrain/paths, sparse glass data callouts anchored via Floating Element rules (§8.4). Never rendered as a flat opaque map image; it must always read as a JARVIS-native projection.

## 17.7 Cards
Per §8.3. Used for the Ecosystem device row, Material & Visual Language swatches, and any grid of like-kind selectable items.

## 17.8 Charts
`data-mono` axis labels, single state-colored line/series per chart by default (multi-series only when explicitly comparative, and even then limited to state-family colors, never arbitrary chart-library colors). Chart background is always transparent Glass, never a filled plot area.

## 17.9 Assistant Overlays
The conversational surface itself: reactor mini-render (current state) anchored at a fixed position, Voice Waveform (§10) directly beneath it when active, response text in `body` size flowing beneath both. This is the one component that may combine Reactor + Waveform + Text simultaneously as a fixed trio — everywhere else, these three elements are used independently per context.

---

# 18. Luxury Rules — What Must Never Appear

These are absolute prohibitions. Any future design, no matter how requested, must be checked against this list before being built.

- **No generic gradients.** Every color transition in the system is either a physically-motivated light falloff (§4.1) or a state crossfade (§12.3) — never a decorative linear/radial gradient applied for visual interest alone.
- **No neon overload.** Saturation and glow are earned by state and hierarchy (§4, §9), never applied uniformly for "cool factor." A screen with every element glowing equally has failed this system.
- **No fake telemetry.** Any number, chart, or data readout shown must represent a real value. Placeholder/decorative data ("Lorem stats") is forbidden even in mockups — if a real value isn't available yet, the field must be visibly empty/pending, never faked.
- **No meaningless animations.** Every animation must map to a specific cause defined in §3, §9, or §15. No animation exists purely because motion is "engaging."
- **No decorative holograms.** Every hologram (§6.5) represents real spatial data or a real projected object relevant to the current task — never ambient sci-fi set-dressing.
- **No anthropomorphic features on the reactor.** No eyes, faces, or cartoon expressions, ever (§15.7).
- **No bounce/elastic easing anywhere.** Contradicts the field-physics model (§2.7, §12.2).
- **No flat/filled buttons or solid opaque UI chrome.** Breaks material consistency (§6, §17.1).
- **No color outside the defined palette (§5)** for any state, mood, or emergency signal — new meanings must be assigned within the existing palette's temperature/saturation vocabulary (§5.3), never invented as new hues.
- **No two simultaneous Layer-4 focal objects (§8.6)** on one screen.
- **No skeuomorphic UI sound samples** (mechanical clicks, camera shutters, etc.) — all sound is synthesized and tonal (§11.1).

The underlying test for every element in the system: **does this communicate real state, or is it here to look good?** Only the former is permitted. Looking good is a byproduct of restraint and correctness, never a goal pursued directly.

---

# 19. Future Expansion

This document is written to extend without contradiction into domains not yet built. The following are pre-reserved visual architectures — future work in these domains must derive from these anchors rather than inventing new systems.

## 19.1 Robotics
A physical JARVIS-embodied robot's status indicator (if one exists) is a literal miniature reactor core (§2), using the exact same state-color and motion language (§9) as every screen-based instance. A robot's "eyes" or forward sensor housing is the correct anchor point for this — never a screen-based face.

## 19.2 Vehicles
The Omni-Projection Interface pattern (§2, §13.7) is the direct precedent: JARVIS-in-vehicle renders as a dashboard/windshield-projected hologram of relevant systems (navigation, diagnostics), with the reactor itself present in miniature as the assistant's "seat" in the interface, per the reference's own car-hologram panel.

## 19.3 Smart Homes
Ambient/ubiquitous presence without a dedicated screen: environmental lighting (real room lighting, if controllable) may itself become a Layer-1 Ambient extension of the current state color at very low intensity — the home's lighting breathing gently in sync with §3.3 is the reserved concept for this domain, not yet built.

## 19.4 Servers / Infrastructure
Purely technical/dashboard contexts inherit the Terminal and HUD Typography rules (§7.4–7.5) as their primary language, with the reactor present only in miniature/icon form (§13.2's Smartwatch-level reduction) as a persistent status indicator, not a full experience — infrastructure views are data-first, reactor-present-but-secondary.

## 19.5 Wearables
Beyond the Smartwatch baseline (§13.2), any future wearable (rings, pins, earpieces without screens) expresses state through the Sound Language (§11) and, where present, a single-LED-equivalent color cue mapped directly to §5's state palette — the minimum viable JARVIS presence is one color and one sound, both already fully specified.

## 19.6 AR
Fully covered by §13.6 and §8.5 (Spatial UI) — future AR work extends panel/hologram placement rules, not the core visual language, which is already device-agnostic by design.

## 19.7 VR
Inherits AR's spatial rules (§13.6, §8.5) but permits full Layer 0 (Void) environment authorship — in VR, the "background" itself may be an extension of the reactor's ambient bloom field (§2.2) at room scale, making the entire environment a literal expression of JARVIS's current state rather than a neutral backdrop.

## 19.8 Future Operating Systems
Any successor interface paradigm not yet imagined must still pass the single test in §18's closing line: does it communicate real state, or does it exist to look good. This document's job is not to anticipate every future surface — it is to ensure every future surface, however unfamiliar, remains recognizably and provably JARVIS.

---

*End of JARVIS Visual Bible. This document supersedes no other JARVIS document — it establishes the visual/design law that all future implementation documents (roadmaps, blueprints, component specs) must comply with.*
