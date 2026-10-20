# v0.5.1 regression fixes

TextGeometry version2 separates physical font scale from wrap width. Artwork and CanvasTextEditor share the same font, padding, fallback spacing, outline/glow and background geometry. Legacy drafts migrate on editing without changing intended physical font size. Input min height and input layout positioning are corrected. The general text-emoji palette is removed; native IME Unicode input remains.

Videos use no photo backdrop surface or blurred still frame. Filter effects are opt-in per video, and raw preview uses 1080x1920 processing. All old GPU style IDs stay mapped explicitly; five additional non-blur grades use IDs10-14. New video imports clear inherited photo filter state.

Plus has short layer/music pages, explicit selected chips, high-contrast layer picker, live percentage labels and media-aware target choices. Dock hides the underlying toolbar, restores it on dismiss and stops playback on dismiss. Text and story canvases remain separate from panel layout.

All 29 Desktop PNGs included, 10 old asset URIs retained and 19 added. Source files read-only. Audit hashes in emoji-audit-v051.json. Recycler thumbnails are lazy-loaded; actual selected images are validated before insertion. User SAF grants remain supported.

New ReleaseV051Test5 covers font reflow/wrap independence, all29 spans and actual picker insertion, small backgrounds at4sizes x3modes, disabled implicit video blur and distinct grades, Plus tabs/selected state/contrast on video. StoryUxTest now inserts ordinary emoji through IME; existing composition, gesture, asset restore and actual export checks retained. Focused Android suite17; pure tests37. Final results and limitations recorded in validation/RELEASE_V051_REPORT.md after release validation.
