# Friday — Hybrid AMOLED Presence

**Status:** active implementation specification  
**Direction:** hybrid organic/crystalline intelligence; spatially integrated with the interface  
**Owner decision:** hybrid selected; no further visual preference decision is required.

## Product intent

Friday's presence is an original, non-anthropomorphic living topology. Preserve its asymmetric filaments, branching relationships, nodes and identity-derived morphology. Evolve it into a premium volumetric presence that feels materially embedded in a black AMOLED environment. Do not replace it with a human avatar, stock orb, particle wallpaper or generic sci-fi HUD.

The target is not "more glow". It is a coherent material and spatial system: a deep black stage, a translucent/faceted inner structure, controlled specular highlights, layered depth, subtle orbital geometry, restrained luminous filaments, meaningful motion, precise typography and quiet controls.

## Current source truth

- Home is Jetpack Compose and uses `FridayAvatar` at 280dp.
- `FridayAvatar` is procedural Compose Canvas geometry. It is 2D drawing with motion and lighting cues; it is **not actual 3D geometry or PBR rendering**.
- `GlassPanel` is a tinted gradient and border, not a refractive backdrop material.
- The app currently has Compose/Material 3 and native CMake code. No Filament renderer integration is established by this source audit.
- Existing AMOLED semantic colors and typography are a useful foundation and should be refined rather than discarded.

## Rendering architecture

### Stage A — immediate, low-risk polish

Use the existing Compose renderer to improve depth cues with sparse radial fields, broken elliptical contours, controlled specular accents and better material hierarchy. Avoid full-screen blur, excessive translucent layers and continuous high-cost effects. Keep the black substrate genuinely black.

This stage is a visual bridge, not a claim that Friday is now rendered in 3D.

### Stage B — true 3D presence

Prototype a single lifecycle-bound Google Filament renderer behind a narrow Compose-facing interface. The presence model should be an authored, asymmetric procedural mesh or compact glTF asset with physically based materials. The scene should use a small number of carefully authored lights, image-based lighting where appropriate, restrained bloom and physically plausible translucent/faceted surfaces. The 3D form must retain Friday's existing topology and morphology seed rather than becoming a stock sphere.

Do not add Filament to production until a prototype proves:
- APK/native library size and ABI impact are acceptable;
- renderer creation/destruction is lifecycle-safe and never leaks a surface or render loop;
- off-screen/background state pauses rendering;
- thermal and battery pressure lower quality deterministically;
- a non-Filament fallback remains visually coherent on unsupported or constrained devices;
- frame pacing, GPU/CPU time, memory and thermal behaviour are measured against the current build on the owner's actual phone.

### Stage C — one spatial interface

Make the presence and interface feel like one designed environment, not a 3D widget floating above ordinary cards. Define a small set of reusable material primitives and layout rules, then apply them consistently to Home, Conversation, Tools, Settings and System. Backdrop glass must be source-backed and used sparingly; opaque/near-black surfaces remain the default for legibility and efficiency.

## Adaptive quality contract

Quality is a runtime budget, not a fixed maximum:
- Foreground and interaction: full supported quality, bounded frame rate.
- Foreground but idle: reduced update frequency and subtle motion.
- App not visible: stop the render loop and release transient GPU work.
- Battery saver, elevated thermal state or resource pressure: reduce resolution, effects, frame rate and scene complexity in that order.
- Unsupported renderer or failed initialization: use the Compose presence without breaking navigation or interaction.

No claim of zero battery/GPU impact is permitted. The target is measured, bounded overhead with graceful degradation.

## Acceptance gates

1. Source review: no generic avatar/orb substitution; existing identity seed and acoustic modulation remain intact.
2. Build: exact-head Android CI passes, including JVM shards, instrumentation and APK assembly.
3. Visual review: screenshots at multiple display sizes; black-level, contrast, typography, clipping and motion review.
4. Performance: compare baseline and candidate cold start, frame time/jank, CPU/GPU, memory, battery drain and thermal state.
5. Device validation: physical handset evidence required before calling adaptive rendering or premium 3D complete.
6. Release: signed artifact, signer/package/version checks, rollback path and incremental delivery strategy.

## Current implementation slice

The current branch adds a restrained depth field, broken orbital contours and specular accents to the existing topology using existing Compose drawing primitives only. It adds no graphics dependency and makes no claim of true 3D. The next slice is a measured Filament feasibility prototype and a coherent spatial Home/Conversation material system.
