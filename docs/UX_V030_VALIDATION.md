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
