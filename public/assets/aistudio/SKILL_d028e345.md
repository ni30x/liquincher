---
name: liquid-glass-android
description: Build Apple-style liquid glass (blur, light-bending lens refraction, colour fringing, glowing rim, soft shadow, springy motion) anywhere in a native Android app made with Kotlin and Jetpack Compose. Covers nav bars, top bars, floating buttons, cards, chips, search bars, banners, sliders, overlays and sheets, not just a music player. Use when the user asks for liquid glass, glassmorphism, frosted glass, iOS 26 glass, floating glass bars, or "make it look like BitChord". Android Compose only.
---

# Liquid Glass for Android (Jetpack Compose), modelled on BitChord

## 0. Read this first

This skill is the distilled recipe from the open source Android music player BitChord (github.com/kushagrasinghx/BitChord). It lets you put real liquid glass on ANY part of an Android Compose app, not only a player.

The user is not a programmer. Therefore:

- Do all the work yourself and explain results in plain words.
- Before every BIG step, say in one or two plain sentences what you are about to do and why, then wait for the user's OK. Big steps are: adding a library, editing the app's root screen, converting many screens at once, deleting or replacing existing UI code. Small steps (one button, one card) can proceed after you announce them.
- Never ask the user to edit files by hand.
- Do not write long explanations of code. Show the result, say what changed, say how to turn it off.

Token-saving rules:

- Do NOT clone or scan the whole BitChord repository. It also contains desktop, TV, watch, web and backend code that is irrelevant.
- Read only the files listed in section 3, in the order given, and stop once you understand the recipe.
- `MainActivity.kt` in BitChord is huge. Read only the two small regions named in section 3.
- This skill already contains the numbers and the gotchas. You usually do not need to open BitChord at all.

## 1. What liquid glass actually is (mental model)

Liquid glass is a surface that shows a blurred, bent, tinted copy of whatever is drawn behind it, with a bright thin rim and a soft shadow. For that to work you need three things:

1. A BACKDROP LAYER: a recording of the app's content that glass can look at. It is created once near the top of the app and shared to every screen.
2. RECORDED CONTENT: the page content (lists, images, gradients) is marked so it draws into that recording.
3. GLASS SURFACES: UI elements (bars, buttons, cards) that sit ON TOP of the recorded content, as siblings, and sample the recording behind themselves.

The effect chain on each glass surface is, in order: colour boost, blur, lens refraction (with optional depth effect and chromatic aberration), a theme-coloured tint, then the rim highlight and the shadow.

Rule that prevents most bugs: a glass surface must never be inside the content that is recorded for it. It must be drawn after (above) that content. A surface that samples itself breaks.

## 2. Pre-flight checks (do these before touching anything)

1. Confirm the project is native Android using Jetpack Compose. If it uses XML layouts, tell the user and offer to embed Compose views for the glass parts. If it is not Android, stop and say so.
2. Find the minimum and compile SDK. BitChord uses minSdk 26 and compileSdk 36 and checks device capability at runtime, so a low minSdk is fine.
3. Note the Compose and Kotlin versions. The engine below needs a recent Compose.
4. Look at what the app already draws behind its bars. Glass over a flat single-colour background shows almost nothing (blur and lens of one colour look like plain tint). Tell the user honestly that glass needs colourful, varied or moving content behind it (photos, album art, gradients, lists with images).
5. Tell the user which Android versions get which quality (section 8) before building.

## 3. Where things live in BitChord (only if you need to look)

Paths are inside `app/src/main/java/com/music/bitchord/`. Pinned commit for raw files: edc1e6f36febf71a118e649411d2fc21b70ead45.

1. `ui/components/LiquidGlass.kt` — READ FIRST. One page that holds the whole recipe: constants, the three glass helpers, fallbacks, colour rules.
2. `ui/components/backdrop/` — the engine. Key files:
   - `DrawBackdropModifier.kt`: the main modifier that records, effects and draws the glass. Its options are listed in section 6.
   - `effects/Lens.kt`: the refraction shader. Works from a rounded-rectangle distance field, so it only accepts rounded-corner shapes.
   - `effects/Blur.kt`, `effects/ColorFilter.kt`: blur and saturation.
   - `highlight/Highlight.kt`: the rim light (styles Default, Ambient, Plain).
   - `shadow/Shadow.kt`: drop shadow, plus `InnerShadow.kt`.
   - `backdrops/LayerBackdrop.kt`: the shared recorded layer.
   - `Platform.kt`: the API-level checks.
