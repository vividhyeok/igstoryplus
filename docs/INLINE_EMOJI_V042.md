# v0.4.2 — inline custom image emoji

The intended emoji are the user's existing Jesus, church and other PNG designs embedded among text characters. The Unicode-only addition in v0.4.1 did not meet this requirement.

A text layer now persists a document-local private-character-to-image-URI map. InlineEmoji.ImageGlyph is a ReplacementSpan with font-relative size/baseline and preserved image aspect/color. CanvasTextEditor reapplies these spans to its native Editable after edits and restores them from saved drafts. Artwork.text uses the identical span implementation in StaticLayout, which is shared by bitmap/JPEG/video overlay rendering. No separate PNG layer is created. Existing text defaults to an empty map; edit sessions copy the map to preserve cancel behavior.

Text controls expose '내 이모지' next to Unicode input. It includes the 10 existing supplied PNGs (including Jesus and church) plus readable PNGs in the existing SAF folder. Folder permission and original assets are retained. These are custom document glyphs, not newly registered standard Unicode or an external-system keyboard font.

Focused validation: 34 unit tests, lint, StoryUxTest 4 cases. New case verifies real UI custom-icon insertion at caret, spans, deletion/reinsertion, multiline layout, recreation during editing, persistent image mapping, colored image rendering and successful actual still JPEG export. Final focused rerun after edit-copy isolation/version update also passed. Full legacy suite/long exports were not rerun, and new MP4 decode, Galaxy S22 device and Instagram sharing are unverified.
