# FreeLocalPlayer — Modern Fusion UI, stage A

Status: source patch prepared against **1.3.8 (versionCode 16)**.
Build / Android device acceptance: **not confirmed in the authoring environment**.

## What is implemented

1. Material 3 color schemes **Studio Dark**, **Soft Light**, **Violet Glass** and shared rounded shapes.
2. New appearance entry point (palette icon in library top bar) with on-device storage using SharedPreferences. No internet required.
3. Motion-reduction option: removes the 160 ms decorative Play/Pause crossfade (the video controls' auto-hide is a functional behavior and remains active).
4. Reusable contrast-safe, semitransparent **Glassmorphism-inspired** panel. It deliberately does not blur live video/background to avoid device-specific performance regressions.
5. Subtle **Neumorphism-inspired** shadow on the main Play/Pause buttons, with proper contrast, content descriptions and minimum touch targets.
6. Library header and mini-player refined to the Professional/Material hybrid.
7. Audio NowPlaying screen gets a graduated background, glass-like artwork panel, central action.
8. Cinematic video overlays use dark gradient top/bottom panels; the existing tap-to-toggle, auto-hide, ±10-second seek, timeline, queue and orientation behavior are preserved.
9. Music Mode title and chord accents no longer force light text/accent colors against the Light theme.
10. JVM unit tests for persisted palette ID fallback and round-trip.

## Design limitations and future enhancements

- Existing tabs and deeply integrated features are preserved rather than radically rearranged.
- The video and audio player remain built on Media3; neither codec support nor analytics algorithms were changed.
- This patch is **not** final FreeLocalPlayer 2.0; UX defect list UX-01 through UX-15 must still be verified on the physical device.
- Artwork-derived palette extraction, optional stronger blurred glass effects, full card-layout refactoring, landscape/large-screen adaptation and UI automation remain follow-up implementation tasks.
- A theme is a palette, not a replacement for the other functional design modes; the Cinematic video presentation works under all three palettes.

## Smoke/regression checklist for Windows/Android

- [ ] `gradlew.bat :app:assembleDebug :app:testDebugUnitTest --no-daemon` succeeds.
- [ ] Installing over existing build preserves Media3 queue, playlists, text mappings, chords and MIDI offsets.
- [ ] Palette setting persists across process termination/relaunch.
- [ ] All three palettes have readable title text, album labels, buttons, selected states and dialogs.
- [ ] Reduced motion removes Play/Pause crossfade without changing playback behavior.
- [ ] Mini-player pause, next, expand, five queue modes and search are still accessible.
- [ ] Music Mode opens and retains text/chords/A–B/notes/editing.
- [ ] Video: tap reveals and hides overlay, ±10 s and seek work, pause holds controls, landscape/portrait return works.
- [ ] No background internet calls, ads or subscriptions.
- [ ] GitHub commit and remote HEAD match after patch installation.

See issue: https://github.com/OlegRakhimov/MyPlayer/issues/1
