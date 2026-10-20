"""Inspect actual Android renderer outputs; fail CI before releasing a broken pipeline."""
import json
import os
import pathlib
import subprocess
import sys
import xml.etree.ElementTree as ET
import math
import struct

root = pathlib.Path(sys.argv[1])
reports = {}
beat_report = json.loads((root / "beat-sync.json").read_text())
assert beat_report["gpuDelta"] > 1 and beat_report["mp4Delta"] > 0.8, beat_report
assert beat_report["compositionPixelError"] < .025, beat_report
assert beat_report["onsetMs"] % 500 < 100, beat_report
subprocess.run(["ffmpeg", "-v", "error", "-xerror", "-i", str(root / "beat-sync-5s.mp4"), "-f", "null", "-"], check=True)
reports["beat_sync"] = beat_report
def tone_level(name, frequency):
    raw = subprocess.check_output(["ffmpeg", "-v", "error", "-ss", "2", "-i", str(root / (name + ".mp4")), "-t", "1", "-vn", "-ac", "1", "-ar", "8000", "-f", "f32le", "-"])
    samples = struct.unpack("<" + "f" * (len(raw) // 4), raw)
    real = sum(v * math.cos(2 * math.pi * frequency * i / 8000) for i, v in enumerate(samples))
    imag = sum(v * math.sin(2 * math.pi * frequency * i / 8000) for i, v in enumerate(samples))
    return math.hypot(real, imag) * 2 / len(samples)
mix = {name: {str(hz): tone_level(name, hz) for hz in (220, 440)} for name in ("mix-both-5s", "mix-music-only-5s")}
assert mix["mix-both-5s"]["440"] > .01, mix
assert mix["mix-music-only-5s"]["440"] < mix["mix-both-5s"]["440"] * .15, mix
assert all(levels["220"] > .01 for levels in mix.values()), mix
reports["audio_mix"] = mix
def frame(path, seconds):
    return subprocess.check_output(["ffmpeg", "-v", "error", "-ss", str(seconds), "-i", str(path), "-frames:v", "1", "-vf", "scale=64:114", "-f", "rawvideo", "-pix_fmt", "rgb24", "-"])

def difference(a, b):
    assert len(a) == len(b) and a
    return sum(abs(x - y) for x, y in zip(a, b)) / len(a) / 255

for name, duration, audio in [("motion-5s", 5, False), ("music-flow-15s", 15, True), ("video-30s", 30, True), ("video-60s", 60, True)]:
    path = root / f"{name}.mp4"
    info = json.loads(subprocess.check_output(["ffprobe", "-v", "error", "-show_streams", "-show_format", "-of", "json", str(path)]))
    video = next(s for s in info["streams"] if s["codec_type"] == "video")
    assert video["codec_name"] == "h264", info
    width, height = video["width"], video["height"]
    rotation = next((s.get("rotation", 0) for s in video.get("side_data_list", []) if "rotation" in s), 0)
    if abs(rotation) % 180 == 90:
        width, height = height, width
    assert (width, height) == (1080, 1920), (width, height, rotation)
    assert abs(float(info["format"]["duration"]) - duration) < 0.35, info
    fps = video["avg_frame_rate"].split("/")
    assert abs(int(fps[0]) / int(fps[1]) - 30) < 0.2, video
    if audio:
        a = next(s for s in info["streams"] if s["codec_type"] == "audio")
        assert a["codec_name"] == "aac", a
    # Decode all frames, catching files whose header is valid but payload is corrupt.
    subprocess.run(["ffmpeg", "-v", "error", "-xerror", "-i", str(path), "-f", "null", "-"], check=True)
    reports[name] = info
    first = frame(path, 0)
    assert first and max(first) - min(first) > 30, "Empty/flat video frame"
    if name in ("motion-5s", "music-flow-15s"):
        quarter = frame(path, duration / 4)
        end = frame(path, duration - 1 / 30)
        motion_delta = difference(first, quarter)
        loop_delta = difference(first, end)
        assert motion_delta > 0.00001, "Motion video contains identical frames"
        assert loop_delta < 0.04, f"Abrupt photo loop boundary: {loop_delta}"
        reports[name]["visual_checks"] = {"quarter_frame_delta": motion_delta, "loop_boundary_delta": loop_delta}
assert (root / "still-korean.jpg").is_file()
assert len(list(root.glob("style-*.png"))) == 15
quality = json.loads((root / "quality" / "styles.json").read_text())
assert len(quality) == 5 and all(len(values) == 15 and max(values) < .0008 for values in quality.values()), quality
reports["photo_quality_zero_identity"] = quality
video_pose = json.loads((root / "video-pose.json").read_text())
assert video_pose["normalizedPixelError"] < .035, video_pose
reports["video_pose"] = video_pose
for name in ("progress-dot-5s", "video-pose-5s"):
    path = root / (name + ".mp4")
    subprocess.run(["ffmpeg", "-v", "error", "-xerror", "-i", str(path), "-f", "null", "-"], check=True)
    info = json.loads(subprocess.check_output(["ffprobe", "-v", "error", "-show_format", "-of", "json", str(path)]))
    assert abs(float(info["format"]["duration"]) - 5) < .35, info
    reports[name] = info
required_screens = ["ux-photo-main", "editor-text-ime", "editor-text-styles", "ux-photo-styles", "ux-music-library", "ux-music-waveform", "ux-music-cards", "ux-png-library", "ux-photo-crop", "ux-preview", "ux-share-progress"]
for screen in required_screens:
    assert (root / (screen + ".png")).stat().st_size > 1000, "Missing actual UI capture: " + screen
# Reject the previously observed empty-photo capture, even if chrome/text made it non-flat.
main_pixels = frame(root / "ux-photo-main.png", 0)
colored = sum(max(main_pixels[i:i+3]) - min(main_pixels[i:i+3]) > 12 for i in range(0, len(main_pixels), 3))
assert colored / (len(main_pixels) / 3) > 0.15, "Main canvas capture is not the loaded fixture photo"
groups = {
    "app_unit": pathlib.Path("app/build/test-results/testDebugUnitTest").glob("TEST-*.xml"),
    "photoeditor_unit": pathlib.Path("photoeditor/build/test-results/testDebugUnitTest").glob("TEST-*.xml"),
    "android_integration": pathlib.Path("app/build/outputs/androidTest-results/connected/debug").glob("TEST-*.xml"),
}
test_counts = {}
for group, files in groups.items():
    suites = [ET.parse(p).getroot() for p in files]
    if suites:
        assert all(int(s.get("failures", 0)) == 0 and int(s.get("errors", 0)) == 0 for s in suites)
        test_counts[group] = sum(int(s.get("tests", 0)) for s in suites)
reports["_verification"] = {
    "commit": os.environ.get("GITHUB_SHA", "local"),
    "run_url": "https://github.com/vividhyeok/igstoryplus/actions/runs/" + os.environ.get("GITHUB_RUN_ID", "local"),
    "passed_tests": test_counts,
    "full_frame_decode": True,
}
(root / "ffprobe-report.json").write_text(json.dumps(reports, indent=2), encoding="utf-8")
print("Verified: JPEG, 10 GPU style images, H.264 1080×1920 30fps 5/15/30/60s; AAC and full frame decode.")
