# FreeLocalPlayer — Modern Fusion: Library UX (v5)

**Base:** `main` commit `ffd84bed33e9ed7cc1e30445dbcb15d91f261e07` (contrast v4).
**Scope:** media library and mini-player only. Current Gradle version still identifies the existing app as `1.3.8 (16)`; this is a UX patch, not a new release version.

## Changes

- Top library actions compacted into one row: Files, Folder, Scan icon; scanner state is visible when scanning.
- Search has a dedicated search icon, single-line entry and an immediate clear button.
- Display-only list sorting: insertion order (default), ascending and descending title. **This does not reorder Media3's queue.**
- Music tab filter: all files, with lyrics, without lyrics. Filtering uses the local `MediaEntry.lyrics` field, not remote metadata.
- Count shows visible/total entries for the chosen library tab. When filters change, the list scrolls to the first item.
- Track rows have a highlighted active border, wider filenames and a compact overflow menu. Favorites and Add to playlist remain single-tap actions; lyrics and Remove from library move to the two-tap menu.
- Mini-player now includes an explicit Previous action next to Play/Pause and Next. Queue, playback modes, progress and additional controls remain available.
- Search/sort/filter work with the current data model, no new media permissions or database migration.
- Added local JVM tests verifying stable ordering, case-insensitive search, text filtering and fallback values.

## Safety and acceptance

- No modifications to player engine, Media3 service, file import, playlist persistence, TXT/LRC data, chord/MIDI analysis or user preferences.
- The existing autoplay, playback order and queue choices are intentionally unchanged; sort/filter only change the **display**.
- Run `gradlew.bat :app:assembleDebug :app:testDebugUnitTest --no-daemon` on Windows.
- Install over the current package with `adb install -r`; **do not uninstall** and do not clear application data.
- On device: test 600+ items scrolling, query/clear, A–Я/Я–А, lyric filters, library action menu, previous/next, repeat/shuffle, three themes, videos and playlists.
- Build + unit-test success is different from Android-device acceptance. Mark UX as verified only after testing the installed APK.

GitHub tracking: https://github.com/OlegRakhimov/MyPlayer/issues/1