3. `MainActivity.kt` — read ONLY: around lines 380 to 405 (where the shared layer is created, with an opaque background painted first, and provided to the whole app) and around lines 2670 to 2690 (where the page content is marked as recorded content, only when glass is active). Do not read the rest.
4. `ui/components/floatingtabbar/GlassSelectionPill.kt` — optional, the springy moving selection pill (section 9).
5. `ui/components/floatingtabbar/FloatingTabBar.kt` — optional and very large (about 54 KB). Skip unless the user wants a full floating tab bar.
6. `ui/components/TopFadeBlur.kt` — optional. A progressive blur band under a top bar that fades to nothing. It uses the Haze library, a different technique.

The engine came from the open source library Kyant0/backdrop version 2.0.0 (Maven name io.github.kyant0:backdrop, Apache-2.0). BitChord copied it as source because prebuilt versions broke at runtime against its Compose version, and added a resolution-scale speed trick.

## 4. One-time setup (BIG step: announce it and wait for OK)

Do these in order and verify the project still builds after each.

1. Add the engine.
   - First choice: add the upstream library io.github.kyant0:backdrop as a normal dependency. Look up its newest published version first, do not assume.
   - If the app then crashes or fails with method-not-found style errors (this happened to BitChord), switch to vendoring: copy the library's source files from the upstream Kyant0/backdrop repository into the project under its own package, keeping the Apache-2.0 header on each file. Copy from upstream, not from BitChord.
2. Create the shared backdrop layer once, at the very top of the app UI, inside the app theme (so the theme background colour is readable).
   - Before the content is drawn into the layer, paint the app's solid background colour first. BitChord learned this the hard way: a transparent recording gives the glass nothing to blur, so sharp text shows straight through the bar.
3. Provide that layer to the whole app through a composition local (BitChord calls it LocalAppBackdrop). Also provide an on/off flag (BitChord: LocalLiquidGlassEnabled).
4. Mark the page content that sits behind glass as recorded content, once, around the page area. Mark only when glass is on, supported, and the user has not chosen reduce blur. Recording a whole page for nobody is wasted cost.
5. Add a user setting "Liquid glass" (on or off) and a "Reduce blur" setting. Keep them in the app's existing settings. Default: glass on.
6. Build the helper kit in section 5 in one file, so every screen uses the same helpers.

## 5. The helper kit to build once (named after BitChord, reuse anywhere)

Create small reusable helpers instead of repeating effect settings on every screen:

- isGlassSupported: true on Android 12 (API 31) and newer.
- liquidGlass (modifier, takes a rounded-corner shape): the FULL effect. Samples the shared layer. If unsupported, return the element unchanged so the caller can fall back. If reduce blur is on, fill with the theme's surface colour plus the hairline edge instead.
- lightweightLiquidGlass (modifier, takes a shape and a fallback colour): NO capture, NO blur, NO lens. Draws the translucent tint, the directional rim and the hairline. When glass is off or unsupported it draws the fallback colour instead, so the control keeps its original look.
- liquidGlassRim (modifier, takes a shape): ONLY the rim highlight, for controls that keep their own fill. Costs about one stroke. Does nothing when glass is off.
- glassContentColor: black in light theme, white in dark theme, decided by the theme surface brightness. Use for icons and text on glass.
- glassIndicatorColor: the opposite (white in light, black in dark). Use for selected-state indicators on glass.
- Shared constants for the hairline edge: 0.5 dp wide, white at 10 percent.

Shapes passed in must be rounded-corner shapes (rounded rectangle, circle-like fully rounded, or capsule). Cut-corner and custom shapes throw an error in the lens. Remember the shape object between redraws so the glass is not rebuilt every frame.

## 6. Parameter dictionary (what every knob does)

Effects in the chain:

| Knob | What it does | BitChord value |
|---|---|---|
| Saturation (colour boost) | Makes colours behind glass richer. 1.0 means unchanged. | 1.5 |
| Blur radius | How frosted the glass is. | 8 dp |
| Lens refraction height | How wide the bent band at the edge is, measured inward from the edge. | 24 dp (0.5 of 48 dp) |
| Lens refraction amount | How strongly light bends inside that band. Bigger looks more liquid. | 24 dp (0.5 of 48 dp) |
| Depth effect | Adds a sense of thickness to the lens. | on |
| Chromatic aberration | Faint rainbow colour fringing at the edges. | on |
| Surface tint opacity | Strength of the light or dark veil on top. Lower is clearer glass. | 0.4 |
| Tint colour | Light theme #FAFAFA, dark theme #121212. | as listed |
| Resolution scale | Fraction of full size the glass is processed at. Big speed saver; blur hides the softness. | 0.33 |

