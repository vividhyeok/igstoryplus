# UX reference — 2026-10-19

## Evidence and limits

This is an interaction benchmark, not a claim that the current Instagram Android binary was tested. No signed-in Instagram device is available here. Account, region and rollout can change its controls.

- [Meta, May 2024: new Stories stickers](https://about.fb.com/news/2024/05/new-stickers-in-instagram-stories/): official documented sticker tray → selection → immediate placement; Frames, Cutouts and Add Yours Music already exist. We do not present them as new features.
- [Meta, September 2019: music in Stories](https://about.fb.com/ltam/news/2019/09/facebook-e-instagram-traen-musica-a-las-stories-alrededor-de-latinoamerica/): inspected the actual paired product screenshots. A partial-height tray retains the photo; music uses a bottom waveform selection, small duration control, top cancel/done, visual text styles. This is **dated official imagery**, not proof of October 2026 geometry.
- [Tom's Guide: Android music walkthrough](https://www.tomsguide.com/how-to/how-to-add-music-to-an-instagram-story): inspected its Android search and music-editing screenshots (original tutorial is older). Observed album-art/search rows, a horizontal visual style strip, circular duration control, waveform frame, unobscured content and Done. Secondary visual reference only.
- [Current Play Store listing](https://play.google.com/store/apps/details?id=com.instagram.android): checked official app identity and listing; marketing images do not verify every editing gesture or panel size.
- [Android edge-to-edge](https://developer.android.com/design/ui/mobile/guides/layout-and-content/edge-to-edge) and [48dp touch targets](https://support.google.com/accessibility/android/answer/7101858): system/cutout/IME insets and usable targets are implementation requirements, not Instagram-specific observations.

Exact current font inventory, gesture thresholds, panel heights and cancellation semantics are unverified. The decisions below are igstoryplus design decisions where direct evidence is absent. No Instagram graphics or screenshots are bundled in the app.

## Interaction contract

| User action | Verified reference / uncertainty | igstoryplus implementation criterion |
|---|---|---|
| Select gallery photo | Meta documents camera/gallery entry | System picker, immediately return to large 9:16 canvas |
| Reframe photo | Exact current Android gesture thresholds unverified | Background-only drag, pinch and rotation; stickers retain their own hit testing; one undo per gesture |
| Enter text | Content-first editor; exact current keyboard UI unverified | Aa → real canvas EditText + IME, Korean composition, no popup |
| Change type/color/alignment | Music imagery shows visual style strip, not complete text editor | Current-phrase thumbnails, palette, immediate updates; keep IME-safe layout |
| Move text | Meta explicitly documents sticker-like movement/rotation/resize of lyrics | Same drag/pinch/two-finger rotation as other layers; double-tap re-edit |
| Choose sticker | Meta 2024 top sticker icon → tray → Use sticker | PNG grid with recent/favorites/folder, no bundled emoji |
| Choose music | Inspected Android search/artwork rows | Offline registered/recent music, search title/artist, import once |
| Choose music section | Inspected official and Android waveform frames | Real decoded/cached peaks; drag selection; visible start/end; no encoding |
| Change duration | Inspected small 15 control; current limits not inferred | Photo 5–15s, one-second steps, remember; video duration unchanged |
| Change music card | Visual style strip in inspected screen | Real current-song card thumbnails; metadata behind edit, not three always-visible inputs |
| Move card/sticker | Meta documents normal sticker transformations | Shared layer engine, topmost selection, no selection jump |
| Inspect result | Photo remains visible behind music controls | Shared preview clock, audio + GPU + overlays; no MP4 encoding |
| Finish/share | Done seen in inspected screens; posting not externally verifiable | Distinct Share CTA → validated export → Instagram composer; never claim posted |
| Cancel/back | Current Android exact behavior not verified | Sheet closes first; crop cancels; text safely commits/back or explicit cancel; deselect; save draft on exit |

## Implementation priorities

Content first, direct manipulation, minimal chrome, immediate feedback, contextual tools, reversible edits and shallow panels apply to **all** media types. Retain PhotoEditor, Media3, GLES, project JSON and SAF. Actual app screenshots and output decoding—not mockups or test totals alone—gate the new release. Device-specific Galaxy S22/Instagram handoff remains a separately reported physical-device check.
