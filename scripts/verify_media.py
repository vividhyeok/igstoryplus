"""Inspect actual Android renderer outputs; fail CI before releasing a broken pipeline."""
import json
import os
import pathlib
import subprocess
import sys
import xml.etree.ElementTree as ET

root = pathlib.Path(sys.argv[1])
reports = {}
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
assert len(list(root.glob("style-*.png"))) == 7
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
print("Verified: JPEG, 7 GPU style images, H.264 1080×1920 30fps 5/15/30/60s; AAC and full frame decode.")
