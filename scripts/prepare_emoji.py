"""Normalize user-owned PNG copies without modifying source files or removing colors."""
import argparse
import hashlib
import json
from pathlib import Path
from PIL import Image

CATALOG = {
    "교회": ("church", "교회·신앙", "예배 건물"),
    "나무십자가": ("wooden-cross", "교회·신앙", "십자가 나무"),
    "성경책": ("bible", "교회·신앙", "성경 책"),
    "예수님": ("jesus", "교회·신앙", "예수 신앙"),
    "꽃": ("flowers", "여행·자연", "꽃다발 선물 자연"),
    "여행": ("travel", "여행·자연", "여권 비행기 여행"),
    "라멘": ("ramen", "음식·카페", "라면 면 요리 음식"),
    "커피": ("coffee", "음식·카페", "아이스 커피 카페 음료"),
    "독서": ("reading", "독서", "책 안경 읽기"),
    "탈의실": ("fitting-room", "패션·빈티지", "옷 피팅룸 쇼핑"),
}

def prepare(source, destination, report):
    destination.mkdir(parents=True, exist_ok=True)
    audit, catalog, seen = [], [], {}
    for path in sorted(source.rglob("*")):
        if not path.is_file():
            continue
        record = {"source": path.relative_to(source).as_posix(), "bytes": path.stat().st_size,
                  "sourceSha256": hashlib.sha256(path.read_bytes()).hexdigest()}
        audit.append(record)
        if path.suffix.lower() != ".png" or path.stem not in CATALOG:
            record["excluded"] = "Not a reviewed catalog PNG"
            continue
        with Image.open(path) as original:
            original.load()
            image = original.convert("RGBA")
            alpha = image.getchannel("A")
            record.update(size=list(image.size), alpha=list(alpha.getextrema()))
            digest = hashlib.sha256(image.tobytes()).hexdigest()
            if digest in seen:
                record["duplicateOf"] = seen[digest]
                continue
            seen[digest] = record["source"]
            bounds = alpha.point(lambda value: 255 if value > 2 else 0).getbbox()
            if not bounds or alpha.getextrema()[0] == 255:
                record["excluded"] = "Needs manual transparency review"
                continue
            trimmed = image.crop(bounds)
            trimmed.thumbnail((448, 448), Image.Resampling.LANCZOS)
            normalized = Image.new("RGBA", (512, 512))
            normalized.alpha_composite(trimmed, ((512-trimmed.width)//2, (512-trimmed.height)//2))
            stable_id, category, keywords = CATALOG[path.stem]
            target = destination / (stable_id + ".png")
            normalized.save(target, optimize=True)
            record.update(id=stable_id, visibleBounds=list(bounds), outputSha256=hashlib.sha256(target.read_bytes()).hexdigest(), outputBytes=target.stat().st_size)
            catalog.append({"id": stable_id, "name": path.stem, "category": category, "keywords": keywords,
                            "uri": "file:///android_asset/emoji/" + target.name})
    (destination / "index.json").write_text(json.dumps(catalog, ensure_ascii=False, indent=2), encoding="utf-8")
    report.parent.mkdir(parents=True, exist_ok=True)
    report.write_text(json.dumps(audit, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Inspected {len(audit)}, integrated {len(catalog)}; sources preserved")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("--destination", type=Path, default=Path("app/src/main/assets/emoji"))
    parser.add_argument("--report", type=Path, default=Path("docs/emoji-audit.json"))
    args = parser.parse_args()
    prepare(args.source, args.destination, args.report)
