#!/usr/bin/env python3
"""Download free-exercise-db demo frames and bundle them as small WebP assets.

Every exercise in app/src/main/assets/seed/exercises.json lists its source images
(usually a start and an end position). This script fetches each one from the
free-exercise-db repository (Unlicense / public domain), scales it to WIDTH pixels
wide and writes app/src/main/assets/exercises/<id>/<n>.webp. The app alternates the
frames to animate the movement. Re-running only fetches missing files.

Requires Pillow: pip install pillow
"""
import io
import json
import sys
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
SEED = ROOT / "app/src/main/assets/seed/exercises.json"
OUT = ROOT / "app/src/main/assets/exercises"
BASE = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/"
WIDTH = 480
QUALITY = 62


def target(image_path: str) -> Path:
    exercise_id, name = image_path.split("/", 1)
    return OUT / exercise_id / (Path(name).stem + ".webp")


def fetch(image_path: str) -> str:
    out = target(image_path)
    if out.exists():
        return "skip"
    for attempt in range(4):
        try:
            with urllib.request.urlopen(BASE + image_path, timeout=30) as response:
                data = response.read()
            break
        except Exception as error:  # noqa: BLE001 - retried, then reported
            if attempt == 3:
                return f"fail {image_path}: {error}"
    image = Image.open(io.BytesIO(data)).convert("RGB")
    if image.width > WIDTH:
        image = image.resize((WIDTH, round(image.height * WIDTH / image.width)), Image.LANCZOS)
    out.parent.mkdir(parents=True, exist_ok=True)
    image.save(out, "WEBP", quality=QUALITY, method=6)
    return "ok"


def main() -> int:
    exercises = json.loads(SEED.read_text())
    paths = [p for exercise in exercises for p in exercise.get("images", [])]
    with ThreadPoolExecutor(max_workers=16) as pool:
        results = list(pool.map(fetch, paths))
    failures = [r for r in results if r.startswith("fail")]
    missing = [e["id"] for e in exercises if not e.get("images")]
    size = sum(f.stat().st_size for f in OUT.rglob("*.webp"))
    print(f"{len(paths)} frames: {results.count('ok')} downloaded, {results.count('skip')} cached, "
          f"{len(failures)} failed; {len(missing)} exercises without images; {size / 1e6:.1f} MB")
    for failure in failures:
        print(failure)
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
