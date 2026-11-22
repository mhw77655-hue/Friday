# Friday visual identity refresh

**Date:** 2026-11-22  
**Scope:** Objective 1 release polish; shared visual foundation for later objectives.

## Direction

Friday's identity is a neural-orbit mark: a fluid F-shaped core surrounded by interlocking orbital paths and synaptic nodes. It represents one continuing individual coordinating multiple replaceable capabilities. The icon is a native Android vector drawable so it remains crisp at launcher sizes and does not add a large raster asset to the APK.

## Canonical semantic palette

| Token | Value | Meaning |
|---|---|---|
| Void / primary background | `#04060B` | Main app background |
| Deep space / panel | `#0A0E1A` | Cards and structured surfaces |
| Primary / idle | `#2E9BFF` | Online, nominal, primary action |
| Listening | `#00D9C0` | Acquiring user input |
| Thinking | `#F5A623` | Analysis, planning, research |
| Building | `#E8ECF2` | Producing output |
| Sleep | `#7C4DFF` | Deep rest / suspended presence |
| Success | `#34D399` | Verified completion only |
| Warning | `#FF3B30` | Warning / attention |
| Critical | `#FF1744` | Emergency |
| Primary text | `#ECEDF1` | Main readable text |
| Secondary text | `#8A94A6` | Supporting text and labels |

Color is semantic, not decoration. Components must not invent a different hue for the same runtime state. State color must not be the only carrier of meaning; pair it with text, iconography or shape.

## Typography

- Inter Regular: body and conversational UI.
- Inter Medium: labels and controls.
- Inter SemiBold: display and headings.
- JetBrains Mono Regular: diagnostics, timestamps, code and actual machine data only.

Material 3 typography is explicitly populated from these bundled fonts. This prevents default Roboto styles or unspecified text colors from silently appearing in components. The primary body scale is 14sp / 20sp; headings and labels follow the existing 4px design scale.

## Contrast and visibility rules

- Main text uses `#ECEDF1`; secondary text uses `#8A94A6` on the dark surfaces.
- Text on saturated accent fills uses a dark foreground, not white by assumption.
- Disabled and offline controls remain distinguishable from missing/hidden controls.
- Never communicate success, warning, listening or thinking through color alone.
- Avoid translucent text surfaces where the final composited contrast cannot be measured.
- New screens must consume the canonical theme/tokens rather than hardcoding divergent state colors.

## Implementation

- `FridayColors` and the root `FridayTheme` now use the same semantic palette as the Visual Foundation.
- Material 3 typography explicitly uses the bundled Inter and JetBrains Mono families.
- Adaptive and legacy launcher icon resources use the new native vector emblem.
- Existing semantic property names are retained where possible to avoid breaking callers.

## Verification required

The Android build must confirm resource and Compose compilation. Visual acceptance must inspect launcher rendering under adaptive masks and verify text/action contrast on the actual device. This change does not claim that every individual screen has already been manually audited on physical hardware.
