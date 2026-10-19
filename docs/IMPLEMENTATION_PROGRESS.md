# Integration progress

Baseline: `bf6f868`, clean main matching origin/main. Last stable release: v0.3.4.

## Checkpoint 1 — bundled emoji

- Inspected all 10 local PNGs visually and by file/pixel SHA-256; 1254×1254 RGBA, no corruption or duplicate pixels.
- User describes these as user-created/selected assets. Visual review found generic illustrations, no private photos, identifying passport data or branded logos. All 10 included; zero exclusions.
- Read-only originals; 512×512 transparent copies, 448px maximum content bounds and centered placement. No background removal or color replacement. `docs/emoji-audit.json` records original and output hashes.
- Five populated categories: food/cafe, fashion, travel/nature, reading, faith. Stable semantic asset URIs survive upgrades. Existing SAF grants/URIs remain supported.
- New default/user/recent/favorite tabs, filename/keyword search and categories; Glide thumbnail caching retained.
- Kotlin compilation passed. Android render, restore and gesture checks follow in the integration suite.

## Remaining checkpoints

- Core editor stability and video pose parity.
- Separate Flow Blur and Zoomblur, 0% identity, existing style redesign and distinct new styles.
- Cached multiband/flux/energy analysis; ambient, aura and card indicator reactions.
- Four music card designs and four text styles with shared preview/export drawing.
- Layer style/motion, draft selection and visual presets.
- Lint, unit/instrumented tests, actual media evidence, persistent signature and stable Release/download verification.

Galaxy S22 is not connected. No physical-device claims are made. Failed historical CI runs remain available; assertions are not removed to pass release gates.

## Checkpoint 2 — integrated editor and rendering

- Shared ten-style registry; redesigned six styles, separated Flow Blur from radial Zoomblur, added Shutter Drag/Sensor Raw/Frame Echo. Noise interpolates periodically to avoid loop flashes. Noise hashes run only for noise styles.
- Shared foreground/pose composition for still preview and exports; video direct gestures use the same inverse transform in GLES. Undo/redo toolbar state refreshes after gestures.
- Cached 50ms energy/three bands/spectral flux/onsets/fast/slow analysis; silence gate, gain normalization and modified-file cache invalidation. Ambient/Aura mask photo foreground; Music Dot uses shared card drawing. Video offers light/Dot without recoloring its foreground under photo-background modes.
- Four new music cards and four new text styles share preview/output rendering. Progress Line advances in actual MP4. Paused editor initializes its Dot too.
- Independent static photo-layer styles; per-layer basic/breath/float motion and strength; multiple named drafts with independent thumbnails, open/rename/delete; visual preset thumbnails.
- Verified locally: emoji 512px alpha/restore, direct photo Undo fix, actual beat GPU+MP4, five-scene 0% identity and style separation, continuous motion loop, foreground protection, long Korean cards/text, exact indicator pixel parity, real 5s Progress/Dot MP4, video drag/Undo and independent draft snapshots. Six focused integration checks passed together.
- Confirmed all ten Desktop originals still match their original SHA-256 and bundled outputs match audit.
- Full lint/unit/24-test suite running; a new gallery UI check and video-pose pixel reference will be included before CI. Stable release/download/signature remain pending.
