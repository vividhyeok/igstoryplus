# v0.3.3 verification

Starting point: clean 1f5cc62, verified v0.3.2. Existing editor components are reused. Public reference: [Meta's music sticker flow](https://about.fb.com/br/news/2019/06/instagram-e-facebook-trazem-musica-para-stories-no-brasil/) describes song, segment and display selection. This app uses user-provided local files.

| Capability | Starting state | Change / remaining scope |
|---|---|---|
| Canvas, vector tools, contextual bar, save/share | Implemented | Retained |
| Photo/layer gestures, guides, history | Implemented | Retained |
| Live Korean text/IME, styles, fonts, double tap | Implemented | Retained, IME regression suite |
| Music library/search/art, waveform, card previews | Implemented | Range-release audition added |
| Music reaction | Partial: OFF default, hidden setting, no isolated pixel test | First music enables reaction, music-panel switch/strength, interpolation, failure feedback, isolated GPU/MP4 onset tests |
| Video source and music volume | Partial: original muted by music | Independent preview/export gain; old drafts default source gain to 1 |
| Video framing | Partial: crop screen | Direct video gestures incomplete |
| Layer motion | Partial: boolean subtle motion | Type/strength selection incomplete |
| Individual photo filter | Missing: button opens global style | Incomplete |
| Drafts | Partial: automatic draft and text recovery | Multiple draft selection incomplete |
| Presets | Implemented, UX limited | Visual preset preview incomplete |
| Custom fonts | Implemented through text tools | Retained |
| Sharing recovery | Implemented | Retained; real Instagram/Samsung check required |

## Acceptance evidence

- `PipelineTest.iBeatChangesGpuPixelsAndExportAtDetectedOnsets`: synthesized 500ms pulses, actual decoder analysis, known event alignment, the shared LivePhoto GPU shader, stable center/changing edges. Exports a Simple movie with continuous motion OFF and compares decoded onset/rest frames. Saves GPU/MP4 PNG pairs and `beat-sync.json`; unrelated animation cannot satisfy this test.
- `EditorUiTest.waveformLibraryVisualStylesAndSharedPreview`: registered music, .55 default, OFF/ON, actual reaction control capture, waveform drag/audition, existing card/style/preview/export checks.
- Unit checks: envelope interpolation, out-of-range behavior, legacy source gain and history.
- CI retains lint, existing assertions, full media decode, persistent signing and download comparison; adds full decode of the isolated beat movie and includes beat evidence in release screenshots.

GPU render frames are evidence, not editor screenshots. Actual editor captures use Android UiAutomation. Emulator results do not establish Galaxy S22 performance, subjective speaker quality or Instagram posting success.

## v0.3.4 re-edit reliability

The v0.3.3 tag was blocked by the existing double-tap-after-IME-cancel regression at EditorUiTest line 230; no v0.3.3 stable release was published. The failure and screenshots remain in Actions run 39258121395. Completed tap pairs now use input event timestamps, density-aware slop, and open the editor after the second gesture ends. Drag/pinch/cancel do not count as taps. A Robolectric queued-input test covers delayed dispatch, single opening, third taps and drag rejection. The existing real double-tap/IME test is retained. v0.3.4 repeats all gates.

## v0.3.5 uninterrupted live controls

Music-panel history checkpoints no longer synchronize temporary animated View transforms while the clock is playing; the project already holds the base layer poses. This preserves audio playback and avoids recording temporary animation as a saved pose. The UI test changes reaction strength and music gain during actual preview, checks the clock playback control remains active, and captures ux-audio-mixer. All previous tests remain enabled.

## v0.3.6 playback test input

v0.3.5 was blocked by Espresso AppNotIdleException while the new test attempted a slider action during continuous preview. The test now locates controls while paused, injects real tap/drag events during playback, asserts playback remains active, captures the mixer, and pauses before returning to Espresso. Assertions and application behavior are retained, matching the established real-touch preview tests. No stable v0.3.5 was published.

## v0.3.7 asynchronous live touch delivery

v0.3.6 retained a re-edit timing failure in one run and stalled after capturing live reaction controls; no release was published. Live input now uses UiAutomation nonblocking injection, validates playing and paused states, and captures controls after explicitly pausing so screenshot synchronization cannot wait on continuous GPU frames. The screen recording retains the actual playing gestures. Double tap uses two actual asynchronous tap pairs after the IME viewport settles, followed by a bounded wait for the editor; no repeated taps or bypass of the editing path. Progress markers identify each live-control stage.
