# v0.3.0 verification contract

The release is gated by lint, unit tests, emulator UI/integration tests, actual media decoding, APK signature inspection and re-download comparison. Screenshots are taken with Android UiAutomation from the running Activity/dialog/IME, not generated mockups. Fixture media exists only in the test APK or test-created app data.

## New checks

- Real 2,048-bin PCM waveform (all samples aggregated, bounded memory), asynchronous full-track decode, source-identity cache, no per-drag decode.
- Music-library selection → waveform → 15 to 10 seconds → start drag → real preview → album-art card → direct card placement → story preview/seek/restart → export progress/cancel.
- Background drag, two-finger scale/rotation, undo/redo and draft recreation.
- CPU final-photo composition versus the GPU foreground/background composition using the same saved pose; image evidence is retained.
- Short non-looping audio is rejected instead of adding silence or repetition. Looping must be explicitly enabled. 30/60-second video fixtures deliberately enable looping of their shorter test track.
- Existing Korean composition/IME, live text cancel/re-edit/history, PNG SAF grants/refresh/alpha, 7 GPU styles, actual H.264/AAC 5/15/30/60-second exports and cancellation remain covered.

## Required actual captures

`ux-photo-main`, `editor-text-ime`, `editor-text-styles`, `ux-photo-styles`, `ux-music-library`, `ux-music-waveform`, `ux-music-cards`, `ux-png-library`, `ux-photo-crop`, `ux-preview`, `ux-share-progress`.

Additional captures include the playing waveform, placed music card, background gesture and GPU/export pose comparison. CI attempts an actual emulator screen recording; screenshot and test failures are not waived if recording is unavailable.

## Compatibility and intentionally bounded behavior

Project additions have constructor defaults: identity photo pose, edge-centered style region, and music looping OFF. Existing source URIs, layers, crop data, presets, volume and text-layout version remain readable. A legacy short-music draft that previously looped implicitly now requires explicit loop consent before preview/export; its source and draft are retained.

The background-centered effect is a soft geometric falloff, not subject segmentation or an AI mask. Thumbnail motion is frozen; the full canvas previews motion with the same GLES style implementation used for export. Music cards are baked into MP4, not Instagram-native interactive music stickers.

Galaxy S22 touch feel, Samsung keyboard, camera capture, device hardware encoder performance, sustained thermal behavior and real installed Instagram composer/handoff require physical-device confirmation. Emulator coverage must not be described as those physical-device results.

## Actual run and visual review

[600de64 full CI](https://github.com/vividhyeok/igstoryplus/actions/runs/39202415188) passed 42 tests: app unit 6, PhotoEditor unit 20, Android UI/integration 16. Four actual movies (5/15/30/60 seconds), JPEG and seven GLES style images passed stream/geometry/duration/fps and full-frame decoding checks. The GPU foreground-pose comparison and cached PCM waveform tests passed. APK signing and release re-download comparison passed. Later compatibility refinements are re-run through the same gates; the release's attached validation-report identifies its exact commit and counts.

The actual captures were downloaded from the [verified development release](https://github.com/vividhyeok/igstoryplus/releases/tag/dev-23-600de64) and visually reviewed. The footage/art in screenshots is test-created media, not shipped sample content or a mockup of the UI.

| Actual screen | Review result |
|---|---|
| Main photo | Loaded photo, large canvas, seven vector tools, distinct white Share CTA; no blank initial capture |
| Text + IME | Real keyboard visible; new phrase above existing text; tools and input remain above IME |
| Text styles | Current phrase in horizontal cards; selection visible, canvas retained |
| Photo styles | All seven real GPU thumbnails ready before capture; strength and motion remain on the same panel |
| Music library | Search, artwork, title/artist/duration and import; no metadata fields crowding the screen |
| Music waveform | Real PCM peaks, bright selection frame, start/end, small duration, play/restart controls |
| Music cards | Current art/text in visibly distinct full-card, compact and art-only designs |
| PNG tray | Actual SAF test folder; grid, tabs, refresh/change; filename is not silently cut without ellipsis |
| Crop | Full-canvas grid, cancel/done and ratios; not a small popup or stale main-screen capture |
| Preview | Editing chrome/helper boxes hidden; play/pause/restart/seek and immediate edit return |
| Export | Compact dark progress with real percentage and cancel; draft retained on cancellation |

Visual QA found and corrected an early blank-photo capture, incomplete thumbnail capture, overlapping new/old text placement, stale crop capture and a large white export dialog. Repeated text editing and software-IME timing were tested again rather than waived. Fixtures do not establish photo aesthetics across all real-world scenes, speaker output quality, or Galaxy S22 performance.

Final application commit [4e0fa9a](https://github.com/vividhyeok/igstoryplus/actions/runs/39204528476) passed 43 tests (7 app, 20 PhotoEditor, 16 Android), full media decoding, signing and download verification. Its eleven required actual captures were downloaded and reviewed again. Two v0.3.0 tag attempts were blocked by an emulator **Quickstep isn't responding** system dialog, visible in the retained screenshot/recording, not an igstoryplus crash. No stable v0.3.0 was published. v0.3.1 adds a boot-settle and launcher-only reset before tests, retaining all assertions and output gates. The installed app does not stop or modify the user's launcher.

v0.3.1 then passed the eight-case UI preflight but caught an IME-transition timing failure when the same text test was repeated in the sixteen-case suite (EditorUiTest line 206: input absent after a double tap immediately after cancel). v0.3.2 waits for actual IME visibility, focused window and stable canvas geometry before that double tap, instead of targeting coordinates during the keyboard-close resize. The double tap, live editor, cancellation, Korean composition, history and full media assertions remain unchanged. No stable v0.3.1 was published.
