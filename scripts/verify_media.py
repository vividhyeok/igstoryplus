"""Inspect actual Android renderer outputs; fail CI before releasing a broken pipeline."""
import json
import pathlib
import subprocess
import sys

root = pathlib.Path(sys.argv[1])
reports = {}
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
assert (root / "still-korean.jpg").is_file()
assert len(list(root.glob("style-*.png"))) == 7
(root / "ffprobe-report.json").write_text(json.dumps(reports, indent=2), encoding="utf-8")
print("Verified: JPEG, 7 GPU style images, H.264 1080×1920 30fps 5/15/30/60s; AAC and full frame decode.")
