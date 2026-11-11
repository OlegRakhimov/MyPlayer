# FreeLocalPlayer Modern Fusion — music ergonomics v6

**Parent roadmap:** [PROJECT_ROADMAP.md](PROJECT_ROADMAP.md). **Tracker:** [GitHub #1](https://github.com/OlegRakhimov/MyPlayer/issues/1).

## Intended changes in v6

1. Music Mode lower controls: large high-contrast Play/Pause, previous and next track, current time and queue in the default compact bar. The transport's expanded area provides the full song seek slider, ±10-second jumps, five existing queue presets and the advanced tools entry point. This does not change Media3 behavior or playback order.
2. Semi-transparent surface from the existing Modern Fusion system is reused for the music transport; no expensive blur or new dependencies.
3. The LRC and Enhanced LRC panel offers a visible **Авто: вкл/выкл** control: it may be paused to read/scroll manually, and reactivated to follow the playing line. Switching songs/lyrics resets the control. Neither timestamps nor underlying text are rewritten by that toggle.
4. The active LRC row uses theme-provided onSecondaryContainer for lyrics and chord markers, including inline and word-synced chords. Inactive rows use onSurface.
5. The TXT/LRC toolbar remains compact: Replace and Edit are still accessible with the same callbacks.
6. Pure Kotlin transport and auto-scroll bounds rules have JVM tests. The existing music analysis, chord corrections, MIDI, A–B and full-screen timing editor are not modified.

## Verification and limits

- Install only after source SHA-256 and GitHub HEAD match, with a backup inside `.patch-backups`.
- `gradlew.bat :app:assembleDebug :app:testDebugUnitTest --no-daemon` must pass before commit/push.
- On phone, test the same song across three palettes, Play/Pause, previous/next, queue, shuffle/repeat, seek and tools, normal LRC and Enhanced LRC with auto-follow on/off, manual TXT and MIDI tabs.
- Confirm playlists and lyrics are retained when updating over an existing installation.
- **This patch is not complete product acceptance.** It is a focused step toward UX-04/09/14 and LRC usability, and requires actual device tests.
