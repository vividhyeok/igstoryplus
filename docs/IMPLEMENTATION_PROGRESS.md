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
