# FreeLocalPlayer — 0-C.3-D: fail-closed canonical JSON guard

Status at packaging: source patch **prepared**, not Android-validated. The exact Git revision and install/test results must be recorded after user runs the installer.

## Why this is required before permanent Room-primary storage

`LibraryStore.readCanonicalJson()` previously used `optJSONArray` and `mapNotNull` on media, playlists, retired tracks and playlist references. A malformed but syntactically valid JSON snapshot could have an item silently skipped. `LibraryStore.save()` could later persist the truncated state and overwrite the user's data. That violates the no-loss gate of 0-C.3.

## Changes

- `CanonicalLibraryJsonDecoder`: strict parsing of all declared tracks, playlists, IDs and retired positions. Missing/invalid array items fail with an exception; **no partial state** is returned. Preserves prior field defaults (`title`, `video`, `lyrics`, `sourceKey`, `sizeBytes`, `durationMs`) and optional legacy `retiredMedia`. An absent `library` key on first install is still an empty library; a present `{}` or malformed value is **not** interpreted as an empty one.
- `CanonicalLibraryStateGuard`: pure structural invariants applied both after decoding and **before committing** JSON. Checks stable UUID uniqueness (live + retired), nonblank ID/URI, playlist identifiers and retired positions. It does not remove records, generate UUIDs, require media to be available, or forbid legacy unresolved playlist references.
- `LibraryStore`: delegates canonical decoding to strict decoder and runs the guard before a disk write. The existing single-writer queue and JSON-first -> Room shadow order remain untouched. A rejected read triggers the existing user-visible fail-closed recovery UI, not a destructive rescan. Rejected writes invoke the existing error callback.
- Tests: 10 JUnit tests for pure state invariants, ten Android instrumentation cases including **real user catalog read-only** and in-memory corruption scenarios.

## Explicit non-changes

No migration to Room-primary; no toggling persistent rollout flags; no database schema version change; no package ID change; no versionCode/versionName change; no changes to backup ZIP format or signing; no uninstall, pm clear, automatic restore or destruction of user data. The handset tests must be run only with a verified backup and a matching APK signing certificate.

## Acceptance gates

1. Windows Gradle build (`assembleDebug`, `assembleDebugAndroidTest`, `testDebugUnitTest`) PASS. Room v1 schema unchanged.
2. On Motorola Edge 60 Pro: existing APK certificate matches both the new app and test APK. `adb install -r` (never uninstall) and instrumented `CanonicalLibraryJsonDecoderDeviceTest` 10/10 PASS. Its real-catalog case validates a SHA-256 before/after and a read-only Room comparison, without calling `LibraryStore.load()`.
3. Verify library, playlists, text and chords visually on the phone. Build+test does not replace this UI check.
4. Retain a **current** SAF ZIP outside the phone. Old 628/3/8 ZIP from Nov 14 is only historical evidence, not guaranteed to include later edits.
5. **Future 0-C.3-E:** test process death, interrupted JSON writes, stale/corrupt Room, recovery from fresh ZIP and performance before any long-lived Room-primary rollout.

A passed strict parser is **not** a tested automatic recovery mechanism and is not itself proof of a successful Room-primary migration.
