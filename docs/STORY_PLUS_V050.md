# Story Plus v0.5.0

## User-facing changes

- Custom Jesus/church/other inline PNG emoji retain image spans while typing rather than replacing every span on each keystroke. Their glyph fits within font ascent/descent. Filled/translucent/outlined multi-line backgrounds use one union contour, avoiding stacked alpha seams and horizontal internal outline borders. Original files and existing draft references remain intact.
- Story Plus: subtle animated heart glitter and starlight anchored to text/sticker/photo silhouettes. Strength and optional per-layer beat pulse are independent. Smooth deterministic looping animation uses the same renderer and timestamps in preview and export. Plain photos become MP4 only when animation is enabled.
- Original video audio can drive reactions without adding or duplicating a music track. Auto chooses added music first, otherwise the original video track; source can be chosen explicitly. Downloaded stories work when their saved MP4 actually contains audio. Missing audio cannot be reconstructed. No music recognition/catalog matching is claimed.
- Reaction target separates the blurred photo background from the complete original photo/video frame. Independent layers only react when explicitly enabled. A video is already flattened: its existing text/stickers cannot be separated from the frame. Background-only reactions do not affect a full-frame video.
- Layer arrangement: horizontal/vertical center, level, position lock, non-exported posting-UI margin guides. Guides are advisory (6% side, 14% top, 20% bottom); they do not promise exact Instagram UI boundaries on every device.
- Fewer selected-layer controls; duplicate/delete/crop/replace are grouped in layer More. Text editing uses actual vector controls instead of mixed font symbols.
- 36 original matching SVG paths, 24-unit grid, 1.8 stroke, round caps/joins, square aspect, 48dp touch targets. Film icon indicates photo treatment; sparkle-plus indicates extra effects. Fixed speaker path overshoot. Native paths and standalone design/icons/*.svg are generated from one source by tools/export_editor_icons.py.

## Implementation notes

Project.reactionAudio and Style.reactionTarget preserve legacy defaults. Layer accent/amount, beatMotion and locked default to disabled. Audio analysis is shared with added music, reading the actual video audio track when requested. Export keeps source audio once, independent source/added-music volume. New effect bitmaps reuse a mutable output buffer; Media3 BitmapOverlay invalidates textures when Bitmap generation IDs change. Reference: https://github.com/androidx/media/blob/1.6.1/libraries/effect/src/main/java/androidx/media3/effect/BitmapOverlay.java . Static per-photo filters remain static.

## Validation scope

New checks cover multi-line filled/translucent/outline backgrounds containing custom image glyphs; span reuse; original video track analysis; actual 5s H.264/AAC export with heart and color reaction; output audio-track count; selected-layer pulse/lock/center; reaction target foreground protection; native vector icon contact sheet. Existing text/emoji UI checks and composition/restore regression are retained. Physical Galaxy S22, Instagram actual share receipt/posting, long exports/thermal behavior remain unverified unless separately recorded in the release report.
