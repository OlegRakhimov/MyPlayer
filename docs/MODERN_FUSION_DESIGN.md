# FreeLocalPlayer — Modern Fusion UI specification

Status: **approved design direction; implementation pending inspection of the actual project sources.**
Date: 2026-11-11
Target: existing Windows project `C:\Users\Oleg\Documents\AndroidProjects\FreeLocalPlayer`.
Repository: `https://github.com/OlegRakhimov/MyPlayer`.

## Non-negotiable requirements

- Keep the existing Kotlin/Jetpack Compose/AndroidX Media3 architecture and existing user data.
- Do not remove music/video playback, file/folder access, MediaStore, playlists, favorites,
  recent tracks, playback queue, five playback modes, audio speed, TXT/LRC/Enhanced LRC,
  chord editing, A–B repeat, MIDI/piano roll or other implemented music-practice tools.
- Avoid large-scale renames before inspecting every real type and method. No invented APIs.
- Maintain one working folder, and keep temporary builds/caches/archives out of Git.
- Design behavior must remain usable without internet, subscriptions or user accounts.

## Style ownership

| Direction | Primary use | Guardrails |
|---|---|---|
| Material 3 Expressive | App shell, navigation, controls, typography, motion, theming | Maintain legibility and accessible sizing |
| Professional | Media list, folders, sorting, filtering, queue and music tools | Tools are accessible in 1–2 actions, not permanently cluttering playback |
| Glassmorphism | Select playback sheets, album-art backdrop, transient panels | Graceful fallback when blur is unavailable or performance is poor |
| Neumorphism | Subtle depth for primary transport control and small accents | Never lower text contrast or use it on every control |
| Cinematic | Video library and immersive video | Video always gets priority over chrome |

## Color directions (initial design tokens, subject to device review)

| Token | Studio Dark | Soft Light | Violet Glass |
|---|---|---|---|
| Background | `#101318` | `#F5F6FA` | `#171426` |
| Surface | `#20252D` | `#E6E9F1` | `#332B4A` |
| Accent | `#A9B9FF` | `#465FBE` | `#C9B5FF` |
| Foreground | `#F4F5F7` | `#202532` | `#F5F0FF` |
| Secondary text | `#A6ADB8` | `#5D6474` | `#BCB1D2` |

Visual targets: corner radius 20–28 dp on large panels, spacing 16–24 dp, control
hit areas >=48 dp, emphasized Play/Pause diameter 64–72 dp. Dynamic color from
album art optional and must preserve adequate contrast. Reduced-motion support.

## Screen contracts

### 1. Library / Professional + Material 3

Sections: Music / Video / Playlists / Folders, omnipresent search, sorting/filter chips,
album art or video thumbnails, now-playing mini-player anchored above bottom navigation.
The mini-player must not cover the bottom list item. Persistent actions:
open current player, play/pause, previous/next track, overflow actions.

### 2. Now playing / Material 3 + Glass + restrained Neumorphism

Hierarchy: collapse button, current playback context, album art (or selected content mode),
one occurrence of track title/artist, time seekbar, skip/play/pause, shuffle/repeat, and
secondary action row: lyrics, chords, A–B, speed, queue. Make secondary tools accessible
without a multi-level overflow menu. Keep the five existing playback modes.

### 3. Lyrics, chords and music practice

Lyrics view: LRC line focus, Enhanced LRC word highlighting when such timing data exists,
smooth follow scrolling, manual scroll with return-to-current shortcut.
Chords view: preserve text alignment and manual chord edits. Controls for font size,
timing offset, A–B loop. Leave MIDI/staff/piano-roll reachable as existing feature,
without claiming that MP3 instrument separation is solved.

### 4. Queue / Professional

Search within queue, current-song highlight, jump to item, reorder/remove where already
supported, clear undo affordances where appropriate, repeat/shuffle modes visible.
The queue should be a dedicated sheet or screen, not a modal that obscures playback.

### 5. Video / Cinematic

Landscape immersive surface; one tap reveals/hides transport controls; another timed
hide should not conflict with accessibility interactions. Subtitle selection and speed
options when supported by current Media3 implementation. Handle portrait, landscape,
rotation, seek gestures, video duration and system bars. Avoid unnecessary video blur.

### 6. Settings / Material 3

Theme picker with previews: Studio Dark, Soft Light, Violet Glass; optional album-art
palette; motion-reduction; contrast-preserving fallback. Persist settings in the current
storage mechanism; inspect project before choosing DataStore/Room.

## Delivery and verification

1. Inspect actual ZIP, version, packages, Compose dependencies and Git baseline.
2. Record tests and snapshot prior to changes.
3. Introduce tokens and reusable components in the existing source hierarchy.
4. Migrate Library and mini-player; then NowPlaying and lyrics/chords; then video.
5. Verify Kotlin compilation, Gradle unit tests, previews where available, device UI.
6. Publish validated changes as commits from the original local folder.
7. Label each feature accurately: planned / coded / built / device-tested.

### Manual acceptance cases

- Open Music/Video/Playlists/Folders; search and sort; no clipping in compact mode.
- Change tracks and modes; check queue ordering and background playback.
- Test TXT, LRC, Enhanced LRC, chords, manual editing, A–B, speed, MIDI features.
- Rotate video, reveal/hide transport, seek, subtitles where supported.
- Switch all three themes, large font, accessibility contrast, reduced-motion.
- Update an existing app installation on the physical phone without loss of data.
- Verify `git status` and GitHub `main` commit hashes match after a successful sync.

The design is **not** considered implemented merely because this specification exists.