IMPORTANT: when resolution scale is below 1, every pixel-sized value (blur radius, lens height, lens amount) must be converted to pixels and then multiplied by that same scale. Forgetting this makes blur and lens look three times too strong.

Rim highlight (Highlight):
- Width 0.5 dp, blur radius half the width, alpha 1.
- Styles: Default (light catches the edge directionally, the real glass look), Ambient (softer, even), Plain (uniform line).
- Alpha can be animated to fade the rim in and out.

Drop shadow (Shadow):
- Default radius 24 dp, offset straight down by one sixth of the radius (4 dp), black at 10 percent.
- Inner shadow is also available for a recessed look.

Other options on the main modifier: layer block (move or scale the surface while glass keeps sampling correctly), draw-behind, draw-surface (where the tint goes), draw-front, and an export option that records the finished glass so another surface can refract it (glass over glass, section 9).

## 7. Where to use it (placement recipes)

Use this ladder. Pick the cheapest tier that still looks right.

TIER 1, full glass (blur plus lens plus rim plus shadow). Use for the hero surfaces people look at most, over varied content:
- Floating bottom navigation bar or tab bar.
- Floating top bar or top controls (back button, title pill, action buttons).
- Floating action button and circular icon buttons over images.
- A mini bar, player bar or any "now playing" style strip.
- Search bar pill floating over a feed.
- Banners, toasts and snackbars that float over content.
- Cards or panels that float over a hero image or gradient.
- Segmented controls and small chips that overlap imagery.

TIER 2, lightweight glass (tint plus rim plus hairline, no capture). Use when the background behind is flat or stable, or when you have many items:
- Buttons and chips on a plain screen.
- List rows and cards inside a scrolling list (avoid dozens of full glass surfaces).
- Cards on a stable colour background.
- Anything repeated many times.

TIER 3, rim only. Use for elements that keep their own fill but should feel glassy:
- Existing filled buttons, thumbnails, avatars, tiles.

Sizing tips (starting points to tune by eye):
- Large surfaces (bars, big cards): use the BitChord defaults in section 6.
- Small pills and controls: BitChord's selection pill uses a lens height of 12 dp and amount of 14 dp. Use smaller lens values on small controls so the bend does not swallow them.
- Fully rounded corners look most like Apple's glass. Cards can use large but not full corner radii.

Special cases:
- Dialogs, popups, dropdown menus and modal bottom sheets are drawn in a separate system window. They are likely NOT able to see the shared backdrop layer (this is an engineering inference, not something BitChord demonstrates; test it). If the glass looks empty or flat there, either draw the sheet or dialog as an in-screen overlay inside the same root so it can sample the layer, or give those windows the lightweight glass tier with a more opaque tint.
- Sliders, switches, toggles: the full effect is too heavy. Use tier 2 for the track and tier 1 only for a small draggable knob if desired.
- Lists and scrolling: glass over scrolling content is the best case. The blur updates live. Keep the glass surface outside the scrolling content, as a sibling above it.
- Video or animated backgrounds: works, but costs more. Lower the resolution scale (for example 0.25) on weak phones.
- Full-screen overlays: use one glass surface, not a grid of small ones.

## 8. Compatibility matrix (tell the user)

| Android version | What happens |
|---|---|
| 13 (API 33) and newer | Full effect: blur, lens refraction, colour fringing, rim, shadow. |
| 12 and 12L (API 31 to 32) | Blur, colour boost, tint, rim and shadow, but NO lens refraction (the shader needs API 33). |
| 11 and older | No backdrop effects. Use the lightweight tier or a plain translucent fill plus the hairline. Never crash and never leave a surface invisible. |

Always check the real device version at runtime. Do not build separate screens per version. The helpers handle it.

## 9. Advanced

Glass over glass: a lens above an existing glass surface should refract that surface, not the page behind it. BitChord handles this by letting the lower glass export what it drew (blurred and tinted, without rim or shadow) into a second layer that the upper lens reads. Use this for a selection pill travelling inside a glass tab bar.

Springy selection pill (optional, from GlassSelectionPill.kt, motion ported from the MIT-licensed Flutter package liquid_glass_easy):
- A tap lifts the pill past the bar's size on two under-damped springs (one for width, one for height), so it overshoots and settles. It stays lifted for the whole trip and is released once it has landed (around 92 percent of the way).
- Travel spring: stiffness 280, damping 31.4.
- Lift springs: stiffness 250, damping 19 across and 22.1 down.
- Glass springs on much faster: stiffness 1000, damping 63.3, so it looks like glass almost immediately.
- While moving, it measures acceleration over a 0.3 second window: it stretches wide and flat as it speeds up, squashes narrow and tall as it brakes, capped at 12 percent.
- Dragging with a finger holds the lift open. Releasing snaps to the nearest tab.
- After landing, the glass fades out and a plain flat pill takes over. The two look identical at rest, so there is no visible swap.
- With "reduce animation" on, jump straight to the new tab with no lift.

Resolution scale trick: record the backdrop at about one third size and draw it back up. The blur hides the softness. Nine times fewer pixels go through the blur and lens, which is what lets BitChord run several glass surfaces at once.

## 10. Colour, contrast and accessibility

- Use pure black or pure white for icons and text on glass (glassContentColor). Greys fail over changing artwork.
- Selected states use the inverse colour (glassIndicatorColor), so they read as a shaded scrim rather than more of the same tint.
- Do not give a glass surface its own opaque Material fill. Make the container transparent and let the glass modifier provide the look. An opaque fill hides the effect.
- Check readability over the lightest and darkest images the app will show. If text becomes hard to read, raise the tint opacity, not the blur.
- Provide the "Reduce blur" fallback (solid theme surface plus the hairline) and honour the system "reduce animation" setting for the pill physics.

## 11. Performance rules

- Record the shared layer only when glass is on, supported, and reduce blur is off.
- Keep full glass surfaces to a handful on screen at once. BitChord manages about six at its peak, in the middle of a transition. Use tier 2 or 3 for anything repeated.
- Keep the resolution scale at 0.33 unless sharpness complaints appear. Raising it to 0.5 was tried in BitChord and was not the fix for the sharp-text bug (the real cause was the transparent recording).
- Do not create a new shape object every frame.
- Test on a mid-range phone, not only a flagship.

## 12. Known gotchas (each one cost BitChord real time)

1. Sharp text visible through the glass: the recorded layer was transparent. Paint the opaque app background into the layer first.
2. Crash when applying the lens: the shape was not a rounded-corner shape.
3. Glass looks like nothing: the background behind it is a flat single colour.
4. Glass rebuilds constantly and stutters: the shape was recreated on each redraw.
5. Misaligned or shifted glass: the sampling position can be wrong when a parent is scaled, rotated or otherwise transformed (the engine itself notes this as an unsolved case). Keep glass surfaces out of transformed parents, or use the layer-block option so the engine can undo the transform.
6. Blur and lens look far too strong or weak after changing resolution scale: pixel values were not multiplied by the same scale.
7. Gestures only work on the inner row, not the whole bar: attach tap and drag handling to the whole glass surface, not just the content inside it.
8. Same surface duplicated in two layers: do not record the backdrop twice. Reuse the layer the surface already recorded this frame.
9. Library works in a quick test but crashes in the real app: the prebuilt library was compiled against a different Compose version. Vendor the source.

## 13. Build and verify (every time)

1. Make the helper kit and the shared layer, then convert ONE simple surface first (a floating bottom bar or a single floating button).
2. Run the app on Android 13 or newer (real phone preferred). Check: content behind is blurred, edges bend light, there is a thin bright rim on the edge facing the light, there is a soft shadow, dark and light themes both work.
3. Check Android 12 and 11 behaviour (or at minimum read the fallback path) and the "reduce blur" setting.
4. Only after step 2 passes, convert more surfaces, one screen at a time. Announce each screen before changing it.
5. If the emulator looks like plain blur, check its Android version before blaming the code; the lens needs Android 13 or newer.

## 14. Reporting to the user

When finished, tell the user in plain words:
- Which screens and elements now use glass, and which tier each uses.
- The minimum Android version for the full look.
- How to switch glass off.
- Anything that could not be done (for example dialogs) and the fallback used.

## 15. Licensing (say it in one plain sentence)

- BitChord is GPL-3.0. Copying its code into an app you publish forces that app to be GPL-3.0 too. Using the recipe, the numbers and the ideas is fine.
- The glass engine (Kyant0/backdrop) is Apache-2.0, and the pill motion origin (liquid_glass_easy) is MIT. Both are safe for any app. Keep their copyright headers.
- BitChord's own glue file credits another GPL-3.0 project (Echo Music). Do not copy that glue; rebuild it from section 5.
